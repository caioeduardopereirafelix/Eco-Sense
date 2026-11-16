package br.com.ecosense.security;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.web.SecurityFilterChain;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.ecosense.api.ApiDtos.ErrorBody;
import br.com.ecosense.persistence.UserEntity;
import br.com.ecosense.persistence.UserRepository;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain api(HttpSecurity http, UserRepository users, ObjectMapper json) throws Exception {
        http
                .csrf(c -> c.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers("/api/users/**").hasRole(UserEntity.ADMIN)
                        .requestMatchers("/api/thingspeak/**").hasRole(UserEntity.ADMIN)
                        .requestMatchers(HttpMethod.GET, "/api/devices/*/settings").hasRole(UserEntity.ADMIN)
                        .requestMatchers(HttpMethod.POST, "/api/devices").hasRole(UserEntity.ADMIN)
                        .requestMatchers(HttpMethod.PUT, "/api/devices/**").hasRole(UserEntity.ADMIN)
                        .requestMatchers(HttpMethod.DELETE, "/api/devices/**").hasRole(UserEntity.ADMIN)
                        .requestMatchers(HttpMethod.PATCH, "/api/maintenance-requests/**").hasRole(UserEntity.ADMIN)
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .oauth2ResourceServer(o -> o
                        .jwt(j -> j.jwtAuthenticationConverter(currentUser(users)))
                        .authenticationEntryPoint((req, res, e) ->
                                error(res, json, HttpServletResponse.SC_UNAUTHORIZED, "Faça login para continuar.")))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) ->
                                error(res, json, HttpServletResponse.SC_UNAUTHORIZED, "Faça login para continuar."))
                        .accessDeniedHandler((req, res, ex) ->
                                error(res, json, HttpServletResponse.SC_FORBIDDEN, "Acesso restrito a administradores.")));
        return http.build();
    }

    @Bean
    JwtDecoder jwtDecoder(TokenService tokens) {
        return tokens.decoder();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    static Converter<Jwt, AbstractAuthenticationToken> currentUser(UserRepository users) {
        return jwt -> {
            UserEntity user;
            try {
                user = users.findById(Long.parseLong(jwt.getSubject())).orElse(null);
            } catch (NumberFormatException e) {
                user = null;
            }
            Number version = jwt.getClaim("ver");
            if (user == null || version == null || version.intValue() != user.getTokenVersion()) {
                throw new InvalidBearerTokenException("Sessão encerrada. Entre novamente.");
            }
            return new AuthUserToken(AuthUser.of(user), jwt,
                    List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole())));
        };
    }

    private static void error(HttpServletResponse res, ObjectMapper json, int status, String message) throws IOException {
        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        json.writeValue(res.getOutputStream(), new ErrorBody(message));
    }
}
