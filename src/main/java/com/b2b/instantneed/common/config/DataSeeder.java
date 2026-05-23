package com.b2b.instantneed.common.config;

import com.b2b.instantneed.catalog.entity.*;
import com.b2b.instantneed.catalog.repository.*;
import com.b2b.instantneed.customer.entity.Address;
import com.b2b.instantneed.customer.entity.Customer;
import com.b2b.instantneed.customer.repository.AddressRepository;
import com.b2b.instantneed.customer.repository.CustomerRepository;
import com.b2b.instantneed.user.entity.AuthProvider;
import com.b2b.instantneed.user.entity.Role;
import com.b2b.instantneed.user.entity.User;
import com.b2b.instantneed.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Seeds one representative object of every domain type for local development and manual testing.
 *
 * Controlled by {@code app.seed-data.enabled} (default: false).
 * Safe to run repeatedly — every block is guarded by an existence check.
 *
 * Credentials created:
 *   Admin     — admin@instantneed.com  / Admin@123
 *   Customer  — buyer@test.com         / Buyer@123
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements ApplicationRunner {

    // ── Dependencies ────────────────────────────────────────────────────────────
    private final UserRepository        userRepository;
    private final CustomerRepository    customerRepository;
    private final AddressRepository     addressRepository;
    private final CategoryRepository    categoryRepository;
    private final ProductRepository     productRepository;
    private final PricingTierRepository pricingTierRepository;
    private final ProductImageRepository productImageRepository;
    private final PasswordEncoder       passwordEncoder;

    @Value("${app.seed-data.enabled:false}")
    private boolean enabled;

    // ── Entry point ─────────────────────────────────────────────────────────────
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.debug("DataSeeder is disabled (app.seed-data.enabled=false)");
            return;
        }
        log.info("DataSeeder starting — seeding reference objects for every domain type...");

        seedAdmin();
        seedCustomer();
        Category officeSupplies = seedParentCategory();
        Category paperCategory  = seedChildCategory(officeSupplies);
        seedPaperProduct(paperCategory);
        seedCleaningProduct(officeSupplies);

        log.info("DataSeeder complete.");
    }

    // ── 1. Admin user ────────────────────────────────────────────────────────────
    private void seedAdmin() {
        String email = "admin@instantneed.com";
        if (userRepository.existsByEmail(email)) {
            log.info("  [SKIP] Admin user already exists: {}", email);
            return;
        }

        User admin = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode("Admin@123"))
                .authProvider(AuthProvider.LOCAL)
                .role(Role.ADMIN)
                .active(true)
                .build();
        userRepository.save(admin);
        log.info("  [OK] Admin created: {} / Admin@123", email);
    }

    // ── 2. Customer user + profile + address ─────────────────────────────────────
    private void seedCustomer() {
        String email = "buyer@test.com";
        if (userRepository.existsByEmail(email)) {
            log.info("  [SKIP] Customer user already exists: {}", email);
            return;
        }

        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode("Buyer@123"))
                .authProvider(AuthProvider.LOCAL)
                .role(Role.CUSTOMER)
                .active(true)
                .build();
        user = userRepository.save(user);

        Customer customer = Customer.builder()
                .user(user)
                .fullName("Raj Sharma")
                .businessName("Sharma Traders Pvt Ltd")
                .gstVatNumber("27AAPFU0939F1ZV")
                .notes("Preferred delivery: morning slot")
                .build();
        customer = customerRepository.save(customer);

        Address address = Address.builder()
                .customer(customer)
                .label("Head Office")
                .line1("12 MG Road, Fort")
                .line2("Near Churchgate Station")
                .city("Mumbai")
                .state("Maharashtra")
                .country("India")
                .postalCode("400001")
                .isDefault(true)
                .build();
        address = addressRepository.save(address);

        customer.setDefaultShippingAddressId(address.getId());
        customerRepository.save(customer);

        log.info("  [OK] Customer created: {} / Buyer@123  (customerId={})", email, customer.getId());
    }

    // ── 3. Parent category ────────────────────────────────────────────────────────
    private Category seedParentCategory() {
        String slug = "office-supplies";
        return categoryRepository.findBySlug(slug).orElseGet(() -> {
            Category cat = Category.builder()
                    .name("Office Supplies")
                    .slug(slug)
                    .sortOrder(1)
                    .active(true)
                    .build();
            cat = categoryRepository.save(cat);
            log.info("  [OK] Parent category created: {} (id={})", cat.getName(), cat.getId());
            return cat;
        });
    }

    // ── 4. Child category ─────────────────────────────────────────────────────────
    private Category seedChildCategory(Category parent) {
        String slug = "paper-notebooks";
        return categoryRepository.findBySlug(slug).orElseGet(() -> {
            Category cat = Category.builder()
                    .name("Paper & Notebooks")
                    .slug(slug)
                    .parent(parent)
                    .sortOrder(1)
                    .active(true)
                    .build();
            cat = categoryRepository.save(cat);
            log.info("  [OK] Child category created: {} (id={})", cat.getName(), cat.getId());
            return cat;
        });
    }

    // ── 5. Product — A4 Copy Paper (3 pricing tiers + 1 image) ─────────────────
    private void seedPaperProduct(Category category) {
        String sku = "PAPER-A4-500";
        if (productRepository.existsBySku(sku)) {
            log.info("  [SKIP] Product already exists: {}", sku);
            return;
        }

        Product product = Product.builder()
                .name("A4 Copy Paper — 500 Sheets")
                .slug("a4-copy-paper-500-sheets")
                .sku(sku)
                .category(category)
                .description("80 GSM acid-free A4 paper. Ideal for laser and inkjet printers. " +
                             "Ream of 500 sheets, box of 5 reams.")
                .unitOfMeasurement("ream")
                .availabilityStatus(AvailabilityStatus.IN_STOCK)
                .basePrice(new BigDecimal("250.00"))
                .active(true)
                .build();
        product = productRepository.save(product);

        pricingTierRepository.saveAll(List.of(
                tier(product, 1,   49,  "250.00"),   // 1–49 reams
                tier(product, 50,  199, "230.00"),   // 50–199 reams
                tier(product, 200, null, "210.00")   // 200+ reams (bulk)
        ));

        productImageRepository.save(ProductImage.builder()
                .product(product)
                .imageUrl("https://placehold.co/600x400?text=A4+Copy+Paper")
                .altText("A4 Copy Paper — 500 Sheets")
                .sortOrder(0)
                .build());

        log.info("  [OK] Product created: {} (id={})", product.getName(), product.getId());
    }

    // ── 6. Product — All-Purpose Cleaner (3 pricing tiers + 1 image) ────────────
    private void seedCleaningProduct(Category category) {
        String sku = "CLEAN-APC-5L";
        if (productRepository.existsBySku(sku)) {
            log.info("  [SKIP] Product already exists: {}", sku);
            return;
        }

        Product product = Product.builder()
                .name("All-Purpose Cleaner — 5 Litre")
                .slug("all-purpose-cleaner-5-litre")
                .sku(sku)
                .category(category)
                .description("Concentrated multi-surface cleaner. Effective on floors, counters, " +
                             "and washrooms. Dilute 1:10 for regular cleaning.")
                .unitOfMeasurement("can")
                .availabilityStatus(AvailabilityStatus.IN_STOCK)
                .basePrice(new BigDecimal("480.00"))
                .active(true)
                .build();
        product = productRepository.save(product);

        pricingTierRepository.saveAll(List.of(
                tier(product, 1,  19,  "480.00"),   // 1–19 cans
                tier(product, 20, 99,  "450.00"),   // 20–99 cans
                tier(product, 100, null, "420.00")  // 100+ cans
        ));

        productImageRepository.save(ProductImage.builder()
                .product(product)
                .imageUrl("https://placehold.co/600x400?text=All-Purpose+Cleaner")
                .altText("All-Purpose Cleaner — 5 Litre")
                .sortOrder(0)
                .build());

        log.info("  [OK] Product created: {} (id={})", product.getName(), product.getId());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────
    private PricingTier tier(Product product, int min, Integer max, String price) {
        return PricingTier.builder()
                .product(product)
                .minQuantity(min)
                .maxQuantity(max)
                .unitPrice(new BigDecimal(price))
                .currencyCode("INR")
                .build();
    }
}
