package com.b2b.instantneed.catalog.controller;

import com.b2b.instantneed.catalog.entity.PincodeMinOrder;
import com.b2b.instantneed.catalog.repository.PincodeMinOrderRepository;
import com.b2b.instantneed.catalog.service.CatalogService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PincodeContractTest {
    @Test
    void unknownPincodeReturns204WithoutBody() throws Exception {
        PincodeMinOrderRepository repository = mock(PincodeMinOrderRepository.class);
        when(repository.findByPincodeAndActiveTrue("411999")).thenReturn(Optional.empty());
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new CatalogController(mock(CatalogService.class), repository)).build();
        mvc.perform(get("/api/v1/catalog/pincode-min-order").param("pincode", "411999"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @Test
    void configuredPincodeReturnsAmount() throws Exception {
        PincodeMinOrderRepository repository = mock(PincodeMinOrderRepository.class);
        when(repository.findByPincodeAndActiveTrue("411001"))
                .thenReturn(Optional.of(PincodeMinOrder.builder().pincode("411001")
                        .minAmount(new BigDecimal("500.00")).active(true).build()));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new CatalogController(mock(CatalogService.class), repository)).build();
        mvc.perform(get("/api/v1/catalog/pincode-min-order").param("pincode", "411001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.minAmount").value(500));
    }
}
