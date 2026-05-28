package com.erp.auth;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用户 — 系统使用者。
 *
 * 用户隶属于某个租户，可拥有多个角色，
 * 最终权限 = 所有角色权限的并集。
 */
public class User implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String id;
    private final String tenantId;
    private String username;
    private String displayName;
    private String email;
    private String phone;
    private String passwordHash;
    private final Set<String> roleIds;
    private final Set<String> directPermissions;
    private UserStatus status;
    private boolean systemAdmin;
    private LocalDateTime lastLoginAt;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private final Map<String, Object> attributes;

    public User(String id, String tenantId, String username) {
        this.id = id;
        this.tenantId = tenantId;
        this.username = username;
        this.displayName = username;
        this.roleIds = ConcurrentHashMap.newKeySet();
        this.directPermissions = ConcurrentHashMap.newKeySet();
        this.status = UserStatus.ACTIVE;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.attributes = new ConcurrentHashMap<>();
    }

    public enum UserStatus {
        ACTIVE,
        INACTIVE,
        LOCKED,
        DISABLED,
        PENDING_VERIFICATION
    }

    // ── 角色 & 权限 ──

    public void addRole(String roleId) { roleIds.add(roleId); }
    public void removeRole(String roleId) { roleIds.remove(roleId); }
    public boolean hasRole(String roleId) { return roleIds.contains(roleId); }
    public Set<String> getRoleIds() { return Collections.unmodifiableSet(roleIds); }

    public void grantDirectPermission(String permissionId) { directPermissions.add(permissionId); }
    public void revokeDirectPermission(String permissionId) { directPermissions.remove(permissionId); }
    public boolean hasDirectPermission(String permissionId) { return directPermissions.contains(permissionId); }
    public Set<String> getDirectPermissions() { return Collections.unmodifiableSet(directPermissions); }

    // ── Getters / Setters ──

    public String getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public UserStatus getStatus() { return status; }
    public void setStatus(UserStatus status) { this.status = status; }
    public boolean isSystemAdmin() { return systemAdmin; }
    public void setSystemAdmin(boolean systemAdmin) { this.systemAdmin = systemAdmin; }
    public LocalDateTime getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(LocalDateTime lastLoginAt) { this.lastLoginAt = lastLoginAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setAttribute(String key, Object value) { attributes.put(key, value); }
    public Object getAttribute(String key) { return attributes.get(key); }

    @Override
    public String toString() {
        return "User{id='" + id + "', username='" + username
            + "', tenant='" + tenantId + "', status=" + status + "}";
    }
}
