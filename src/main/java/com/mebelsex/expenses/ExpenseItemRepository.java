package com.mebelsex.expenses;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExpenseItemRepository extends JpaRepository<ExpenseItem, Long> {

    List<ExpenseItem> findByCategoryIdOrderByNameAsc(Long categoryId);

    Optional<ExpenseItem> findByCategoryIdAndNameIgnoreCase(Long categoryId, String name);

    long countByCategoryId(Long categoryId);
}
