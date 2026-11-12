package br.com.ecosense.api;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.jayway.jsonpath.JsonPath;

/** Ajuda dos testes: faz login de verdade e devolve um MockMvc que envia o token em toda requisição. */
final class TestAuth {

    static final String ADMIN_EMAIL = "admin@teste.local";
    static final String ADMIN_PASSWORD = "senha-admin-teste"; // definida em application-test.yml

    private TestAuth() {}

    static MockMvc anonymous(WebApplicationContext ctx) {
        return MockMvcBuilders.webAppContextSetup(ctx).apply(springSecurity()).build();
    }

    static String token(WebApplicationContext ctx, String email, String password) throws Exception {
        String body = anonymous(ctx).perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    static MockMvc as(WebApplicationContext ctx, String token) {
        return MockMvcBuilders.webAppContextSetup(ctx).apply(springSecurity())
                .defaultRequest(get("/").header("Authorization", "Bearer " + token)).build();
    }

    static MockMvc asAdmin(WebApplicationContext ctx) throws Exception {
        return as(ctx, token(ctx, ADMIN_EMAIL, ADMIN_PASSWORD));
    }
}
