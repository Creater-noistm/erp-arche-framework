package com.erp.router;

import com.erp.kernel.KernelConfig;
import com.erp.kernel.MicroKernel;
import com.erp.tenant.TenantContext;
import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DataRouter 核心测试 — 路由注册、Handler 分发、拦截器链。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DataRouterTest {

    private static MicroKernel kernel;
    private static DataRouter router;

    @BeforeAll
    static void setUp() {
        kernel = MicroKernel.boot(new KernelConfig());
        router = kernel.getDataRouter();
    }

    @BeforeEach
    void setTenant() {
        TenantContext.setCurrentTenant("test-tenant-001");
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    // ── Handler 注册与分发 ──

    @Test
    @DisplayName("注册 CRUD Handler 后 route 能正确分发到对应操作")
    void crudHandlerDispatch() {
        AtomicInteger createCount = new AtomicInteger(0);
        AtomicInteger readCount = new AtomicInteger(0);

        router.registerCrudHandlers("test.entity", new DataRouter.CrudHandler() {
            @Override
            public Object create(DataRouter.RoutingRequest req, DataRouter.RoutingContext ctx) {
                createCount.incrementAndGet();
                return "created";
            }
            @Override
            public Object read(DataRouter.RoutingRequest req, DataRouter.RoutingContext ctx) {
                readCount.incrementAndGet();
                return "read";
            }
        });

        DataRouter.RoutingResponse r1 = router.route(
            new DataRouter.RoutingRequest("test.entity", "create", "payload", "test-tenant-001"));
        DataRouter.RoutingResponse r2 = router.route(
            new DataRouter.RoutingRequest("test.entity", "read", "123", "test-tenant-001"));

        assertTrue(r1.success());
        assertEquals("created", r1.getData());
        assertEquals(1, createCount.get());

        assertTrue(r2.success());
        assertEquals("read", r2.getData());
        assertEquals(1, readCount.get());
    }

    @Test
    @DisplayName("未注册的 entityType 返回 error 响应")
    void unregisteredEntityError() {
        DataRouter.RoutingResponse resp = router.route(
            new DataRouter.RoutingRequest("unknown.type", "read", "123", "test-tenant-001"));
        assertFalse(resp.success());
        assertTrue(resp.message().contains("No handlers"));
    }

    @Test
    @DisplayName("已注册 entityType 但未注册的 operation 返回 error")
    void unregisteredOperationError() {
        router.registerCrudHandlers("limited.entity", new DataRouter.CrudHandler() {
            @Override
            public Object read(DataRouter.RoutingRequest req, DataRouter.RoutingContext ctx) {
                return "ok";
            }
        });

        DataRouter.RoutingResponse resp = router.route(
            new DataRouter.RoutingRequest("limited.entity", "custom_op", "data", "test-tenant-001"));
        assertFalse(resp.success());
        assertTrue(resp.message().contains("No handler"));
    }

    @Test
    @DisplayName("Handler 内部异常不会影响其他路由")
    void handlerExceptionIsolated() {
        router.registerCrudHandlers("buggy.entity", new DataRouter.CrudHandler() {
            @Override
            public Object read(DataRouter.RoutingRequest req, DataRouter.RoutingContext ctx) {
                throw new RuntimeException("内部错误");
            }
        });

        DataRouter.RoutingResponse resp = router.route(
            new DataRouter.RoutingRequest("buggy.entity", "read", "123", "test-tenant-001"));
        assertFalse(resp.success());
        assertTrue(resp.message().contains("Routing error"));

        // 路由表无变化
        assertEquals(1, router.getRouteTable().getOrDefault("buggy.entity", Set.of()).size());
    }

    // ── 拦截器 ──

    @Test
    @DisplayName("前置拦截器返回 false 则终止请求")
    void preHandleFalseBlocks() {
        router.registerCrudHandlers("blocked.entity", new DataRouter.CrudHandler() {
            @Override
            public Object read(DataRouter.RoutingRequest req, DataRouter.RoutingContext ctx) {
                return "should not reach here";
            }
        });

        router.registerInterceptor(new DataRouter.RoutingInterceptor() {
            @Override
            public boolean preHandle(DataRouter.RoutingContext ctx) {
                return false;
            }
            @Override
            public int order() { return Integer.MIN_VALUE; }
        });

        DataRouter.RoutingResponse resp = router.route(
            new DataRouter.RoutingRequest("blocked.entity", "read", "123", "test-tenant-001"));
        assertFalse(resp.success());
        assertTrue(resp.message().contains("Blocked by interceptor"));
    }

    @Test
    @DisplayName("租户隔离拦截器 — 跨租户请求被拒绝")
    void tenantIsolationBlocksCrossTenant() {
        router.registerCrudHandlers("secured.entity", new DataRouter.CrudHandler() {
            @Override
            public Object read(DataRouter.RoutingRequest req, DataRouter.RoutingContext ctx) {
                return "data";
            }
        });

        TenantContext.setCurrentTenant("tenant-A");

        DataRouter.RoutingResponse resp = router.route(
            new DataRouter.RoutingRequest("secured.entity", "read", "123", "tenant-B"));
        assertFalse(resp.success());
        assertTrue(resp.message().contains("interceptor"));
    }

    @Test
    @DisplayName("审计拦截器记录请求耗时")
    void auditInterceptorRecordsTiming() {
        // 审计拦截器已在 DataRouter.initialize() 时注册
        // 验证它不阻止正常请求
        router.registerCrudHandlers("audited.entity", new DataRouter.CrudHandler() {
            @Override
            public Object read(DataRouter.RoutingRequest req, DataRouter.RoutingContext ctx) {
                Long startTime = ctx.getAttribute("audit.startTime");
                assertNotNull(startTime, "审计开始时间应被记录");
                return "audited data";
            }
        });

        DataRouter.RoutingResponse resp = router.route(
            new DataRouter.RoutingRequest("audited.entity", "read", "123", "test-tenant-001"));
        assertTrue(resp.success());
    }

    // ── 路由表管理 ──

    @Test
    @DisplayName("routeTable 正确反映注册状态")
    void routeTableReflectsRegistration() {
        router.registerCrudHandlers("table.test", new DataRouter.CrudHandler() {
            @Override
            public Object create(DataRouter.RoutingRequest r, DataRouter.RoutingContext c) { return "ok"; }
            @Override
            public Object query(DataRouter.RoutingRequest r, DataRouter.RoutingContext c) { return "ok"; }
        });

        Map<String, Set<String>> table = router.getRouteTable();
        assertTrue(table.containsKey("table.test"));
        assertTrue(table.get("table.test").contains("create"));
        assertTrue(table.get("table.test").contains("query"));
        assertFalse(table.get("table.test").contains("unknown_op"));
    }

    @Test
    @DisplayName("unregisterHandlers 移除所有操作")
    void unregisterRemovesAllOperations() {
        router.registerCrudHandlers("temp.entity", new DataRouter.CrudHandler() {
            @Override
            public Object read(DataRouter.RoutingRequest r, DataRouter.RoutingContext c) { return "ok"; }
        });

        assertTrue(router.getRouteTable().containsKey("temp.entity"));

        router.unregisterHandlers("temp.entity");
        assertFalse(router.getRouteTable().containsKey("temp.entity"));
    }

    @Test
    @DisplayName("routeCount 正确统计注册的 Handler 数量")
    void routeCountCorrect() {
        int before = router.getRouteCount();

        router.registerCrudHandlers("count.test.entity", new DataRouter.CrudHandler() {
            @Override
            public Object create(DataRouter.RoutingRequest r, DataRouter.RoutingContext c) { return "ok"; }
            @Override
            public Object read(DataRouter.RoutingRequest r, DataRouter.RoutingContext c) { return "ok"; }
        });

        assertEquals(before + 2, router.getRouteCount());

        router.unregisterHandlers("count.test.entity");
    }
}
