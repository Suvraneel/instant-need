package com.b2b.instantneed.auth.service;

import com.b2b.instantneed.auth.dto.*;
import com.b2b.instantneed.common.exception.ApiException;
import com.b2b.instantneed.common.util.HtmlSanitizer;
import com.b2b.instantneed.common.security.JwtUtil;
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
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final AddressRepository addressRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        if (request.email() == null && request.phoneNumber() == null) {
            throw ApiException.badRequest("VALIDATION_ERROR", "Either email or phone number is required");
        }
        if (request.email() != null && userRepository.existsByEmail(request.email())) {
            throw ApiException.conflict("EMAIL_TAKEN", "An account with this email already exists");
        }
        if (request.phoneNumber() != null && userRepository.existsByPhoneNumber(request.phoneNumber())) {
            throw ApiException.conflict("PHONE_TAKEN", "An account with this phone number already exists");
        }

        User user = User.builder()
                .email(request.email())
                .phoneNumber(request.phoneNumber())
                .passwordHash(passwordEncoder.encode(request.password()))
                .authProvider(AuthProvider.LOCAL)
                .role(Role.CUSTOMER)
                .active(true)
                .build();
        user = userRepository.save(user);

        Customer customer = Customer.builder()
                .user(user)
                .fullName(HtmlSanitizer.strip(request.fullName()))
                .businessName(HtmlSanitizer.strip(request.businessName()))
                .gstVatNumber(HtmlSanitizer.strip(request.gstVatNumber()))
                .notes(HtmlSanitizer.strip(request.notes()))
                .build();
        customer = customerRepository.save(customer);

        AddressRequest addr = request.address();
        Address address = Address.builder()
                .customer(customer)
                .label("Default")
                .line1(addr.line1())
                .line2(addr.line2())
                .city(addr.city())
                .state(addr.state())
                .country(addr.country())
                .postalCode(addr.postalCode())
                .isDefault(true)
                .build();
        address = addressRepository.save(address);

        customer.setDefaultShippingAddressId(address.getId());
        customerRepository.save(customer);

        log.info("Registered new customer: userId={}, customerId={}", user.getId(), customer.getId());
        return new RegisterResponse(user.getId(), customer.getId(), "Registration successful");
    }

    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );

        User user = (User) authentication.getPrincipal();

        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        Customer customer = customerRepository.findByUserId(user.getId())
                .orElseThrow(() -> ApiException.notFound("CUSTOMER_NOT_FOUND", "Customer profile not found"));

        String accessToken = jwtUtil.generateAccessToken(user);
        String refreshToken = jwtUtil.generateRefreshToken(user);

        return new AuthResponse(
                accessToken,
                refreshToken,
                new AuthResponse.UserInfo(user.getId(), customer.getFullName(), user.getEmail())
        );
    }

    public AuthResponse refresh(RefreshTokenRequest request) {
        String username;
        try {
            username = jwtUtil.extractUsername(request.refreshToken());
        } catch (Exception e) {
            throw ApiException.unauthorized("INVALID_TOKEN", "Invalid or expired refresh token");
        }

        User user = userRepository.findByEmail(username)
                .or(() -> userRepository.findByPhoneNumber(username))
                .orElseThrow(() -> ApiException.unauthorized("INVALID_TOKEN", "Invalid refresh token"));

        if (!jwtUtil.isRefreshTokenValid(request.refreshToken(), user)) {
            throw ApiException.unauthorized("INVALID_TOKEN", "Invalid or expired refresh token");
        }

        Customer customer = customerRepository.findByUserId(user.getId())
                .orElseThrow(() -> ApiException.notFound("CUSTOMER_NOT_FOUND", "Customer profile not found"));

        return new AuthResponse(
                jwtUtil.generateAccessToken(user),
                jwtUtil.generateRefreshToken(user),
                new AuthResponse.UserInfo(user.getId(), customer.getFullName(), user.getEmail())
        );
    }

    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        userRepository.findByEmail(request.email()).ifPresent(user -> {
            String token = UUID.randomUUID().toString();
            user.setPasswordResetToken(token);
            user.setPasswordResetTokenExpiresAt(Instant.now().plusSeconds(3600));
            userRepository.save(user);
            // TODO: dispatch PasswordResetEmailEvent(user.getEmail(), token) once email service is wired
            log.info("Password reset token generated for userId={}", user.getId());
        });
        // Always return success to prevent email enumeration
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        User user = userRepository.findByPasswordResetToken(request.token())
                .orElseThrow(() -> ApiException.badRequest("INVALID_TOKEN", "Invalid or expired reset token"));

        if (user.getPasswordResetTokenExpiresAt() == null ||
                Instant.now().isAfter(user.getPasswordResetTokenExpiresAt())) {
            throw ApiException.badRequest("TOKEN_EXPIRED", "Reset token has expired");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setPasswordResetToken(null);
        user.setPasswordResetTokenExpiresAt(null);
        userRepository.save(user);

        log.info("Password reset completed for userId={}", user.getId());
    }
}
