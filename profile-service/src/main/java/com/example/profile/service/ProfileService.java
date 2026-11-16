package com.example.profile.service;

import com.example.profile.config.RabbitConfig;
import com.example.profile.model.AppUser;
import com.example.profile.model.StudentProfile;
import com.example.profile.repository.ProfileRepository;
import com.example.profile.repository.UserRepository;
import com.example.profile.security.AuthenticatedUser;
import jakarta.annotation.PostConstruct;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Service
public class ProfileService {

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @PostConstruct
    @Transactional
    public void initSampleData() {
        if (profileRepository.count() == 0) {
            profileRepository.save(new StudentProfile("SV001", "Nguyễn Văn A",
                    LocalDate.of(2003, 5, 12), "MALE", "nva@student.edu.vn", "0901234567",
                    "Kỹ thuật phần mềm", "Hà Nội", "ACTIVE"));
            profileRepository.save(new StudentProfile("SV002", "Trần Thị B",
                    LocalDate.of(2003, 8, 20), "FEMALE", "ttb@student.edu.vn", "0912345678",
                    "Hệ thống thông tin", "Đà Nẵng", "COMPLETED"));
            profileRepository.save(new StudentProfile("SV003", "Lê Hoàng C",
                    LocalDate.of(2004, 2, 15), "MALE", "lhc@student.edu.vn", "0987654321",
                    "Khoa học máy tính", "TP.HCM", "PENDING"));
        }

        // TH5 Giai đoạn 2 & 3: Khởi tạo tài khoản mẫu với mật khẩu băm bằng BCrypt
        if (userRepository.count() == 0) {
            String hashedPwd = passwordEncoder.encode("DemoPassword123!");
            userRepository.save(new AppUser("student01", hashedPwd, "STUDENT", "SV001", true));
            userRepository.save(new AppUser("student02", hashedPwd, "STUDENT", "SV002", true));
            userRepository.save(new AppUser("staff01", hashedPwd, "STAFF", null, true));
            userRepository.save(new AppUser("admin01", hashedPwd, "ADMIN", null, true));
            userRepository.save(new AppUser("locked01", hashedPwd, "STUDENT", "SV003", false));
        }
    }

    // TH5: Kiểm tra quyền sở hữu tài nguyên (Resource Ownership Check)
    public void verifyOwnershipOrStaff(AuthenticatedUser currentUser, String targetStudentId, String actionName) {
        if (currentUser == null) {
            throw new AccessDeniedException("Chưa xác thực người dùng");
        }
        String role = currentUser.getRole();
        if ("ADMIN".equals(role) || "STAFF".equals(role)) {
            return;
        }
        // Nếu là STUDENT, chỉ được phép thao tác trên chính studentId của mình
        if ("STUDENT".equals(role) && targetStudentId.equalsIgnoreCase(currentUser.getStudentId())) {
            return;
        }
        // Ghi log cảnh báo bảo mật khi phát hiện truy cập trái phép (IDOR)
        publishAuditEvent("SECURITY_ACCESS_DENIED", targetStudentId, currentUser.getUsername(),
                "Cảnh báo bảo mật: Tài khoản " + currentUser.getUsername() + " cố gắng " + actionName + " hồ sơ của " + targetStudentId);
        throw new AccessDeniedException("Bạn chỉ được phép " + actionName + " hồ sơ của chính mình (" + currentUser.getStudentId() + ")");
    }

