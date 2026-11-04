package com.campusclaw.config;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import com.campusclaw.auth.JpaUserDetailsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = SecurityConfigTest.ProbeController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = AsyncConfig.class))
@AutoConfigureMockMvc
@Import({SecurityConfig.class, SecurityConfigTest.ProbeController.class})
class SecurityConfigTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean private AppProperties appProperties;
    @MockitoBean private JwtProperties jwtProperties;
    @MockitoBean private JpaUserDetailsService userDetailsService;
    @MockitoBean private JwtDecoder jwtDecoder;

    @Test
    void permitsDocumentationButProtectsBusinessApisWithBearerAuthentication() throws Exception {
        mvc.perform(get("/v3/api-docs/probe"))
                .andExpect(status().isOk());
        mvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/probe"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.error").value("Authentication required"));
        mvc.perform(get("/api/probe").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-Id"));
    }

    /** 仅为安全过滤链切片测试提供可预测的公开与受保护端点。 */
    @RestController
    public static class ProbeController {
        @GetMapping({"/v3/api-docs/probe", "/swagger-ui/index.html", "/api/probe"})
        public String ok() {
            return "ok";
        }
    }
}
