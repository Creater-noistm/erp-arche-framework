package com.erp.kernel;

import com.erp.module.ErpModule;
import com.erp.module.ModuleManager;
import com.erp.event.EventBus;
import com.erp.router.DataRouter;
import com.erp.auth.AuthManager;
import com.erp.org.OrgStructureManager;
import com.erp.model.DataModelRegistry;
import com.erp.api.ServiceRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * ═══════════════════════════════════════════════════════════════
 * MicroKernel — 始祖ERP框架的微内核
 *
 * 职责：
 * 1. 模块生命周期管理（加载→初始化→启动→停止→卸载）
 * 2. 核心服务注册与发现
 * 3. 全局配置管理
 * 4. 系统健康监控
 * 5. 热部署协调
 *
 * 设计原则：
 * - 内核极简：仅保留不可削减的底层能力
 * - 无业务逻辑：所有业务由插件模块提供
 * - 模块完全隔离：模块间仅通过EventBus/ServiceRouter通信
 * - 可替换：每个核心服务都有SPI，可被模块替换
 * ═══════════════════════════════════════════════════════════════
 */
public class MicroKernel {

    private static final Logger log = LoggerFactory.getLogger(MicroKernel.class);

    /** 全局唯一内核实例 */
    private static final AtomicReference<MicroKernel> INSTANCE = new AtomicReference<>();

    /** 内核状态 */
    private volatile KernelState state = KernelState.CREATED;

    /** 模块管理器 */
    private final ModuleManager moduleManager;

    /** 事件总线（模块间通信主干道） */
    private final EventBus eventBus;

    /** 数据路由器 */
    private final DataRouter dataRouter;

    /** 权限管理器 */
    private final AuthManager authManager;

    /** 组织架构管理器 */
    private final OrgStructureManager orgStructureManager;

    /** 数据模型注册表 */
    private final DataModelRegistry dataModelRegistry;

    /** 服务注册表（API协议层） */
    private final ServiceRegistry serviceRegistry;

    /** 内核级配置 */
    private final KernelConfig config;

    /** 系统级线程池 */
    private final ScheduledExecutorService scheduler;

    /** 已注册的核心服务（内核自带服务，非模块） */
    private final Map<Class<?>, Object> kernelServices = new ConcurrentHashMap<>();

    /** 模块热部署监听器 */
    private final List<HotDeployListener> hotDeployListeners = new CopyOnWriteArrayList<>();

    // ─────────────────────────────────────────────────────────
    // 构造 & 单例
    // ─────────────────────────────────────────────────────────

    private MicroKernel(KernelConfig config) {
        this.config = config != null ? config : new KernelConfig();
        this.scheduler = Executors.newScheduledThreadPool(
            this.config.getCoreThreadPoolSize(),
            r -> {
                Thread t = new Thread(r, "kernel-scheduler");
                t.setDaemon(true);
                return t;
            }
        );

        // 按依赖顺序初始化核心子系统
        this.eventBus          = new EventBus(this);
        this.dataModelRegistry = new DataModelRegistry(this);
        this.orgStructureManager = new OrgStructureManager(this);
        this.authManager       = new AuthManager(this);
        this.dataRouter        = new DataRouter(this);
        this.serviceRegistry   = new ServiceRegistry(this);
        this.moduleManager     = new ModuleManager(this);

        // 注册内核服务自身
        registerKernelService(MicroKernel.class, this);
        registerKernelService(EventBus.class, eventBus);
        registerKernelService(DataModelRegistry.class, dataModelRegistry);
        registerKernelService(OrgStructureManager.class, orgStructureManager);
        registerKernelService(AuthManager.class, authManager);
        registerKernelService(DataRouter.class, dataRouter);
        registerKernelService(ServiceRegistry.class, serviceRegistry);
        registerKernelService(ModuleManager.class, moduleManager);

        log.info("☰ MicroKernel constructed with config: {}", config);
    }

    /**
     * 初始化内核（单例创建点）。
     * 线程安全，仅首次调用生效。
     */
    public static MicroKernel boot(KernelConfig config) {
        if (INSTANCE.get() != null) {
            log.warn("MicroKernel already booted, returning existing instance");
            return INSTANCE.get();
        }
        MicroKernel kernel = new MicroKernel(config != null ? config : new KernelConfig());
        if (INSTANCE.compareAndSet(null, kernel)) {
            kernel.startup();
        }
        return INSTANCE.get();
    }

    /** 获取已启动的内核实例 */
    public static MicroKernel getInstance() {
        MicroKernel kernel = INSTANCE.get();
        if (kernel == null) {
            throw new IllegalStateException(
                "MicroKernel has not been booted. Call MicroKernel.boot() first."
            );
        }
        return kernel;
    }

    /** 检查内核是否已启动 */
    public static boolean isActive() {
        return INSTANCE.get() != null
            && INSTANCE.get().state == KernelState.RUNNING;
    }

    // ─────────────────────────────────────────────────────────
    // 内核生命周期
    // ─────────────────────────────────────────────────────────

