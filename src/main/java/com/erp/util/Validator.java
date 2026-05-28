package com.erp.util;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 输入校验工具 — 数据入库前的最后一道防线。
 *
 * 用法：
 *   ValidationResult r = Validator.of("客户名称", name).notBlank().maxLen(100).validate();
 *   if (!r.isValid()) throw new ValidationException(r.getErrors());
 */
public class Validator {

    private final String fieldName;
    private final String value;
    private final List<String> errors = new ArrayList<>();

    private Validator(String fieldName, String value) {
        this.fieldName = fieldName;
        this.value = value;
    }

    public static Validator of(String fieldName, String value) {
        return new Validator(fieldName, value);
    }

    public Validator notBlank() {
        if (value == null || value.trim().isEmpty()) {
            errors.add(fieldName + " 不能为空");
        }
        return this;
    }

    public Validator maxLen(int max) {
        if (value != null && value.length() > max) {
            errors.add(fieldName + " 长度不能超过 " + max + " 字符");
        }
        return this;
    }

    public Validator minLen(int min) {
        if (value != null && value.trim().length() < min) {
            errors.add(fieldName + " 长度不能少于 " + min + " 字符");
        }
        return this;
    }

    public Validator isNumeric() {
        if (value != null && !value.trim().isEmpty()) {
            try { new BigDecimal(value.trim()); }
            catch (NumberFormatException e) { errors.add(fieldName + " 必须是数字"); }
        }
        return this;
    }

    public Validator isPositive() {
        if (value != null && !value.trim().isEmpty()) {
            try {
                if (new BigDecimal(value.trim()).compareTo(BigDecimal.ZERO) <= 0) {
                    errors.add(fieldName + " 必须是正数");
                }
            } catch (NumberFormatException e) {
                errors.add(fieldName + " 必须是数字");
            }
        }
        return this;
    }

    public Validator isDate() {
        if (value != null && !value.trim().isEmpty()) {
            if (!value.matches("\\d{4}-\\d{2}-\\d{2}")) {
                errors.add(fieldName + " 格式应为 YYYY-MM-DD");
            }
        }
        return this;
    }

    public ValidationResult validate() {
        return new ValidationResult(errors.isEmpty(), List.copyOf(errors));
    }

    public record ValidationResult(boolean valid, List<String> errors) {}

    public static class ValidationException extends RuntimeException {
        private final List<String> errors;
        public ValidationException(List<String> errors) {
            super(String.join("; ", errors));
            this.errors = errors;
        }
        public List<String> getErrors() { return errors; }
    }
}
