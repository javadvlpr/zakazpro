package com.mebelsex.employees;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PositionRepository extends JpaRepository<Position, Long> {

    Optional<Position> findByNameIgnoreCase(String name);

    List<Position> findAllByOrderByNameAsc();
}
