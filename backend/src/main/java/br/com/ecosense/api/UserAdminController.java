package br.com.ecosense.api;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.ecosense.api.ApiDtos.NewUser;
import br.com.ecosense.api.ApiDtos.PasswordReset;
import br.com.ecosense.api.ApiDtos.UserInfo;
import br.com.ecosense.api.ApiDtos.UserUpdate;
import br.com.ecosense.security.AuthUser;
import br.com.ecosense.service.UserService;

/** Gestão de usuários — só administradores (regra em SecurityConfig). */
@RestController
@RequestMapping("/api/users")
public class UserAdminController {

    private final UserService users;

    public UserAdminController(UserService users) {
        this.users = users;
    }

    @GetMapping
    public List<UserInfo> list() {
        return users.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserInfo create(@Valid @RequestBody NewUser body) {
        return users.create(body);
    }

    @PutMapping("/{id}")
    public UserInfo update(@AuthenticationPrincipal AuthUser user, @PathVariable long id,
                           @Valid @RequestBody UserUpdate body) {
        return users.update(user, id, body);
    }

    @PutMapping("/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@PathVariable long id, @Valid @RequestBody PasswordReset body) {
        users.resetPassword(id, body.password());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthUser user, @PathVariable long id) {
        users.delete(user, id);
    }
}
