package com.erp.modules.inventory;

import com.erp.kernel.MicroKernel;
import com.erp.kernel.KernelConfig;
import com.erp.model.FieldType;
import com.erp.module.*;
import com.erp.tenant.TenantContext;
import com.erp.ui.CrudPanel;
import com.erp.ui.FrameworkWindow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.util.*;

/**
 * 库存/供应链模块 — 展示：自定义字段扩展 + 事件发布 + 模块间协作
 */
public class InventoryModule implements ErpModule {

    private static final Logger log = LoggerFactory.getLogger(InventoryModule.class);

    private volatile ModuleState state = ModuleState.CREATED;

    @Override
    public String getId() { return "erp.inventory"; }

    @Override
    public String getName() { return "库存管理"; }

    @Override
    public String getVersion() { return "1.0.0"; }

    @Override
    public String getVendor() { return "Arche ERP Team"; }

    @Override
    public String getDescription() {
        return "库存管理、出入库、盘点、供应链协同";
    }

    @Override
    public List<ModuleDependency> getDependencies() {
        return List.of();
    }

    // ── 生命周期 ──

    @Override
    public void init(MicroKernel kernel, KernelConfig config) {
        log.info("InventoryModule initializing...");
    }

    @Override
    public void start(MicroKernel kernel) {
        log.info("InventoryModule starting...");

        // 1. 注册数据模型
        kernel.getDataModelRegistry().registerEntityDefinition(
            ModuleEntityDefinition.builder("Product")
                .displayName("产品")
                .moduleId(getId())
                .description("产品/物料主数据")
                .field("productCode", FieldType.STRING, true)
                .field("productName", FieldType.STRING, true)
                .field("category", FieldType.ENUM, true)
                .field("unit", FieldType.STRING, true)
                .field("price", FieldType.DECIMAL, true)
                .field("cost", FieldType.DECIMAL, false)
                .field("stockQuantity", FieldType.DECIMAL, true)
                .field("minStock", FieldType.DECIMAL, false)
                .field("maxStock", FieldType.DECIMAL, false)
                .maxCustomFields(100)
                .build()
        );

        kernel.getDataModelRegistry().registerEntityDefinition(
            ModuleEntityDefinition.builder("InventoryMovement")
                .displayName("库存移动")
                .moduleId(getId())
                .description("出入库记录")
                .field("movementType", FieldType.ENUM, true)
                .field("productCode", FieldType.STRING, true)
                .field("quantity", FieldType.DECIMAL, true)
                .field("unitPrice", FieldType.DECIMAL, false)
                .field("totalAmount", FieldType.DECIMAL, false)
                .field("warehouse", FieldType.STRING, true)
                .field("referenceNo", FieldType.STRING, false)
                .field("movementDate", FieldType.DATE, true)
                .maxCustomFields(60)
                .build()
        );

        // 2. 注册数据处理器
        kernel.getDataRouter().registerCrudHandlers("Product", new InventoryCrudHandler());
        kernel.getDataRouter().registerCrudHandlers("InventoryMovement", new MovementCrudHandler(kernel));

        // 3. 注册服务
        kernel.getServiceRegistry().registerService(
            InventoryService.class, new InventoryService(), "1.0.0", getId());

        // 4. 演示功能：运行时添加自定义字段
        kernel.getDataModelRegistry().addCustomField(
            com.erp.model.CustomField.builder("Product", "batchNo")
                .type(FieldType.STRING)
                .displayName("批号")
                .description("生产批号/批次")
                .maxLength(50)
                .build()
        );
        kernel.getDataModelRegistry().addCustomField(
            com.erp.model.CustomField.builder("Product", "shelfLife")
                .type(FieldType.INTEGER)
                .displayName("保质期(天)")
                .description("产品保质期天数")
                .defaultValue("365")
                .build()
        );

        log.info("InventoryModule started — added 2 custom fields to Product");
    }

    @Override
    public void stop(MicroKernel kernel) {
        log.info("InventoryModule stopping...");
        kernel.getDataRouter().unregisterHandlers("Product");
        kernel.getDataRouter().unregisterHandlers("InventoryMovement");
        kernel.getServiceRegistry().unregisterAllByModule(getId());
        log.info("InventoryModule stopped");
    }

    @Override
    public void destroy(MicroKernel kernel) {
        log.info("InventoryModule destroyed");
    }

    // ── 状态 ──

    @Override
    public ModuleState getState() { return state; }
    @Override
    public void setState(ModuleState state) { this.state = state; }

    // ── UI 贡献 ──

