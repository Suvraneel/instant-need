package com.b2b.instantneed.order.service;

import com.b2b.instantneed.order.repository.OrderRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class OrderNumberService {
    private final EntityManager entityManager;
    private final OrderRepository orderRepository;

    /** Called inside the order transaction; the lock lasts until commit. */
    public String next() {
        String prefix = "WB-" + LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.BASIC_ISO_DATE) + "-";
        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(hashtext(:prefix))")
                .setParameter("prefix", prefix)
                .getSingleResult();
        int next = orderRepository.findMaxSequenceForPrefix(prefix) + 1;
        return prefix + String.format("%04d", next);
    }
}
