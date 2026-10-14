package com.Wealthify.backend.security;

import com.Wealthify.backend.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserPrincipalTest {

    @Test
    @DisplayName("UserPrincipal creates correctly from User entity preserving all financial and identity attributes")
    void testFromEntity() {
        UUID id = UUID.randomUUID();
        User user = User.builder()
                .id(id)
                .name("Jane Doe")
                .email("jane@wealthify.test")
                .password("encoded_pass")
                .monthlyIncome(BigDecimal.valueOf(120000))
                .savingPercentage(BigDecimal.valueOf(25))
                .investmentPercentage(BigDecimal.valueOf(35))
                .build();

        UserPrincipal principal = UserPrincipal.fromEntity(user);

        assertThat(principal).isNotNull();
        assertThat(principal.getId()).isEqualTo(id);
        assertThat(principal.getName()).isEqualTo("Jane Doe");
        assertThat(principal.getEmail()).isEqualTo("jane@wealthify.test");
        assertThat(principal.getUsername()).isEqualTo("jane@wealthify.test");
        assertThat(principal.getPassword()).isEqualTo("encoded_pass");
        assertThat(principal.getMonthlyIncome()).isEqualByComparingTo(BigDecimal.valueOf(120000));
        assertThat(principal.getSavingPercentage()).isEqualByComparingTo(BigDecimal.valueOf(25));
        assertThat(principal.getInvestmentPercentage()).isEqualByComparingTo(BigDecimal.valueOf(35));
        assertThat(principal.getAuthorities())
                .extracting(org.springframework.security.core.GrantedAuthority::getAuthority)
                .containsExactly("ROLE_USER");
        assertThat(principal.isAccountNonExpired()).isTrue();
        assertThat(principal.isAccountNonLocked()).isTrue();
        assertThat(principal.isCredentialsNonExpired()).isTrue();
        assertThat(principal.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("UserPrincipal fromEntity returns null when given null")
    void testFromEntityNull() {
        assertThat(UserPrincipal.fromEntity(null)).isNull();
    }

    @Test
    @DisplayName("UserPrincipal equals and hashCode are based solely on UUID id")
    void testEqualsAndHashCode() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        UserPrincipal p1 = new UserPrincipal(id1, "Name 1", "email1@test.com", "pass",
                BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ONE, Collections.emptyList());
        UserPrincipal p1Duplicate = new UserPrincipal(id1, "Different Name", "diff@test.com", "diffPass",
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, Collections.emptyList());
        UserPrincipal p2 = new UserPrincipal(id2, "Name 1", "email1@test.com", "pass",
                BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ONE, Collections.emptyList());

        assertThat(p1).isEqualTo(p1Duplicate);
        assertThat(p1.hashCode()).isEqualTo(p1Duplicate.hashCode());
        assertThat(p1).isNotEqualTo(p2);
    }
}
