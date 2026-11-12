package br.com.ecosense.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.ecosense.api.ApiDtos.NewUser;
import br.com.ecosense.api.ApiDtos.PasswordChange;
import br.com.ecosense.api.ApiDtos.ProfileUpdate;
import br.com.ecosense.api.ApiDtos.Session;
import br.com.ecosense.api.ApiDtos.UserInfo;
import br.com.ecosense.api.ApiDtos.UserUpdate;
import br.com.ecosense.api.ApiException;
import br.com.ecosense.config.AuthProperties;
import br.com.ecosense.persistence.UserEntity;
import br.com.ecosense.persistence.UserRepository;
import br.com.ecosense.security.AuthUser;
import br.com.ecosense.security.TokenService;
import br.com.ecosense.security.TokenService.IssuedToken;

/**
 * Login, conta do usuário e gestão de usuários (admin).
 * Na primeira inicialização (tabela vazia) cria o administrador definido em ecosense.auth.
 */
@Service
public class UserService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    private static final String BAD_LOGIN = "E-mail ou senha incorretos.";

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final TokenService tokens;
    private final AuthProperties props;
    private final Clock clock;
    /* hash de uma senha qualquer: o login de e-mail inexistente leva o mesmo tempo que o de senha errada */
    private final String dummyHash;

    public UserService(UserRepository users, PasswordEncoder encoder, TokenService tokens,
                       AuthProperties props, Clock clock) {
        this.users = users;
        this.encoder = encoder;
        this.tokens = tokens;
        this.props = props;
        this.clock = clock;
        this.dummyHash = encoder.encode("ecosense-dummy-password");
    }

    /* ---------------- primeiro administrador ---------------- */

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.count() > 0) return;
        String password = props.adminPassword();
        boolean generated = password == null || password.isBlank();
        if (generated) password = randomPassword();
        else if (password.length() < 8) throw new IllegalStateException("ECOSENSE_ADMIN_PASSWORD precisa ter pelo menos 8 caracteres.");
        UserEntity admin = newEntity(props.adminName(), props.adminEmail(), UserEntity.ADMIN, password);
        users.save(admin);
        if (generated) {
            log.warn("""

                    ==============================================================
                     Primeiro administrador criado.
                       E-mail: {}
                       Senha:  {}
                     Anote a senha (ela não será mostrada de novo) e troque-a em
                     Configurações → Minha senha. Para escolher a senha, defina
                     ECOSENSE_ADMIN_PASSWORD no .env antes da primeira execução.
                    ==============================================================""", admin.getEmail(), password);
        } else {
            log.info("Primeiro administrador criado: {}", admin.getEmail());
        }
    }

    /* ---------------- login e conta ---------------- */

    @Transactional(readOnly = true)
    public Session login(String email, String password) {
        UserEntity user = users.findByEmailIgnoreCase(email.trim()).orElse(null);
        if (user == null) {
            encoder.matches(password, dummyHash);
            throw new ApiException(HttpStatus.UNAUTHORIZED, BAD_LOGIN);
        }
        if (!encoder.matches(password, user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, BAD_LOGIN);
        }
        return session(user);
    }

    @Transactional(readOnly = true)
    public UserInfo me(AuthUser current) {
        return toInfo(entity(current.id()));
    }

    @Transactional
    public UserInfo updateProfile(AuthUser current, ProfileUpdate body) {
        UserEntity user = entity(current.id());
        user.setName(body.name().trim());
        return toInfo(users.save(user));
    }

    /** Troca a própria senha. Encerra as outras sessões e devolve um token novo para esta. */
    @Transactional
    public Session changePassword(AuthUser current, PasswordChange body) {
        UserEntity user = entity(current.id());
        if (!encoder.matches(body.currentPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("A senha atual está incorreta.");
        }
        setPassword(user, body.newPassword());
        return session(users.save(user));
    }

    /* ---------------- gestão de usuários (admin) ---------------- */

    @Transactional(readOnly = true)
    public List<UserInfo> list() {
        return users.findAllByOrderByNameAscIdAsc().stream().map(UserService::toInfo).toList();
    }

    @Transactional
    public UserInfo create(NewUser body) {
        String email = normalizeEmail(body.email());
        if (users.existsByEmailIgnoreCase(email)) throw emailInUse(email);
        return toInfo(users.save(newEntity(body.name().trim(), email, body.role(), body.password())));
    }

    /** Não é permitido mudar o próprio perfil: assim sempre resta pelo menos um administrador. */
    @Transactional
    public UserInfo update(AuthUser current, long id, UserUpdate body) {
        UserEntity user = entity(id);
        String email = normalizeEmail(body.email());
        if (!email.equalsIgnoreCase(user.getEmail()) && users.existsByEmailIgnoreCase(email)) throw emailInUse(email);
        if (id == current.id() && !body.role().equals(user.getRole())) {
            throw ApiException.badRequest("Você não pode alterar o seu próprio perfil.");
        }
        user.setName(body.name().trim());
        user.setEmail(email);
        user.setRole(body.role());
        return toInfo(users.save(user));
    }

    /** Define uma nova senha para o usuário (ex.: esqueceu a senha) e encerra as sessões dele. */
    @Transactional
    public void resetPassword(long id, String password) {
        UserEntity user = entity(id);
        setPassword(user, password);
        users.save(user);
    }

    @Transactional
    public void delete(AuthUser current, long id) {
        if (id == current.id()) throw ApiException.badRequest("Você não pode remover o seu próprio usuário.");
        users.delete(entity(id));
    }

    /* ---------------- auxiliares ---------------- */

    private Session session(UserEntity user) {
        IssuedToken token = tokens.issue(user);
        return new Session(token.value(), token.expiresAt(), toInfo(user));
    }

    private UserEntity newEntity(String name, String email, String role, String password) {
        UserEntity e = new UserEntity();
        e.setName(name);
        e.setEmail(normalizeEmail(email));
        e.setRole(role);
        e.setPasswordHash(encoder.encode(password));
        e.setTokenVersion(0);
        e.setCreatedAt(Instant.now(clock).truncatedTo(ChronoUnit.SECONDS));
        return e;
    }

    private void setPassword(UserEntity user, String password) {
        user.setPasswordHash(encoder.encode(password));
        user.setTokenVersion(user.getTokenVersion() + 1); // tokens antigos deixam de valer
    }

    private UserEntity entity(long id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("Usuário não encontrado."));
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static ApiException emailInUse(String email) {
        return new ApiException(HttpStatus.CONFLICT, "Já existe um usuário com o e-mail " + email + ".");
    }

    private static UserInfo toInfo(UserEntity e) {
        return new UserInfo(e.getId(), e.getName(), e.getEmail(), e.getRole(), e.getCreatedAt());
    }

    private static String randomPassword() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 14; i++) sb.append(chars.charAt(random.nextInt(chars.length())));
        return sb.toString();
    }
}
