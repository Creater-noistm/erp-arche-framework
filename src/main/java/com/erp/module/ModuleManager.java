package com.erp.module;

import com.erp.kernel.MicroKernel;
import com.erp.event.ErpEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * 模块管理器 — 负责模块的全生命周期管理。
 *
 * 核心能力：
 * 1. 部署/卸载模块（支持热部署）
 * 2. 依赖解析与启动排序
 * 3. 状态转换控制
 * 4. 模块隔离（ClassLoader 隔离）
 * 5. 错误恢复与自动重启
 */
public class ModuleManager {

    private static final Logger log = LoggerFactory.getLogger(ModuleManager.class);

    private final MicroKernel kernel;
    private final Map<String, ErpModule> modules = new ConcurrentHashMap<>();
    private final Map<String, ModuleState> moduleStates = new ConcurrentHashMap<>();
    private final Map<String, ClassLoader> moduleClassLoaders = new ConcurrentHashMap<>();

    /* 模块元数据缓存 */
    private final Map<String, ModuleMetadata> metadataMap = new ConcurrentHashMap<>();

    public ModuleManager(MicroKernel kernel) {
        this.kernel = kernel;
    }

    // ── 部署 ──

    /**
     * 部署一个模块（完整生命周期：init → start）。
     * 如果模块有依赖，先确保依赖已部署。
     */
    public synchronized void deploy(ErpModule module) {
        String id = module.getId();
        if (modules.containsKey(id)) {
            log.warn("Module '{}' already deployed, skipping", id);
            return;
        }

        log.info("Deploying module: {} v{}", id, module.getVersion());
        module.setState(ModuleState.CREATED);
        modules.put(id, module);

        // 检查依赖
        List<ModuleDependency> deps = module.getDependencies();
        for (ModuleDependency dep : deps) {
            if (dep.required() && !modules.containsKey(dep.moduleId())) {
                log.error("Required dependency '{}' not found for module '{}'", dep.moduleId(), id);
                module.setState(ModuleState.ERROR);
                return;
            }
        }

        try {
            // Phase 1: init
            module.init(kernel, kernel.getConfig());
            module.setState(ModuleState.INITIALIZED);
            metadataMap.put(id, new ModuleMetadata(module));

            // 注册模块服务
            registerModuleServices(module);

            // 注册模块权限
            registerModulePermissions(module);

            // 注册模块实体定义
            registerModuleEntities(module);

            // Phase 2: start
            module.start(kernel);
            module.setState(ModuleState.STARTED);

            // 发布模块启动事件
            kernel.getEventBus().publish(new ErpEvent(
                "module.started",
                Map.of("moduleId", id, "moduleName", module.getName(), "version", module.getVersion()),
                ErpEvent.Priority.NORMAL
            ));

            log.info("Module '{}' started successfully", id);

        } catch (Exception e) {
            log.error("Failed to deploy module '{}'", id, e);
            module.setState(ModuleState.ERROR);
        }
    }

    /** 卸载模块 */
    public synchronized void undeploy(String moduleId) {
        ErpModule module = modules.get(moduleId);
        if (module == null) {
            log.warn("Module '{}' not found, cannot undeploy", moduleId);
            return;
        }

        log.info("Undeploying module: {}", moduleId);

        // 检查是否有其他模块依赖于本模块
        List<String> dependents = findDependents(moduleId);
        if (!dependents.isEmpty()) {
            log.warn("Module '{}' has dependents: {} — undeploying them first", moduleId, dependents);
            for (String depId : dependents) {
                undeploy(depId);
            }
        }

        try {
            module.stop(kernel);
            module.setState(ModuleState.STOPPED);
            closeSubscriptions(module, "undeploy");

            module.destroy(kernel);
            module.setState(ModuleState.DESTROYED);

            // 清理服务注册
            kernel.getServiceRegistry().unregisterAllByModule(moduleId);

            modules.remove(moduleId);
            moduleStates.remove(moduleId);
            moduleClassLoaders.remove(moduleId);
            metadataMap.remove(moduleId);

            kernel.getEventBus().publish(new ErpEvent(
                "module.stopped",
                Map.of("moduleId", moduleId),
                ErpEvent.Priority.NORMAL
            ));

            log.info("Module '{}' undeployed successfully", moduleId);

        } catch (Exception e) {
            log.error("Error during module '{}' undeploy", moduleId, e);
            module.setState(ModuleState.ERROR);
        }
    }

    /** 重启模块 */
    public synchronized void restartModule(String moduleId) {
        log.info("Restarting module: {}", moduleId);
        ErpModule module = modules.get(moduleId);
        if (module != null) {
            // 不卸载，仅 stop + start
        try {
            module.stop(kernel);
            module.setState(ModuleState.STOPPED);
            closeSubscriptions(module, "restart");

            module.start(kernel);
                module.setState(ModuleState.STARTED);
                log.info("Module '{}' restarted", moduleId);
            } catch (Exception e) {
                log.error("Failed to restart module '{}'", moduleId, e);
                module.setState(ModuleState.ERROR);
            }
        }
    }

    /** 停止所有模块（用于内核关闭） */
    public void stopAllModules() {
        // 逆依赖顺序停止
        List<String> ordered = topologicalSort();
        Collections.reverse(ordered);
        for (String id : ordered) {
            ErpModule module = modules.get(id);
            if (module != null && module.getState() == ModuleState.STARTED) {
                try {
                    module.stop(kernel);
                    module.setState(ModuleState.STOPPED);
                    closeSubscriptions(module, "shutdown");
                } catch (Exception e) {
                    log.error("Error stopping module '{}'", id, e);
                }
            }
        }
        log.info("All modules stopped");
    }

