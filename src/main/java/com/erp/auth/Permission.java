package com.erp.auth;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 权限 — 系统中最小的授权单元。
 *
 * 采用 "资源:操作" 命名规范，支持通配符：
 *   - "finance:invoice:create"  — 创建发票
 *   - "finance:invoice:*"       — 所有发票操作
 *   - "finance:*"              — 财务模块所有操作
 *   - "*:invoice:read"         — 所有模块的发票读取
 */
public class Permission implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String id;
    private final String displayName;
    private final String description;
    private final String category;   // 分组
    private final boolean builtIn;
    private final LocalDateTime createdAt;
    private final Map<String, Object> attributes;

    public Permission(String id, String displayName, String description, String category) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.category = category;
        this.builtIn = false;
        this.createdAt = LocalDateTime.now();
        this.attributes = new ConcurrentHashMap<>();
    }

    public Permission(String id, String displayName, String description,
                      String category, boolean builtIn) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.category = category;
        this.builtIn = builtIn;
        this.createdAt = LocalDateTime.now();
        this.attributes = new ConcurrentHashMap<>();
    }

    // ── Getters ──

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public String getCategory() { return category; }
    public boolean isBuiltIn() { return builtIn; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public Map<String, Object> getAttributes() { return attributes; }

    // ── 工具方法 ──

    /**
     * 检查此权限是否匹配给定的权限请求。
     * 支持通配符：
     *   "finance:*" 匹配 "finance:invoice:create"
     *   "finance:invoice:*" 匹配 "finance:invoice:read"
     */
    public boolean matches(String permissionRequest) {
        return matchPattern(this.id, permissionRequest);
    }

    private boolean matchPattern(String pattern, String request) {
        if (pattern.equals(request)) return true;
        if (pattern.equals("*")) return true;

        String[] patternParts = pattern.split(":");
        String[] requestParts = request.split(":");

        for (int i = 0; i < Math.min(patternParts.length, requestParts.length); i++) {
            if (patternParts[i].equals("*")) return true;
            if (!patternParts[i].equals(requestParts[i])) return false;
        }

        return patternParts.length == requestParts.length;
    }

    @Override
    public String toString() {
        return "Permission{id='" + id + "', category='" + category + "'}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Permission that)) return false;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