    @Override
    public Map<String, Runnable> getMenuContributions() {
        String t = com.erp.tenant.TenantContext.getCurrentTenantId();
        Map<String, Runnable> menus = new LinkedHashMap<>();

        // 产品列表（可编辑）
        menus.put("产品管理/产品列表", () ->
            FrameworkWindow.INSTANCE.openContentTab("产品列表",
                new CrudPanel("产品列表",
                    "SELECT p.id, p.product_code, p.product_name, p.category, p.unit, p.unit_price, IF(p.is_active=1,'启用','停用') " +
                    "FROM inv_products p WHERE p.tenant_id=? ORDER BY p.product_code",
                    "inv_products", false,
                    CrudPanel.CrudColumn.id("ID", "id"),
                    CrudPanel.CrudColumn.text("编码", "product_code", 2),
                    CrudPanel.CrudColumn.text("名称", "product_name", 3),
                    CrudPanel.CrudColumn.text("分类", "category", 4),
                    CrudPanel.CrudColumn.text("单位", "unit", 5),
                    CrudPanel.CrudColumn.text("单价", "unit_price", 6),
                    CrudPanel.CrudColumn.readOnly("状态", "status", 7)).withParams(t)));

        // 入库记录（可编辑）—— JOIN 产品名但 product_id 不映射到表单
        menus.put("入库管理/采购入库", () ->
            FrameworkWindow.INSTANCE.openContentTab("采购入库",
                new CrudPanel("入库记录",
                    "SELECT si.id, si.product_id, p.product_name, si.quantity, si.unit_price, si.supplier, si.in_date " +
                    "FROM inv_stock_in si JOIN inv_products p ON p.id=si.product_id " +
                    "WHERE si.tenant_id=? ORDER BY si.in_date DESC",
                    "inv_stock_in", false,
                    CrudPanel.CrudColumn.id("ID", "id"),
                    CrudPanel.CrudColumn.text("产品ID", "product_id", 2),
                    CrudPanel.CrudColumn.readOnly("产品", "product_name", 3),
                    CrudPanel.CrudColumn.text("数量", "quantity", 4),
                    CrudPanel.CrudColumn.text("单价", "unit_price", 5),
                    CrudPanel.CrudColumn.text("供应商", "supplier", 6),
                    CrudPanel.CrudColumn.text("日期", "in_date", 7)).withParams(t)));

        // 出库记录（可编辑）—— JOIN 产品名但 product_id 不映射到表单
        menus.put("出库管理/销售出库", () ->
            FrameworkWindow.INSTANCE.openContentTab("销售出库",
                new CrudPanel("出库记录",
                    "SELECT so.id, so.product_id, p.product_name, so.quantity, so.unit_price, so.customer, so.out_date " +
                    "FROM inv_stock_out so JOIN inv_products p ON p.id=so.product_id " +
                    "WHERE so.tenant_id=? ORDER BY so.out_date DESC",
                    "inv_stock_out", false,
                    CrudPanel.CrudColumn.id("ID", "id"),
                    CrudPanel.CrudColumn.text("产品ID", "product_id", 2),
                    CrudPanel.CrudColumn.readOnly("产品", "product_name", 3),
                    CrudPanel.CrudColumn.text("数量", "quantity", 4),
                    CrudPanel.CrudColumn.text("单价", "unit_price", 5),
                    CrudPanel.CrudColumn.text("客户", "customer", 6),
                    CrudPanel.CrudColumn.text("日期", "out_date", 7)).withParams(t)));

        // 库存价值（只读 — 实时计算）
        menus.put("报表/库存价值报表", () ->
            FrameworkWindow.INSTANCE.openContentTab("库存价值",
                new CrudPanel("库存价值报表",
                    "SELECT p.category, p.product_name, " +
                    "COALESCE(si.total_in,0) - COALESCE(so.total_out,0) as stock_qty, " +
                    "ROUND((COALESCE(si.total_in,0) - COALESCE(so.total_out,0)) * p.unit_price, 2) as stock_value " +
                    "FROM inv_products p " +
                    "LEFT JOIN (SELECT product_id, SUM(quantity) total_in FROM inv_stock_in WHERE tenant_id=? GROUP BY product_id) si ON si.product_id=p.id " +
                    "LEFT JOIN (SELECT product_id, SUM(quantity) total_out FROM inv_stock_out WHERE tenant_id=? GROUP BY product_id) so ON so.product_id=p.id " +
                    "WHERE p.tenant_id=? " +
                    "HAVING stock_qty > 0 ORDER BY stock_value DESC",
                    "inv_products", true,
                    CrudPanel.CrudColumn.readOnly("分类", "category", 1),
                    CrudPanel.CrudColumn.readOnly("产品", "product_name", 2),
                    CrudPanel.CrudColumn.readOnly("库存量", "stock_qty", 3),
                    CrudPanel.CrudColumn.readOnly("库存价值", "stock_value", 4)).withParams(t, t, t)));

        return menus;
    }

