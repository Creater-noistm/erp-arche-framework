package com.erp.model;

import com.erp.tenant.TenantContext;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ═══════════════════════════════════════════════════════════════
 * DynamicEntity — 动态实体：所有业务数据的"始祖容器"
 *
 * 核心设计：
 * - 实体的"形态"由 entityType 决定，而非 Java 类
 * - 标准字段以强类型属性存取
 * - 自定义字段以 Map<String, Object> 存储
 * - 运行时可通过 DataModelRegistry 添加自定义字段
 * - 所有实体自带元数据（创建时间、修改时间、租户、版本）
 *
 * 这使得：
 * - 无需为每个业务对象创建 Java 类
 * - 用户/实施方可在运行时添加字段
 * - 不同行业的同一实体可以有不同结构
 * - 模块可以扩展其他模块的实体
 * ═══════════════════════════════════════════════════════════════
 */
public class DynamicEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    // ── 核心标识 ──

    /** 实体唯一ID（UUID） */
    private final String id;

    /** 实体类型（如 "SalesOrder", "Invoice", "Employee"） */
    private final String entityType;

    /** 所属租户ID */
    private final String tenantId;

    // ── 标准字段（每种实体类型都有的基础字段） ──

    private String code;                  // 业务编码
    private String name;                  // 名称/标题
    private String description;           // 描述
    private EntityStatus status;          // 状态
    private String createdBy;             // 创建人
    private String updatedBy;             // 修改人
    private LocalDateTime createdAt;      // 创建时间
    private LocalDateTime updatedAt;      // 修改时间
    private long version;                 // 乐观锁版本号

    // ── 自定义字段存储 ──

    private final Map<String, Object> customFields = new ConcurrentHashMap<>();

    // ── 扩展属性（元数据级别的扩展，不持久化到业务表） ──

    private final Map<String, Object> attributes = new ConcurrentHashMap<>();

    // ── 构造 ──

    public DynamicEntity(String entityType) {
        this.id = UUID.randomUUID().toString();
        this.entityType = entityType;
        this.tenantId = TenantContext.getCurrentTenantId();
        this.status = EntityStatus.DRAFT;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.version = 1;
    }

    public DynamicEntity(String entityType, String code, String name) {
        this(entityType);
        this.code = code;
        this.name = name;
    }

    /** 从已有数据重建实体（用于持久化恢复） */
    public DynamicEntity(String id, String entityType, String tenantId,
                          LocalDateTime createdAt, LocalDateTime updatedAt, long version) {
        this.id = id;
        this.entityType = entityType;
        this.tenantId = tenantId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
        this.status = EntityStatus.DRAFT;
    }

    // ── 自定义字段操作 ──

    /** 获取自定义字段值 */
    public Object get(String fieldName) {
        return customFields.get(fieldName);
    }

    /** 设置自定义字段值 */
    public DynamicEntity set(String fieldName, Object value) {
        this.customFields.put(fieldName, value);
        this.updatedAt = LocalDateTime.now();
        return this;
    }

    /** 批量设置自定义字段 */
    public DynamicEntity setAll(Map<String, Object> fields) {
        this.customFields.putAll(fields);
        this.updatedAt = LocalDateTime.now();
        return this;
    }

    /** 获取所有自定义字段 */
    public Map<String, Object> getCustomFields() {
        return Collections.unmodifiableMap(customFields);
    }

    /** 移除自定义字段 */
    public Object remove(String fieldName) {
        this.updatedAt = LocalDateTime.now();
        return customFields.remove(fieldName);
    }

    /** 检查是否有某自定义字段 */
    public boolean has(String fieldName) {
        return customFields.containsKey(fieldName);
    }

    // ── 类型安全的 getter ──

    public String getString(String fieldName) {
        Object v = customFields.get(fieldName);
        return v != null ? v.toString() : null;
    }

    public Integer getInt(String fieldName) {
        Object v = customFields.get(fieldName);
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s) return Integer.parseInt(s);
        return null;
    }

    public Boolean getBoolean(String fieldName) {
        Object v = customFields.get(fieldName);
        if (v instanceof Boolean b) return b;
        if (v instanceof String s) return Boolean.parseBoolean(s);
        return null;
    }

    public java.math.BigDecimal getDecimal(String fieldName) {
        Object v = customFields.get(fieldName);
        if (v instanceof java.math.BigDecimal bd) return bd;
        if (v instanceof Number n) return java.math.BigDecimal.valueOf(n.doubleValue());
        if (v instanceof String s) return new java.math.BigDecimal(s);
        return null;
    }

    @SuppressWarnings("unchecked")
    public <T> List<T> getList(String fieldName) {
        Object v = customFields.get(fieldName);
        return v instanceof List ? (List<T>) v : null;
    }

    // ── 扩展属性（元数据） ──

    public DynamicEntity setAttribute(String key, Object value) {
        attributes.put(key, value);
        return this;
    }

    public Object getAttribute(String key) {
        return attributes.get(key);
    }

    public Map<String, Object> getAttributes() {
        return Collections.unmodifiableMap(attributes);
    }

    // ── 克隆 ──

    /** 创建实体的深拷贝 */
    public DynamicEntity copy() {
        DynamicEntity copy = new DynamicEntity(this.entityType);
        copy.code = this.code;
        copy.name = this.name;
        copy.description = this.description;
        copy.status = this.status;
        copy.createdBy = this.createdBy;
        copy.updatedBy = this.updatedBy;
        copy.customFields.putAll(this.customFields);
        copy.attributes.putAll(this.attributes);
        return copy;
    }

    /** 创建新版本（增加版本号） */
    public DynamicEntity newVersion() {
        DynamicEntity copy = copy();
        copy.version = this.version + 1;
        return copy;
    }

    // ── 标准 Getter / Setter ──

    public String getId() { return id; }
    public String getEntityType() { return entityType; }
    public String getTenantId() { return tenantId; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; this.updatedAt = LocalDateTime.now(); }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; this.updatedAt = LocalDateTime.now(); }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; this.updatedAt = LocalDateTime.now(); }
    public EntityStatus getStatus() { return status; }
    public void setStatus(EntityStatus status) { this.status = status; this.updatedAt = LocalDateTime.now(); }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }

    // ── 实体状态 ──

    public enum EntityStatus {
        DRAFT,      // 草稿
        PENDING,    // 待审批
        APPROVED,   // 已审批
        ACTIVE,     // 生效中
        COMPLETED,  // 已完成
        CANCELLED,  // 已取消
        ARCHIVED    // 已归档
    }

    @Override
    public String toString() {
        return "DynamicEntity{" +
            "id='" + id + '\'' +
            ", type='" + entityType + '\'' +
            ", code='" + code + '\'' +
            ", name='" + name + '\'' +
            ", status=" + status +
            ", customFields=" + customFields.size() +
            ", tenant='" + tenantId + '\'' +
            '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DynamicEntity that)) return false;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
