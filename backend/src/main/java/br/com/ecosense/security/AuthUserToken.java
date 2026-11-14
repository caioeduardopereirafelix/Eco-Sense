package br.com.ecosense.security;

import java.util.Collection;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class AuthUserToken extends AbstractAuthenticationToken {

    private final AuthUser user;
    private final Jwt jwt;

    AuthUserToken(AuthUser user, Jwt jwt, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.user = user;
        this.jwt = jwt;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return jwt;
    }

    @Override
    public AuthUser getPrincipal() {
        return user;
    }

    @Override
    public String getName() {
        return user.email();
    }
}
