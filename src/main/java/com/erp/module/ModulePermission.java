package com.erp.module;

/**
 * 模块定义的权限点 — 模块向权限系统注册的受控操作。
 *
 * 示例：
 *   new ModulePermission("finance:journal:post", "过账", "允许执行总账过账操作")
 */
public record ModulePermission(
    /** 权限标识（全局唯一，建议 "module:resource:action"） */
    String id,

    /** 权限显示名称 */
    String displayName,

    /** 权限描述 */
    String description,

    /** 所属模块ID */
    String moduleId
) {
    public ModulePermission(String id, String displayName, String description) {
        this(id, displayName, description, "");
    }

    public ModulePermission withModuleId(String moduleId) {
        return new ModulePermission(this.id, this.displayName, this.description, moduleId);
    }
}
