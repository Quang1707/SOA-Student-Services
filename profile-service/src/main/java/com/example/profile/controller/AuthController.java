package com.example.profile.controller;

import com.example.profile.model.AppUser;
import com.example.profile.repository.UserRepository;
import com.example.profile.security.AuthenticatedUser;
import com.example.profile.security.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping({"/api/v1/auth", "/api/auth"})
public class AuthController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    // TH5 Giai đoạn 2 Bước 2: API Đăng nhập xác thực BCrypt và cấp phát JWT
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> credentials) {
        String username = credentials.get("username");
        String password = credentials.get("password");

        if (username == null || password == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("status", 400, "message", "Vui lòng nhập đầy đủ username và password"));
        }

        Optional<AppUser> userOpt = userRepository.findByUsername(username.trim());
        // Thông báo lỗi chung, không tiết lộ tài khoản có tồn tại hay không (TH5 Giai đoạn 2 Bước 3)
        if (userOpt.isEmpty() || !passwordEncoder.matches(password, userOpt.get().getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("status", 401, "error", "Unauthorized", "message", "Tên đăng nhập hoặc mật khẩu không hợp lệ"));
        }

        AppUser user = userOpt.get();
        if (!user.isActive()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("status", 401, "error", "Unauthorized", "message", "Tài khoản đã bị vô hiệu hóa"));
        }

        String token = jwtService.generateToken(user.getUsername(), user.getRole(), user.getStudentId());
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("accessToken", token);
        response.put("tokenType", "Bearer");
        response.put("expiresIn", jwtService.getExpirationSeconds());
        response.put("username", user.getUsername());
        response.put("role", user.getRole());
        response.put("studentId", user.getStudentId());
        return ResponseEntity.ok(response);
    }

    // API hỗ trợ sinh token đã hết hạn để sinh viên kiểm thử nhanh trường hợp Expired Token -> 401
    @PostMapping("/demo-expired-token")
    public ResponseEntity<Map<String, Object>> generateExpiredToken() {
        String expiredToken = jwtService.generateExpiredToken("student01", "STUDENT", "SV001");
        return ResponseEntity.ok(Map.of(
                "accessToken", expiredToken,
                "tokenType", "Bearer",
                "expiresIn", -60,
                "note", "Token này đã hết hạn, hãy dùng để gọi API bảo vệ và kiểm tra mã lỗi 401"
        ));
    }

    // Lấy thông tin danh tính hiện tại từ JWT
    @GetMapping("/me")
    public ResponseEntity<?> currentUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("status", 401, "message", "Chưa đăng nhập"));
        }
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("username", user.getUsername());
        info.put("role", user.getRole());
        info.put("studentId", user.getStudentId());
        return ResponseEntity.ok(info);
    }

    // Danh sách tài khoản mẫu (Không lộ password_hash) để hiển thị hướng dẫn trên UI
    @GetMapping("/demo-accounts")
    public ResponseEntity<List<Map<String, Object>>> getDemoAccounts() {
        List<Map<String, Object>> accounts = userRepository.findAll().stream().map(u -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("username", u.getUsername());
            m.put("role", u.getRole());
            m.put("studentId", u.getStudentId());
            m.put("active", u.isActive());
            return m;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(accounts);
    }
}
