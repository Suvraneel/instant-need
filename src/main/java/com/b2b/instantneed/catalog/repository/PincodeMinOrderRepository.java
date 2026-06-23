package com.b2b.instantneed.catalog.repository;

import com.b2b.instantneed.catalog.entity.PincodeMinOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PincodeMinOrderRepository extends JpaRepository<PincodeMinOrder, UUID> {
    Optional<PincodeMinOrder> findByPincodeAndActiveTrue(String pincode);
    boolean existsByPincode(String pincode);
    boolean existsByPincodeAndIdNot(String pincode, UUID id);
}
