package com.example.profile.model;

import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", unique = true, nullable = false, length = 50)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "role", nullable = false, length = 20)
    private String role; // STUDENT, STAFF, ADMIN

    @Column(name = "student_id", length = 20)
    private String studentId; // Liên kết tới mã sinh viên (nếu là STUDENT)

    @Column(name = "active", nullable = false)
    private boolean active = true;

    public AppUser() {}

    public AppUser(String username, String passwordHash, String role, String studentId, boolean active) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
        this.studentId = studentId;
        this.active = active;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
