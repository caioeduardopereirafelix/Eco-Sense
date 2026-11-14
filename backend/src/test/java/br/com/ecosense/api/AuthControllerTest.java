package br.com.ecosense.api;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    WebApplicationContext ctx;
    MockMvc anon;
    MockMvc admin;

    @BeforeEach
    void setUp() throws Exception {
        anon = TestAuth.anonymous(ctx);
        admin = TestAuth.asAdmin(ctx);
    }

    private long createUser(String email, String password) throws Exception {
        String body = admin.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Maria Teste\",\"email\":\"" + email + "\",\"role\":\"USER\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("USER"))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    @Test
    void semLoginRecebe401() throws Exception {
        anon.perform(get("/api/devices"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Faça login para continuar."));
        anon.perform(get("/api/devices").header("Authorization", "Bearer token-invalido"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginCorretoEIncorreto() throws Exception {
        anon.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ADMIN@teste.local\",\"password\":\"senha-admin-teste\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.expiresAt").isString())
                .andExpect(jsonPath("$.user.role").value("ADMIN"));
        anon.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@teste.local\",\"password\":\"errada\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("E-mail ou senha incorretos."));
        anon.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ninguem@teste.local\",\"password\":\"qualquer1\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("E-mail ou senha incorretos."));
    }

    @Test
    void usuarioComumNaoAcessaFuncoesDeAdmin() throws Exception {
        createUser("comum@teste.local", "senha-comum-1");
        MockMvc user = TestAuth.as(ctx, TestAuth.token(ctx, "comum@teste.local", "senha-comum-1"));

        user.perform(get("/api/me")).andExpect(jsonPath("$.role").value("USER"));
        user.perform(get("/api/devices")).andExpect(status().isOk());
        user.perform(get("/api/forecasts")).andExpect(status().isOk());

        user.perform(get("/api/users"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Acesso restrito a administradores."));
        user.perform(delete("/api/devices/IOT-001")).andExpect(status().isForbidden());
        user.perform(get("/api/devices/IOT-001/settings")).andExpect(status().isForbidden());
        user.perform(get("/api/thingspeak/channels/1000001")).andExpect(status().isForbidden());
        user.perform(patch("/api/maintenance-requests/1").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"Agendada\"}")).andExpect(status().isForbidden());
    }

    @Test
    void usuarioVeSoAsPropriasManutencoes() throws Exception {
        createUser("pede@teste.local", "senha-pede-1");
        MockMvc user = TestAuth.as(ctx, TestAuth.token(ctx, "pede@teste.local", "senha-pede-1"));
        admin.perform(post("/api/maintenance-requests").contentType(MediaType.APPLICATION_JSON)
                .content("{\"deviceId\":\"IOT-002\",\"reason\":\"Pedido do admin\"}")).andExpect(status().isCreated());

        user.perform(post("/api/maintenance-requests").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deviceId\":\"IOT-003\",\"reason\":\"Bateria baixa\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requestedBy").value("Maria Teste"));
        user.perform(get("/api/maintenance-requests"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].reason").value("Bateria baixa"));
        admin.perform(get("/api/maintenance-requests"))
                .andExpect(jsonPath("$[*].reason", hasItem("Bateria baixa")))
                .andExpect(jsonPath("$[*].reason", hasItem("Pedido do admin")));
    }

    @Test
    void trocaDeSenhaEncerraAsOutrasSessoes() throws Exception {
        createUser("senha@teste.local", "senha-antiga-1");
        String oldToken = TestAuth.token(ctx, "senha@teste.local", "senha-antiga-1");
        MockMvc user = TestAuth.as(ctx, oldToken);

        user.perform(put("/api/me/password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"errada\",\"newPassword\":\"senha-nova-12\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A senha atual está incorreta."));
        user.perform(put("/api/me/password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"senha-antiga-1\",\"newPassword\":\"curta\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A nova senha deve ter de 8 a 72 caracteres."));
        String body = user.perform(put("/api/me/password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"senha-antiga-1\",\"newPassword\":\"senha-nova-12\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        TestAuth.as(ctx, oldToken).perform(get("/api/me")).andExpect(status().isUnauthorized());
        TestAuth.as(ctx, JsonPath.read(body, "$.token")).perform(get("/api/me")).andExpect(status().isOk());
        TestAuth.as(ctx, TestAuth.token(ctx, "senha@teste.local", "senha-nova-12"))
                .perform(put("/api/me").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Maria Renomeada\"}"))
                .andExpect(jsonPath("$.name").value("Maria Renomeada"));
    }

    @Test
    void gestaoDeUsuariosPeloAdmin() throws Exception {
        long id = createUser("gestao@teste.local", "senha-gestao-1");
        String userToken = TestAuth.token(ctx, "gestao@teste.local", "senha-gestao-1");

        admin.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Outro\",\"email\":\"GESTAO@teste.local\",\"role\":\"USER\",\"password\":\"12345678\"}"))
                .andExpect(status().isConflict());
        admin.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Outro\",\"email\":\"nao-e-email\",\"role\":\"USER\",\"password\":\"12345678\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Informe um e-mail válido."));

        admin.perform(put("/api/users/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Gestor\",\"email\":\"gestao@teste.local\",\"role\":\"ADMIN\"}"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
        TestAuth.as(ctx, userToken).perform(get("/api/users")).andExpect(status().isOk());

        admin.perform(put("/api/users/" + id + "/password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"redefinida-1\"}")).andExpect(status().isNoContent());
        TestAuth.as(ctx, userToken).perform(get("/api/me")).andExpect(status().isUnauthorized());

        admin.perform(delete("/api/users/" + id)).andExpect(status().isNoContent());
        admin.perform(get("/api/users")).andExpect(jsonPath("$[*].email", not(hasItem("gestao@teste.local"))));
    }

    @Test
    void adminNaoSeRemoveNemMudaOProprioPerfil() throws Exception {
        String me = admin.perform(get("/api/me")).andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(me, "$.id")).longValue();
        admin.perform(delete("/api/users/" + id))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Você não pode remover o seu próprio usuário."));
        admin.perform(put("/api/users/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Admin\",\"email\":\"admin@teste.local\",\"role\":\"USER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Você não pode alterar o seu próprio perfil."));
    }
}
