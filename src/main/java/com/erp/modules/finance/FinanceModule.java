package com.erp.modules.finance;

import com.erp.db.DatabaseManager;
import com.erp.kernel.MicroKernel;
import com.erp.kernel.KernelConfig;
import com.erp.model.FieldType;
import com.erp.module.*;
import com.erp.router.DataRouter;
import com.erp.tenant.TenantContext;
import com.erp.ui.CrudPanel;
import com.erp.ui.FrameworkWindow;
import com.erp.ui.SalesPanel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.util.*;

/**
 * 财务模块 — 展示：服务注册 + 数据模型定义 + 数据路由 + 菜单贡献 + 权限注册
 */
public class FinanceModule implements ErpModule {

    private static final Logger log = LoggerFactory.getLogger(FinanceModule.class);

    private volatile ModuleState state = ModuleState.CREATED;
    private final List<AutoCloseable> subscriptions = new ArrayList<>();

    @Override
    public String getId() { return "erp.finance"; }

    @Override
    public String getName() { return "财务管理"; }

    @Override
    public String getVersion() { return "1.0.0"; }

    @Override
    public String getVendor() { return "Arche ERP Team"; }

    @Override
    public String getDescription() {
        return "总账、应收应付、资产管理、财务报表";
    }

    @Override
    public List<ModuleDependency> getDependencies() {
        return List.of(new ModuleDependency("erp.inventory", "1.0.x", false));
    }

    // ── 生命周期 ──

    @Override
    public void init(MicroKernel kernel, KernelConfig config) {
        log.info("FinanceModule initializing...");
    }

    @Override
    public void start(MicroKernel kernel) {
        log.info("FinanceModule starting...");

        // 1. 注册数据模型
        kernel.getDataModelRegistry().registerEntityDefinition(
            ModuleEntityDefinition.builder("Invoice")
                .displayName("发票")
                .moduleId(getId())
                .description("财务模块 — 发票管理")
                .field("invoiceNo", FieldType.STRING, true)
                .field("amount", FieldType.DECIMAL, true)
                .field("taxAmount", FieldType.DECIMAL, false)
                .field("totalAmount", FieldType.DECIMAL, true)
                .field("currency", FieldType.ENUM, true)
                .field("invoiceDate", FieldType.DATE, true)
                .field("dueDate", FieldType.DATE, true)
                .field("customerName", FieldType.STRING, true)
                .field("status", FieldType.ENUM, true)
                .field("remark", FieldType.TEXT, false)
                .maxCustomFields(80)
                .enableAudit(true)
                .build()
        );

        kernel.getDataModelRegistry().registerEntityDefinition(
            ModuleEntityDefinition.builder("AccountEntry")
                .displayName("会计科目")
                .moduleId(getId())
                .description("会计科目表")
                .field("accountCode", FieldType.STRING, true)
                .field("accountName", FieldType.STRING, true)
                .field("accountType", FieldType.ENUM, true)
                .field("balanceDirection", FieldType.ENUM, true)
                .field("parentCode", FieldType.STRING, false)
                .maxCustomFields(40)
                .build()
        );

        // 2. 注册数据处理器
        kernel.getDataRouter().registerCrudHandlers("Invoice", new DataRouter.CrudHandler() {
            @Override
            public Object create(DataRouter.RoutingRequest req, DataRouter.RoutingContext ctx) {
                log.info("Creating invoice...");
                return DataRouter.RoutingResponse.success(Map.of("invoiceId", "INV-" + System.currentTimeMillis()));
            }

            @Override
            public Object read(DataRouter.RoutingRequest req, DataRouter.RoutingContext ctx) {
                return DataRouter.RoutingResponse.success(Map.of(
                    "invoiceNo", "INV-2024-001",
                    "amount", "10000.00",
                    "status", "pending"
                ));
            }

            @Override
            public Object query(DataRouter.RoutingRequest req, DataRouter.RoutingContext ctx) {
                return DataRouter.RoutingResponse.success(List.of(
                    Map.of("invoiceNo", "INV-2024-001", "amount", "10000.00"),
                    Map.of("invoiceNo", "INV-2024-002", "amount", "25000.00")
                ));
            }
        });

        // 3. 注册服务
        kernel.getServiceRegistry().registerService(
            FinanceService.class, new FinanceService(), "1.0.0", getId());

        // 4. 注册事件监听 (订阅库存事件)
        subscriptions.add(kernel.getEventBus().subscribe("data.entity.created", event -> {
            String entityType = event.getPayloadValue("entityType");
            if ("InventoryMovement".equals(entityType)) {
                log.info("FinanceModule received inventory movement event — generating accounting entry");
            }
        }));

        log.info("FinanceModule started");
    }

