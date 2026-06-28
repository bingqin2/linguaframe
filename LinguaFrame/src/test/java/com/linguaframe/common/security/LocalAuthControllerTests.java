package com.linguaframe.common.security;

import com.linguaframe.LinguaFrameApplication;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LocalAuthControllerTests {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";
    private static final String PASSWORD = "owner-password";

    @Nested
    @SpringBootTest(classes = LinguaFrameApplication.class)
    @AutoConfigureMockMvc
    @ActiveProfiles("test")
    class DisabledAuthMode {

        @Autowired
        private MockMvc mockMvc;

        @Test
        void returnsDisabledSessionStatusWithoutSecrets() throws Exception {
            mockMvc.perform(get("/api/auth/session"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.enabled").value(false))
                    .andExpect(jsonPath("$.configured").value(false))
                    .andExpect(jsonPath("$.authenticated").value(false))
                    .andExpect(jsonPath("$.authMode").value("LOCAL_AUTH_DISABLED"))
                    .andExpect(jsonPath("$.ownershipScope").value("CONFIGURED_DEMO_OWNER"))
                    .andExpect(jsonPath("$.ownerId").value("demo-owner"))
                    .andExpect(jsonPath("$.username").value("owner"));
        }

        @Test
        void rejectsLoginWhenLocalAuthIsNotConfigured() throws Exception {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"owner\",\"password\":\"owner-password\"}"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.authenticated").value(false))
                    .andExpect(jsonPath("$.authMode").value("LOCAL_AUTH_DISABLED"))
                    .andExpect(content().string(not(containsString(PASSWORD))));
        }
    }

    @Nested
    @SpringBootTest(
            classes = LinguaFrameApplication.class,
            properties = {
                    "linguaframe.auth.enabled=true",
                    "linguaframe.auth.owner-username=owner",
                    "linguaframe.auth.owner-password=" + PASSWORD,
                    "linguaframe.auth.jwt-secret=" + SECRET,
                    "linguaframe.auth.token-ttl-minutes=15",
                    "linguaframe.demo.owner-id=owner-alpha"
            }
    )
    @AutoConfigureMockMvc
    @ActiveProfiles("test")
    class ConfiguredAuthMode {

        @Autowired
        private MockMvc mockMvc;

        @Test
        void returnsConfiguredUnauthenticatedSessionStatusWithoutSecrets() throws Exception {
            mockMvc.perform(get("/api/auth/session"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.enabled").value(true))
                    .andExpect(jsonPath("$.configured").value(true))
                    .andExpect(jsonPath("$.authenticated").value(false))
                    .andExpect(jsonPath("$.authMode").value("LOCAL_AUTH_REQUIRED"))
                    .andExpect(jsonPath("$.ownershipScope").value("CONFIGURED_DEMO_OWNER"))
                    .andExpect(jsonPath("$.ownerId").value("owner-alpha"))
                    .andExpect(jsonPath("$.username").value("owner"))
                    .andExpect(content().string(not(containsString(SECRET))))
                    .andExpect(content().string(not(containsString(PASSWORD))));
        }

        @Test
        void rejectsWrongPasswordWithoutLeakingSecrets() throws Exception {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"owner\",\"password\":\"wrong-password\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.session.authenticated").value(false))
                    .andExpect(jsonPath("$.session.authMode").value("LOCAL_AUTH_REQUIRED"))
                    .andExpect(content().string(not(containsString(SECRET))))
                    .andExpect(content().string(not(containsString(PASSWORD))))
                    .andExpect(content().string(not(containsString("wrong-password"))));
        }

        @Test
        void acceptsValidLoginAndReturnsBearerTokenWithSession() throws Exception {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"owner\",\"password\":\"owner-password\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").isString())
                    .andExpect(jsonPath("$.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.expiresAt").isString())
                    .andExpect(jsonPath("$.session.enabled").value(true))
                    .andExpect(jsonPath("$.session.configured").value(true))
                    .andExpect(jsonPath("$.session.authenticated").value(true))
                    .andExpect(jsonPath("$.session.authMode").value("LOCAL_AUTH_ACTIVE"))
                    .andExpect(jsonPath("$.session.ownershipScope").value("LOCAL_AUTH_OWNER"))
                    .andExpect(jsonPath("$.session.ownerId").value("owner-alpha"))
                    .andExpect(jsonPath("$.session.username").value("owner"))
                    .andExpect(content().string(not(containsString(SECRET))))
                    .andExpect(content().string(not(containsString(PASSWORD))));
        }

        @Test
        void logoutReturnsUnauthenticatedStatelessSession() throws Exception {
            mockMvc.perform(post("/api/auth/logout"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.enabled").value(true))
                    .andExpect(jsonPath("$.configured").value(true))
                    .andExpect(jsonPath("$.authenticated").value(false))
                    .andExpect(jsonPath("$.authMode").value("LOCAL_AUTH_REQUIRED"))
                    .andExpect(jsonPath("$.ownershipScope").value("CONFIGURED_DEMO_OWNER"));
        }
    }
}