    @Transactional(readOnly = true)
    public Map<String, Object> queryProfiles(AuthenticatedUser currentUser, String name, String gender, String status, String major,
                                             int page, int size, String sort) {
        if (currentUser == null) {
            throw new AccessDeniedException("Chưa xác thực người dùng.");
        }
        if ("INTERNAL_SERVICE".equals(currentUser.getRole())) {
            throw new AccessDeniedException("INTERNAL_SERVICE chỉ được phép tra cứu một hồ sơ theo mã sinh viên.");
        }
        if (!List.of("STUDENT", "STAFF", "ADMIN").contains(currentUser.getRole())) {
            throw new AccessDeniedException("Vai trò không được phép truy vấn danh sách hồ sơ.");
        }

        // Nếu người gọi có vai trò STUDENT -> Chỉ trả về đúng hồ sơ của chính sinh viên đó
        if (currentUser != null && "STUDENT".equals(currentUser.getRole())) {
            if (currentUser.getStudentId() == null || currentUser.getStudentId().isBlank()) {
                throw new AccessDeniedException("Tài khoản sinh viên chưa được liên kết với mã sinh viên.");
            }
            Optional<StudentProfile> selfProfile = profileRepository.findById(currentUser.getStudentId());
            List<StudentProfile> content = selfProfile.map(List::of).orElse(List.of());
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("content", content);
            result.put("page", 0);
            result.put("size", size);
            result.put("totalElements", content.size());
            result.put("totalPages", 1);
            return result;
        }

        if (page < 0) {
            throw new IllegalArgumentException("Số trang không được âm.");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("Kích thước trang phải từ 1 đến 100.");
        }
        if (name != null && name.length() > 100) throw new IllegalArgumentException("Từ khóa tìm kiếm tối đa 100 ký tự.");
        if (major != null && major.length() > 100) throw new IllegalArgumentException("Chuyên ngành lọc tối đa 100 ký tự.");
        if (gender != null && !gender.isBlank() && !Set.of("MALE", "FEMALE", "OTHER").contains(gender.toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Bộ lọc giới tính không hợp lệ.");
        }
        if (status != null && !status.isBlank() && !Set.of("PENDING", "ACTIVE", "COMPLETED").contains(status.toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Bộ lọc trạng thái không hợp lệ.");
        }

        Sort sortOrder = Sort.by(Sort.Direction.ASC, "studentId");
        if (sort != null && !sort.isBlank()) {
            String[] parts = sort.split(",", -1);
            String sortField = parts[0].trim();
            Set<String> allowedSortFields = Set.of("studentId", "fullName", "dateOfBirth", "gender", "email", "major", "status", "createdAt", "updatedAt");
            if (!allowedSortFields.contains(sortField)) {
                throw new IllegalArgumentException("Trường sắp xếp không được hỗ trợ.");
            }
            if (parts.length > 2 || (parts.length == 2 && !Set.of("asc", "desc").contains(parts[1].trim().toLowerCase(Locale.ROOT)))) {
                throw new IllegalArgumentException("Định dạng sort phải là field,asc hoặc field,desc.");
            }
            Sort.Direction dir = (parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim()))
                    ? Sort.Direction.DESC : Sort.Direction.ASC;
            sortOrder = Sort.by(dir, sortField);
        }

        Pageable pageable = PageRequest.of(page, size, sortOrder);
        // PostgreSQL không suy luận được kiểu của tham số NULL trong "LOWER(CONCAT('%', :x, '%'))",
        // nên luôn truyền chuỗi rỗng (query đã coi '' là "không lọc").
        Page<StudentProfile> pageResult = profileRepository.searchAndFilterProfiles(
                blankIfNull(name), blankIfNull(gender), blankIfNull(status), blankIfNull(major), pageable);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", pageResult.getContent());
        result.put("page", pageResult.getNumber());
        result.put("size", pageResult.getSize());
        result.put("totalElements", pageResult.getTotalElements());
        result.put("totalPages", pageResult.getTotalPages());
        return result;
    }

    @Transactional(readOnly = true)
    public StudentProfile getProfile(String studentId, AuthenticatedUser currentUser) {
        if (currentUser == null) {
            throw new AccessDeniedException("Chưa xác thực người dùng.");
        }
        // INTERNAL_SERVICE chỉ được đi qua hàm tra cứu một hồ sơ cụ thể này.
        // Các hàm liệt kê/cập nhật không cấp quyền đặc biệt cho vai trò nội bộ.
        if (!"INTERNAL_SERVICE".equals(currentUser.getRole())) {
            verifyOwnershipOrStaff(currentUser, studentId, "xem");
        }
        StudentProfile profile = profileRepository.findById(studentId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy hồ sơ sinh viên với mã " + studentId));
        if (currentUser != null && !"INTERNAL_SERVICE".equals(currentUser.getRole())) {
            publishAuditEvent("PROFILE_VIEWED", profile.getStudentId(), currentUser.getUsername(),
                    "Người dùng " + currentUser.getUsername() + " (" + currentUser.getRole() + ") đã xem hồ sơ " + studentId);
        }
        return profile;
    }

    @Transactional
    public StudentProfile createProfile(StudentProfile profile, AuthenticatedUser currentUser) {
        if (profileRepository.existsById(profile.getStudentId())) {
            throw new IllegalStateException("Mã sinh viên " + profile.getStudentId() + " đã tồn tại trong CSDL");
        }
        if (profile.getStatus() == null || profile.getStatus().isBlank()) {
            profile.setStatus("PENDING");
        }
        StudentProfile saved = profileRepository.save(profile);
        publishEvent("PROFILE_CREATED", saved, "Tạo mới bởi " + (currentUser != null ? currentUser.getUsername() : "system"));
        return saved;
    }

    @Transactional
    public StudentProfile updateProfile(String studentId, StudentProfile updated, AuthenticatedUser currentUser) {
        StudentProfile existing = profileRepository.findById(studentId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy hồ sơ sinh viên với mã " + studentId));
        existing.setFullName(updated.getFullName());
        existing.setDateOfBirth(updated.getDateOfBirth());
        existing.setGender(updated.getGender());
        existing.setEmail(updated.getEmail());
        existing.setPhone(updated.getPhone());
        existing.setMajor(updated.getMajor());
        existing.setAddress(updated.getAddress());
        if (updated.getStatus() != null && !updated.getStatus().isBlank()) {
            existing.setStatus(updated.getStatus());
        }
        StudentProfile saved = profileRepository.save(existing);
        publishEvent("PROFILE_UPDATED", saved, "Cập nhật bởi " + (currentUser != null ? currentUser.getUsername() : "system"));
        return saved;
    }

    @Transactional
    public StudentProfile patchProfile(String studentId, Map<String, Object> updates, AuthenticatedUser currentUser) {
        verifyOwnershipOrStaff(currentUser, studentId, "cập nhật");
        if (updates == null || updates.isEmpty()) {
            throw new IllegalArgumentException("Nội dung cập nhật không được để trống.");
        }

        Set<String> allowedFields = Set.of("fullName", "email", "phone", "major", "address", "gender", "status", "dateOfBirth");
        for (String field : updates.keySet()) {
            if (!allowedFields.contains(field)) {
                throw new IllegalArgumentException("Trường cập nhật không được hỗ trợ: " + field);
            }
        }

        if ("STUDENT".equals(currentUser.getRole())) {
            Set<String> studentFields = Set.of("email", "phone", "address");
            if (!studentFields.containsAll(updates.keySet())) {
                throw new AccessDeniedException("Sinh viên chỉ được cập nhật Email, Số điện thoại và Địa chỉ.");
            }
        }

        StudentProfile existing = profileRepository.findById(studentId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy hồ sơ sinh viên với mã " + studentId));

        if (updates.containsKey("fullName")) {
            String value = requireString(updates, "fullName").trim();
            if (value.length() < 3 || value.length() > 100) throw new IllegalArgumentException("Họ tên phải từ 3 đến 100 ký tự.");
            existing.setFullName(value);
        }
        if (updates.containsKey("email")) {
            String value = requireString(updates, "email").trim();
            if (value.length() > 100 || !value.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
                throw new IllegalArgumentException("Email không hợp lệ hoặc vượt quá 100 ký tự.");
            }
            existing.setEmail(value);
        }
        if (updates.containsKey("phone")) {
            String value = requireString(updates, "phone").trim();
            if (!value.matches("\\d{10}")) throw new IllegalArgumentException("Số điện thoại phải gồm đúng 10 chữ số.");
            existing.setPhone(value);
        }
        if (updates.containsKey("major")) {
            String value = requireString(updates, "major").trim();
            if (value.isEmpty() || value.length() > 100) throw new IllegalArgumentException("Chuyên ngành phải từ 1 đến 100 ký tự.");
            existing.setMajor(value);
        }
        if (updates.containsKey("address")) {
            String value = requireString(updates, "address").trim();
            if (value.length() > 255) throw new IllegalArgumentException("Địa chỉ tối đa 255 ký tự.");
            existing.setAddress(value);
        }
        if (updates.containsKey("gender")) {
            String value = requireString(updates, "gender").trim().toUpperCase(Locale.ROOT);
            if (!Set.of("MALE", "FEMALE", "OTHER").contains(value)) throw new IllegalArgumentException("Giới tính phải là MALE, FEMALE hoặc OTHER.");
            existing.setGender(value);
        }
        if (updates.containsKey("status")) {
            String value = requireString(updates, "status").trim().toUpperCase(Locale.ROOT);
            if (!Set.of("PENDING", "ACTIVE", "COMPLETED").contains(value)) throw new IllegalArgumentException("Trạng thái không hợp lệ.");
            existing.setStatus(value);
        }
        if (updates.containsKey("dateOfBirth")) {
            String value = requireString(updates, "dateOfBirth").trim();
            try {
                LocalDate dob = LocalDate.parse(value);
                if (!dob.isBefore(LocalDate.now())) throw new IllegalArgumentException("Ngày sinh phải nhỏ hơn ngày hiện tại.");
                existing.setDateOfBirth(dob);
            } catch (java.time.format.DateTimeParseException ex) {
                throw new IllegalArgumentException("Ngày sinh phải theo định dạng YYYY-MM-DD.");
            }
        }

        StudentProfile saved = profileRepository.save(existing);
        publishEvent("PROFILE_PATCHED", saved, "Cập nhật một phần bởi " + currentUser.getUsername());
        return saved;
    }

    private String blankIfNull(String value) {
        return value == null ? "" : value.trim();
    }

    private String requireString(Map<String, Object> updates, String field) {
        Object value = updates.get(field);
        if (!(value instanceof String text)) {
            throw new IllegalArgumentException("Giá trị của trường " + field + " phải là chuỗi.");
        }
        return text;
    }

    @Transactional
    public StudentProfile approveProfile(String studentId, AuthenticatedUser currentUser) {
        StudentProfile profile = profileRepository.findById(studentId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy hồ sơ sinh viên với mã " + studentId));
        profile.setStatus("ACTIVE");
        StudentProfile saved = profileRepository.save(profile);
        publishEvent("PROFILE_ACTIVATED", saved, "Phê duyệt kích hoạt bởi ADMIN (" + (currentUser != null ? currentUser.getUsername() : "admin") + ")");
        return saved;
    }

    @Transactional
    public void deleteProfile(String studentId, AuthenticatedUser currentUser) {
        StudentProfile existing = profileRepository.findById(studentId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy hồ sơ sinh viên với mã " + studentId));
        profileRepository.delete(existing);
        publishEvent("PROFILE_DELETED", existing, "Xóa hồ sơ bởi ADMIN (" + (currentUser != null ? currentUser.getUsername() : "admin") + ")");
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", profileRepository.count());
        stats.put("male", profileRepository.countByGenderIgnoreCase("MALE"));
        stats.put("female", profileRepository.countByGenderIgnoreCase("FEMALE"));
        stats.put("active", profileRepository.countByStatusIgnoreCase("ACTIVE"));
        stats.put("pending", profileRepository.countByStatusIgnoreCase("PENDING"));
        stats.put("completed", profileRepository.countByStatusIgnoreCase("COMPLETED"));
        return stats;
    }

    private void publishEvent(String action, StudentProfile profile, String description) {
        try {
            Map<String, String> eventMsg = new HashMap<>();
            eventMsg.put("eventId", UUID.randomUUID().toString());
            eventMsg.put("action", action);
            eventMsg.put("studentId", profile.getStudentId());
            eventMsg.put("fullName", profile.getFullName());
            eventMsg.put("email", profile.getEmail());
            eventMsg.put("description", description);
            eventMsg.put("timestamp", OffsetDateTime.now(ZoneOffset.UTC).toString());
            rabbitTemplate.convertAndSend(RabbitConfig.PROFILE_QUEUE, eventMsg);
        } catch (Exception e) {
            System.err.println("[Cảnh báo] RabbitMQ chưa sẵn sàng: " + e.getMessage());
        }
    }

    private void publishAuditEvent(String action, String studentId, String actor, String description) {
        try {
            Map<String, String> eventMsg = new HashMap<>();
            eventMsg.put("eventId", UUID.randomUUID().toString());
            eventMsg.put("action", action);
            eventMsg.put("studentId", studentId);
            eventMsg.put("fullName", "Actor: " + actor);
            eventMsg.put("email", actor + "@security.audit");
            eventMsg.put("description", description);
            eventMsg.put("timestamp", OffsetDateTime.now(ZoneOffset.UTC).toString());
            rabbitTemplate.convertAndSend(RabbitConfig.PROFILE_QUEUE, eventMsg);
        } catch (Exception e) {
            System.err.println("[Cảnh báo] RabbitMQ chưa sẵn sàng: " + e.getMessage());
        }
    }
}