    @Override
    public List<AutoCloseable> getSubscriptions() { return subscriptions; }

    @Override
    public void stop(MicroKernel kernel) {
        log.info("FinanceModule stopping...");
        kernel.getDataRouter().unregisterHandlers("Invoice");
        kernel.getServiceRegistry().unregisterAllByModule(getId());
        log.info("FinanceModule stopped");
    }

    @Override
    public void destroy(MicroKernel kernel) {
        log.info("FinanceModule destroyed");
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

        // 可编辑的会计科目
        menus.put("总账/会计科目", () ->
            FrameworkWindow.INSTANCE.openContentTab("会计科目",
                new CrudPanel("会计科目",
                    "SELECT id, subject_code, subject_name, subject_type, balance_direction, IF(is_active=1,'启用','停用') " +
                    "FROM fin_account_subjects WHERE tenant_id=? ORDER BY subject_code",
                    "fin_account_subjects", false,
                    CrudPanel.CrudColumn.id("ID", "id"),
                    CrudPanel.CrudColumn.text("编码", "subject_code", 2),
                    CrudPanel.CrudColumn.text("名称", "subject_name", 3),
                    CrudPanel.CrudColumn.text("类型", "subject_type", 4),
                    CrudPanel.CrudColumn.text("余额方向", "balance_direction", 5),
                    CrudPanel.CrudColumn.readOnly("状态", "status", 6)).withParams(t)));

        // 凭证列表
        menus.put("总账/凭证列表", () ->
            FrameworkWindow.INSTANCE.openContentTab("凭证列表",
                new CrudPanel("凭证列表",
                    "SELECT v.id, v.voucher_no, v.voucher_date, v.description, v.status " +
                    "FROM fin_vouchers v WHERE v.tenant_id=? ORDER BY v.voucher_date DESC",
                    "fin_vouchers", false,
                    CrudPanel.CrudColumn.id("ID", "id"),
                    CrudPanel.CrudColumn.text("凭证号", "voucher_no", 2),
                    CrudPanel.CrudColumn.text("日期", "voucher_date", 3),
                    CrudPanel.CrudColumn.text("摘要", "description", 4),
                    CrudPanel.CrudColumn.text("状态", "status", 5)).withParams(t)));

        // 科目余额（只读 — 从凭证明细计算）
        menus.put("总账/科目余额", () ->
            FrameworkWindow.INSTANCE.openContentTab("科目余额",
                new CrudPanel("科目余额表",
                    "SELECT s.subject_code, s.subject_name, s.subject_type, " +
                    "ROUND(COALESCE(SUM(CASE WHEN s.balance_direction='DEBIT' THEN i.debit_amount - i.credit_amount ELSE i.credit_amount - i.debit_amount END),0),2) as balance " +
                    "FROM fin_account_subjects s " +
                    "LEFT JOIN fin_voucher_items i ON i.subject_id = s.id AND i.tenant_id=s.tenant_id " +
                    "WHERE s.tenant_id=? " +
                    "GROUP BY s.id HAVING balance != 0 ORDER BY s.subject_code",
                    "fin_account_subjects", true,
                    CrudPanel.CrudColumn.readOnly("编码", "subject_code", 1),
                    CrudPanel.CrudColumn.readOnly("名称", "subject_name", 2),
                    CrudPanel.CrudColumn.readOnly("类型", "subject_type", 3),
                    CrudPanel.CrudColumn.readOnly("余额", "balance", 4)).withParams(t)));

        // 客户发票（可编辑）
        menus.put("应收/客户发票", () ->
            FrameworkWindow.INSTANCE.openContentTab("客户发票",
                new CrudPanel("客户发票",
                    "SELECT id, invoice_no, customer_name, amount, tax_amount, total_amount, invoice_date, status " +
                    "FROM fin_invoices WHERE tenant_id=? AND invoice_type='SALES' ORDER BY invoice_date DESC",
                    "fin_invoices", false,
                    CrudPanel.CrudColumn.id("ID", "id"),
                    CrudPanel.CrudColumn.text("发票号", "invoice_no", 2),
                    CrudPanel.CrudColumn.text("客户", "customer_name", 3),
                    CrudPanel.CrudColumn.text("金额", "amount", 4),
                    CrudPanel.CrudColumn.text("税额", "tax_amount", 5),
                    CrudPanel.CrudColumn.text("合计", "total_amount", 6),
                    CrudPanel.CrudColumn.text("日期", "invoice_date", 7),
                    CrudPanel.CrudColumn.text("状态", "status", 8)).withParams(t)));

        // 销售管理（联动事务）
        menus.put("销售/销售开单", () ->
            FrameworkWindow.INSTANCE.openContentTab("销售开单", new SalesPanel()));

        return menus;
    }

