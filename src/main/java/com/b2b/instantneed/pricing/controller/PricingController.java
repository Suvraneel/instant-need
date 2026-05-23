package com.b2b.instantneed.pricing.controller;

import com.b2b.instantneed.pricing.dto.PriceCalculateRequest;
import com.b2b.instantneed.pricing.dto.PriceCalculateResponse;
import com.b2b.instantneed.pricing.service.PricingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Pricing", description = "Server-authoritative tier-based price calculation")
@RestController
@RequestMapping("/api/v1/pricing")
@RequiredArgsConstructor
public class PricingController {

    private final PricingService pricingService;

    @Operation(summary = "Calculate unit price and line total for a product + quantity")
    @PostMapping("/calculate")
    public ResponseEntity<PriceCalculateResponse> calculate(
            @Valid @RequestBody PriceCalculateRequest request) {
        return ResponseEntity.ok(pricingService.calculate(request));
    }
}
