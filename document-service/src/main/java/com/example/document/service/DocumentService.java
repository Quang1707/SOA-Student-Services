package com.example.document.service;

import com.example.document.client.ProfileClient;
import com.example.document.model.StudentDocument;
import com.example.document.repository.DocumentRepository;
import com.example.document.security.AuthenticatedUser;
import com.example.document.storage.DocumentFileStorageService;
import com.example.document.storage.DocumentFileStorageService.StoredFile;
import jakarta.annotation.PostConstruct;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.regex.Pattern;

@Service
public class DocumentService {

    private static final Pattern STUDENT_ID_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{1,20}$");

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private ProfileClient profileClient;

    @Autowired
    private DocumentFileStorageService fileStorageService;

    // BỔ SUNG TH6: Inject RabbitTemplate để giao tiếp với
    // audit-notification-service
    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Value("${audit.rabbitmq.exchange:audit_exchange}")
    private String auditExchange;

    @Value("${audit.rabbitmq.routing-key:audit_routing_key}")
    private String auditRoutingKey;

    @PostConstruct
    @Transactional
    public void initSampleDocuments() {
        if (documentRepository.count() == 0) {
            documentRepository.save(new StudentDocument("SV001", "Nguyễn Văn A", "CCCD (Đã xác thực)",
                    "https://example.com/cccd-sv001.pdf", "VERIFIED"));
            documentRepository.save(new StudentDocument("SV001", "Nguyễn Văn A", "Giấy khám sức khỏe",
                    "https://example.com/gksk-sv001.pdf", "PENDING"));
            documentRepository.save(new StudentDocument("SV002", "Trần Thị B", "Bằng tốt nghiệp THPT",
                    "https://example.com/bang-sv002.pdf", "VERIFIED"));
        }
    }

    @Transactional(readOnly = true)
    public List<StudentDocument> getDocuments(String studentId, AuthenticatedUser currentUser) {
        AuthenticatedUser user = requireAuthenticatedUser(currentUser);
        String role = normalizedRole(user);

        if ("STUDENT".equals(role)) {
            String ownerStudentId = requireStudentId(user);
            if (StringUtils.hasText(studentId) && !studentId.equalsIgnoreCase(ownerStudentId)) {
                throw new AccessDeniedException("Sinh viên chỉ được phép xem danh sách tài liệu của chính mình.");
            }
            return documentRepository.findByStudentIdIgnoreCase(ownerStudentId);
        }

        requireStaffOrAdmin(user);
        if (!StringUtils.hasText(studentId))
            return documentRepository.findAll();
        validateStudentId(studentId);
        return documentRepository.findByStudentIdIgnoreCase(studentId);
    }

    /**
     * Giữ API URL cũ để tương thích dữ liệu/link đã có, nhưng chỉ chấp nhận HTTPS.
     */
    @Transactional
    public StudentDocument createDocument(String studentId, String docType, String docUrl,
            boolean simulateDelay, boolean simulateRollback,
            AuthenticatedUser currentUser) {
        authorizeUpload(studentId, simulateDelay, simulateRollback, currentUser);
        validateDocumentType(docType);
        validateHttpsUrl(docUrl);

        // TH6: Consumer gọi Provider (Profile Service) để kiểm tra nghiệp vụ
        Map<String, Object> studentProfile = profileClient.verifyStudentProfile(studentId, simulateDelay);
        String studentName = studentNameFrom(studentProfile, studentId);

        StudentDocument newDoc = new StudentDocument(studentId, studentName, docType.trim(), docUrl.trim(), "PENDING");
        StudentDocument saved = documentRepository.save(newDoc);

        if (simulateRollback) {
            throw new RuntimeException(
                    "ROLLBACK_DEMO: Giả lập lỗi hệ thống ở bước cuối; giao dịch cơ sở dữ liệu đã được rollback.");
        }

        // TH6: Gửi thông báo audit log sau khi hoàn thành nghiệp vụ
        sendAuditLog("CREATE_DOCUMENT_URL", studentId,
                "Người dùng " + currentUser.getUsername() + " đã thêm link tài liệu cho sinh viên " + studentId);

        return saved;
    }

