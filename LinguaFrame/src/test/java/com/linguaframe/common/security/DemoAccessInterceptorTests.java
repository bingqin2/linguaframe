package com.linguaframe.common.security;

import com.linguaframe.LinguaFrameApplication;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DemoAccessInterceptorTests {

    @Nested
    @SpringBootTest(classes = LinguaFrameApplication.class)
    @AutoConfigureMockMvc
    @ActiveProfiles("test")
    class OpenDemoMode {

        @Autowired
        private MockMvc mockMvc;

        @Test
        void leavesApiOpenWhenNoTokenConfigured() throws Exception {
            mockMvc.perform(get("/api/runtime/dependencies"))
                    .andExpect(status().isOk());
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
        void rejectsProtectedApiRequestWithoutToken() throws Exception {
            mockMvc.perform(get("/api/runtime/dependencies"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.error").value("DEMO_ACCESS_REQUIRED"))
                    .andExpect(jsonPath("$.message").value("Demo access token is required."))
                    .andExpect(content().string(not(containsString("test-token"))));
        }

        @Test
        void rejectsProtectedApiRequestWithWrongToken() throws Exception {
            mockMvc.perform(get("/api/runtime/dependencies")
                            .header("X-LinguaFrame-Demo-Token", "wrong-token"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("DEMO_ACCESS_REQUIRED"));
        }

        @Test
        void acceptsProtectedApiRequestWithConfiguredToken() throws Exception {
            mockMvc.perform(get("/api/runtime/dependencies")
                            .header("X-LinguaFrame-Demo-Token", "test-token"))
                    .andExpect(status().isOk());
        }

        @Test
        void acceptsProtectedApiRequestWithConfiguredCookieToken() throws Exception {
            mockMvc.perform(get("/api/runtime/dependencies")
                            .cookie(new MockCookie("LinguaFrame-Demo-Token", "test-token")))
                    .andExpect(status().isOk());
        }

        @Test
        void leavesDeploymentReadinessEndpointsOpen() throws Exception {
            mockMvc.perform(get("/actuator/health"))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/v3/api-docs"))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/swagger-ui/index.html"))
                    .andExpect(status().isOk());
        }
    }
}
