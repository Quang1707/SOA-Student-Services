package com.example.document.controller;

import com.example.document.model.StudentDocument;
import com.example.document.client.ProfileClient;
import com.example.document.security.AuthenticatedUser;
import com.example.document.service.DocumentService;
import com.example.document.storage.DocumentFileStorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping({"/api/v1/documents", "/api/documents"})
public class DocumentController {

    @Autowired
    private DocumentService documentService;

    @Autowired
    private ProfileClient profileClient;

    @Autowired
    private DocumentFileStorageService fileStorageService;

    private AuthenticatedUser getUser(Authentication auth) {
        return (auth != null && auth.getPrincipal() instanceof AuthenticatedUser user) ? user : null;
    }

    @GetMapping
    public ResponseEntity<?> getAllDocuments(Authentication authentication,
                                               @RequestParam(required = false) String studentId) {
        return ResponseEntity.ok(documentService.getDocuments(studentId, getUser(authentication)));
    }

    /** API minh chung Consumer -> Provider qua Eureka; endpoint van yeu cau JWT nhu API tai lieu. */
    @GetMapping("/discovery/profile-instance-info")
    public ResponseEntity<?> getProfileInstanceInfo() {
        return ResponseEntity.ok(profileClient.getProfileInstanceInfo());
    }

    /** API tương thích để lưu URL HTTPS đã có sẵn. */
    @PostMapping("/{studentId}")
    public ResponseEntity<?> uploadDocumentLink(Authentication authentication,
                                                @PathVariable String studentId,
                                                @RequestParam String docType,
                                                @RequestParam String docUrl,
                                                @RequestParam(defaultValue = "false") boolean simulateDelay,
                                                @RequestParam(defaultValue = "false") boolean simulateRollback) {
        try {
            StudentDocument created = documentService.createDocument(
                    studentId, docType, docUrl, simulateDelay, simulateRollback, getUser(authentication));
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (AccessDeniedException ex) {
            return error(HttpStatus.FORBIDDEN, "Forbidden", ex.getMessage());
        } catch (NoSuchElementException ex) {
            return error(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage());
        } catch (IllegalArgumentException ex) {
            return error(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage());
        } catch (IllegalStateException ex) {
            return error(HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable", "Profile Service tạm thời không khả dụng.");
        } catch (RuntimeException ex) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", "Không thể lưu tài liệu.");
        }
    }

    /** Upload multipart/form-data; API Gateway chuyển tiếp nguyên multipart body tới Document Service. */
    @PostMapping(value = "/{studentId}/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadDocumentFile(Authentication authentication,
                                                @PathVariable String studentId,
                                                @RequestParam String docType,
                                                @RequestPart("file") MultipartFile file,
                                                @RequestParam(defaultValue = "false") boolean simulateDelay,
                                                @RequestParam(defaultValue = "false") boolean simulateRollback) {
        try {
            StudentDocument created = documentService.createDocumentFromUpload(
                    studentId, docType, file, simulateDelay, simulateRollback, getUser(authentication));
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (AccessDeniedException ex) {
            return error(HttpStatus.FORBIDDEN, "Forbidden", ex.getMessage());
        } catch (NoSuchElementException ex) {
            return error(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage());
        } catch (IllegalArgumentException ex) {
            return error(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage());
        } catch (IllegalStateException ex) {
            return error(HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable", "Không thể xác minh hồ sơ hoặc lưu file. Vui lòng thử lại.");
        } catch (RuntimeException ex) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", "Không thể lưu tài liệu.");
        }
    }

    @GetMapping("/{docId}/file")
    public ResponseEntity<?> downloadDocumentFile(Authentication authentication, @PathVariable Long docId) {
        try {
            StudentDocument doc = documentService.getDocumentForDownload(docId, getUser(authentication));
            Resource resource = fileStorageService.loadAsResource(doc.getStorageKey());
            String contentType = StringUtils.hasText(doc.getContentType()) ? doc.getContentType() : MediaType.APPLICATION_OCTET_STREAM_VALUE;
            String filename = StringUtils.hasText(doc.getOriginalFilename()) ? doc.getOriginalFilename() : "student-document";

            ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
                    .header("X-Content-Type-Options", "nosniff")
                    .cacheControl(CacheControl.noStore());
            if (doc.getFileSize() != null) response.contentLength(doc.getFileSize());
            return response.body(resource);
        } catch (AccessDeniedException ex) {
            return error(HttpStatus.FORBIDDEN, "Forbidden", ex.getMessage());
        } catch (NoSuchElementException | IllegalArgumentException ex) {
            return error(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage());
        }
    }

    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    @PutMapping("/{docId}/verify")
    public ResponseEntity<?> verifyDocument(Authentication authentication, @PathVariable Long docId) {
        try {
            return ResponseEntity.ok(documentService.verifyDocument(docId, getUser(authentication)));
        } catch (AccessDeniedException ex) {
            return error(HttpStatus.FORBIDDEN, "Forbidden", ex.getMessage());
        } catch (NoSuchElementException ex) {
            return error(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage());
        }
    }

    @DeleteMapping("/{docId}")
    public ResponseEntity<?> deleteDocument(Authentication authentication, @PathVariable Long docId) {
        try {
            documentService.deleteDocument(docId, getUser(authentication));
            return ResponseEntity.noContent().build();
        } catch (AccessDeniedException ex) {
            return error(HttpStatus.FORBIDDEN, "Forbidden", ex.getMessage());
        } catch (IllegalArgumentException ex) {
            return error(HttpStatus.BAD_REQUEST, "Business Rule Violation", ex.getMessage());
        } catch (NoSuchElementException ex) {
            return error(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage());
        }
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String error, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "status", status.value(),
                "error", error,
                "message", message == null ? "Yêu cầu không hợp lệ." : message));
    }
}
