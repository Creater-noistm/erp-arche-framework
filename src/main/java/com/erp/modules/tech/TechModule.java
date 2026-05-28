package com.erp.modules.tech;

import com.erp.kernel.MicroKernel;
import com.erp.kernel.KernelConfig;
import com.erp.module.*;
import com.erp.ui.CrudPanel;
import com.erp.ui.FrameworkWindow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.util.*;

/**
 * 技术管理模块 — 产品资料、BOM 维护（占位）。
 */
public class TechModule implements ErpModule {

    private static final Logger log = LoggerFactory.getLogger(TechModule.class);
    private volatile ModuleState state = ModuleState.CREATED;

    @Override public String getId() { return "erp.tech"; }
    @Override public String getName() { return "技术管理"; }
    @Override public String getVersion() { return "1.0.0"; }
    @Override public String getVendor() { return "Arche ERP Team"; }
    @Override public String getDescription() { return "产品技术资料、BOM、工艺路线"; }
    @Override public List<ModuleDependency> getDependencies() { return List.of(); }

    @Override public void init(MicroKernel kernel, KernelConfig config) {}
    @Override public void start(MicroKernel kernel) { log.info("TechModule started"); }
    @Override public void stop(MicroKernel kernel) {}
    @Override public void destroy(MicroKernel kernel) {}
    @Override public ModuleState getState() { return state; }
    @Override public void setState(ModuleState state) { this.state = state; }

    @Override
    public Map<String, Runnable> getMenuContributions() {
        String t = com.erp.tenant.TenantContext.getCurrentTenantId();
        Map<String, Runnable> menus = new LinkedHashMap<>();

        menus.put("产品管理/产品资料", () ->
            FrameworkWindow.INSTANCE.openContentTab("产品资料",
                new CrudPanel("产品技术资料",
                    "SELECT p.id, p.product_code, p.product_name, p.category, p.unit, p.unit_price " +
                    "FROM inv_products p WHERE p.tenant_id=? ORDER BY p.product_code",
                    "inv_products", false,
                    CrudPanel.CrudColumn.id("ID", "id"),
                    CrudPanel.CrudColumn.text("编码", "product_code", 2),
                    CrudPanel.CrudColumn.text("名称", "product_name", 3),
                    CrudPanel.CrudColumn.text("分类", "category", 4),
                    CrudPanel.CrudColumn.text("单位", "unit", 5),
                    CrudPanel.CrudColumn.text("单价", "unit_price", 6)).withParams(t)));

        menus.put("产品管理/BOM清单", () ->
            FrameworkWindow.INSTANCE.openContentTab("BOM清单",
                new CrudPanel("BOM清单",
                    "SELECT b.id, p.product_name, b.component_name, b.component_code, b.quantity, b.unit, b.level " +
                    "FROM tech_bom b LEFT JOIN inv_products p ON p.id=b.product_id AND p.tenant_id=b.tenant_id " +
                    "WHERE b.tenant_id=? ORDER BY b.component_code",
                    "tech_bom", false,
                    CrudPanel.CrudColumn.id("ID", "id"),
                    CrudPanel.CrudColumn.readOnly("所属产品", "product_name", 2),
                    CrudPanel.CrudColumn.text("部件名称", "component_name", 3),
                    CrudPanel.CrudColumn.text("部件编码", "component_code", 4),
                    CrudPanel.CrudColumn.text("数量", "quantity", 5),
                    CrudPanel.CrudColumn.text("单位", "unit", 6),
                    CrudPanel.CrudColumn.text("层级", "level", 7)).withParams(t)));

        return menus;
    }

    @Override
    public Map<String, JPanel> getPanelContributions() {
        return Map.of("🔧 技术概览", new TechPanel());
    }

    @Override public List<Action> getToolbarActions() { return List.of(); }
    @Override public List<ModulePermission> getDefinedPermissions() { return List.of(); }
    @Override public List<Class<?>> getProvidedServices() { return List.of(); }
    @Override public Set<String> getSubscribedEventTypes() { return Set.of(); }
}
