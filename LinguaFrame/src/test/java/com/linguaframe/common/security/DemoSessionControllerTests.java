package com.linguaframe.common.security;

import com.linguaframe.LinguaFrameApplication;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DemoSessionControllerTests {

    @Nested
    @SpringBootTest(classes = LinguaFrameApplication.class)
    @AutoConfigureMockMvc
    @ActiveProfiles("test")
    class OpenDemoMode {

        @Autowired
        private MockMvc mockMvc;

        @Test
        void returnsOpenSessionStatusWhenNoTokenConfigured() throws Exception {
            mockMvc.perform(get("/api/demo-session"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessGateEnabled").value(false))
                    .andExpect(jsonPath("$.authenticated").value(true))
                    .andExpect(jsonPath("$.headerName").value("X-LinguaFrame-Demo-Token"))
                    .andExpect(jsonPath("$.mode").value("OPEN"));
        }
    }

    @Nested
    @SpringBootTest(
            classes = LinguaFrameApplication.class,
            properties = "linguaframe.demo.access-token=test-token"
    )
    @AutoConfigureMockMvc
    @ActiveProfiles("test")
    class GatedDemoMode {

        @Autowired
        private MockMvc mockMvc;

        @Test
        void returnsUnauthenticatedStatusWithoutExposingConfiguredToken() throws Exception {
            mockMvc.perform(get("/api/demo-session"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.accessGateEnabled").value(true))
                    .andExpect(jsonPath("$.authenticated").value(false))
                    .andExpect(jsonPath("$.headerName").value("X-LinguaFrame-Demo-Token"))
                    .andExpect(jsonPath("$.mode").value("OWNER_SESSION_REQUIRED"))
                    .andExpect(content().string(not(containsString("test-token"))));
        }

        @Test
        void rejectsInvalidLoginWithoutSettingSessionCookie() throws Exception {
            mockMvc.perform(post("/api/demo-session/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"token\":\"wrong-token\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.accessGateEnabled").value(true))
                    .andExpect(jsonPath("$.authenticated").value(false))
                    .andExpect(jsonPath("$.mode").value("OWNER_SESSION_REQUIRED"))
                    .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                    .andExpect(content().string(not(containsString("test-token"))));
        }

        @Test
        void acceptsValidLoginAndSetsHttpOnlySameSiteSessionCookie() throws Exception {
            mockMvc.perform(post("/api/demo-session/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"token\":\"test-token\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessGateEnabled").value(true))
                    .andExpect(jsonPath("$.authenticated").value(true))
                    .andExpect(jsonPath("$.mode").value("OWNER_SESSION_ACTIVE"))
                    .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("LinguaFrame-Demo-Token=test-token")))
                    .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                    .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Lax")))
                    .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/")));
        }

        @Test
        void clearsSessionCookieOnLogout() throws Exception {
            mockMvc.perform(post("/api/demo-session/logout"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessGateEnabled").value(true))
                    .andExpect(jsonPath("$.authenticated").value(false))
                    .andExpect(jsonPath("$.mode").value("OWNER_SESSION_REQUIRED"))
                    .andExpect(cookie().maxAge("LinguaFrame-Demo-Token", 0))
                    .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Lax")));
        }

        @Test
        void acceptsProtectedApiAfterSessionLoginCookieIsReturned() throws Exception {
            MvcResult login = mockMvc.perform(post("/api/demo-session/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"token\":\"test-token\"}"))
                    .andExpect(status().isOk())
                    .andReturn();

            String setCookie = login.getResponse().getHeader(HttpHeaders.SET_COOKIE);
            String cookieValue = setCookie.substring(
                    "LinguaFrame-Demo-Token=".length(),
                    setCookie.indexOf(';')
            );

            mockMvc.perform(get("/api/runtime/dependencies")
                            .cookie(new MockCookie("LinguaFrame-Demo-Token", cookieValue)))
                    .andExpect(status().isOk());
        }
    }
}
