package com.b2b.instantneed.order.service;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** Allocates invoice numbers from a database-backed financial-year sequence. */
@Service
@RequiredArgsConstructor
public class InvoiceNumberService {

    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");

    private final EntityManager entityManager;

    @Transactional
    public String next(Instant issuedAt) {
        ZonedDateTime date = issuedAt.atZone(INDIA);
        int calendarYear = date.getYear();
        int month = date.getMonthValue();
        int financialYearStart = month >= 4 ? calendarYear : calendarYear - 1;
        String financialYear = String.format("%02d-%02d", financialYearStart % 100,
                (financialYearStart + 1) % 100);

        Number sequence = (Number) entityManager.createNativeQuery("""
                INSERT INTO invoice_sequences (financial_year, last_sequence)
                VALUES (:financialYear, 1)
                ON CONFLICT (financial_year) DO UPDATE
                SET last_sequence = invoice_sequences.last_sequence + 1
                RETURNING last_sequence
                """)
                .setParameter("financialYear", financialYear)
                .getSingleResult();

        int value = sequence.intValue();
        if (value > 9999) {
            throw new IllegalStateException("Invoice sequence exceeded 9999 for financial year " + financialYear);
        }
        return String.format("INV-%04d-%02d-%04d", calendarYear, month, value);
    }
}
