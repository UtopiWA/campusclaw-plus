package com.campusclaw.common;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.campusclaw.health.DependencyHealthService;
import com.campusclaw.health.DeploymentVersion;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class HealthControllerTest {
    @Test void statusVersionTimeAndQueryCannotChangeProbeScope() throws Exception {
        var dependencies = mock(DependencyHealthService.class);
        var version = mock(DeploymentVersion.class);
        when(version.value()).thenReturn("0.1.0");
        var mvc = MockMvcBuilders.standaloneSetup(new HealthController(dependencies, version)).build();
        when(dependencies.snapshot()).thenReturn(Map.of("database", "up", "qdrant", "up"));
        mvc.perform(get("/health?target=chat&url=http://private"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.version").value("0.1.0")).andExpect(jsonPath("$.time").exists());
        for (var failed : new String[]{"database", "qdrant"}) {
            when(dependencies.snapshot()).thenReturn(Map.of("database", failed.equals("database") ? "down" : "up",
                    "qdrant", failed.equals("qdrant") ? "down" : "up"));
            mvc.perform(get("/health")).andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.status").value("unhealthy"));
        }
        verify(dependencies, times(3)).snapshot();
        verifyNoMoreInteractions(dependencies);
    }
}
