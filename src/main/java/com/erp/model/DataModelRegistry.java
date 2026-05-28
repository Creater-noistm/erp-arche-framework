package com.erp.model;

import com.erp.kernel.MicroKernel;
import com.erp.module.ModuleEntityDefinition;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * 数据模型注册表 — 管理所有实体类型定义和自定义字段。
 *
 * 职责：
 * 1. 注册/注销实体类型定义
 * 2. 管理自定义字段（添加、修改、删除）
 * 3. 提供字段验证服务
 * 4. 提供实体元数据查询
 *
 * 模块在 init() 或 start() 阶段注册其实体定义，
 * 之后任何模块或用户都可向已有实体添加自定义字段。
 */
public class DataModelRegistry {

    private static final Logger log = LoggerFactory.getLogger(DataModelRegistry.class);

    @SuppressWarnings("unused")
    private final MicroKernel kernel;

    /** 实体类型定义 */
    private final Map<String, EntityTypeDefinition> entityDefinitions = new ConcurrentHashMap<>();

    /** 自定义字段索引： entityType → Map<fieldName, CustomField> */
    private final Map<String, Map<String, CustomField>> customFields = new ConcurrentHashMap<>();

    /** 实体变更监听器 */
    private final List<EntityModelListener> listeners = new CopyOnWriteArrayList<>();

    public DataModelRegistry(MicroKernel kernel) {
        this.kernel = kernel;
    }

    public void initialize() {
        log.info("DataModelRegistry initialized");
    }

    // ── 实体类型定义 ──

    /** 注册实体类型定义（由模块调用） */
    public void registerEntityDefinition(ModuleEntityDefinition def) {
        EntityTypeDefinition existing = entityDefinitions.get(def.entityType());
        if (existing != null) {
            log.warn("Entity type '{}' already registered by module '{}', merging fields",
                def.entityType(), existing.moduleId());
        }

        EntityTypeDefinition typeDef = new EntityTypeDefinition(
            def.entityType(),
            def.displayName(),
            def.moduleId(),
            def.description(),
            def.fields().stream()
                .map(f -> CustomField.builder(def.entityType(), f.name())
                    .type(f.type())
                    .displayName(f.displayName())
                    .required(f.required())
                    .defaultValue(f.defaultValue())
                    .maxLength(f.maxLength())
                    .build())
                .collect(Collectors.toList()),
            def.maxCustomFields(),
            def.enableAudit()
        );

        entityDefinitions.put(def.entityType(), typeDef);
        customFields.computeIfAbsent(def.entityType(), k -> new ConcurrentHashMap<>());

        log.info("Entity type '{}' registered by module '{}' with {} fields",
            def.entityType(), def.moduleId(), def.fields().size());
    }

    /** 注销实体类型定义 */
    public void unregisterEntityDefinition(String entityType) {
        entityDefinitions.remove(entityType);
        customFields.remove(entityType);
        log.info("Entity type '{}' unregistered", entityType);
    }

    // ── 自定义字段管理 ──

    /** 添加自定义字段到指定实体类型 */
    public void addCustomField(CustomField field) {
        String entityType = field.getEntityType();
        Map<String, CustomField> fields = customFields.get(entityType);

        if (fields == null) {
            throw new IllegalArgumentException(
                "Entity type '" + entityType + "' is not registered");
        }

        int maxFields = entityDefinitions.get(entityType).maxCustomFields();
        if (fields.size() >= maxFields) {
            throw new IllegalStateException(
                "Max custom fields (" + maxFields + ") reached for entity type '"
                + entityType + "'");
        }

        fields.put(field.getFieldName(), field);
        log.info("Custom field '{}' added to '{}'", field.getFieldName(), entityType);
        notifyListeners(entityType, field, EntityModelEvent.FIELD_ADDED);
    }

    /** 批量添加自定义字段 */
    public void addCustomFields(List<CustomField> fields) {
        for (CustomField field : fields) {
            addCustomField(field);
        }
    }

    /** 删除自定义字段 */
    public void removeCustomField(String entityType, String fieldName) {
        Map<String, CustomField> fields = customFields.get(entityType);
        if (fields != null) {
            CustomField removed = fields.remove(fieldName);
            if (removed != null) {
                log.info("Custom field '{}' removed from '{}'", fieldName, entityType);
                notifyListeners(entityType, removed, EntityModelEvent.FIELD_REMOVED);
            }
        }
    }

    /** 获取实体的所有字段（标准字段 + 自定义字段） */
    public List<CustomField> getAllFields(String entityType) {
        List<CustomField> result = new ArrayList<>();

        EntityTypeDefinition def = entityDefinitions.get(entityType);
        if (def != null) {
            result.addAll(def.baseFields());
        }

        Map<String, CustomField> custom = customFields.get(entityType);
        if (custom != null) {
            result.addAll(custom.values());
        }

        return result;
    }

    /** 仅获取自定义字段 */
    public List<CustomField> getCustomFields(String entityType) {
        Map<String, CustomField> fields = customFields.get(entityType);
        return fields != null ? List.copyOf(fields.values()) : List.of();
    }

    /** 获取实体类型的标准字段定义 */
    public List<CustomField> getBaseFields(String entityType) {
        EntityTypeDefinition def = entityDefinitions.get(entityType);
        return def != null ? List.copyOf(def.baseFields()) : List.of();
    }

