package br.com.ecosense.api;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.ecosense.api.ApiDtos.LoginRequest;
import br.com.ecosense.api.ApiDtos.PasswordChange;
import br.com.ecosense.api.ApiDtos.ProfileUpdate;
import br.com.ecosense.api.ApiDtos.Session;
import br.com.ecosense.api.ApiDtos.UserInfo;
import br.com.ecosense.security.AuthUser;
import br.com.ecosense.service.UserService;

/** Login e conta do próprio usuário (qualquer perfil). */
@RestController
@RequestMapping("/api")
public class AuthController {

    private final UserService users;

    public AuthController(UserService users) {
        this.users = users;
    }

    /** Público. Devolve o token a enviar em {@code Authorization: Bearer <token>}. */
    @PostMapping("/auth/login")
    public Session login(@Valid @RequestBody LoginRequest body) {
        return users.login(body.email(), body.password());
    }

    @GetMapping("/me")
    public UserInfo me(@AuthenticationPrincipal AuthUser user) {
        return users.me(user);
    }

    @PutMapping("/me")
    public UserInfo updateMe(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody ProfileUpdate body) {
        return users.updateProfile(user, body);
    }

    /** Troca a própria senha; as outras sessões são encerradas e um token novo é devolvido. */
    @PutMapping("/me/password")
    public Session changePassword(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody PasswordChange body) {
        return users.changePassword(user, body);
    }
}
