package com.b2b.instantneed.common.config;

import com.b2b.instantneed.catalog.repository.CategoryRepository;
import com.b2b.instantneed.catalog.repository.PricingTierRepository;
import com.b2b.instantneed.catalog.repository.ProductImageRepository;
import com.b2b.instantneed.catalog.repository.ProductRepository;
import com.b2b.instantneed.customer.repository.AddressRepository;
import com.b2b.instantneed.customer.repository.CustomerRepository;
import com.b2b.instantneed.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class DataSeederTest {

    @Mock UserRepository         userRepository;
    @Mock CustomerRepository     customerRepository;
    @Mock AddressRepository      addressRepository;
    @Mock CategoryRepository     categoryRepository;
    @Mock ProductRepository      productRepository;
    @Mock PricingTierRepository  pricingTierRepository;
    @Mock ProductImageRepository productImageRepository;
    @Mock PasswordEncoder        passwordEncoder;
    @Mock ApplicationArguments   appArgs;

    @InjectMocks DataSeeder seeder;

    @Test
    void whenDisabled_runsNothing() throws Exception {
        ReflectionTestUtils.setField(seeder, "enabled", false);

        seeder.run(appArgs);

        verify(userRepository, never()).existsByEmail(any());
        verify(productRepository, never()).existsBySku(any());
    }

    @Test
    void whenEnabled_andDataAlreadyExists_skipsAllInserts() throws Exception {
        ReflectionTestUtils.setField(seeder, "enabled", true);

        // Everything "already exists"
        given(userRepository.existsByEmail(anyString())).willReturn(true);
        given(productRepository.existsBySku(anyString())).willReturn(true);

        // Categories are looked up by slug
        given(categoryRepository.findBySlug("office-supplies"))
                .willReturn(Optional.of(stubCategory("office-supplies")));
        given(categoryRepository.findBySlug("paper-notebooks"))
                .willReturn(Optional.of(stubCategory("paper-notebooks")));

        seeder.run(appArgs);

        // Admin and customer users skipped
        verify(userRepository, never()).save(any());
        // Products skipped
        verify(productRepository, never()).save(any());
    }

    @Test
    void whenEnabled_andNoData_createsAllObjects() throws Exception {
        ReflectionTestUtils.setField(seeder, "enabled", true);

        given(userRepository.existsByEmail(anyString())).willReturn(false);
        given(passwordEncoder.encode(anyString())).willReturn("hashed");

        var savedUser = com.b2b.instantneed.user.entity.User.builder()
                .id(java.util.UUID.randomUUID())
                .email("admin@instantneed.com")
                .passwordHash("hashed")
                .authProvider(com.b2b.instantneed.user.entity.AuthProvider.LOCAL)
                .role(com.b2b.instantneed.user.entity.Role.ADMIN)
                .active(true).build();
        given(userRepository.save(any())).willReturn(savedUser);

        var savedCustomer = com.b2b.instantneed.customer.entity.Customer.builder()
                .id(java.util.UUID.randomUUID())
                .user(savedUser).fullName("Raj Sharma").build();
        given(customerRepository.save(any())).willReturn(savedCustomer);

        var savedAddress = com.b2b.instantneed.customer.entity.Address.builder()
                .id(java.util.UUID.randomUUID())
                .customer(savedCustomer).label("Head Office")
                .line1("12 MG Road").city("Mumbai")
                .state("Maharashtra").country("India").postalCode("400001")
                .isDefault(true).build();
        given(addressRepository.save(any())).willReturn(savedAddress);

        // Categories don't exist yet → create them
        given(categoryRepository.findBySlug("office-supplies")).willReturn(Optional.empty());
        given(categoryRepository.findBySlug("paper-notebooks")).willReturn(Optional.empty());
        var parentCat = stubCategory("office-supplies");
        var childCat  = stubCategory("paper-notebooks");
        given(categoryRepository.save(any()))
                .willReturn(parentCat)
                .willReturn(childCat);

        given(productRepository.existsBySku(anyString())).willReturn(false);
        var savedProduct = com.b2b.instantneed.catalog.entity.Product.builder()
                .id(java.util.UUID.randomUUID()).name("A4 Paper").sku("PAPER-A4-500")
                .slug("a4-copy-paper-500-sheets")
                .availabilityStatus(com.b2b.instantneed.catalog.entity.AvailabilityStatus.IN_STOCK)
                .basePrice(new java.math.BigDecimal("250.00")).active(true).build();
        given(productRepository.save(any())).willReturn(savedProduct);

        seeder.run(appArgs);

        // Both users, categories, and products should have been saved
        verify(userRepository, atLeast(2)).save(any());
        verify(productRepository, atLeast(2)).save(any()); // 2 products
        verify(pricingTierRepository, atLeast(2)).saveAll(any());
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private com.b2b.instantneed.catalog.entity.Category stubCategory(String slug) {
        return com.b2b.instantneed.catalog.entity.Category.builder()
                .id(java.util.UUID.randomUUID())
                .name(slug).slug(slug).active(true).build();
    }
}
