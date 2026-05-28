# 始祖ERP 框架 — 开发者手册 v1.0

## 架构总览

```
┌─────────────────────────────────────────────┐
│                  ErpLauncher                 │
├─────────────────────────────────────────────┤
│               MicroKernel (单例)             │
│  ┌──────────┬──────────┬──────────────┐     │
│  │EventBus  │DataRouter│ServiceReg    │     │
│  ├──────────┼──────────┼──────────────┤     │
│  │AuthMgr   │OrgStruct │ModuleMgr     │     │
│  └──────────┴──────────┴──────────────┘     │
├─────────────────────────────────────────────┤
│            插件模块 (5+ 热插拔)               │
│  Finance │ Inventory │ HR │ Purchase │ Tech │
└─────────────────────────────────────────────┘
```

## 模块开发

### 最小模块

```java
public class MyModule implements ErpModule {
    private volatile ModuleState state = ModuleState.CREATED;
    private final List<AutoCloseable> subscriptions = new ArrayList<>();

    @Override public String getId() { return "my.module"; }
    @Override public String getName() { return "我的模块"; }
    @Override public String getVersion() { return "1.0.0"; }
    @Override public ModuleState getState() { return state; }
    @Override public void setState(ModuleState s) { this.state = s; }
    @Override public List<AutoCloseable> getSubscriptions() { return subscriptions; }
}
```

### 生命周期

```
CREATED → deploy() → INITIALIZED → start() → STARTED
                                                ↓ stop()
                                              STOPPED
                                                ↓ destroy()
                                            DESTROYED
```

| 方法 | 时机 | 可用资源 |
|------|------|---------|
| `init(kernel, config)` | 部署时，依赖尚未就绪 | Kernel, Config |
| `start(kernel)` | 所有依赖已就绪 | 全部内核服务 |
| `stop(kernel)` | 模块被移除 | 清理路由/服务 |
| `destroy(kernel)` | 最终释放 | 关闭文件/连接 |

### 菜单贡献

```java
@Override
public Map<String, Runnable> getMenuContributions() {
    return Map.of(
        "我的模块/功能A", () -> showPanelA(),
        "我的模块/功能B", () -> showPanelB()
    );
}
```

### 面板贡献

```java
@Override
public Map<String, JPanel> getPanelContributions() {
    return Map.of("📊 仪表盘", new MyDashboard());
}
```

### 权限定义

```java
@Override
public List<ModulePermission> getDefinedPermissions() {
    return List.of(
        new ModulePermission("my:read", "读取", "读取数据", getId()),
        new ModulePermission("my:write", "写入", "修改数据", getId())
    );
}
```

### 数据模型

```java
@Override
public List<ModuleEntityDefinition> getEntityDefinitions() {
    return List.of(
        ModuleEntityDefinition.builder("MyEntity")
            .displayName("我的实体")
            .field("name", FieldType.STRING, true)
            .field("amount", FieldType.DECIMAL, false)
            .build()
    );
}
```

### 事件订阅（必须用 subscriptions 追踪）

```java
@Override
public void start(MicroKernel kernel) {
    subscriptions.add(kernel.getEventBus().subscribe("data.entity.created", event -> {
        String entityType = event.getPayloadValue("entityType");
        log.info("实体创建: {}", entityType);
    }));
}

@Override
public List<AutoCloseable> getSubscriptions() {
    return subscriptions;  // ModuleManager 在 stop 时自动关闭
}
```

## 内核 API

### EventBus

```java
// 发布
kernel.getEventBus().publish(new ErpEvent("my.event", Map.of("key", val), Priority.NORMAL));

// 订阅
AutoCloseable sub = kernel.getEventBus().subscribe(listener);
```

### DataRouter

```java
// 注册 CRUD
router.registerCrudHandlers("MyEntity", new CrudHandler() { ... });

// 路由
RoutingResponse resp = router.route(new RoutingRequest("MyEntity", "create", entity, tenantId));
```

### ServiceRegistry

```java
// 注册服务
kernel.getServiceRegistry().registerService(MyService.class, new MyServiceImpl(), "1.0.0", getId());

// 获取服务
MyService svc = kernel.getServiceRegistry().getService(MyService.class);
```

### AuthManager

```java
// 权限检查
boolean ok = kernel.getAuthManager().checkPermission("my:write");
```

## 环境变量

| 变量 | 说明 | 默认值 |
|------|------|--------|
| `ERP_DB_HOST` | MySQL 地址 | `127.0.0.1` |
| `ERP_DB_PORT` | MySQL 端口 | `3306` |
| `ERP_DB_NAME` | 数据库名 | `erp` |
| `ERP_DB_USER` | 数据库用户 | `root` |
| `ERP_DB_PASS` | 数据库密码 | `erp2024` |

环境变量优先级 > `app.properties` > 默认值。

## 部署

```bash
# Docker
docker-compose up -d

# 手动
mvn package
java -cp target/erp-framework-1.0.0-alpha.jar:target/lib/* com.erp.ErpLauncher
```

## 健康检查

```java
Map<String, Object> status = HealthCheck.report();
// {status: "UP", uptime: "3600s", kernel: "RUNNING", modules: 5, db: {connected: true}, ...}
```

## 测试

```bash
mvn test
```

3 个核心测试套件：
- `ModuleManagerTest` — 拓扑排序、循环依赖、部署/卸载
- `DataRouterTest` — 路由分发、拦截器链、租户隔离
- `DatabaseManagerTest` — 连接池、事务提交/回滚
