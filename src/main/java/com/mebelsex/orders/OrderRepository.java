package com.mebelsex.orders;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
        boolean existsByCustomerId(Long customerId);

        @Query("SELECT MAX(CAST(o.orderNumber AS integer)) FROM Order o")
        Integer findMaxOrderNumber();

        @Query("SELECT o FROM Order o WHERE " +
                        "(:status IS NULL OR o.status = :status) AND " +
                        "(:customerId IS NULL OR o.customer.id = :customerId) AND " +
                        "(:query IS NULL OR o.orderNumber LIKE CONCAT('%', :query, '%') OR " +
                        "  LOWER(o.productDescription) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
                        "  LOWER(o.customer.fullName) LIKE LOWER(CONCAT('%', :query, '%'))) AND " +
                        "(cast(:startDate as date) IS NULL OR o.dueDate >= :startDate) AND " +
                        "(cast(:endDate as date) IS NULL OR o.dueDate <= :endDate) " +
                        "ORDER BY o.dueDate ASC, CAST(o.orderNumber as integer) ASC")
        List<Order> searchOrders(@Param("status") OrderStatus status,
                        @Param("customerId") Long customerId,
                        @Param("query") String query,
                        @Param("startDate") LocalDate startDate,
                        @Param("endDate") LocalDate endDate);
}
