package com.java.model;

public class User {
    private long userId;
    private String fullName;
    private String username;
    private String email;
    private String password;
    private String avatar;
    private String role;          // USER, MODERATOR, ADMIN
    private String status;        // ACTIVE, BLOCKED, RESTRICTED
    private int warningCount;
    private String restrictedUntil;
    private String createdAt;

    // Getters & Setters
    public long getUserId() { return userId; }
    public void setUserId(long userId) { this.userId = userId; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getWarningCount() { return warningCount; }
    public void setWarningCount(int warningCount) { this.warningCount = warningCount; }
    public String getRestrictedUntil() { return restrictedUntil; }
    public void setRestrictedUntil(String restrictedUntil) { this.restrictedUntil = restrictedUntil; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}