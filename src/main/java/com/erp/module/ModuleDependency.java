package com.erp.module;

import java.util.Objects;

/**
 * 模块依赖声明 — 支持语义化版本范围。
 *
 * 示例：
 *   new ModuleDependency("erp.finance", "1.0.0", true)
 *   new ModuleDependency("erp.inventory", "1.2.x", false)
 */
public record ModuleDependency(
    /** 目标模块ID */
    String moduleId,

    /** 版本约束（支持 "x.y.z", "x.y.x", ">=x.y.z" 等） */
    String versionConstraint,

    /** true=必须依赖；false=可选依赖 */
    boolean required,

    /** 依赖描述 */
    String description
) {
    public ModuleDependency {
        Objects.requireNonNull(moduleId, "moduleId must not be null");
        Objects.requireNonNull(versionConstraint, "versionConstraint must not be null");
    }

    public ModuleDependency(String moduleId, String versionConstraint, boolean required) {
        this(moduleId, versionConstraint, required, "");
    }

    public ModuleDependency(String moduleId, String versionConstraint) {
        this(moduleId, versionConstraint, true, "");
    }

    /** 检查给定版本是否满足本依赖约束 */
    public boolean isSatisfiedBy(String actualVersion) {
        if (versionConstraint.equals("*") || versionConstraint.equals("x")) {
            return true;
        }
        // 简单前缀匹配： "1.2.x" → 以 "1.2." 开头
        if (versionConstraint.endsWith(".x") || versionConstraint.endsWith(".*")) {
            String prefix = versionConstraint.substring(0, versionConstraint.length() - 2);
            return actualVersion.startsWith(prefix);
        }
        // 精确匹配
        return versionConstraint.equals(actualVersion);
    }
}
