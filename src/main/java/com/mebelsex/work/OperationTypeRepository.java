package com.mebelsex.work;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OperationTypeRepository extends JpaRepository<OperationType, Long> {

    Optional<OperationType> findByNameIgnoreCase(String name);

    List<OperationType> findAllByOrderByNameAsc();
}
