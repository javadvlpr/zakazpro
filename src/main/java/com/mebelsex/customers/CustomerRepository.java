package com.mebelsex.customers;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    @Query("SELECT c FROM Customer c WHERE " +
            "LOWER(c.fullName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "(c.phone IS NOT NULL AND c.phone LIKE CONCAT('%', :query, '%'))")
    List<Customer> searchByNameOrPhone(@Param("query") String query);
}