    @Override
    public Map<String, JPanel> getPanelContributions() {
        return Map.of("库存概览", new InventoryPanel());
    }

    @Override
    public List<Action> getToolbarActions() {
        return List.of(
            new AbstractAction("📦 入库") {
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    String t2 = TenantContext.getCurrentTenantId();
                    FrameworkWindow.INSTANCE.openContentTab("采购入库",
                        new CrudPanel("入库记录",
                            "SELECT si.id, si.product_id, p.product_name, si.quantity, si.unit_price, si.supplier, si.in_date " +
                            "FROM inv_stock_in si JOIN inv_products p ON p.id=si.product_id " +
                            "WHERE si.tenant_id=? ORDER BY si.in_date DESC",
                            "inv_stock_in", false,
                            CrudPanel.CrudColumn.id("ID", "id"),
                            CrudPanel.CrudColumn.text("产品ID", "product_id", 2),
                            CrudPanel.CrudColumn.readOnly("产品", "product_name", 3),
                            CrudPanel.CrudColumn.text("数量", "quantity", 4),
                            CrudPanel.CrudColumn.text("单价", "unit_price", 5),
                            CrudPanel.CrudColumn.text("供应商", "supplier", 6),
                            CrudPanel.CrudColumn.text("日期", "in_date", 7)).withParams(t2));
                }
            },
            new AbstractAction("📤 出库") {
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    String t2 = TenantContext.getCurrentTenantId();
                    FrameworkWindow.INSTANCE.openContentTab("销售出库",
                        new CrudPanel("出库记录",
                            "SELECT so.id, so.product_id, p.product_name, so.quantity, so.unit_price, so.customer, so.out_date " +
                            "FROM inv_stock_out so JOIN inv_products p ON p.id=so.product_id " +
                            "WHERE so.tenant_id=? ORDER BY so.out_date DESC",
                            "inv_stock_out", false,
                            CrudPanel.CrudColumn.id("ID", "id"),
                            CrudPanel.CrudColumn.text("产品ID", "product_id", 2),
                            CrudPanel.CrudColumn.readOnly("产品", "product_name", 3),
                            CrudPanel.CrudColumn.text("数量", "quantity", 4),
                            CrudPanel.CrudColumn.text("单价", "unit_price", 5),
                            CrudPanel.CrudColumn.text("客户", "customer", 6),
                            CrudPanel.CrudColumn.text("日期", "out_date", 7)).withParams(t2));
                }
            },
            new AbstractAction("🔍 盘点") {
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    String t2 = TenantContext.getCurrentTenantId();
                    FrameworkWindow.INSTANCE.openContentTab("库存盘点",
                        new CrudPanel("库存盘点",
                            "SELECT p.id, p.product_name, p.category, p.unit, p.unit_price, " +
                            "COALESCE(si.qty,0)-COALESCE(so.qty,0) AS stock_qty, " +
                            "ROUND((COALESCE(si.qty,0)-COALESCE(so.qty,0))*p.unit_price,2) AS stock_value " +
                            "FROM inv_products p " +
                            "LEFT JOIN (SELECT product_id,SUM(quantity) qty FROM inv_stock_in WHERE tenant_id=? GROUP BY product_id) si ON si.product_id=p.id " +
                            "LEFT JOIN (SELECT product_id,SUM(quantity) qty FROM inv_stock_out WHERE tenant_id=? GROUP BY product_id) so ON so.product_id=p.id " +
                            "WHERE p.tenant_id=? HAVING stock_qty > 0 ORDER BY stock_value DESC",
                            "inv_products", true,
                            CrudPanel.CrudColumn.readOnly("分类", "category", 1),
                            CrudPanel.CrudColumn.readOnly("产品", "product_name", 2),
                            CrudPanel.CrudColumn.readOnly("库存量", "stock_qty", 3),
                            CrudPanel.CrudColumn.readOnly("库存价值", "stock_value", 4)).withParams(t2, t2, t2));
                }
            }
        );
    }

    // ── 权限 ──

    @Override
    public List<ModulePermission> getDefinedPermissions() {
        return List.of(
            new ModulePermission("inventory:product:read", "查看产品", "查看产品信息"),
            new ModulePermission("inventory:product:write", "管理产品", "创建/修改产品"),
            new ModulePermission("inventory:movement:create", "库存移动", "执行出入库操作"),
            new ModulePermission("inventory:count:execute", "执行盘点", "执行库存盘点")
        ).stream().map(p -> p.withModuleId(getId())).toList();
    }

    @Override
    public List<Class<?>> getProvidedServices() {
        return List.of(InventoryService.class);
    }
}
