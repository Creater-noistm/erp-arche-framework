package com.erp.auth;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 角色 — 权限的集合。
 *
 * 角色可以继承其他角色（角色树），
 * 最终权限 = 自身权限 + 所有父角色权限。
 */
public class Role implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String id;
    private final String tenantId;
    private String name;
    private String description;
    private final Set<String> permissionIds;
    private final Set<String> parentRoleIds;
    private RoleType type;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Role(String id, String tenantId, String name) {
        this.id = id;
        this.tenantId = tenantId;
        this.name = name;
        this.permissionIds = ConcurrentHashMap.newKeySet();
        this.parentRoleIds = ConcurrentHashMap.newKeySet();
        this.type = RoleType.CUSTOM;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public enum RoleType {
        BUILT_IN,   // 系统内置（不可删除）
        TEMPLATE,   // 角色模板
        CUSTOM      // 自定义角色
    }

    // ── 权限管理 ──

    public void grantPermission(String permissionId) { permissionIds.add(permissionId); }
    public void revokePermission(String permissionId) { permissionIds.remove(permissionId); }
    public boolean hasPermission(String permissionId) { return permissionIds.contains(permissionId); }
    public Set<String> getPermissionIds() { return Collections.unmodifiableSet(permissionIds); }

    // ── 角色继承 ──

    public void addParentRole(String roleId) { parentRoleIds.add(roleId); }
    public void removeParentRole(String roleId) { parentRoleIds.remove(roleId); }
    public Set<String> getParentRoleIds() { return Collections.unmodifiableSet(parentRoleIds); }

    // ── Getters / Setters ──

    public String getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public RoleType getType() { return type; }
    public void setType(RoleType type) { this.type = type; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    @Override
    public String toString() {
        return "Role{id='" + id + "', name='" + name + "', permissions="
            + permissionIds.size() + "}";
    }
}
