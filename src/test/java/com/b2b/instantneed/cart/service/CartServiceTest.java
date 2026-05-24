package com.b2b.instantneed.cart.service;

import com.b2b.instantneed.cart.dto.AddToCartRequest;
import com.b2b.instantneed.cart.dto.CartResponse;
import com.b2b.instantneed.cart.dto.UpdateCartItemRequest;
import com.b2b.instantneed.cart.entity.Cart;
import com.b2b.instantneed.cart.entity.CartItem;
import com.b2b.instantneed.cart.entity.CartStatus;
import com.b2b.instantneed.cart.repository.CartItemRepository;
import com.b2b.instantneed.cart.repository.CartRepository;
import com.b2b.instantneed.catalog.entity.AvailabilityStatus;
import com.b2b.instantneed.catalog.entity.Product;
import com.b2b.instantneed.catalog.repository.ProductRepository;
import com.b2b.instantneed.common.exception.ApiException;
import com.b2b.instantneed.common.security.SecurityUtils;
import com.b2b.instantneed.customer.entity.Customer;
import com.b2b.instantneed.pricing.dto.PriceCalculateResponse;
import com.b2b.instantneed.pricing.service.PricingService;
import com.b2b.instantneed.user.entity.AuthProvider;
import com.b2b.instantneed.user.entity.Role;
import com.b2b.instantneed.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock CartRepository     cartRepository;
    @Mock CartItemRepository cartItemRepository;
    @Mock ProductRepository  productRepository;
    @Mock PricingService     pricingService;
    @Mock SecurityUtils      securityUtils;

    @InjectMocks CartService cartService;

    private Customer customer;
    private Product  product;

    @BeforeEach
    void setUp() {
        User user = User.builder()
                .id(UUID.randomUUID()).email("buyer@test.com")
                .passwordHash("hash").authProvider(AuthProvider.LOCAL)
                .role(Role.CUSTOMER).active(true).build();

        customer = Customer.builder()
                .id(UUID.randomUUID()).user(user).fullName("Raj Sharma").build();

        product = Product.builder()
                .id(UUID.randomUUID()).name("A4 Paper").sku("SKU-001")
                .slug("a4-paper").basePrice(new BigDecimal("250.00"))
                .availabilityStatus(AvailabilityStatus.IN_STOCK).active(true).build();

        given(securityUtils.currentCustomer()).willReturn(customer);
    }

    // ── getCart ───────────────────────────────────────────────────────────────

    @Test
    void getCart_noActiveCart_returnsEmpty() {
        given(cartRepository.findByCustomerIdAndStatus(customer.getId(), CartStatus.ACTIVE))
                .willReturn(Optional.empty());

        CartResponse res = cartService.getCart();

        assertThat(res.cartId()).isNull();
        assertThat(res.items()).isEmpty();
        assertThat(res.grandTotal()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void getCart_existingCart_returnsItems() {
        Cart cart = activeCart();
        CartItem item = cartItem(cart, product, 5);
        cart.getItems().add(item);

        given(cartRepository.findByCustomerIdAndStatus(customer.getId(), CartStatus.ACTIVE))
                .willReturn(Optional.of(cart));
        given(cartRepository.findWithItemsById(cart.getId())).willReturn(Optional.of(cart));

        CartResponse res = cartService.getCart();

        assertThat(res.items()).hasSize(1);
        assertThat(res.grandTotal()).isEqualByComparingTo("1250.00"); // 5 * 250
    }

    // ── addItem ───────────────────────────────────────────────────────────────

    @Test
    void addItem_noExistingCart_createsCartAndAddsItem() {
        given(cartRepository.findByCustomerIdAndStatus(customer.getId(), CartStatus.ACTIVE))
                .willReturn(Optional.empty());

        Cart savedCart = activeCart();
        given(cartRepository.save(any())).willReturn(savedCart);
        given(productRepository.findById(product.getId())).willReturn(Optional.of(product));
        given(cartItemRepository.findByCartIdAndProductId(savedCart.getId(), product.getId()))
                .willReturn(Optional.empty());
        given(pricingService.calculate(product.getId(), 3)).willReturn(price("250.00", 3));
        given(cartRepository.findWithItemsById(savedCart.getId()))
                .willReturn(Optional.of(savedCart));

        CartResponse res = cartService.addItem(new AddToCartRequest(product.getId(), 3));

        verify(cartItemRepository).save(any(CartItem.class));
        assertThat(res).isNotNull();
    }

    @Test
    void addItem_existingItem_accumulatesQuantity() {
        Cart cart = activeCart();
        CartItem existing = cartItem(cart, product, 5);
        cart.getItems().add(existing);

        given(cartRepository.findByCustomerIdAndStatus(customer.getId(), CartStatus.ACTIVE))
                .willReturn(Optional.of(cart));
        given(productRepository.findById(product.getId())).willReturn(Optional.of(product));
        given(cartItemRepository.findByCartIdAndProductId(cart.getId(), product.getId()))
                .willReturn(Optional.of(existing));
        // New quantity = 5 + 3 = 8
        given(pricingService.calculate(product.getId(), 8)).willReturn(price("230.00", 8));
        given(cartRepository.findWithItemsById(cart.getId())).willReturn(Optional.of(cart));

        cartService.addItem(new AddToCartRequest(product.getId(), 3));

        assertThat(existing.getQuantity()).isEqualTo(8);
        verify(cartItemRepository).save(existing);
    }

    @Test
    void addItem_productNotFound_throws404() {
        Cart cart = activeCart();
        given(cartRepository.findByCustomerIdAndStatus(customer.getId(), CartStatus.ACTIVE))
                .willReturn(Optional.of(cart));
        given(productRepository.findById(product.getId())).willReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.addItem(new AddToCartRequest(product.getId(), 1)))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getHttpStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── updateItem ────────────────────────────────────────────────────────────

    @Test
    void updateItem_setsNewQuantityAndRecalculatesPrice() {
        Cart cart = activeCart();
        CartItem item = cartItem(cart, product, 5);

        given(cartRepository.findByCustomerIdAndStatus(customer.getId(), CartStatus.ACTIVE))
                .willReturn(Optional.of(cart));
        given(cartItemRepository.findById(item.getId())).willReturn(Optional.of(item));
        given(pricingService.calculate(product.getId(), 10)).willReturn(price("230.00", 10));
        given(cartRepository.findWithItemsById(cart.getId())).willReturn(Optional.of(cart));

        cartService.updateItem(item.getId(), new UpdateCartItemRequest(10));

        assertThat(item.getQuantity()).isEqualTo(10);
        assertThat(item.getAppliedUnitPrice()).isEqualByComparingTo("230.00");
        verify(cartItemRepository).save(item);
    }

    @Test
    void updateItem_itemBelongsToAnotherCart_throws404() {
        Cart myCart    = activeCart();
        Cart otherCart = activeCart(); // different ID

        CartItem foreignItem = cartItem(otherCart, product, 2);

        given(cartRepository.findByCustomerIdAndStatus(customer.getId(), CartStatus.ACTIVE))
                .willReturn(Optional.of(myCart));
        given(cartItemRepository.findById(foreignItem.getId())).willReturn(Optional.of(foreignItem));

        assertThatThrownBy(() -> cartService.updateItem(foreignItem.getId(), new UpdateCartItemRequest(5)))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getHttpStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── removeItem ────────────────────────────────────────────────────────────

    @Test
    void removeItem_removesFromCartAndDeletes() {
        Cart cart = activeCart();
        CartItem item = cartItem(cart, product, 3);
        cart.getItems().add(item);

        given(cartRepository.findByCustomerIdAndStatus(customer.getId(), CartStatus.ACTIVE))
                .willReturn(Optional.of(cart));
        given(cartItemRepository.findById(item.getId())).willReturn(Optional.of(item));
        given(cartRepository.findWithItemsById(cart.getId())).willReturn(Optional.of(cart));

        cartService.removeItem(item.getId());

        verify(cartItemRepository).delete(item);
    }

    // ── clearCart ─────────────────────────────────────────────────────────────

    @Test
    void clearCart_existingCart_clearsItems() {
        Cart cart = activeCart();
        CartItem item = cartItem(cart, product, 3);
        cart.getItems().add(item);

        given(cartRepository.findByCustomerIdAndStatus(customer.getId(), CartStatus.ACTIVE))
                .willReturn(Optional.of(cart));

        cartService.clearCart();

        assertThat(cart.getItems()).isEmpty();
        verify(cartRepository).save(cart);
    }

    @Test
    void clearCart_noActiveCart_doesNothing() {
        given(cartRepository.findByCustomerIdAndStatus(customer.getId(), CartStatus.ACTIVE))
                .willReturn(Optional.empty());

        assertThatCode(() -> cartService.clearCart()).doesNotThrowAnyException();
        verify(cartRepository, never()).save(any());
    }

    // ── null-pointer / deleted-product guards ─────────────────────────────────

    @Test
    void updateItem_productIsNull_throwsBadRequest() {
        // CartItem whose product was hard-deleted from DB (getProduct() == null)
        Cart cart = activeCart();
        CartItem item = CartItem.builder()
                .id(UUID.randomUUID()).cart(cart)
                .product(null)                       // deleted product
                .quantity(3)
                .appliedUnitPrice(new BigDecimal("250.00"))
                .lineTotal(new BigDecimal("750.00"))
                .currencyCode("INR")
                .build();

        given(cartRepository.findByCustomerIdAndStatus(customer.getId(), CartStatus.ACTIVE))
                .willReturn(Optional.of(cart));
        given(cartItemRepository.findById(item.getId())).willReturn(Optional.of(item));

        assertThatThrownBy(() -> cartService.updateItem(item.getId(), new UpdateCartItemRequest(5)))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getHttpStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updateItem_itemNotFound_throws404() {
        Cart cart = activeCart();
        UUID missingId = UUID.randomUUID();

        given(cartRepository.findByCustomerIdAndStatus(customer.getId(), CartStatus.ACTIVE))
                .willReturn(Optional.of(cart));
        given(cartItemRepository.findById(missingId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.updateItem(missingId, new UpdateCartItemRequest(5)))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getHttpStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void removeItem_itemNotFound_throws404() {
        Cart cart = activeCart();
        UUID missingId = UUID.randomUUID();

        given(cartRepository.findByCustomerIdAndStatus(customer.getId(), CartStatus.ACTIVE))
                .willReturn(Optional.of(cart));
        given(cartItemRepository.findById(missingId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.removeItem(missingId))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getHttpStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private Cart activeCart() {
        return Cart.builder()
                .id(UUID.randomUUID())
                .customer(customer)
                .status(CartStatus.ACTIVE)
                .build();
    }

    private CartItem cartItem(Cart cart, Product prod, int qty) {
        return CartItem.builder()
                .id(UUID.randomUUID())
                .cart(cart)
                .product(prod)
                .quantity(qty)
                .appliedUnitPrice(new BigDecimal("250.00"))
                .lineTotal(new BigDecimal("250.00").multiply(BigDecimal.valueOf(qty)))
                .currencyCode("INR")
                .build();
    }

    private PriceCalculateResponse price(String unitPrice, int qty) {
        BigDecimal unit = new BigDecimal(unitPrice);
        return new PriceCalculateResponse(
                product.getId(), qty, unit,
                unit.multiply(BigDecimal.valueOf(qty)), "INR", null);
    }
}
