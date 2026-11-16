package com.civilmate.api.health;

import java.time.Instant;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {
    @GetMapping
    public Map<String, Object> health() {
        return Map.of("success", true, "message", "CivilMate API is running", "data", Map.of("status", "UP"), "timestamp", Instant.now());
    }
}
