package com.Wealthify.backend.security;

import com.Wealthify.backend.entity.User;
import com.Wealthify.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserDetailServiceImplTest {

    @Mock
    private UserRepository userRepository;

    private UserDetailServiceImpl userDetailsService;

    @BeforeEach
    void setUp() {
        userDetailsService = new UserDetailServiceImpl(userRepository);
    }

    @Test
    @DisplayName("loadUserByUsername returns UserPrincipal with complete user attributes")
    void testLoadUserByUsernameSuccess() {
        UUID id = UUID.randomUUID();
        User user = User.builder()
                .id(id)
                .name("Alice Smith")
                .email("alice@wealthify.test")
                .password("hashed_password")
                .monthlyIncome(BigDecimal.valueOf(80000))
                .savingPercentage(BigDecimal.valueOf(20))
                .investmentPercentage(BigDecimal.valueOf(30))
                .build();

        when(userRepository.findByEmail("alice@wealthify.test")).thenReturn(Optional.of(user));

        UserDetails userDetails = userDetailsService.loadUserByUsername("alice@wealthify.test");

        assertThat(userDetails).isInstanceOf(UserPrincipal.class);
        UserPrincipal principal = (UserPrincipal) userDetails;
        assertThat(principal.getId()).isEqualTo(id);
        assertThat(principal.getName()).isEqualTo("Alice Smith");
        assertThat(principal.getEmail()).isEqualTo("alice@wealthify.test");
        assertThat(principal.getMonthlyIncome()).isEqualByComparingTo(BigDecimal.valueOf(80000));
        assertThat(principal.getSavingPercentage()).isEqualByComparingTo(BigDecimal.valueOf(20));
        assertThat(principal.getInvestmentPercentage()).isEqualByComparingTo(BigDecimal.valueOf(30));
    }

    @Test
    @DisplayName("loadUserByUsername throws UsernameNotFoundException when user is not present")
    void testLoadUserByUsernameNotFound() {
        when(userRepository.findByEmail("unknown@wealthify.test")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("unknown@wealthify.test"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("User not found: unknown@wealthify.test");
    }
}
