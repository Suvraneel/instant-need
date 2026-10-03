package com.b2b.instantneed.common.config;

import com.b2b.instantneed.common.security.JwtAuthFilter;
import com.b2b.instantneed.common.security.JwtUtil;
import com.b2b.instantneed.common.security.TokenBlacklistService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = SecurityConfigTest.ProbeController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = com.b2b.instantneed.common.security.RateLimitFilter.class))
@Import({SecurityConfig.class, JwtAuthFilter.class, SecurityConfigTest.ProbeController.class})
class SecurityConfigTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean UserDetailsService userDetailsService;
    @MockitoBean JwtUtil jwtUtil;
    @MockitoBean TokenBlacklistService tokenBlacklistService;

    @Test
    void catalogIsPublic() throws Exception {
        mockMvc.perform(get("/api/v1/products/security-probe"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/catalog/pincode-min-order"))
                .andExpect(status().isOk());
    }

    @Test
    void privateApiRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/admin/security-probe"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void customerCannotUseAdminApiOrReadStaticInvoice() throws Exception {
        mockMvc.perform(get("/api/v1/admin/security-probe"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/uploads/invoices/InstantNeed-INV-2026-10-0001.pdf"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanUseAdminApiButCannotReadStaticInvoice() throws Exception {
        mockMvc.perform(get("/api/v1/admin/security-probe"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/uploads/invoices/InstantNeed-INV-2026-10-0001.pdf"))
                .andExpect(status().isForbidden());
    }

    @RestController
    static class ProbeController {
        @GetMapping("/api/v1/products/security-probe")
        String catalog() { return "ok"; }

        @GetMapping("/api/v1/catalog/pincode-min-order")
        String pincode() { return "ok"; }

        @PreAuthorize("hasRole('ADMIN')")
        @GetMapping("/api/v1/admin/security-probe")
        String admin() { return "ok"; }
    }
}
