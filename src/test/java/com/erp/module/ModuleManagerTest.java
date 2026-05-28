package com.erp.module;

import com.erp.kernel.KernelConfig;
import com.erp.kernel.MicroKernel;
import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ModuleManager 核心测试 — 拓扑排序 / 循环依赖检测 / 部署顺序。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ModuleManagerTest {

    private static MicroKernel kernel;

    @BeforeAll
    static void setUp() {
        kernel = MicroKernel.boot(new KernelConfig());
    }

    @AfterEach
    void tearDown() {
        ModuleManager mgr = kernel.getModuleManager();
        List<String> ids = new ArrayList<>(mgr.getAllModules().stream().map(ErpModule::getId).toList());
        for (String id : ids) {
            try { mgr.undeploy(id); } catch (Exception ignored) {}
        }
    }

    // ── 拓扑排序 ──

    @Test
    @DisplayName("拓扑排序 — 线性依赖 A→B→C")
    void linearDependencyOrder() {
        ModuleManager mgr = kernel.getModuleManager();

        ErpModule modA = new TestModule("a", "ModuleA", List.of());
        ErpModule modB = new TestModule("b", "ModuleB", List.of(new ModuleDependency("a", "*", true)));
        ErpModule modC = new TestModule("c", "ModuleC", List.of(new ModuleDependency("b", "*", true)));

        mgr.deploy(modA);
        mgr.deploy(modB);
        mgr.deploy(modC);

        List<ErpModule> ordered = mgr.getModulesInStartOrder();
        List<String> names = ordered.stream().map(ErpModule::getId).toList();

        // A 必须在 B 前面，B 必须在 C 前面
        int idxA = names.indexOf("a");
        int idxB = names.indexOf("b");
        int idxC = names.indexOf("c");
        assertTrue(idxA < idxB, "A 应在 B 之前");
        assertTrue(idxB < idxC, "B 应在 C 之前");
        assertEquals(3, ordered.size());
    }

    @Test
    @DisplayName("拓扑排序 — 菱形依赖")
    void diamondDependencyOrder() {
        ModuleManager mgr = kernel.getModuleManager();

        // A ← B, C ← D (D 依赖 B 和 C, B/C 都依赖 A)
        ErpModule modA = new TestModule("a", "ModuleA", List.of());
        ErpModule modB = new TestModule("b", "ModuleB", List.of(new ModuleDependency("a", "*", true)));
        ErpModule modC = new TestModule("c", "ModuleC", List.of(new ModuleDependency("a", "*", true)));
        ErpModule modD = new TestModule("d", "ModuleD",
            List.of(new ModuleDependency("b", "*", true), new ModuleDependency("c", "*", true)));

        mgr.deploy(modA);
        mgr.deploy(modB);
        mgr.deploy(modC);
        mgr.deploy(modD);

        List<ErpModule> ordered = mgr.getModulesInStartOrder();
        List<String> names = ordered.stream().map(ErpModule::getId).toList();

        int idxA = names.indexOf("a");
        int idxB = names.indexOf("b");
        int idxC = names.indexOf("c");
        int idxD = names.indexOf("d");
        assertTrue(idxA < idxB, "A 应在 B 之前");
        assertTrue(idxA < idxC, "A 应在 C 之前");
        assertTrue(idxB < idxD, "B 应在 D 之前");
        assertTrue(idxC < idxD, "C 应在 D 之前");
        assertEquals(4, ordered.size());
    }

    @Test
    @DisplayName("拓扑排序 — 无依赖模块排在最前")
    void noDependenciesFirst() {
        ModuleManager mgr = kernel.getModuleManager();

        ErpModule modA = new TestModule("leaf1", "Leaf1", List.of());
        ErpModule modB = new TestModule("leaf2", "Leaf2", List.of());
        ErpModule modC = new TestModule("root", "Root",
            List.of(new ModuleDependency("leaf1", "*", true), new ModuleDependency("leaf2", "*", true)));

        mgr.deploy(modA);
        mgr.deploy(modB);
        mgr.deploy(modC);

        List<ErpModule> ordered = mgr.getModulesInStartOrder();
        List<String> names = ordered.stream().map(ErpModule::getId).toList();

        int idxA = names.indexOf("leaf1");
        int idxB = names.indexOf("leaf2");
        int idxC = names.indexOf("root");
        assertTrue(idxA < idxC, "leaf1 应在 root 之前");
        assertTrue(idxB < idxC, "leaf2 应在 root 之前");
    }

    @Test
    @DisplayName("拓扑排序 — 可选依赖不影响排序")
    void optionalDependencyDoesNotAffectOrder() {
        ModuleManager mgr = kernel.getModuleManager();

        ErpModule modA = new TestModule("a", "A", List.of());
        ErpModule modB = new TestModule("b", "B",
            List.of(new ModuleDependency("a", "*", false)));

        mgr.deploy(modA);
        mgr.deploy(modB);

        List<ErpModule> ordered = mgr.getModulesInStartOrder();
        List<String> names = ordered.stream().map(ErpModule::getId).toList();

        // 可选依赖不出现在拓扑图里，所以 B 可以在 A 前面
        assertTrue(names.containsAll(List.of("a", "b")), "两个模块都应出现在排序中");
    }

    // ── 循环依赖检测 ──

    @Test
    @DisplayName("循环依赖 — A→B→A 直接循环")
    void directCycleDetection() {
        ModuleManager mgr = kernel.getModuleManager();

        ErpModule modA = new TestModule("cycle_a", "CycleA",
            List.of(new ModuleDependency("cycle_b", "*", true)));
        ErpModule modB = new TestModule("cycle_b", "CycleB",
            List.of(new ModuleDependency("cycle_a", "*", true)));

        mgr.deploy(modA);
        mgr.deploy(modB);

        // 循环依赖不应导致异常抛出 — topologicalSort() 内部 catch 了 CycleDetectedException
        List<ErpModule> ordered = mgr.getModulesInStartOrder();
        // 循环会被检测并跳过，所以结果应至少包含一个模块
        assertFalse(ordered.isEmpty(), "至少有一个模块被排序");
    }

    // ── 部署/卸载 ──

    @Test
    @DisplayName("部署 — 状态转换 CREATED→STARTED")
    void deployStateTransition() {
        ModuleManager mgr = kernel.getModuleManager();

        ErpModule mod = new TestModule("test_state", "StateTest", List.of());
        assertEquals(ModuleState.CREATED, mod.getState());

        mgr.deploy(mod);
        assertEquals(ModuleState.STARTED, mod.getState());
    }

    @Test
    @DisplayName("卸载 — 级联卸载依赖者")
    void undeployCascadesToDependents() {
        ModuleManager mgr = kernel.getModuleManager();

        ErpModule modA = new TestModule("base", "BaseModule", List.of());
        ErpModule modB = new TestModule("dep", "Dependent",
            List.of(new ModuleDependency("base", "*", true)));

        mgr.deploy(modA);
        mgr.deploy(modB);

        assertEquals(2, mgr.getActiveModuleCount());

        // 卸载 base — 依赖者 dep 应被级联卸载
        mgr.undeploy("base");

        assertNull(mgr.getModule("base"));
        assertNull(mgr.getModule("dep"));
        assertEquals(0, mgr.getAllModules().size());
    }

    @Test
    @DisplayName("部署 — 缺少必须依赖应进入 ERROR 状态")
    void missingRequiredDependencyError() {
        ModuleManager mgr = kernel.getModuleManager();

        ErpModule mod = new TestModule("orphan", "OrphanMod",
            List.of(new ModuleDependency("non.existent", "*", true)));

        mgr.deploy(mod);
        assertEquals(ModuleState.ERROR, mod.getState());
    }

    @Test
    @DisplayName("重启 — 模块从 STARTED 状态重启后仍为 STARTED")
    void restartModule() {
        ModuleManager mgr = kernel.getModuleManager();

        ErpModule mod = new TestModule("restart_test", "RestartTest", List.of());
        mgr.deploy(mod);
        assertEquals(ModuleState.STARTED, mod.getState());

        mgr.restartModule("restart_test");
        assertEquals(ModuleState.STARTED, mod.getState());
    }

    // ── Test helper ──

    static class TestModule implements ErpModule {
        private final String id;
        private final String name;
        private final List<ModuleDependency> deps;
        private ModuleState state = ModuleState.CREATED;

        TestModule(String id, String name, List<ModuleDependency> deps) {
            this.id = id;
            this.name = name;
            this.deps = deps;
        }

        @Override public String getId() { return id; }
        @Override public String getName() { return name; }
        @Override public String getVersion() { return "1.0.0"; }
        @Override public List<ModuleDependency> getDependencies() { return deps; }
        @Override public ModuleState getState() { return state; }
        @Override public void setState(ModuleState state) { this.state = state; }
    }
}
