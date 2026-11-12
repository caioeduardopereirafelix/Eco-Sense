package br.com.ecosense.security;

import br.com.ecosense.persistence.UserEntity;

/**
 * Usuário autenticado na requisição atual. Nos controllers: {@code @AuthenticationPrincipal AuthUser user}.
 * Montado a partir do banco a cada requisição, então mudança de perfil vale na hora.
 */
public record AuthUser(long id, String email, String name, String role) {

    public boolean isAdmin() {
        return UserEntity.ADMIN.equals(role);
    }

    static AuthUser of(UserEntity e) {
        return new AuthUser(e.getId(), e.getEmail(), e.getName(), e.getRole());
    }
}