    /** 启动内核 */
    private synchronized void startup() {
        if (state != KernelState.CREATED) {
            log.warn("Cannot start kernel from state: {}", state);
            return;
        }
        log.info("☰ MicroKernel starting up...");
        state = KernelState.STARTING;

        // 初始化各子系统
        eventBus.initialize();
        dataModelRegistry.initialize();
        orgStructureManager.initialize();
        authManager.initialize();
        dataRouter.initialize();
        serviceRegistry.initialize();

        // 启动周期性健康检查
        scheduler.scheduleAtFixedRate(
            this::healthCheck,
            30, 60, TimeUnit.SECONDS
        );

        state = KernelState.RUNNING;
        log.info("☰ MicroKernel startup complete — state: RUNNING");
    }

    /** 优雅关闭内核 */
    public synchronized void shutdown() {
        log.info("☰ MicroKernel shutting down...");
        state = KernelState.STOPPING;

        // 1. 停止所有模块
        moduleManager.stopAllModules();

        // 2. 关闭调度器
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // 3. 关闭子系统
        eventBus.shutdown();
        dataRouter.shutdown();
        serviceRegistry.shutdown();

        state = KernelState.TERMINATED;
        INSTANCE.set(null);
        log.info("☰ MicroKernel shutdown complete");
    }

    // ─────────────────────────────────────────────────────────
    // 内核服务注册
    // ─────────────────────────────────────────────────────────

    /** 注册一个内核级服务 */
    public <T> void registerKernelService(Class<T> serviceClass, T implementation) {
        kernelServices.put(serviceClass, implementation);
        log.debug("Kernel service registered: {}", serviceClass.getSimpleName());
    }

    /** 获取内核级服务 */
    @SuppressWarnings("unchecked")
    public <T> T getKernelService(Class<T> serviceClass) {
        T service = (T) kernelServices.get(serviceClass);
        if (service == null) {
            // 同时查模块服务
            service = serviceRegistry.getService(serviceClass);
        }
        return service;
    }

    // ─────────────────────────────────────────────────────────
    // 模块管理委托
    // ─────────────────────────────────────────────────────────

    /** 安装并启动一个模块（热部署入口） */
    public void deployModule(ErpModule module) {
        moduleManager.deploy(module);
        fireHotDeployEvent(module, HotDeployEventType.DEPLOYED);
    }

    /** 卸载一个模块 */
    public void undeployModule(String moduleId) {
        ErpModule module = moduleManager.getModule(moduleId);
        if (module != null) {
            moduleManager.undeploy(moduleId);
            fireHotDeployEvent(module, HotDeployEventType.UNDEPLOYED);
        }
    }

    /** 获取所有已部署模块 */
    public List<ErpModule> getDeployedModules() {
        return moduleManager.getAllModules();
    }

    // ─────────────────────────────────────────────────────────
    // 公共访问器
    // ─────────────────────────────────────────────────────────

    public KernelState getState() { return state; }
    public ModuleManager getModuleManager() { return moduleManager; }
    public EventBus getEventBus() { return eventBus; }
    public DataRouter getDataRouter() { return dataRouter; }
    public AuthManager getAuthManager() { return authManager; }
    public OrgStructureManager getOrgStructureManager() { return orgStructureManager; }
    public DataModelRegistry getDataModelRegistry() { return dataModelRegistry; }
    public ServiceRegistry getServiceRegistry() { return serviceRegistry; }
    public KernelConfig getConfig() { return config; }
    public ScheduledExecutorService getScheduler() { return scheduler; }

    // ─────────────────────────────────────────────────────────
    // 热部署监听
    // ─────────────────────────────────────────────────────────

    public void addHotDeployListener(HotDeployListener listener) {
        hotDeployListeners.add(listener);
    }

    public void removeHotDeployListener(HotDeployListener listener) {
        hotDeployListeners.remove(listener);
    }

    private void fireHotDeployEvent(ErpModule module, HotDeployEventType type) {
        HotDeployEvent event = new HotDeployEvent(module, type, System.currentTimeMillis());
        for (HotDeployListener listener : hotDeployListeners) {
            try {
                listener.onHotDeploy(event);
            } catch (Exception e) {
                log.error("HotDeployListener error", e);
            }
        }
    }

    // ─────────────────────────────────────────────────────────
    // 健康检查
    // ─────────────────────────────────────────────────────────

    private void healthCheck() {
        if (state != KernelState.RUNNING) return;
        int moduleCount = moduleManager.getActiveModuleCount();
        int serviceCount = serviceRegistry.getServiceCount();
        log.debug("Health check — modules: {}, services: {}", moduleCount, serviceCount);

        // 检测异常状态的模块
        moduleManager.getAllModules().forEach(module -> {
            if (module.getState() == com.erp.module.ModuleState.ERROR) {
                log.warn("Module {} is in ERROR state, attempting restart...", module.getId());
                moduleManager.restartModule(module.getId());
            }
        });
    }

    // ─────────────────────────────────────────────────────────
    // 内部类型
    // ─────────────────────────────────────────────────────────

    public enum KernelState {
        CREATED,        // 已创建
        STARTING,       // 启动中
        RUNNING,        // 运行中
        STOPPING,       // 停止中
        TERMINATED      // 已终止
    }

    public enum HotDeployEventType {
        DEPLOYED,
        UNDEPLOYED,
        STARTED,
        STOPPED,
        ERROR
    }

    public record HotDeployEvent(
        ErpModule module,
        HotDeployEventType type,
        long timestamp
    ) {}

    @FunctionalInterface
    public interface HotDeployListener {
        void onHotDeploy(HotDeployEvent event);
    }
}
