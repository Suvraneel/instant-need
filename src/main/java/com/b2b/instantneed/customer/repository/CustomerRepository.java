package com.b2b.instantneed.customer.repository;

import com.b2b.instantneed.customer.entity.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    Optional<Customer> findByUserId(UUID userId);

    long countByCreatedAtGreaterThanEqual(Instant from);

    @Query("SELECT c FROM Customer c JOIN c.user u WHERE " +
           "LOWER(c.fullName) LIKE :q OR LOWER(u.email) LIKE :q OR LOWER(c.businessName) LIKE :q")
    Page<Customer> searchByNameOrEmail(@Param("q") String q, Pageable pageable);
}
