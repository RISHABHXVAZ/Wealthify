package com.Wealthify.backend.security;

import com.Wealthify.backend.entity.User;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.UUID;

/**
 * Immutable typed authenticated user principal.
 *
 * Encapsulates the verified user identity (UUID id, email, name, financial profile)
 * loaded during JWT authentication without passing mutable detached JPA entities across
 * request layers.
 */
@Getter
@ToString(exclude = "password")
@EqualsAndHashCode(of = "id")
public class UserPrincipal implements UserDetails {

    private final UUID id;
    private final String name;
    private final String email;
    private final String password;
    private final BigDecimal monthlyIncome;
    private final BigDecimal savingPercentage;
    private final BigDecimal investmentPercentage;
    private final Collection<? extends GrantedAuthority> authorities;

    public UserPrincipal(UUID id, String name, String email, String password,
                         BigDecimal monthlyIncome, BigDecimal savingPercentage,
                         BigDecimal investmentPercentage,
                         Collection<? extends GrantedAuthority> authorities) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.password = password;
        this.monthlyIncome = monthlyIncome;
        this.savingPercentage = savingPercentage;
        this.investmentPercentage = investmentPercentage;
        this.authorities = authorities != null ? authorities
                : Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"));
    }

    public static UserPrincipal fromEntity(User user) {
        if (user == null) {
            return null;
        }
        return new UserPrincipal(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getPassword(),
                user.getMonthlyIncome(),
                user.getSavingPercentage(),
                user.getInvestmentPercentage(),
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }

    @Override
    public String getUsername() {
        return this.email;
    }

    @Override
    public String getPassword() {
        return this.password;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return this.authorities;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
