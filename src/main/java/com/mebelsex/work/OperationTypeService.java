package com.mebelsex.work;

import com.mebelsex.common.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/** Operation types / work kinds (ish turlari). Deactivate instead of delete; unique names. */
@Service
@Transactional(readOnly = true)
public class OperationTypeService {

    private final OperationTypeRepository operationTypeRepository;

    public OperationTypeService(OperationTypeRepository operationTypeRepository) {
        this.operationTypeRepository = operationTypeRepository;
    }

    @Transactional
    public OperationTypeRow create(String name, String unit, BigDecimal defaultPrice) {
        String clean = requireName(name);
        ensureUnique(clean, null);
        OperationType type = new OperationType();
        type.setName(clean);
        type.setUnit(normalizeUnit(unit));
        type.setDefaultPrice(normalizePrice(defaultPrice));
        return toRow(operationTypeRepository.save(type));
    }

    @Transactional
    public OperationTypeRow update(Long id, String name, String unit, BigDecimal defaultPrice) {
        OperationType type = operationTypeRepository.findById(id)
                .orElseThrow(() -> new BusinessException("operation.error.notfound"));
        String clean = requireName(name);
        ensureUnique(clean, id);
        type.setName(clean);
        type.setUnit(normalizeUnit(unit));
        type.setDefaultPrice(normalizePrice(defaultPrice));
        return toRow(operationTypeRepository.save(type));
    }

    @Transactional
    public void setActive(Long id, boolean active) {
        OperationType type = operationTypeRepository.findById(id)
                .orElseThrow(() -> new BusinessException("operation.error.notfound"));
        type.setActive(active);
        operationTypeRepository.save(type);
    }

    public OperationTypeRow findById(Long id) {
        return toRow(operationTypeRepository.findById(id)
                .orElseThrow(() -> new BusinessException("operation.error.notfound")));
    }

    public List<OperationTypeRow> list(String search, boolean includeInactive) {
        String query = search == null ? "" : search.trim().toLowerCase();
        return operationTypeRepository.findAllByOrderByNameAsc().stream()
                .filter(t -> includeInactive || t.isActive())
                .filter(t -> query.isEmpty() || t.getName().toLowerCase().contains(query))
                .map(this::toRow)
                .collect(Collectors.toList());
    }

    public List<OperationTypeRow> listActive() {
        return list(null, false);
    }

    private String requireName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new BusinessException("operation.error.name_required");
        }
        return name.trim();
    }

    private void ensureUnique(String name, Long selfId) {
        operationTypeRepository.findByNameIgnoreCase(name)
                .filter(existing -> !existing.getId().equals(selfId))
                .ifPresent(existing -> {
                    throw new BusinessException("operation.error.duplicate");
                });
    }

    private BigDecimal normalizePrice(BigDecimal defaultPrice) {
        if (defaultPrice == null) {
            return BigDecimal.ZERO;
        }
        if (defaultPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("operation.error.price_invalid");
        }
        return defaultPrice;
    }

    private String normalizeUnit(String unit) {
        if (unit == null) {
            return null;
        }
        String clean = unit.trim();
        return clean.isEmpty() ? null : clean;
    }

    private OperationTypeRow toRow(OperationType type) {
        return new OperationTypeRow(type.getId(), type.getName(), type.getUnit(), type.getDefaultPrice(),
                type.isActive());
    }
}
