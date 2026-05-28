package com.erp.model;

import java.io.Serializable;
import java.util.*;

/**
 * 自定义字段定义 — 运行时动态扩展实体的数据结构。
 *
 * 每个字段由 entityType + fieldName 唯一标识。
 * 支持默认值、枚举值列表、验证规则、权限控制。
 *
 * 使用示例：
 *   CustomField.builder("SalesOrder", "vatRate")
 *       .type(FieldType.PERCENTAGE)
 *       .displayName("增值税率")
 *       .defaultValue("0.13")
 *       .required(true)
 *       .build();
 */
public class CustomField implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String entityType;
    private final String fieldName;
    private final FieldType type;
    private final String displayName;
    private final String description;
    private final boolean required;
    private final boolean unique;
    private final boolean indexed;
    private final String defaultValue;
    private final int maxLength;
    private final String validationPattern;
    private final List<String> allowedValues;
    private final String groupName;
    private final int sortOrder;
    private final boolean visible;
    private final boolean readOnly;
    private final Map<String, Object> extensionAttributes;

    private CustomField(Builder builder) {
        this.entityType = builder.entityType;
        this.fieldName = builder.fieldName;
        this.type = builder.type;
        this.displayName = builder.displayName;
        this.description = builder.description;
        this.required = builder.required;
        this.unique = builder.unique;
        this.indexed = builder.indexed;
        this.defaultValue = builder.defaultValue;
        this.maxLength = builder.maxLength;
        this.validationPattern = builder.validationPattern;
        this.allowedValues = List.copyOf(builder.allowedValues);
        this.groupName = builder.groupName;
        this.sortOrder = builder.sortOrder;
        this.visible = builder.visible;
        this.readOnly = builder.readOnly;
        this.extensionAttributes = Map.copyOf(builder.extensionAttributes);
    }

    // ── Getters ──

    public String getEntityType() { return entityType; }
    public String getFieldName() { return fieldName; }
    public FieldType getType() { return type; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public boolean isRequired() { return required; }
    public boolean isUnique() { return unique; }
    public boolean isIndexed() { return indexed; }
    public String getDefaultValue() { return defaultValue; }
    public int getMaxLength() { return maxLength; }
    public String getValidationPattern() { return validationPattern; }
    public List<String> getAllowedValues() { return allowedValues; }
    public String getGroupName() { return groupName; }
    public int getSortOrder() { return sortOrder; }
    public boolean isVisible() { return visible; }
    public boolean isReadOnly() { return readOnly; }
    public Map<String, Object> getExtensionAttributes() { return extensionAttributes; }

    // ── Builder ──

    public static Builder builder(String entityType, String fieldName) {
        return new Builder(entityType, fieldName);
    }

    public static class Builder {
        private final String entityType;
        private final String fieldName;
        private FieldType type = FieldType.STRING;
        private String displayName;
        private String description = "";
        private boolean required = false;
        private boolean unique = false;
        private boolean indexed = false;
        private String defaultValue = "";
        private int maxLength = 255;
        private String validationPattern = "";
        private List<String> allowedValues = List.of();
        private String groupName = "基本属性";
        private int sortOrder = 0;
        private boolean visible = true;
        private boolean readOnly = false;
        private Map<String, Object> extensionAttributes = Map.of();

        Builder(String entityType, String fieldName) {
            this.entityType = entityType;
            this.fieldName = fieldName;
            this.displayName = fieldName;
        }

        public Builder type(FieldType type) { this.type = type; return this; }
        public Builder displayName(String displayName) { this.displayName = displayName; return this; }
        public Builder description(String desc) { this.description = desc; return this; }
        public Builder required(boolean required) { this.required = required; return this; }
        public Builder unique(boolean unique) { this.unique = unique; return this; }
        public Builder indexed(boolean indexed) { this.indexed = indexed; return this; }
        public Builder defaultValue(String defaultValue) { this.defaultValue = defaultValue; return this; }
        public Builder maxLength(int maxLength) { this.maxLength = maxLength; return this; }
        public Builder validationPattern(String pattern) { this.validationPattern = pattern; return this; }
        public Builder allowedValues(List<String> values) { this.allowedValues = values; return this; }
        public Builder groupName(String groupName) { this.groupName = groupName; return this; }
        public Builder sortOrder(int sortOrder) { this.sortOrder = sortOrder; return this; }
        public Builder visible(boolean visible) { this.visible = visible; return this; }
        public Builder readOnly(boolean readOnly) { this.readOnly = readOnly; return this; }
        public Builder extensionAttributes(Map<String, Object> attrs) { this.extensionAttributes = attrs; return this; }

        public CustomField build() {
            validate();
            return new CustomField(this);
        }

        private void validate() {
            if (entityType == null || entityType.isBlank()) {
                throw new IllegalArgumentException("entityType must not be blank");
            }
            if (fieldName == null || fieldName.isBlank()) {
                throw new IllegalArgumentException("fieldName must not be blank");
            }
            if (type == null) {
                throw new IllegalArgumentException("type must not be null");
            }
        }
    }

    @Override
    public String toString() {
        return entityType + "." + fieldName + ":" + type;
    }
}
