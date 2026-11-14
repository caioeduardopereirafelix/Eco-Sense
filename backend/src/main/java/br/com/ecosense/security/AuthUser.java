package br.com.ecosense.security;

import br.com.ecosense.persistence.UserEntity;

public record AuthUser(long id, String email, String name, String role) {

    public boolean isAdmin() {
        return UserEntity.ADMIN.equals(role);
    }

    static AuthUser of(UserEntity e) {
        return new AuthUser(e.getId(), e.getEmail(), e.getName(), e.getRole());
    }
}
