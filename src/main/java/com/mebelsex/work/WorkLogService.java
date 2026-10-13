package com.mebelsex.work;

import com.mebelsex.common.BusinessException;
import com.mebelsex.common.RecordStatus;
import com.mebelsex.employees.Employee;
import com.mebelsex.employees.EmployeeRepository;
import com.mebelsex.orders.Order;
import com.mebelsex.orders.OrderRepository;
import com.mebelsex.orders.OrderStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Work logs (bajarilgan ishlar). unit_price and amount are frozen at creation
 * (amount = quantity x unit_price, scale 0 HALF_UP). Nothing is deleted: VOIDED + reason.
 */
@Service
@Transactional(readOnly = true)
public class WorkLogService {

    private final WorkLogRepository workLogRepository;
    private final OrderRepository orderRepository;
    private final EmployeeRepository employeeRepository;
    private final OperationTypeRepository operationTypeRepository;

    public WorkLogService(WorkLogRepository workLogRepository, OrderRepository orderRepository,
            EmployeeRepository employeeRepository, OperationTypeRepository operationTypeRepository) {
        this.workLogRepository = workLogRepository;
        this.orderRepository = orderRepository;
        this.employeeRepository = employeeRepository;
        this.operationTypeRepository = operationTypeRepository;
    }

    @Transactional
    public WorkLogRow addWorkLog(Long orderId, Long employeeId, Long operationTypeId, LocalDate date,
            BigDecimal quantity, BigDecimal unitPrice, String comment) {

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException("order.error.notfound"));
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new BusinessException("work.error.order_cancelled");
        }

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new BusinessException("employee.error.notfound"));
        if (!employee.isActive()) {
            throw new BusinessException("work.error.employee_inactive");
        }

        OperationType operationType = operationTypeRepository.findById(operationTypeId)
                .orElseThrow(() -> new BusinessException("operation.error.notfound"));
        if (!operationType.isActive()) {
            throw new BusinessException("work.error.operation_inactive");
        }

        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("work.error.quantity_invalid");
        }
        if (unitPrice == null || unitPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("work.error.price_invalid");
        }
        if (date == null) {
            throw new BusinessException("work.error.date_required");
        }

        WorkLog workLog = new WorkLog();
        workLog.setOrder(order);
        workLog.setEmployee(employee);
        workLog.setOperationType(operationType);
        workLog.setWorkDate(date);
        workLog.setQuantity(quantity);
        workLog.setUnitPrice(unitPrice);
        workLog.setAmount(quantity.multiply(unitPrice).setScale(0, RoundingMode.HALF_UP));
        workLog.setComment(comment == null ? null : comment.trim());

        return toRow(workLogRepository.save(workLog));
    }

    @Transactional
    public void voidWorkLog(Long id, String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new BusinessException("work.error.void_reason_required");
        }
        WorkLog workLog = workLogRepository.findById(id)
                .orElseThrow(() -> new BusinessException("work.error.notfound"));
        if (workLog.getStatus() == RecordStatus.VOIDED) {
            throw new BusinessException("work.error.already_voided");
        }
        workLog.setStatus(RecordStatus.VOIDED);
        workLog.setVoidReason(reason.trim());
        workLogRepository.save(workLog);
    }

    /** Newest first (by work date, then id); VOIDED rows are included. */
    public List<WorkLogRow> listForOrder(Long orderId) {
        return workLogRepository.findByOrderId(orderId).stream()
                .sorted(newestFirst())
                .map(this::toRow)
                .collect(Collectors.toList());
    }

    /** Newest first; optional inclusive date range; VOIDED rows are included. */
    public List<WorkLogRow> listForEmployee(Long employeeId, LocalDate from, LocalDate to) {
        return workLogRepository.findByEmployeeId(employeeId).stream()
                .filter(w -> from == null || !w.getWorkDate().isBefore(from))
                .filter(w -> to == null || !w.getWorkDate().isAfter(to))
                .sorted(newestFirst())
                .map(this::toRow)
                .collect(Collectors.toList());
    }

    private Comparator<WorkLog> newestFirst() {
        return Comparator
                .comparing(WorkLog::getWorkDate, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(WorkLog::getId, Comparator.reverseOrder());
    }

    private WorkLogRow toRow(WorkLog workLog) {
        return new WorkLogRow(
                workLog.getId(),
                workLog.getWorkDate(),
                workLog.getOrder().getId(),
                workLog.getOrder().getOrderNumber(),
                workLog.getEmployee().getId(),
                workLog.getEmployee().getFullName(),
                workLog.getOperationType().getName(),
                workLog.getOperationType().getUnit(),
                workLog.getQuantity(),
                workLog.getUnitPrice(),
                workLog.getAmount(),
                workLog.getComment(),
                workLog.getStatus(),
                workLog.getVoidReason());
    }
}
