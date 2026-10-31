package com.campusclaw.common;

import com.campusclaw.health.DependencyHealthService;
import com.campusclaw.health.DeploymentVersion;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
    private final DependencyHealthService dependencies;
    private final DeploymentVersion version;

    public HealthController(DependencyHealthService dependencies, DeploymentVersion version) {
        this.dependencies = dependencies;
        this.version = version;
    }

    @GetMapping("/health")
    public ResponseEntity<HealthResponse> health() {
        Map<String, String> states = dependencies.snapshot();
        boolean ready = "up".equals(states.get("database")) && "up".equals(states.get("qdrant"));
        return ResponseEntity.status(ready ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .body(new HealthResponse(ready ? "ok" : "unhealthy", version.value(), Instant.now(), states));
    }

    public record HealthResponse(String status, String version, Instant time, Map<String, String> dependencies) { }
}
