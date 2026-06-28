package com.linguaframe.common.security;

import com.linguaframe.LinguaFrameApplication;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LocalAuthInterceptorTests {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";
    private static final String PASSWORD = "owner-password";

    @Nested
    @SpringBootTest(
            classes = LinguaFrameApplication.class,
            properties = {
                    "linguaframe.auth.enabled=true",
                    "linguaframe.auth.owner-username=owner",
                    "linguaframe.auth.owner-password=" + PASSWORD,
                    "linguaframe.auth.jwt-secret=" + SECRET,
                    "linguaframe.demo.owner-id=owner-alpha"
            }
    )
    @AutoConfigureMockMvc
    @ActiveProfiles("test")
    class ConfiguredAuthMode {

        @Autowired
        private MockMvc mockMvc;

        @Test
        void rejectsProtectedApiWithoutBearerTokenWhenLocalAuthIsConfigured() throws Exception {
            mockMvc.perform(get("/api/runtime/dependencies"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.error").value("LOCAL_AUTH_REQUIRED"))
                    .andExpect(jsonPath("$.message").value("Local account authentication is required."))
                    .andExpect(content().string(not(containsString(SECRET))))
                    .andExpect(content().string(not(containsString(PASSWORD))));
        }

        @Test
        void rejectsProtectedApiWithInvalidBearerToken() throws Exception {
            mockMvc.perform(get("/api/runtime/dependencies")
                            .header("Authorization", "Bearer invalid-token"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("LOCAL_AUTH_REQUIRED"))
                    .andExpect(content().string(not(containsString("invalid-token"))));
        }

        @Test
        void acceptsProtectedApiWithBearerToken() throws Exception {
            String token = loginToken();

            mockMvc.perform(get("/api/media/uploads/preflight")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("\"ownerId\":\"owner-alpha\"")));
        }

        @Test
        void leavesAuthAndDemoSessionEndpointsOpen() throws Exception {
            mockMvc.perform(get("/api/auth/session"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.configured").value(true));

            mockMvc.perform(get("/api/demo-session"))
                    .andExpect(status().isOk());
        }

        private String loginToken() throws Exception {
            MvcResult result = mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"owner\",\"password\":\"owner-password\"}"))
                    .andExpect(status().isOk())
                    .andReturn();
            String body = result.getResponse().getContentAsString();
            int start = body.indexOf("\"token\":\"") + "\"token\":\"".length();
            int end = body.indexOf('"', start);
            return body.substring(start, end);
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
                    "linguaframe.demo.access-token=demo-token",
                    "linguaframe.demo.owner-id=owner-alpha"
            }
    )
    @AutoConfigureMockMvc
    @ActiveProfiles("test")
    class DemoCompatibilityMode {

        @Autowired
        private MockMvc mockMvc;

        @Test
        void acceptsProtectedApiWithExistingDemoHeaderToken() throws Exception {
            mockMvc.perform(get("/api/runtime/dependencies")
                            .header("X-LinguaFrame-Demo-Token", "demo-token"))
                    .andExpect(status().isOk());
        }

        @Test
        void acceptsProtectedApiWithExistingDemoCookieToken() throws Exception {
            mockMvc.perform(get("/api/runtime/dependencies")
                            .cookie(new MockCookie("LinguaFrame-Demo-Token", "demo-token")))
                    .andExpect(status().isOk());
        }

        @Test
        void acceptsProtectedApiWithBearerTokenWhenDemoGateIsAlsoConfigured() throws Exception {
            String token = loginToken();

            mockMvc.perform(get("/api/media/uploads/preflight")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("\"ownerId\":\"owner-alpha\"")));
        }

        private String loginToken() throws Exception {
            MvcResult result = mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"owner\",\"password\":\"owner-password\"}"))
                    .andExpect(status().isOk())
                    .andReturn();
            String body = result.getResponse().getContentAsString();
            int start = body.indexOf("\"token\":\"") + "\"token\":\"".length();
            int end = body.indexOf('"', start);
            return body.substring(start, end);
        }
    }
}
