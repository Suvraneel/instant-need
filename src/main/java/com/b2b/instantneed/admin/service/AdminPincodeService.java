package com.b2b.instantneed.admin.service;

import com.b2b.instantneed.admin.dto.PincodeMinOrderRequest;
import com.b2b.instantneed.admin.dto.PincodeMinOrderResponse;
import com.b2b.instantneed.catalog.entity.PincodeMinOrder;
import com.b2b.instantneed.catalog.repository.PincodeMinOrderRepository;
import com.b2b.instantneed.common.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminPincodeService {

    private final PincodeMinOrderRepository repository;

    @Transactional(readOnly = true)
    public List<PincodeMinOrderResponse> listAll() {
        return repository.findAll().stream()
                .map(PincodeMinOrderResponse::from)
                .toList();
    }

    @Transactional
    public PincodeMinOrderResponse create(PincodeMinOrderRequest request) {
        if (repository.existsByPincode(request.pincode())) {
            throw ApiException.badRequest("PINCODE_EXISTS",
                    "A rule for pincode " + request.pincode() + " already exists");
        }
        PincodeMinOrder entity = PincodeMinOrder.builder()
                .pincode(request.pincode())
                .minAmount(request.minAmount())
                .active(request.active() == null || request.active())
                .build();
        return PincodeMinOrderResponse.from(repository.save(entity));
    }

    @Transactional
    public PincodeMinOrderResponse update(UUID id, PincodeMinOrderRequest request) {
        PincodeMinOrder entity = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("PINCODE_RULE_NOT_FOUND",
                        "Pincode rule not found: " + id));
        if (repository.existsByPincodeAndIdNot(request.pincode(), id)) {
            throw ApiException.badRequest("PINCODE_EXISTS",
                    "A rule for pincode " + request.pincode() + " already exists");
        }
        entity.setPincode(request.pincode());
        entity.setMinAmount(request.minAmount());
        if (request.active() != null) {
            entity.setActive(request.active());
        }
        return PincodeMinOrderResponse.from(repository.save(entity));
    }

    @Transactional
    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw ApiException.notFound("PINCODE_RULE_NOT_FOUND", "Pincode rule not found: " + id);
        }
        repository.deleteById(id);
    }
}
