package com.example.profile.security;

public class AuthenticatedUser {
    private final String username;
    private final String role;
    private final String studentId;

    public AuthenticatedUser(String username, String role, String studentId) {
        this.username = username;
        this.role = role;
        this.studentId = studentId;
    }

    public String getUsername() { return username; }
    public String getRole() { return role; }
    public String getStudentId() { return studentId; }
}
