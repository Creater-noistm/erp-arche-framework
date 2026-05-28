package com.erp.modules.purchase;

import com.erp.kernel.MicroKernel;
import com.erp.kernel.KernelConfig;
import com.erp.module.*;
import com.erp.ui.CrudPanel;
import com.erp.ui.FrameworkWindow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.util.*;

public class PurchaseModule implements ErpModule {

    private static final Logger log = LoggerFactory.getLogger(PurchaseModule.class);
    private volatile ModuleState state = ModuleState.CREATED;

    @Override public String getId() { return "erp.purchase"; }
    @Override public String getName() { return "采购管理"; }
    @Override public String getVersion() { return "1.0.0"; }
    @Override public String getVendor() { return "Arche ERP Team"; }
    @Override public String getDescription() { return "采购申请、询价、订单管理"; }
    @Override public List<ModuleDependency> getDependencies() { return List.of(); }

    @Override public void init(MicroKernel kernel, KernelConfig config) {}
    @Override public void start(MicroKernel kernel) { log.info("PurchaseModule started"); }
    @Override public void stop(MicroKernel kernel) {}
    @Override public void destroy(MicroKernel kernel) {}
    @Override public ModuleState getState() { return state; }
    @Override public void setState(ModuleState state) { this.state = state; }

    @Override
    public Map<String, Runnable> getMenuContributions() {
        String t = com.erp.tenant.TenantContext.getCurrentTenantId();
        Map<String, Runnable> menus = new LinkedHashMap<>();

        menus.put("采购管理/供应商管理", () ->
            FrameworkWindow.INSTANCE.openContentTab("供应商管理",
                new CrudPanel("供应商管理",
                    "SELECT id, supplier_name FROM (SELECT DISTINCT supplier as supplier_name, supplier as id FROM inv_stock_in WHERE tenant_id=?) sub",
                    "inv_stock_in", true,
                    CrudPanel.CrudColumn.text("ID", "id", 1),
                    CrudPanel.CrudColumn.text("供应商", "supplier_name", 2)).withParams(t)));

        menus.put("采购管理/采购产品", () ->
            FrameworkWindow.INSTANCE.openContentTab("采购产品",
                new CrudPanel("采购产品",
                    "SELECT p.id, p.product_code, p.product_name, p.category, p.unit, p.unit_price, " +
                    "COALESCE(si.qty,0)-COALESCE(so.qty,0) as stock " +
                    "FROM inv_products p " +
                    "LEFT JOIN (SELECT product_id, SUM(quantity) qty FROM inv_stock_in WHERE tenant_id=? GROUP BY product_id) si ON si.product_id=p.id " +
                    "LEFT JOIN (SELECT product_id, SUM(quantity) qty FROM inv_stock_out WHERE tenant_id=? GROUP BY product_id) so ON so.product_id=p.id " +
                    "WHERE p.tenant_id=? AND (COALESCE(si.qty,0)-COALESCE(so.qty,0)) < p.min_stock + 10",
                    "inv_products", true,
                    CrudPanel.CrudColumn.readOnly("ID", "id", 1),
                    CrudPanel.CrudColumn.readOnly("编码", "product_code", 2),
                    CrudPanel.CrudColumn.readOnly("名称", "product_name", 3),
                    CrudPanel.CrudColumn.readOnly("类别", "category", 4),
                    CrudPanel.CrudColumn.readOnly("单位", "unit", 5),
                    CrudPanel.CrudColumn.readOnly("单价", "unit_price", 6),
                    CrudPanel.CrudColumn.readOnly("库存", "stock", 7)).withParams(t, t, t)));

        return menus;
    }

    @Override
    public Map<String, JPanel> getPanelContributions() {
        return Map.of("采购概览", new PurchasePanel());
    }

    @Override public List<Action> getToolbarActions() { return List.of(); }
    @Override public List<ModulePermission> getDefinedPermissions() { return List.of(); }
    @Override public List<Class<?>> getProvidedServices() { return List.of(); }
    @Override public Set<String> getSubscribedEventTypes() { return Set.of(); }
}
