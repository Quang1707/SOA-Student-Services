package com.example.document.client;

import org.springframework.beans.factory.annotation.Value;
import jakarta.annotation.PostConstruct;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.SocketTimeoutException;
import java.util.NoSuchElementException;
import java.util.Map;

@Component
public class ProfileClient {

    private final RestTemplate restTemplate;

    @Value("${profile.service.url:http://profile-service/api/v1/profiles}")
    private String profileServiceUrl;

    @Value("${profile.service.instance-info-url:http://profile-service/api/v1/instance-info}")
    private String profileServiceInstanceInfoUrl;

    @Value("${app.internal.service-key}")
    private String internalServiceKey;

    public ProfileClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @PostConstruct
    void validateInternalServiceKey() {
        if (internalServiceKey == null || internalServiceKey.isBlank()) {
            throw new IllegalStateException("INTERNAL_SERVICE_KEY chưa được cấu hình. Hãy chạy scripts/configure-local-secrets.ps1.");
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public Map<String, Object> verifyStudentProfile(String studentId, boolean simulateDelay) {
        String url = profileServiceUrl + "/" + studentId + "?simulateDelay=" + simulateDelay;
        try {
            // Giao tiếp liên dịch vụ qua Eureka/LoadBalancer, không phụ thuộc IP/cổng cố
            // định.
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-Service-Key", internalServiceKey);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            return (Map<String, Object>) response.getBody();
        } catch (HttpClientErrorException.NotFound ex) {
            throw new NoSuchElementException(
                    "STUDENT_NOT_FOUND: Sinh viên với mã " + studentId + " không tồn tại bên Profile Service.");
        } catch (ResourceAccessException ex) {
            if (ex.getCause() instanceof SocketTimeoutException) {
                throw new IllegalStateException("TIMEOUT: Profile Service phản hồi quá thời gian quy định");
            }
            throw new IllegalStateException(
                    "PROFILE_SERVICE_UNAVAILABLE: Không thể kết nối tới Profile Service qua Eureka Discovery");
        } catch (HttpClientErrorException | HttpServerErrorException ex) {
            throw new RuntimeException("INTEGRATION_ERROR: Profile Service trả về lỗi HTTP " + ex.getStatusCode());
        } catch (IllegalStateException ex) {
            // Spring Cloud LoadBalancer ném IllegalStateException khi Registry không còn
            // instance phù hợp (ví dụ: "No instances available for profile-service").
            throw new IllegalStateException(
                    "PROFILE_SERVICE_UNAVAILABLE: Không tìm thấy instance Profile Service khả dụng qua Eureka.", ex);
        } catch (Exception ex) {
            throw new RuntimeException("INTEGRATION_ERROR: Lỗi hệ thống không xác định khi kiểm tra hồ sơ.");
        }
    }

    /** Gọi endpoint metadata qua Service ID để quan sát instance do LoadBalancer lựa chọn. */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public Map<String, Object> getProfileInstanceInfo() {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-Service-Key", internalServiceKey);
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity response = restTemplate.exchange(
                    profileServiceInstanceInfoUrl, HttpMethod.GET, entity, Map.class);
            return (Map<String, Object>) response.getBody();
        } catch (ResourceAccessException ex) {
            throw new IllegalStateException(
                    "PROFILE_SERVICE_UNAVAILABLE: Không thể kết nối tới Profile Service qua Eureka Discovery.", ex);
        } catch (IllegalStateException ex) {
            throw new IllegalStateException(
                    "PROFILE_SERVICE_UNAVAILABLE: Không tìm thấy instance Profile Service khả dụng qua Eureka.", ex);
        } catch (HttpClientErrorException | HttpServerErrorException ex) {
            throw new RuntimeException("INTEGRATION_ERROR: Profile Service trả về lỗi HTTP " + ex.getStatusCode(), ex);
        } catch (Exception ex) {
            throw new RuntimeException("INTEGRATION_ERROR: Không thể lấy thông tin instance của Profile Service.", ex);
        }
    }
}