    // ── 查询 ──

    public ErpModule getModule(String moduleId) {
        return modules.get(moduleId);
    }

    public List<ErpModule> getAllModules() {
        return List.copyOf(modules.values());
    }

    public int getActiveModuleCount() {
        return (int) modules.values().stream()
            .filter(m -> m.getState() == ModuleState.STARTED)
            .count();
    }

    public ModuleState getModuleState(String moduleId) {
        ErpModule m = modules.get(moduleId);
        return m != null ? m.getState() : null;
    }

    public ModuleMetadata getModuleMetadata(String moduleId) {
        return metadataMap.get(moduleId);
    }

    /** 获取按启动顺序排列的模块列表 */
    public List<ErpModule> getModulesInStartOrder() {
        return topologicalSort().stream()
            .map(modules::get)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    /** 获取贡献了UI菜单的所有模块 */
    public Map<String, Map<String, Runnable>> getAllMenuContributions() {
        Map<String, Map<String, Runnable>> result = new LinkedHashMap<>();
        for (ErpModule module : getModulesInStartOrder()) {
            Map<String, Runnable> menus = module.getMenuContributions();
            if (!menus.isEmpty()) {
                result.put(module.getName(), menus);
            }
        }
        return result;
    }

    /** 获取所有模块贡献的面板 */
    public Map<String, JPanel> getAllPanelContributions() {
        Map<String, JPanel> result = new LinkedHashMap<>();
        for (ErpModule module : getModulesInStartOrder()) {
            Map<String, JPanel> panels = module.getPanelContributions();
            result.putAll(panels);
        }
        return result;
    }

    // ── 内部 ──

    private void registerModuleServices(ErpModule module) {
        for (Class<?> serviceClass : module.getProvidedServices()) {
            log.debug("Module '{}' declares service: {}", module.getId(), serviceClass.getSimpleName());
        }
    }

    private void registerModulePermissions(ErpModule module) {
        for (ModulePermission perm : module.getDefinedPermissions()) {
            kernel.getAuthManager().registerPermission(
                perm.withModuleId(module.getId())
            );
        }
    }

    private void registerModuleEntities(ErpModule module) {
        for (ModuleEntityDefinition def : module.getEntityDefinitions()) {
            kernel.getDataModelRegistry().registerEntityDefinition(def);
        }
    }

    /** 查找直接依赖于指定模块的所有模块 */
    private List<String> findDependents(String moduleId) {
        return modules.values().stream()
            .filter(m -> m.getDependencies().stream()
                .anyMatch(d -> d.moduleId().equals(moduleId) && d.required()))
            .map(ErpModule::getId)
            .collect(Collectors.toList());
    }

    /** 拓扑排序（按依赖顺序） */
    private List<String> topologicalSort() {
        Map<String, Set<String>> graph = new HashMap<>();
        for (ErpModule module : modules.values()) {
            graph.computeIfAbsent(module.getId(), k -> new HashSet<>());
            for (ModuleDependency dep : module.getDependencies()) {
                if (dep.required()) {
                    graph.get(module.getId()).add(dep.moduleId());
                }
            }
        }

        List<String> sorted = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> visiting = new HashSet<>();

        for (String id : modules.keySet()) {
            if (!visited.contains(id)) {
                try {
                    dfsTopological(id, graph, visited, visiting, sorted);
                } catch (CycleDetectedException e) {
                    log.error("Module dependency cycle detected involving '{}'", id);
                }
            }
        }

        return sorted;
    }

    private void dfsTopological(String node, Map<String, Set<String>> graph,
                                 Set<String> visited, Set<String> visiting,
                                 List<String> sorted) {
        if (visiting.contains(node)) {
            throw new CycleDetectedException("Cycle detected at: " + node);
        }
        if (visited.contains(node)) return;

        visiting.add(node);
        Set<String> deps = graph.getOrDefault(node, Collections.emptySet());
        for (String dep : deps) {
            if (modules.containsKey(dep)) {
                dfsTopological(dep, graph, visited, visiting, sorted);
            }
        }
        visiting.remove(node);
        visited.add(node);
        sorted.add(node);
    }

    // ── 内部类型 ──

    private static class CycleDetectedException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        CycleDetectedException(String msg) { super(msg); }
    }

    /** 模块元数据（供 UI 和管理控制台使用） */
    public record ModuleMetadata(
        String id,
        String name,
        String version,
        String vendor,
        String description,
        List<String> dependencies,
        int serviceCount,
        int permissionCount,
        int entityCount
    ) {
        ModuleMetadata(ErpModule module) {
            this(
                module.getId(),
                module.getName(),
                module.getVersion(),
                module.getVendor(),
                module.getDescription(),
                module.getDependencies().stream().map(ModuleDependency::moduleId).toList(),
                module.getProvidedServices().size(),
                module.getDefinedPermissions().size(),
                module.getEntityDefinitions().size()
            );
        }
    }

    private void closeSubscriptions(ErpModule module, String reason) {
        for (AutoCloseable sub : module.getSubscriptions()) {
            try { sub.close(); }
            catch (Exception e) { log.debug("Sub close({}): {}", module.getId(), e.getMessage()); }
        }
        log.debug("Module '{}' subscriptions closed ({})", module.getId(), reason);
    }
}
