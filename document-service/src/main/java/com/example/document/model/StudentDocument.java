package com.example.document.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "student_documents")
public class StudentDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // TH4: Không tạo FK vật lý sang Profile DB, chỉ lưu student_id và kiểm tra qua REST API
    @Column(name = "student_id", length = 20, nullable = false)
    private String studentId;

    @Column(name = "student_name", length = 100)
    private String studentName;

    @Column(name = "doc_type", length = 100, nullable = false)
    private String docType;

    @Column(name = "doc_url", length = 255, nullable = false)
    private String docUrl;

    // TH5 Giai đoạn 4 Bước 5: Trạng thái tài liệu (PENDING / VERIFIED) dùng để kiểm tra điều kiện nghiệp vụ khi xóa
    @Column(name = "doc_status", length = 20, nullable = false)
    private String docStatus = "PENDING";

    @Column(name = "storage_key", length = 100)
    private String storageKey;

    @Column(name = "original_filename", length = 255)
    private String originalFilename;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime uploadedAt;

    public StudentDocument() {}

    public StudentDocument(String studentId, String studentName, String docType, String docUrl, String docStatus) {
        this.studentId = studentId;
        this.studentName = studentName;
        this.docType = docType;
        this.docUrl = docUrl;
        this.docStatus = docStatus;
    }

    @PrePersist
    protected void onCreate() {
        this.uploadedAt = LocalDateTime.now();
        if (this.docStatus == null || this.docStatus.isBlank()) {
            this.docStatus = "PENDING";
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }
    public String getStudentName() { return studentName; }
    public void setStudentName(String studentName) { this.studentName = studentName; }
    public String getDocType() { return docType; }
    public void setDocType(String docType) { this.docType = docType; }
    public String getDocUrl() { return docUrl; }
    public void setDocUrl(String docUrl) { this.docUrl = docUrl; }
    public String getDocStatus() { return docStatus; }
    public void setDocStatus(String docStatus) { this.docStatus = docStatus; }
    public String getStorageKey() { return storageKey; }
    public void setStorageKey(String storageKey) { this.storageKey = storageKey; }
    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String originalFilename) { this.originalFilename = originalFilename; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    public LocalDateTime getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(LocalDateTime uploadedAt) { this.uploadedAt = uploadedAt; }
}