    /**
     * Upload file thật; file nằm ngoài web root và chỉ được tải qua endpoint đã xác
     * thực.
     */
    @Transactional
    public StudentDocument createDocumentFromUpload(String studentId, String docType, MultipartFile file,
            boolean simulateDelay, boolean simulateRollback,
            AuthenticatedUser currentUser) {
        authorizeUpload(studentId, simulateDelay, simulateRollback, currentUser);
        validateDocumentType(docType);

        // TH6: Consumer gọi Provider (Profile Service) để kiểm tra nghiệp vụ
        Map<String, Object> studentProfile = profileClient.verifyStudentProfile(studentId, simulateDelay);
        String studentName = studentNameFrom(studentProfile, studentId);
        StoredFile storedFile = fileStorageService.store(file);
        cleanupFileIfTransactionRollsBack(storedFile.storageKey());

        try {
            StudentDocument newDoc = new StudentDocument(
                    studentId, studentName, docType.trim(), "/pending-file-location", "PENDING");
            newDoc.setStorageKey(storedFile.storageKey());
            newDoc.setOriginalFilename(storedFile.originalFilename());
            newDoc.setContentType(storedFile.contentType());
            newDoc.setFileSize(storedFile.size());

            StudentDocument saved = documentRepository.saveAndFlush(newDoc);
            saved.setDocUrl("/api/v1/documents/" + saved.getId() + "/file");
            saved = documentRepository.save(saved);

            if (simulateRollback) {
                throw new RuntimeException(
                        "ROLLBACK_DEMO: Giả lập lỗi hệ thống ở bước cuối; dữ liệu DB được rollback.");
            }

            // TH6: Gửi thông báo audit log
            sendAuditLog("UPLOAD_DOCUMENT", studentId,
                    "Người dùng " + currentUser.getUsername() + " đã tải lên tài liệu cho sinh viên " + studentId);

            return saved;
        } catch (RuntimeException ex) {
            fileStorageService.deleteQuietly(storedFile.storageKey());
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public StudentDocument getDocumentForDownload(Long id, AuthenticatedUser currentUser) {
        AuthenticatedUser user = requireAuthenticatedUser(currentUser);
        String role = normalizedRole(user);
        StudentDocument doc = documentRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy tài liệu với mã ID: " + id));

        if ("STUDENT".equals(role)) {
            String ownerStudentId = requireStudentId(user);
            if (!doc.getStudentId().equalsIgnoreCase(ownerStudentId)) {
                throw new AccessDeniedException("Bạn không có quyền tải tài liệu của sinh viên khác.");
            }
        } else {
            requireStaffOrAdmin(user);
        }
        if (!StringUtils.hasText(doc.getStorageKey())) {
            throw new NoSuchElementException(
                    "Tài liệu này chỉ có đường dẫn tham chiếu, không có file được lưu trên hệ thống.");
        }

        sendAuditLog("DOWNLOAD_DOCUMENT", doc.getStudentId(),
                "Người dùng " + currentUser.getUsername() + " đã tải file của tài liệu ID " + id);
        return doc;
    }

    @Transactional
    public StudentDocument verifyDocument(Long id, AuthenticatedUser currentUser) {
        requireStaffOrAdmin(requireAuthenticatedUser(currentUser));
        StudentDocument doc = documentRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy tài liệu với mã ID: " + id));
        doc.setDocStatus("VERIFIED");

        StudentDocument saved = documentRepository.save(doc);

        sendAuditLog("VERIFY_DOCUMENT", saved.getStudentId(), "Người dùng " + currentUser.getUsername() + " đã duyệt tài liệu ID " + id);
        return saved;
    }

    @Transactional
    public void deleteDocument(Long id, AuthenticatedUser currentUser) {
        AuthenticatedUser user = requireAuthenticatedUser(currentUser);
        String role = normalizedRole(user);
        if (!"STUDENT".equals(role))
            requireStaffOrAdmin(user);

        StudentDocument doc = documentRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy tài liệu với mã ID: " + id));

        if ("STUDENT".equals(role)) {
            String ownerStudentId = requireStudentId(user);
            if (!doc.getStudentId().equalsIgnoreCase(ownerStudentId)) {
                throw new AccessDeniedException("Bạn không có quyền xóa tài liệu của sinh viên khác.");
            }
            if ("VERIFIED".equalsIgnoreCase(doc.getDocStatus())) {
                throw new IllegalArgumentException(
                        "BUSINESS_RULE: Tài liệu đã được nhà trường xác thực (VERIFIED), sinh viên không thể tự ý xóa.");
            }
        }

        String storageKey = doc.getStorageKey();
        documentRepository.delete(doc);
        deleteFileAfterCommit(storageKey);

        sendAuditLog("DELETE_DOCUMENT", doc.getStudentId(), "Người dùng " + currentUser.getUsername() + " đã xóa tài liệu ID " + id);
    }

    // ================= HELPER METHODS =================

    /**
     * TH6: Hàm helper đóng gói việc gửi message qua RabbitMQ.
     * Sử dụng try-catch để đảm bảo nếu RabbitMQ sập thì tiến trình chính (lưu
     * DB/File) vẫn thành công.
     */
    private void sendAuditLog(String action, String studentId, String details) {
        try {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("eventId", UUID.randomUUID().toString());
            event.put("action", action);
            event.put("studentId", studentId);
            event.put("description", details);
            event.put("timestamp", OffsetDateTime.now(ZoneOffset.UTC).toString());
            rabbitTemplate.convertAndSend(auditExchange, auditRoutingKey, event);
        } catch (Exception e) {
            // Audit is best-effort and must not roll back a successful document operation.
            System.err.println("CẢNH BÁO: Không thể gửi audit log tới RabbitMQ: " + e.getMessage());
        }
    }

    private AuthenticatedUser authorizeUpload(String studentId, boolean simulateDelay, boolean simulateRollback,
            AuthenticatedUser currentUser) {
        AuthenticatedUser user = requireAuthenticatedUser(currentUser);
        validateStudentId(studentId);
        String role = normalizedRole(user);

        if ("STUDENT".equals(role)) {
            String ownerStudentId = requireStudentId(user);
            if (!studentId.equalsIgnoreCase(ownerStudentId)) {
                throw new AccessDeniedException("Sinh viên chỉ được nộp tài liệu vào hồ sơ của chính mình.");
            }
            if (simulateDelay || simulateRollback) {
                throw new AccessDeniedException("Chức năng mô phỏng kiểm thử chỉ dành cho STAFF/ADMIN.");
            }
        } else {
            requireStaffOrAdmin(user);
        }
        return user;
    }

    private String studentNameFrom(Map<String, Object> studentProfile, String studentId) {
        if (studentProfile == null) {
            throw new IllegalStateException("Profile Service không trả về thông tin hồ sơ.");
        }
        Object fullName = studentProfile.get("fullName");
        return fullName instanceof String name && StringUtils.hasText(name) ? name.trim() : studentId;
    }

    private void validateStudentId(String studentId) {
        if (!StringUtils.hasText(studentId) || !STUDENT_ID_PATTERN.matcher(studentId.trim()).matches()) {
            throw new IllegalArgumentException("Mã sinh viên không hợp lệ.");
        }
    }

    private void validateDocumentType(String docType) {
        if (!StringUtils.hasText(docType) || docType.trim().length() > 100 || docType.trim().length() < 2) {
            throw new IllegalArgumentException("Loại tài liệu phải có từ 2 đến 100 ký tự.");
        }
    }

    private void validateHttpsUrl(String docUrl) {
        if (!StringUtils.hasText(docUrl) || docUrl.trim().length() > 255) {
            throw new IllegalArgumentException("Đường dẫn tài liệu không được để trống và tối đa 255 ký tự.");
        }
        try {
            URI uri = URI.create(docUrl.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || !StringUtils.hasText(uri.getHost())
                    || uri.getUserInfo() != null) {
                throw new IllegalArgumentException("Chỉ chấp nhận URL HTTPS hợp lệ.");
            }
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("URL tài liệu không hợp lệ; chỉ chấp nhận HTTPS.");
        }
    }

    private void cleanupFileIfTransactionRollsBack(String storageKey) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != TransactionSynchronization.STATUS_COMMITTED) {
                        fileStorageService.deleteQuietly(storageKey);
                    }
                }
            });
        }
    }

    private void deleteFileAfterCommit(String storageKey) {
        if (!StringUtils.hasText(storageKey))
            return;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    fileStorageService.deleteQuietly(storageKey);
                }
            });
        } else {
            fileStorageService.deleteQuietly(storageKey);
        }
    }

    private AuthenticatedUser requireAuthenticatedUser(AuthenticatedUser user) {
        if (user == null || !StringUtils.hasText(user.getUsername()) || !StringUtils.hasText(user.getRole())) {
            throw new AccessDeniedException("Bạn cần đăng nhập để thực hiện thao tác này.");
        }
        return user;
    }

    private String normalizedRole(AuthenticatedUser user) {
        return user.getRole().trim().toUpperCase(Locale.ROOT);
    }

    private String requireStudentId(AuthenticatedUser user) {
        if (!StringUtils.hasText(user.getStudentId())) {
            throw new AccessDeniedException("Tài khoản sinh viên chưa được liên kết với mã sinh viên.");
        }
        return user.getStudentId().trim();
    }

    private void requireStaffOrAdmin(AuthenticatedUser user) {
        String role = normalizedRole(user);
        if (!"STAFF".equals(role) && !"ADMIN".equals(role)) {
            throw new AccessDeniedException("Chỉ STAFF hoặc ADMIN mới được phép thực hiện thao tác này.");
        }
    }
}