package com.erp.module;

import com.erp.kernel.MicroKernel;
import com.erp.kernel.KernelConfig;

import javax.swing.*;
import java.util.*;

/**
 * ═══════════════════════════════════════════════════════════════
 * ErpModule — 所有业务模块的"始祖接口"
 *
 * 每个模块是一个独立的业务能力单元：财务、供应链、生产、HR...
 * 模块间完全解耦，仅通过 EventBus / ServiceRegistry 交互。
 *
 * 生命周期：
 *   CREATED → INITIALIZED → STARTED → STOPPED → DESTROYED
 *                ↓ (异常)                         ↑
 *              ERROR ──────────────────────────────
 *
 * 实现者需要提供：
 * - 基本信息（id, name, version）
 * - 依赖声明（dependencies）
 * - 生命周期方法
 * - 可选的UI贡献
 * - 可选的权限定义
 * ═══════════════════════════════════════════════════════════════
 */
public interface ErpModule {

    // ── 模块标识 ──

    /** 模块唯一ID（如 "erp.finance"） */
    String getId();

    /** 模块显示名称 */
    String getName();

    /** 模块版本号（语义化版本） */
    String getVersion();

    /** 模块供应商/作者 */
    default String getVendor() { return "unknown"; }

    /** 模块描述 */
    default String getDescription() { return ""; }

    // ── 依赖 ──

    /** 本模块依赖的其他模块 */
    default List<ModuleDependency> getDependencies() { return List.of(); }

    // ── 生命周期 ──

    /** 模块被安装后初始化（此时其他模块可能尚未就绪） */
    default void init(MicroKernel kernel, KernelConfig config) {}

    /** 模块启动（此时所有依赖模块已就绪） */
    default void start(MicroKernel kernel) {}

    /** 模块停止 */
    default void stop(MicroKernel kernel) {}

    /** 模块销毁（释放资源） */
    default void destroy(MicroKernel kernel) {}

    // ── 状态 ──

    /** 当前模块状态 */
    ModuleState getState();

    /** 设置状态（通常由 ModuleManager 调用） */
    void setState(ModuleState state);

    // ── UI 贡献 ──

    /**
     * 模块贡献的菜单项。
     * 返回 Map<菜单路径, 动作>，例如：
     *   "财务/总账/过账" → () -> showPostingDialog()
     */
    default Map<String, Runnable> getMenuContributions() { return Map.of(); }

    /**
     * 模块贡献的主面板。
     * 返回 Map<面板标题, JPanel>。
     */
    default Map<String, JPanel> getPanelContributions() { return Map.of(); }

    /**
     * 模块贡献的工具栏按钮。
     */
    default List<Action> getToolbarActions() { return List.of(); }

    // ── 权限 ──

    /** 模块定义的权限列表 */
    default List<ModulePermission> getDefinedPermissions() { return List.of(); }

    // ── 服务 ──

    /** 模块提供的服务类列表（框架自动注册到 ServiceRegistry） */
    default List<Class<?>> getProvidedServices() { return List.of(); }

    // ── 数据模型 ──

    /** 模块定义的动态实体类型 */
    default List<ModuleEntityDefinition> getEntityDefinitions() { return List.of(); }

    // ── 事件订阅 ──

    /** 模块关心的全局事件类型（字符串） */
    default Set<String> getSubscribedEventTypes() { return Set.of(); }

    /** 模块持有的订阅句柄（ModuleManager 在 stop 时自动关闭） */
    default List<AutoCloseable> getSubscriptions() { return List.of(); }
}
