package com.erp.tenant;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 租户 — 多租户架构中的企业/组织单元。
 *
 * 每个租户拥有：
 * - 独立的数据隔离空间
 * - 独立的配置（时区、货币、语言等）
 * - 独立的用户体系
 * - 可定制的工作流和字段
 */
public class Tenant implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String id;
    private final String name;
    private String domain;
    private String contactEmail;
    private String contactPhone;
    private TenantStatus status;
    private final Map<String, String> settings;
    private final Set<String> enabledModules;
    private final Map<String, Object> extendedAttributes;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Tenant(String id, String name) {
        this.id = id;
        this.name = name;
        this.status = TenantStatus.ACTIVE;
        this.settings = new HashMap<>();
        this.enabledModules = new HashSet<>();
        this.extendedAttributes = new HashMap<>();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();

        // 默认设置
        settings.put("timezone", "Asia/Shanghai");
        settings.put("currency", "CNY");
        settings.put("language", "zh_CN");
        settings.put("dateFormat", "yyyy-MM-dd");
    }

    // ── Getters ──

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; this.updatedAt = LocalDateTime.now(); }
    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String email) { this.contactEmail = email; this.updatedAt = LocalDateTime.now(); }
    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String phone) { this.contactPhone = phone; this.updatedAt = LocalDateTime.now(); }
    public TenantStatus getStatus() { return status; }
    public void setStatus(TenantStatus status) { this.status = status; this.updatedAt = LocalDateTime.now(); }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    // ── 设置管理 ──

    public String getSetting(String key) { return settings.get(key); }
    public String getSetting(String key, String defaultValue) {
        return settings.getOrDefault(key, defaultValue);
    }
    public void setSetting(String key, String value) {
        settings.put(key, value);
        this.updatedAt = LocalDateTime.now();
    }
    public Map<String, String> getSettings() {
        return Collections.unmodifiableMap(settings);
    }

    // ── 模块管理 ──

    public void enableModule(String moduleId) { enabledModules.add(moduleId); }
    public void disableModule(String moduleId) { enabledModules.remove(moduleId); }
    public boolean isModuleEnabled(String moduleId) { return enabledModules.contains(moduleId); }
    public Set<String> getEnabledModules() { return Collections.unmodifiableSet(enabledModules); }

    // ── 扩展属性 ──

    public void setAttribute(String key, Object value) { extendedAttributes.put(key, value); }
    public Object getAttribute(String key) { return extendedAttributes.get(key); }
    public Map<String, Object> getExtendedAttributes() {
        return Collections.unmodifiableMap(extendedAttributes);
    }

    public enum TenantStatus {
        ACTIVE,     // 正常
        SUSPENDED,  // 暂停
        TRIAL,      // 试用
        EXPIRED,    // 已过期
        DISABLED    // 已禁用
    }

    @Override
    public String toString() {
        return "Tenant{id='" + id + "', name='" + name + "', status=" + status + "}";
    }
}
