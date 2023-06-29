package com.mt.access.application.user.representation;

import com.mt.access.domain.model.user.User;
import java.util.Collection;
import java.util.Collections;
import lombok.Data;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

@Data
public class UserSpringRepresentation implements UserDetails {
    private String id;
    private String password;
    private Boolean locked;

    public UserSpringRepresentation(User user) {
        id = user.getUserId().getDomainId();
        password = user.getPassword().getPassword();
        locked = user.getLocked();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.emptySet();
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return id;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return !locked;
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