    /** 获取指定字段的完整定义 */
    public CustomField getField(String entityType, String fieldName) {
        // 先查自定义字段
        Map<String, CustomField> fields = customFields.get(entityType);
        if (fields != null) {
            CustomField cf = fields.get(fieldName);
            if (cf != null) return cf;
        }
        // 再查标准字段
        EntityTypeDefinition def = entityDefinitions.get(entityType);
        if (def != null) {
            for (CustomField base : def.baseFields()) {
                if (base.getFieldName().equals(fieldName)) return base;
            }
        }
        return null;
    }

    // ── 查询 ──

    /** 获取所有已注册实体类型 */
    public Set<String> getRegisteredEntityTypes() {
        return Set.copyOf(entityDefinitions.keySet());
    }

    /** 获取实体类型定义 */
    public EntityTypeDefinition getEntityDefinition(String entityType) {
        return entityDefinitions.get(entityType);
    }

    /** 获取实体类型的显示名称 */
    public String getDisplayName(String entityType) {
        EntityTypeDefinition def = entityDefinitions.get(entityType);
        return def != null ? def.displayName() : entityType;
    }

    /** 获取所有实体类型摘要 */
    public List<EntityTypeSummary> listEntityTypes() {
        return entityDefinitions.values().stream()
            .map(def -> new EntityTypeSummary(
                def.entityType(),
                def.displayName(),
                def.moduleId(),
                def.baseFields().size(),
                customFields.getOrDefault(def.entityType(), Map.of()).size(),
                def.maxCustomFields()
            ))
            .sorted(Comparator.comparing(EntityTypeSummary::entityType))
            .toList();
    }

    // ── 验证 ──

    /** 验证实体数据是否符合字段定义 */
    public List<ValidationError> validate(DynamicEntity entity) {
        List<ValidationError> errors = new ArrayList<>();
        String entityType = entity.getEntityType();

        List<CustomField> fields = getAllFields(entityType);
        for (CustomField field : fields) {
            String fn = field.getFieldName();
            Object value = entity.get(fn);

            // 必填检查
            if (field.isRequired() && (value == null || value.toString().isBlank())) {
                errors.add(new ValidationError(fn, field.getDisplayName() + " is required"));
                continue;
            }

            if (value == null) continue;

            // 类型检查
            if (!isTypeCompatible(field.getType(), value)) {
                errors.add(new ValidationError(fn,
                    "Type mismatch: expected " + field.getType() + " for " + field.getDisplayName()));
            }

            // 长度检查
            if (field.getMaxLength() > 0 && value.toString().length() > field.getMaxLength()) {
                errors.add(new ValidationError(fn,
                    field.getDisplayName() + " exceeds max length of " + field.getMaxLength()));
            }

            // 正则验证
            if (!field.getValidationPattern().isBlank()
                && !value.toString().matches(field.getValidationPattern())) {
                errors.add(new ValidationError(fn,
                    field.getDisplayName() + " does not match pattern: " + field.getValidationPattern()));
            }

            // 枚举值检查
            if (!field.getAllowedValues().isEmpty()
                && !field.getAllowedValues().contains(value.toString())) {
                errors.add(new ValidationError(fn,
                    field.getDisplayName() + " must be one of: " + field.getAllowedValues()));
            }
        }

        return errors;
    }

    private boolean isTypeCompatible(FieldType type, Object value) {
        if (value == null) return true;
        return switch (type) {
            case STRING, TEXT, ENUM, MULTI_ENUM, REFERENCE, FILE_REF, JSON ->
                value instanceof String;
            case INTEGER -> value instanceof Integer;
            case LONG -> value instanceof Long;
            case DECIMAL, CURRENCY, PERCENTAGE ->
                value instanceof java.math.BigDecimal || value instanceof Number;
            case BOOLEAN -> value instanceof Boolean;
            case DATE -> value instanceof java.time.LocalDate;
            case DATETIME -> value instanceof java.time.LocalDateTime;
        };
    }

    // ── 监听器 ──

    public void addListener(EntityModelListener listener) {
        listeners.add(listener);
    }

    public void removeListener(EntityModelListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners(String entityType, CustomField field, EntityModelEvent event) {
        for (EntityModelListener l : listeners) {
            try {
                l.onEntityModelEvent(entityType, field, event);
            } catch (Exception e) {
                log.error("EntityModelListener error", e);
            }
        }
    }

    // ── 内部类型 ──

    public record EntityTypeDefinition(
        String entityType,
        String displayName,
        String moduleId,
        String description,
        List<CustomField> baseFields,
        int maxCustomFields,
        boolean enableAudit
    ) {}

    public record EntityTypeSummary(
        String entityType,
        String displayName,
        String moduleId,
        int baseFieldCount,
        int customFieldCount,
        int maxCustomFields
    ) {}

    public record ValidationError(String fieldName, String message) {}

    public enum EntityModelEvent { FIELD_ADDED, FIELD_REMOVED, FIELD_MODIFIED }

    @FunctionalInterface
    public interface EntityModelListener {
        void onEntityModelEvent(String entityType, CustomField field, EntityModelEvent event);
    }
}
