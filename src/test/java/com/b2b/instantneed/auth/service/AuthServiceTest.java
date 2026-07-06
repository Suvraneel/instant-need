package com.b2b.instantneed.auth.service;

import com.b2b.instantneed.auth.dto.*;
import com.b2b.instantneed.common.exception.ApiException;
import com.b2b.instantneed.common.security.JwtUtil;
import com.b2b.instantneed.common.service.EmailService;
import com.b2b.instantneed.customer.entity.Address;
import com.b2b.instantneed.customer.entity.Customer;
import com.b2b.instantneed.customer.repository.AddressRepository;
import com.b2b.instantneed.customer.repository.CustomerRepository;
import com.b2b.instantneed.user.entity.AuthProvider;
import com.b2b.instantneed.user.entity.Role;
import com.b2b.instantneed.user.entity.User;
import com.b2b.instantneed.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

    @Mock UserRepository        userRepository;
    @Mock CustomerRepository    customerRepository;
    @Mock AddressRepository     addressRepository;
    @Mock PasswordEncoder       passwordEncoder;
    @Mock AuthenticationManager authenticationManager;
    @Mock JwtUtil               jwtUtil;
    @Mock EmailService          emailService;

    @InjectMocks AuthService authService;

    // ── register ──────────────────────────────────────────────────────────────

    @Test
    void register_success_createsUserCustomerAndAddress() {
        given(userRepository.existsByEmail(any())).willReturn(false);
        given(userRepository.existsByPhoneNumber(any())).willReturn(false);
        given(passwordEncoder.encode(any())).willReturn("hashed");

        User savedUser = user(Role.CUSTOMER);
        given(userRepository.save(any())).willReturn(savedUser);

        Customer savedCustomer = customer(savedUser);
        given(customerRepository.save(any())).willReturn(savedCustomer);

        Address savedAddress = address(savedCustomer);
        given(addressRepository.save(any())).willReturn(savedAddress);

        given(jwtUtil.generateAccessToken(any())).willReturn("access-token");
        given(jwtUtil.generateRefreshToken(any())).willReturn("refresh-token");

        AuthResponse res = authService.register(validRegisterRequest());

        assertThat(res.accessToken()).isEqualTo("access-token");
        assertThat(res.user().email()).isEqualTo(savedUser.getEmail());
        verify(customerRepository, times(2)).save(any()); // once for create, once for defaultAddressId
    }

    @Test
    void register_emailTaken_throwsConflict() {
        given(userRepository.existsByEmail("taken@example.com")).willReturn(true);

        assertThatThrownBy(() -> authService.register(validRegisterRequest()))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getHttpStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void register_phoneTaken_throwsConflict() {
        given(userRepository.existsByEmail(any())).willReturn(false);
        given(userRepository.existsByPhoneNumber("+910000000000")).willReturn(true);

        var req = new RegisterRequest(
                "Test User", null, null, "+910000000000",
                "Password1!", null, null,
                new AddressRequest("Line 1", null, "City", "State", "IN", "100000"));

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getHttpStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void register_neitherEmailNorPhone_throwsBadRequest() {
        var req = new RegisterRequest(
                "Test", null, null, null, "Password1!",
                null, null, new AddressRequest("L1", null, "C", "S", "IN", "000000"));

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getHttpStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ── login ─────────────────────────────────────────────────────────────────

    @Test
    void login_success_returnsTokensAndUserInfo() {
        User u = user(Role.CUSTOMER);
        given(authenticationManager.authenticate(any()))
                .willReturn(new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities()));
        given(userRepository.save(any())).willReturn(u);
        given(customerRepository.findByUserId(u.getId())).willReturn(Optional.of(customer(u)));
        given(jwtUtil.generateAccessToken(u)).willReturn("access-token");
        given(jwtUtil.generateRefreshToken(u)).willReturn("refresh-token");

        AuthResponse res = authService.login(new LoginRequest("test@example.com", "pass"));

        assertThat(res.accessToken()).isEqualTo("access-token");
        assertThat(res.refreshToken()).isEqualTo("refresh-token");
        assertThat(res.user().email()).isEqualTo(u.getEmail());
    }

    // ── refresh ───────────────────────────────────────────────────────────────

    @Test
    void refresh_validRefreshToken_returnsNewTokens() {
        User u = user(Role.CUSTOMER);
        given(jwtUtil.extractUsername("good-refresh")).willReturn(u.getEmail());
        given(userRepository.findByEmail(u.getEmail())).willReturn(Optional.of(u));
        given(jwtUtil.isRefreshTokenValid("good-refresh", u)).willReturn(true);
        given(customerRepository.findByUserId(u.getId())).willReturn(Optional.of(customer(u)));
        given(jwtUtil.generateAccessToken(u)).willReturn("new-access");
        given(jwtUtil.generateRefreshToken(u)).willReturn("new-refresh");

        AuthResponse res = authService.refresh(new RefreshTokenRequest("good-refresh"));

        assertThat(res.accessToken()).isEqualTo("new-access");
    }

    @Test
    void refresh_invalidToken_throwsUnauthorized() {
        User u = user(Role.CUSTOMER);
        given(jwtUtil.extractUsername("bad-token")).willReturn(u.getEmail());
        given(userRepository.findByEmail(u.getEmail())).willReturn(Optional.of(u));
        given(jwtUtil.isRefreshTokenValid("bad-token", u)).willReturn(false);

        assertThatThrownBy(() -> authService.refresh(new RefreshTokenRequest("bad-token")))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getHttpStatus())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void refresh_accessTokenUsedAsRefresh_throwsUnauthorized() {
        User u = user(Role.CUSTOMER);
        given(jwtUtil.extractUsername("access-token")).willReturn(u.getEmail());
        given(userRepository.findByEmail(u.getEmail())).willReturn(Optional.of(u));
        // isRefreshTokenValid returns false because type == "access"
        given(jwtUtil.isRefreshTokenValid("access-token", u)).willReturn(false);

        assertThatThrownBy(() -> authService.refresh(new RefreshTokenRequest("access-token")))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getHttpStatus())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ── forgotPassword ────────────────────────────────────────────────────────

    @Test
    void forgotPassword_existingUser_setsResetTokenHash() {
        User u = user(Role.CUSTOMER);
        given(userRepository.findByEmail(u.getEmail())).willReturn(Optional.of(u));

        authService.forgotPassword(new ForgotPasswordRequest(u.getEmail()));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        String stored = userCaptor.getValue().getPasswordResetTokenHash();
        assertThat(stored).isNotBlank();
        // A raw UUID token is 36 chars with dashes; a SHA-256 hex digest is 64
        // hex chars — this shape check alone proves it's a hash, not the raw
        // token, without needing to know the token's actual value.
        assertThat(stored).hasSize(64).matches("^[0-9a-f]+$");
        assertThat(userCaptor.getValue().getPasswordResetTokenExpiresAt()).isAfter(Instant.now());

        // Stronger check: capture the raw token actually emailed to the user
        // and confirm the stored value is its hash — not the token itself.
        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordReset(eq(u.getEmail()), tokenCaptor.capture());
        String emailedToken = tokenCaptor.getValue();
        assertThat(stored).isEqualTo(AuthService.hashToken(emailedToken));
        assertThat(stored).isNotEqualTo(emailedToken);
    }

    @Test
    void forgotPassword_unknownEmail_silentlySucceeds() {
        given(userRepository.findByEmail("ghost@example.com")).willReturn(Optional.empty());

        // No exception — prevents email enumeration
        assertThatCode(() -> authService.forgotPassword(new ForgotPasswordRequest("ghost@example.com")))
                .doesNotThrowAnyException();
        verify(userRepository, never()).save(any());
    }

    // ── resetPassword ─────────────────────────────────────────────────────────

    @Test
    void resetPassword_validToken_updatesHash() {
        User u = user(Role.CUSTOMER);
        u.setPasswordResetTokenHash(AuthService.hashToken("valid-token"));
        u.setPasswordResetTokenExpiresAt(Instant.now().plusSeconds(3600));
        given(userRepository.findByPasswordResetTokenHash(AuthService.hashToken("valid-token")))
                .willReturn(Optional.of(u));
        given(passwordEncoder.encode("NewPass@123")).willReturn("new-hash");

        authService.resetPassword(new ResetPasswordRequest("valid-token", "NewPass@123"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPasswordResetTokenHash()).isNull();
    }

    @Test
    void resetPassword_expiredToken_throwsBadRequest() {
        User u = user(Role.CUSTOMER);
        u.setPasswordResetTokenHash(AuthService.hashToken("expired"));
        u.setPasswordResetTokenExpiresAt(Instant.now().minusSeconds(1)); // already expired
        given(userRepository.findByPasswordResetTokenHash(AuthService.hashToken("expired")))
                .willReturn(Optional.of(u));

        assertThatThrownBy(() -> authService.resetPassword(new ResetPasswordRequest("expired", "NewPass@1")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void resetPassword_invalidToken_throwsBadRequest() {
        given(userRepository.findByPasswordResetTokenHash(any())).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.resetPassword(new ResetPasswordRequest("no-such-token", "x")))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getHttpStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private static User user(Role role) {
        return User.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .passwordHash("hash")
                .authProvider(AuthProvider.LOCAL)
                .role(role)
                .active(true)
                .build();
    }

    private static Customer customer(User user) {
        return Customer.builder()
                .id(UUID.randomUUID())
                .user(user)
                .fullName("Test User")
                .businessName("Test Biz")
                .build();
    }

    private static Address address(Customer customer) {
        return Address.builder()
                .id(UUID.randomUUID())
                .customer(customer)
                .label("Default")
                .line1("Line 1")
                .city("City")
                .state("State")
                .country("India")
                .postalCode("100000")
                .isDefault(true)
                .build();
    }

    private static RegisterRequest validRegisterRequest() {
        return new RegisterRequest(
                "Test User", "Test Biz", "taken@example.com", null,
                "Password1!", null, null,
                new AddressRequest("123 Main St", null, "Mumbai", "Maharashtra", "India", "400001"));
    }
}
