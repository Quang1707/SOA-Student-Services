package com.example.profile.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "student_profiles")
public class StudentProfile {

    @Id
    @Column(name = "student_id", length = 20, nullable = false)
    @NotBlank(message = "Mã sinh viên không được để trống")
    @Pattern(regexp = "[A-Za-z0-9_-]{1,20}", message = "Mã sinh viên chỉ gồm chữ, số, dấu gạch ngang hoặc gạch dưới (tối đa 20 ký tự)")
    private String studentId;

    @Column(name = "full_name", length = 100, nullable = false)
    @NotBlank(message = "Họ và tên không được để trống")
    @Size(min = 3, max = 100, message = "Họ và tên phải từ 3 đến 100 ký tự")
    private String fullName;

    @Column(name = "date_of_birth", nullable = false)
    @NotNull(message = "Ngày sinh là bắt buộc")
    @Past(message = "Ngày sinh phải nhỏ hơn ngày hiện tại")
    private LocalDate dateOfBirth;

    @Column(name = "gender", length = 10, nullable = false)
    @NotBlank(message = "Giới tính là bắt buộc")
    @Pattern(regexp = "MALE|FEMALE|OTHER", message = "Giới tính phải là MALE, FEMALE hoặc OTHER")
    private String gender;

    @Column(name = "email", length = 100, nullable = false)
    @NotBlank(message = "Email không được để trống")
    @Email(message = "Định dạng email không hợp lệ")
    @Size(max = 100, message = "Email tối đa 100 ký tự")
    private String email;

    @Column(name = "phone", length = 15, nullable = false)
    @NotBlank(message = "Số điện thoại là bắt buộc")
    @Pattern(regexp = "\\d{10}", message = "Số điện thoại phải gồm đúng 10 chữ số")
    private String phone;

    @Column(name = "major", length = 100, nullable = false)
    @NotBlank(message = "Chuyên ngành không được để trống")
    @Size(max = 100, message = "Chuyên ngành tối đa 100 ký tự")
    private String major;

    @Column(name = "address", length = 255)
    @Size(max = 255, message = "Địa chỉ tối đa 255 ký tự")
    private String address;

    @Column(name = "status", length = 20, nullable = false)
    @Pattern(regexp = "PENDING|ACTIVE|COMPLETED", message = "Trạng thái phải là PENDING, ACTIVE hoặc COMPLETED")
    private String status; // PENDING, ACTIVE, COMPLETED

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public StudentProfile() {}

    public StudentProfile(String studentId, String fullName, LocalDate dateOfBirth, String gender,
                          String email, String phone, String major, String address, String status) {
        this.studentId = studentId;
        this.fullName = fullName;
        this.dateOfBirth = dateOfBirth;
        this.gender = gender;
        this.email = email;
        this.phone = phone;
        this.major = major;
        this.address = address;
        this.status = status;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.status == null || this.status.isBlank()) {
            this.status = "PENDING";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getMajor() { return major; }
    public void setMajor(String major) { this.major = major; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
