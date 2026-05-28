package com.erp.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 动态字段支持的所有数据类型。
 * 每种类型映射到对应的 Java 类型。
 */
public enum FieldType {
    STRING(String.class),
    TEXT(String.class),         // 长文本
    INTEGER(Integer.class),
    LONG(Long.class),
    DECIMAL(BigDecimal.class),
    BOOLEAN(Boolean.class),
    DATE(LocalDate.class),
    DATETIME(LocalDateTime.class),
    ENUM(String.class),         // 枚举（配合 allowedValues）
    MULTI_ENUM(String.class),   // 多选枚举
    REFERENCE(String.class),    // 引用其他实体ID
    FILE_REF(String.class),     // 文件引用
    JSON(String.class),         // JSON字符串
    CURRENCY(BigDecimal.class), // 货币金额
    PERCENTAGE(BigDecimal.class);

    private final Class<?> javaType;

    FieldType(Class<?> javaType) {
        this.javaType = javaType;
    }

    public Class<?> getJavaType() {
        return javaType;
    }

    public boolean isNumeric() {
        return this == INTEGER || this == LONG || this == DECIMAL
            || this == CURRENCY || this == PERCENTAGE;
    }

    public boolean isTemporal() {
        return this == DATE || this == DATETIME;
    }
}
