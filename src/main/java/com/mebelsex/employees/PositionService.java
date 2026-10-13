package com.mebelsex.employees;

import com.mebelsex.common.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/** Positions (lavozimlar). Only deactivated, never deleted; names are unique (case-insensitive). */
@Service
@Transactional(readOnly = true)
public class PositionService {

    private final PositionRepository positionRepository;
    private final EmployeeRepository employeeRepository;

    public PositionService(PositionRepository positionRepository, EmployeeRepository employeeRepository) {
        this.positionRepository = positionRepository;
        this.employeeRepository = employeeRepository;
    }

    @Transactional
    public PositionRow create(String name) {
        String clean = requireName(name);
        ensureUnique(clean, null);
        return toRow(positionRepository.save(new Position(clean)));
    }

    @Transactional
    public PositionRow rename(Long id, String name) {
        String clean = requireName(name);
        Position position = positionRepository.findById(id)
                .orElseThrow(() -> new BusinessException("position.error.notfound"));
        ensureUnique(clean, id);
        position.setName(clean);
        return toRow(positionRepository.save(position));
    }

    @Transactional
    public void setActive(Long id, boolean active) {
        Position position = positionRepository.findById(id)
                .orElseThrow(() -> new BusinessException("position.error.notfound"));
        position.setActive(active);
        positionRepository.save(position);
    }

    public List<PositionRow> list(boolean includeInactive) {
        return positionRepository.findAllByOrderByNameAsc().stream()
                .filter(p -> includeInactive || p.isActive())
                .map(this::toRow)
                .collect(Collectors.toList());
    }

    public List<PositionRow> listActive() {
        return list(false);
    }

    private String requireName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new BusinessException("position.error.name_required");
        }
        return name.trim();
    }

    private void ensureUnique(String name, Long selfId) {
        positionRepository.findByNameIgnoreCase(name)
                .filter(existing -> !existing.getId().equals(selfId))
                .ifPresent(existing -> {
                    throw new BusinessException("position.error.duplicate");
                });
    }

    private PositionRow toRow(Position position) {
        return new PositionRow(position.getId(), position.getName(), position.isActive(),
                employeeRepository.countByPositionId(position.getId()));
    }
}
