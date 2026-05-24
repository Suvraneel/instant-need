package com.b2b.instantneed.common.security;

import com.b2b.instantneed.common.exception.ApiException;
import com.b2b.instantneed.customer.repository.CustomerRepository;
import com.b2b.instantneed.user.entity.AuthProvider;
import com.b2b.instantneed.user.entity.Role;
import com.b2b.instantneed.user.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class SecurityUtilsTest {

    @Mock CustomerRepository customerRepository;

    @InjectMocks SecurityUtils securityUtils;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    // ── currentUser ───────────────────────────────────────────────────────────

    @Test
    void currentUser_nullAuthentication_throwsUnauthorized() {
        SecurityContextHolder.clearContext(); // no auth set

        assertThatThrownBy(() -> securityUtils.currentUser())
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getHttpStatus())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void currentUser_anonymousAuthentication_throwsUnauthorized() {
        // Set an authentication whose principal is the String "anonymousUser"
        var anon = new UsernamePasswordAuthenticationToken("anonymousUser", null, List.of());
        SecurityContextHolder.getContext().setAuthentication(anon);

        assertThatThrownBy(() -> securityUtils.currentUser())
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getHttpStatus())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void currentUser_validUserPrincipal_returnsUser() {
        User user = validUser();
        var auth = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);

        User result = securityUtils.currentUser();

        assertThat(result.getId()).isEqualTo(user.getId());
        assertThat(result.getEmail()).isEqualTo(user.getEmail());
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private User validUser() {
        return User.builder()
                .id(UUID.randomUUID())
                .email("buyer@test.com")
                .passwordHash("hash")
                .authProvider(AuthProvider.LOCAL)
                .role(Role.CUSTOMER)
                .active(true)
                .build();
    }
}
