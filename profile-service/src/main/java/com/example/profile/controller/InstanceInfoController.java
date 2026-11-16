package com.example.profile.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Endpoint minh hoa Service Discovery; chi cong bo metadata khong nhay cam
 * va chi cho phep service noi bo co khoa hop le truy cap.
 */
@RestController
public class InstanceInfoController {

    @Value("${spring.application.name:profile-service}")
    private String applicationName;

    @Value("${server.port:8081}")
    private int instancePort;

    @Value("${eureka.instance.instance-id:}")
    private String instanceId;

    @Value("${app.internal.service-key:}")
    private String internalServiceKey;

    @GetMapping("/api/v1/instance-info")
    public ResponseEntity<Map<String, Object>> getInstanceInfo(
            @RequestHeader(value = "X-Internal-Service-Key", required = false) String suppliedKey) {
        if (!hasValidInternalKey(suppliedKey)) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("status", HttpStatus.FORBIDDEN.value());
            error.put("error", "Forbidden");
            error.put("message", "Endpoint chi danh cho giao tiep giua cac service.");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("serviceName", applicationName.toUpperCase().replace('_', '-'));
        result.put("serviceId", applicationName);
        result.put("instancePort", instancePort);
        result.put("port", instancePort);
        result.put("instanceId", (instanceId == null || instanceId.isBlank())
                ? applicationName + ":" + instancePort
                : instanceId);
        return ResponseEntity.ok(result);
    }

    private boolean hasValidInternalKey(String suppliedKey) {
        if (internalServiceKey == null || internalServiceKey.isBlank()
                || suppliedKey == null || suppliedKey.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                internalServiceKey.getBytes(StandardCharsets.UTF_8),
                suppliedKey.getBytes(StandardCharsets.UTF_8));
    }
}
