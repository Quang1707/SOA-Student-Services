package com.example.profile.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class InstanceInfoControllerTest {

    private InstanceInfoController controller;

    @BeforeEach
    void setUp() {
        controller = new InstanceInfoController();
        ReflectionTestUtils.setField(controller, "applicationName", "profile-service");
        ReflectionTestUtils.setField(controller, "instancePort", 8084);
        ReflectionTestUtils.setField(controller, "instanceId", "profile-service:8084");
        ReflectionTestUtils.setField(controller, "internalServiceKey", "test-internal-key");
    }

    @Test
    void rejectsMissingOrInvalidInternalKey() {
        assertEquals(HttpStatus.FORBIDDEN,
                controller.getInstanceInfo(null).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN,
                controller.getInstanceInfo("wrong-key").getStatusCode());
    }

    @Test
    void returnsOnlyInstanceMetadataForValidInternalKey() {
        var response = controller.getInstanceInfo("test-internal-key");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("PROFILE-SERVICE", response.getBody().get("serviceName"));
        assertEquals("profile-service", response.getBody().get("serviceId"));
        assertEquals(8084, response.getBody().get("instancePort"));
        assertEquals("profile-service:8084", response.getBody().get("instanceId"));
        assertFalse(response.getBody().containsKey("internalServiceKey"));
    }
}
