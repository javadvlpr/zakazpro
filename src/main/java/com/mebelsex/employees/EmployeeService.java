package com.mebelsex.employees;

import com.mebelsex.common.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/** Workers (ishchilar). Deactivate instead of delete; opening_balance may be negative. */
@Service
@Transactional(readOnly = true)
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final PositionRepository positionRepository;

    public EmployeeService(EmployeeRepository employeeRepository, PositionRepository positionRepository) {
        this.employeeRepository = employeeRepository;
        this.positionRepository = positionRepository;
    }

    @Transactional
    public EmployeeRow create(String fullName, String phone, Long positionIdOrNull, BigDecimal openingBalance) {
        String clean = requireName(fullName);
        Employee employee = new Employee();
        employee.setFullName(clean);
        employee.setPhone(normalizeOptional(phone));
        employee.setPosition(resolvePosition(positionIdOrNull));
        employee.setOpeningBalance(normalizeBalance(openingBalance));
        return toRow(employeeRepository.save(employee));
    }

    @Transactional
    public EmployeeRow update(Long id, String fullName, String phone, Long positionIdOrNull,
            BigDecimal openingBalance, boolean active) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new BusinessException("employee.error.notfound"));
        employee.setFullName(requireName(fullName));
        employee.setPhone(normalizeOptional(phone));
        employee.setPosition(resolvePosition(positionIdOrNull));
        employee.setOpeningBalance(normalizeBalance(openingBalance));
        employee.setActive(active);
        return toRow(employeeRepository.save(employee));
    }

    @Transactional
    public void setActive(Long id, boolean active) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new BusinessException("employee.error.notfound"));
        employee.setActive(active);
        employeeRepository.save(employee);
    }

    public EmployeeRow findForEdit(Long id) {
        return toRow(employeeRepository.findById(id)
                .orElseThrow(() -> new BusinessException("employee.error.notfound")));
    }

    public List<EmployeeRow> list(String search, boolean includeInactive) {
        String query = search == null ? "" : search.trim().toLowerCase();
        return employeeRepository.findAllByOrderByFullNameAsc().stream()
                .filter(e -> includeInactive || e.isActive())
                .filter(e -> query.isEmpty() || e.getFullName().toLowerCase().contains(query))
                .map(this::toRow)
                .collect(Collectors.toList());
    }

    public List<EmployeeRow> listActive() {
        return list(null, false);
    }

    private String requireName(String fullName) {
        if (fullName == null || fullName.trim().isEmpty()) {
            throw new BusinessException("employee.error.name_required");
        }
        return fullName.trim();
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String clean = value.trim();
        return clean.isEmpty() ? null : clean;
    }

    private BigDecimal normalizeBalance(BigDecimal openingBalance) {
        return openingBalance == null ? BigDecimal.ZERO : openingBalance;
    }

    private Position resolvePosition(Long positionIdOrNull) {
        if (positionIdOrNull == null) {
            return null;
        }
        return positionRepository.findById(positionIdOrNull)
                .orElseThrow(() -> new BusinessException("position.error.notfound"));
    }

    private EmployeeRow toRow(Employee employee) {
        Position position = employee.getPosition();
        return new EmployeeRow(
                employee.getId(),
                employee.getFullName(),
                employee.getPhone(),
                position == null ? null : position.getId(),
                position == null ? null : position.getName(),
                employee.isActive(),
                employee.getOpeningBalance());
    }
}