    @Override
    public Map<String, JPanel> getPanelContributions() {
        return Map.of("财务概览", new FinancePanel());
    }

    @Override
    public List<Action> getToolbarActions() {
        return List.of(
            new AbstractAction("🧾 新发票") {
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    String t = TenantContext.getCurrentTenantId();
                    FrameworkWindow.INSTANCE.openContentTab("客户发票",
                        new CrudPanel("客户发票",
                            "SELECT id, invoice_no, customer_name, amount, tax_amount, total_amount, invoice_date, status " +
                            "FROM fin_invoices WHERE tenant_id=? AND invoice_type='SALES' ORDER BY invoice_date DESC",
                            "fin_invoices", false,
                            CrudPanel.CrudColumn.id("ID", "id"),
                            CrudPanel.CrudColumn.text("发票号", "invoice_no", 2),
                            CrudPanel.CrudColumn.text("客户", "customer_name", 3),
                            CrudPanel.CrudColumn.text("金额", "amount", 4),
                            CrudPanel.CrudColumn.text("税额", "tax_amount", 5),
                            CrudPanel.CrudColumn.text("合计", "total_amount", 6),
                            CrudPanel.CrudColumn.text("日期", "invoice_date", 7),
                            CrudPanel.CrudColumn.text("状态", "status", 8)).withParams(t));
                }
            },
            new AbstractAction("✔ 过账") {
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    String t = TenantContext.getCurrentTenantId();
                    int count = DatabaseManager.getInstance().executeUpdate(
                        "UPDATE fin_vouchers SET status='POSTED' WHERE tenant_id=? AND status='DRAFT' AND voucher_date <= CURDATE()", t);
                    JOptionPane.showMessageDialog(null,
                        (count > 0 ? "✅ 已过账 " + count + " 张凭证" : "没有待过账的凭证"),
                        "批量过账", JOptionPane.INFORMATION_MESSAGE);
                }
            }
        );
    }

    // ── 权限 ──

    @Override
    public List<ModulePermission> getDefinedPermissions() {
        return List.of(
            new ModulePermission("finance:invoice:create", "创建发票", "创建客户/供应商发票"),
            new ModulePermission("finance:invoice:read", "查看发票", "查看发票详情"),
            new ModulePermission("finance:invoice:update", "修改发票", "修改发票信息"),
            new ModulePermission("finance:invoice:delete", "删除发票", "删除发票"),
            new ModulePermission("finance:invoice:approve", "审批发票", "审批发票"),
            new ModulePermission("finance:posting:execute", "执行过账", "执行总账过账操作"),
            new ModulePermission("finance:account:manage", "管理科目", "管理会计科目表"),
            new ModulePermission("finance:report:view", "查看报表", "查看财务报表")
        ).stream().map(p -> p.withModuleId(getId())).toList();
    }

    // ── 服务 ──

    @Override
    public List<Class<?>> getProvidedServices() {
        return List.of(FinanceService.class);
    }

    @Override
    public Set<String> getSubscribedEventTypes() {
        return Set.of("data.entity.*");
    }
}
