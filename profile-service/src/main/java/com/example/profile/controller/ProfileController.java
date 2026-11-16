package com.example.profile.controller;

import com.example.profile.model.StudentProfile;
import com.example.profile.security.AuthenticatedUser;
import com.example.profile.service.ProfileService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping({"/api/v1/profiles", "/api/profiles"})
public class ProfileController {

    @Autowired
    private ProfileService profileService;

    private AuthenticatedUser getUser(Authentication auth) {
        return (auth != null && auth.getPrincipal() instanceof AuthenticatedUser u) ? u : null;
    }

    @GetMapping("/hello")
    public Map<String, String> hello() {
        return Map.of("message", "Welcome to Student Profile Service (JPA + Spring Security + JWT)");
    }

    @GetMapping("/info")
    public Map<String, String> info() {
        return Map.of(
                "service", "Student Profile & Auth Service",
                "database", "profile_db (PostgreSQL/Flyway)",
                "security", "Spring Security + JWT (HS256) + BCrypt + RBAC + Ownership",
                "version", "5.0",
                "status", "UP"
        );
    }

    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getStatistics() {
        return ResponseEntity.ok(profileService.getStatistics());
    }

    @PreAuthorize("hasAnyRole('STUDENT', 'STAFF', 'ADMIN')")
    @GetMapping
    public ResponseEntity<Map<String, Object>> getProfiles(
            Authentication authentication,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String gender,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String major,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "studentId,asc") String sort) {
        return ResponseEntity.ok(profileService.queryProfiles(getUser(authentication), name, gender, status, major, page, size, sort));
    }

    @PreAuthorize("hasAnyRole('STUDENT', 'STAFF', 'ADMIN', 'INTERNAL_SERVICE')")
    @GetMapping("/{id}")
    public ResponseEntity<StudentProfile> getProfile(
            Authentication authentication,
            @PathVariable String id,
            @RequestParam(defaultValue = "false") boolean simulateDelay) throws InterruptedException {
        if (simulateDelay) {
            Thread.sleep(5000);
        }
        return ResponseEntity.ok(profileService.getProfile(id, getUser(authentication)));
    }

    @PreAuthorize("hasAnyRole('STUDENT', 'STAFF', 'ADMIN')")
    @GetMapping("/v2/{id}")
    public ResponseEntity<Map<String, Object>> getProfileV2(Authentication authentication, @PathVariable String id) {
        StudentProfile p = profileService.getProfile(id, getUser(authentication));
        String[] parts = p.getFullName().trim().split("\\s+", 2);
        Map<String, Object> v2Response = new LinkedHashMap<>();
        v2Response.put("studentId", p.getStudentId());
        v2Response.put("name", Map.of(
                "lastName", parts[0],
                "firstName", parts.length > 1 ? parts[1] : ""
        ));
        v2Response.put("email", p.getEmail());
        v2Response.put("major", p.getMajor());
        v2Response.put("status", p.getStatus());
        return ResponseEntity.ok(v2Response);
    }

    // TH5 Giai đoạn 3 Bước 2: Chỉ STAFF và ADMIN mới được tiếp nhận/tạo mới hồ sơ sinh viên
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    @PostMapping
    public ResponseEntity<StudentProfile> createProfile(
            Authentication authentication,
            @Valid @RequestBody StudentProfile profile) {
        StudentProfile created = profileService.createProfile(profile, getUser(authentication));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<StudentProfile> updateProfile(
            Authentication authentication,
            @PathVariable String id,
            @Valid @RequestBody StudentProfile profile) {
        return ResponseEntity.ok(profileService.updateProfile(id, profile, getUser(authentication)));
    }

    // STUDENT được phép PATCH hồ sơ của chính mình (kiểm tra ownership trong service).
    // Không cấp quyền PATCH cho INTERNAL_SERVICE.
    @PreAuthorize("hasAnyRole('STUDENT', 'STAFF', 'ADMIN')")
    @PatchMapping("/{id}")
    public ResponseEntity<StudentProfile> patchProfile(
            Authentication authentication,
            @PathVariable String id,
            @RequestBody Map<String, Object> updates) {
        return ResponseEntity.ok(profileService.patchProfile(id, updates, getUser(authentication)));
    }

    // TH5: Chỉ ADMIN mới có thẩm quyền phê duyệt kích hoạt hồ sơ
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}/approve")
    public ResponseEntity<StudentProfile> approveProfile(Authentication authentication, @PathVariable String id) {
        return ResponseEntity.ok(profileService.approveProfile(id, getUser(authentication)));
    }

    // TH5: Chỉ ADMIN mới có quyền xóa hồ sơ
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProfile(Authentication authentication, @PathVariable String id) {
        profileService.deleteProfile(id, getUser(authentication));
        return ResponseEntity.noContent().build();
    }
}
