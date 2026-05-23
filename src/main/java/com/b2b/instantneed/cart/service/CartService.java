package com.b2b.instantneed.cart.service;

import com.b2b.instantneed.cart.dto.AddToCartRequest;
import com.b2b.instantneed.cart.dto.CartResponse;
import com.b2b.instantneed.cart.dto.UpdateCartItemRequest;
import com.b2b.instantneed.cart.entity.Cart;
import com.b2b.instantneed.cart.entity.CartItem;
import com.b2b.instantneed.cart.entity.CartStatus;
import com.b2b.instantneed.cart.repository.CartItemRepository;
import com.b2b.instantneed.cart.repository.CartRepository;
import com.b2b.instantneed.catalog.entity.Product;
import com.b2b.instantneed.catalog.repository.ProductRepository;
import com.b2b.instantneed.common.exception.ApiException;
import com.b2b.instantneed.common.security.SecurityUtils;
import com.b2b.instantneed.customer.entity.Customer;
import com.b2b.instantneed.pricing.dto.PriceCalculateResponse;
import com.b2b.instantneed.pricing.service.PricingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final PricingService pricingService;
    private final SecurityUtils securityUtils;

    @Transactional(readOnly = true)
    public CartResponse getCart() {
        Customer customer = securityUtils.currentCustomer();
        return cartRepository
                .findByCustomerIdAndStatus(customer.getId(), CartStatus.ACTIVE)
                .map(CartResponse::from)
                .orElseGet(() -> CartResponse.empty(customer.getId()));
    }

    @Transactional
    public CartResponse addItem(AddToCartRequest request) {
        Customer customer = securityUtils.currentCustomer();
        Cart cart = cartRepository
                .findByCustomerIdAndStatus(customer.getId(), CartStatus.ACTIVE)
                .orElseGet(() -> createCart(customer));

        Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> ApiException.notFound("PRODUCT_NOT_FOUND",
                        "Product not found: " + request.productId()));

        CartItem existing = cartItemRepository
                .findByCartIdAndProductId(cart.getId(), product.getId())
                .orElse(null);

        int newQuantity = existing != null
                ? existing.getQuantity() + request.quantity()
                : request.quantity();

        PriceCalculateResponse price = pricingService.calculate(product.getId(), newQuantity);

        if (existing != null) {
            existing.setQuantity(newQuantity);
            existing.setAppliedUnitPrice(price.appliedUnitPrice());
            existing.setLineTotal(price.lineTotal());
            existing.setCurrencyCode(price.currencyCode());
            cartItemRepository.save(existing);
        } else {
            CartItem item = CartItem.builder()
                    .cart(cart)
                    .product(product)
                    .quantity(newQuantity)
                    .appliedUnitPrice(price.appliedUnitPrice())
                    .lineTotal(price.lineTotal())
                    .currencyCode(price.currencyCode())
                    .build();
            cart.getItems().add(item);
            cartItemRepository.save(item);
        }

        Cart reloaded = cartRepository
                .findByCustomerIdAndStatus(customer.getId(), CartStatus.ACTIVE)
                .orElseThrow();
        return CartResponse.from(reloaded);
    }

    @Transactional
    public CartResponse updateItem(UUID itemId, UpdateCartItemRequest request) {
        Customer customer = securityUtils.currentCustomer();
        Cart cart = activeCart(customer.getId());

        CartItem item = cartItemRepository.findById(itemId)
                .orElseThrow(() -> ApiException.notFound("CART_ITEM_NOT_FOUND",
                        "Cart item not found: " + itemId));

        if (!item.getCart().getId().equals(cart.getId())) {
            throw ApiException.notFound("CART_ITEM_NOT_FOUND", "Cart item not found: " + itemId);
        }

        PriceCalculateResponse price = pricingService.calculate(
                item.getProduct().getId(), request.quantity());

        item.setQuantity(request.quantity());
        item.setAppliedUnitPrice(price.appliedUnitPrice());
        item.setLineTotal(price.lineTotal());
        item.setCurrencyCode(price.currencyCode());
        cartItemRepository.save(item);

        Cart reloaded = cartRepository
                .findByCustomerIdAndStatus(customer.getId(), CartStatus.ACTIVE)
                .orElseThrow();
        return CartResponse.from(reloaded);
    }

    @Transactional
    public CartResponse removeItem(UUID itemId) {
        Customer customer = securityUtils.currentCustomer();
        Cart cart = activeCart(customer.getId());

        CartItem item = cartItemRepository.findById(itemId)
                .orElseThrow(() -> ApiException.notFound("CART_ITEM_NOT_FOUND",
                        "Cart item not found: " + itemId));

        if (!item.getCart().getId().equals(cart.getId())) {
            throw ApiException.notFound("CART_ITEM_NOT_FOUND", "Cart item not found: " + itemId);
        }

        cart.getItems().remove(item);
        cartItemRepository.delete(item);

        Cart reloaded = cartRepository
                .findByCustomerIdAndStatus(customer.getId(), CartStatus.ACTIVE)
                .orElseThrow();
        return CartResponse.from(reloaded);
    }

    @Transactional
    public void clearCart() {
        Customer customer = securityUtils.currentCustomer();
        cartRepository.findByCustomerIdAndStatus(customer.getId(), CartStatus.ACTIVE)
                .ifPresent(cart -> {
                    cart.getItems().clear();
                    cartRepository.save(cart);
                });
    }

    private Cart activeCart(UUID customerId) {
        return cartRepository.findByCustomerIdAndStatus(customerId, CartStatus.ACTIVE)
                .orElseThrow(() -> ApiException.notFound("CART_NOT_FOUND", "No active cart found"));
    }

    private Cart createCart(Customer customer) {
        Cart cart = Cart.builder()
                .customer(customer)
                .status(CartStatus.ACTIVE)
                .build();
        return cartRepository.save(cart);
    }

}
