package com.erp.module;

import com.erp.model.FieldType;

import java.util.*;

/**
 * 模块定义的实体类型 — 模块向 DataModelRegistry 注册的数据结构。
 *
 * 示例：
 *   ModuleEntityDefinition.builder("Invoice")
 *       .displayName("发票")
 *       .field("invoiceNo", FieldType.STRING, true)
 *       .field("amount", FieldType.DECIMAL, true)
 *       .field("dueDate", FieldType.DATE, false)
 *       .build();
 */
public record ModuleEntityDefinition(
    /** 实体类型名称（如 "Invoice", "PurchaseOrder"） */
    String entityType,

    /** 显示名称 */
    String displayName,

    /** 所属模块 */
    String moduleId,

    /** 描述 */
    String description,

    /** 默认字段定义 */
    List<FieldDef> fields,

    /** 自定义字段允许的最大数量 */
    int maxCustomFields,

    /** 启用审计日志 */
    boolean enableAudit
) {
    public static Builder builder(String entityType) {
        return new Builder(entityType);
    }

    public record FieldDef(
        String name,
        FieldType type,
        boolean required,
        String displayName,
        String defaultValue,
        int maxLength,
        boolean indexed,
        String validationPattern
    ) {
        public FieldDef(String name, FieldType type, boolean required) {
            this(name, type, required, name, "", 255, false, "");
        }
    }

    public static class Builder {
        private final String entityType;
        private String displayName;
        private String moduleId;
        private String description = "";
        private final List<FieldDef> fields = new ArrayList<>();
        private int maxCustomFields = 50;
        private boolean enableAudit = true;

        Builder(String entityType) {
            this.entityType = entityType;
            this.displayName = entityType;
        }

        public Builder displayName(String displayName) { this.displayName = displayName; return this; }
        public Builder moduleId(String moduleId) { this.moduleId = moduleId; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder maxCustomFields(int max) { this.maxCustomFields = max; return this; }
        public Builder enableAudit(boolean enable) { this.enableAudit = enable; return this; }

        public Builder field(String name, FieldType type, boolean required) {
            fields.add(new FieldDef(name, type, required));
            return this;
        }

        public Builder field(FieldDef fieldDef) {
            fields.add(fieldDef);
            return this;
        }

        public ModuleEntityDefinition build() {
            return new ModuleEntityDefinition(
                entityType, displayName, moduleId, description,
                List.copyOf(fields), maxCustomFields, enableAudit
            );
        }
    }
}
