package com.erp.modules.hr;

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
 * 人力资源模块 — 展示：数据模型 + 组织架构集成 + 事件响应 + 权限
 */
public class HRModule implements ErpModule {

    private static final Logger log = LoggerFactory.getLogger(HRModule.class);

    private volatile ModuleState state = ModuleState.CREATED;
    private final List<AutoCloseable> subscriptions = new ArrayList<>();

    @Override
    public String getId() { return "erp.hr"; }

    @Override
    public String getName() { return "人力资源"; }

    @Override
    public String getVersion() { return "1.0.0"; }

    @Override
    public String getVendor() { return "Arche ERP Team"; }

    @Override
    public String getDescription() {
        return "员工管理、考勤、薪资、招聘、培训";
    }

    // ── 生命周期 ──

    @Override
    public void init(MicroKernel kernel, KernelConfig config) {
        log.info("HRModule initializing...");
    }

    @Override
    public void start(MicroKernel kernel) {
        log.info("HRModule starting...");

        // 1. 注册数据模型
        kernel.getDataModelRegistry().registerEntityDefinition(
            ModuleEntityDefinition.builder("Employee")
                .displayName("员工")
                .moduleId(getId())
                .description("员工主数据")
                .field("employeeNo", FieldType.STRING, true)
                .field("fullName", FieldType.STRING, true)
                .field("gender", FieldType.ENUM, true)
                .field("birthDate", FieldType.DATE, false)
                .field("idCard", FieldType.STRING, false)
                .field("phone", FieldType.STRING, false)
                .field("email", FieldType.STRING, false)
                .field("department", FieldType.STRING, true)
                .field("position", FieldType.STRING, true)
                .field("hireDate", FieldType.DATE, true)
                .field("employeeType", FieldType.ENUM, true)
                .field("salary", FieldType.DECIMAL, false)
                .field("status", FieldType.ENUM, true)
                .maxCustomFields(80)
                .enableAudit(true)
                .build()
        );

        kernel.getDataModelRegistry().registerEntityDefinition(
            ModuleEntityDefinition.builder("Attendance")
                .displayName("考勤记录")
                .moduleId(getId())
                .description("员工考勤数据")
                .field("employeeNo", FieldType.STRING, true)
                .field("date", FieldType.DATE, true)
                .field("clockIn", FieldType.DATETIME, false)
                .field("clockOut", FieldType.DATETIME, false)
                .field("status", FieldType.ENUM, true)
                .field("workHours", FieldType.DECIMAL, false)
                .maxCustomFields(30)
                .build()
        );

        // 2. 组织架构初始示例
        var orgMgr = kernel.getOrgStructureManager();
        var tenant = com.erp.tenant.TenantContext.getCurrentTenantId();

        var company = new com.erp.org.OrgUnit("org-1", tenant, "HQ", "始祖集团", com.erp.org.OrgUnit.OrgUnitType.GROUP);
        var finance = new com.erp.org.OrgUnit("org-2", tenant, "FIN", "财务部", com.erp.org.OrgUnit.OrgUnitType.DEPARTMENT);
        var it = new com.erp.org.OrgUnit("org-3", tenant, "IT", "信息技术部", com.erp.org.OrgUnit.OrgUnitType.DEPARTMENT);
        var hr = new com.erp.org.OrgUnit("org-4", tenant, "HR", "人力资源部", com.erp.org.OrgUnit.OrgUnitType.DEPARTMENT);
        var sales = new com.erp.org.OrgUnit("org-5", tenant, "SALES", "销售部", com.erp.org.OrgUnit.OrgUnitType.DEPARTMENT);

        company.addChild(finance);
        company.addChild(it);
        company.addChild(hr);
        company.addChild(sales);

        orgMgr.addOrgUnit(company);
        orgMgr.addOrgUnit(finance);
        orgMgr.addOrgUnit(it);
        orgMgr.addOrgUnit(hr);
        orgMgr.addOrgUnit(sales);

        // 3. 注册服务
        kernel.getServiceRegistry().registerService(
            HRService.class, new HRService(), "1.0.0", getId());

        // 4. 订阅事件
        subscriptions.add(kernel.getEventBus().subscribe("module.started", event -> {
            String moduleName = event.getPayloadValue("moduleName");
            log.info("HRModule noticed module started: {}", moduleName);
        }));

        log.info("HRModule started — org structure initialized with 5 units");
    }

    @Override
    public List<AutoCloseable> getSubscriptions() { return subscriptions; }

    @Override
    public void stop(MicroKernel kernel) {
        log.info("HRModule stopping...");
        kernel.getServiceRegistry().unregisterAllByModule(getId());
        log.info("HRModule stopped");
    }

    @Override
    public void destroy(MicroKernel kernel) {
        log.info("HRModule destroyed");
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

        // 员工花名册（离职处理会自动禁用ERP账号）
        menus.put("员工管理/员工花名册", () -> {
            String t2 = com.erp.tenant.TenantContext.getCurrentTenantId();
            CrudPanel empPanel = new CrudPanel("员工花名册",
                "SELECT id, emp_code, emp_name, gender, department, position, hire_date, status " +
                "FROM hr_employees WHERE tenant_id=? ORDER BY emp_code",
                "hr_employees", true,
                CrudPanel.CrudColumn.id("ID", "id"),
                CrudPanel.CrudColumn.text("工号", "emp_code", 2),
                CrudPanel.CrudColumn.text("姓名", "emp_name", 3),
                CrudPanel.CrudColumn.choices("性别", "gender", 4, "男", "女"),
                CrudPanel.CrudColumn.text("部门", "department", 5),
                CrudPanel.CrudColumn.text("职位", "position", 6),
                CrudPanel.CrudColumn.text("入职日期", "hire_date", 7),
                CrudPanel.CrudColumn.choices("状态", "status", 8, "ACTIVE", "ON_LEAVE", "RESIGNED"))
                .withParams(t2);
            empPanel.addToolbarButton("✏️ 编辑", () -> editEmployee(empPanel));
            empPanel.addToolbarButton("🚫 离职处理", () -> resignEmployee(empPanel, t2));
            FrameworkWindow.INSTANCE.openContentTab("员工花名册", empPanel);
        });

        // 考勤记录（可编辑，状态下拉）
        menus.put("员工管理/考勤日报", () ->
            FrameworkWindow.INSTANCE.openContentTab("考勤日报",
                new CrudPanel("考勤日报",
                    "SELECT a.id, a.emp_id, e.emp_name, a.att_date, a.check_in, a.check_out, a.status " +
                    "FROM hr_attendance a JOIN hr_employees e ON e.id=a.emp_id " +
                    "WHERE a.tenant_id=? ORDER BY a.att_date DESC LIMIT 50",
                    "hr_attendance", false,
                    CrudPanel.CrudColumn.id("ID", "id"),
                    CrudPanel.CrudColumn.text("员工ID", "emp_id", 2),
                    CrudPanel.CrudColumn.readOnly("姓名", "emp_name", 3),
                    CrudPanel.CrudColumn.text("日期", "att_date", 4),
                    CrudPanel.CrudColumn.text("签到", "check_in", 5),
                    CrudPanel.CrudColumn.text("签退", "check_out", 6),
                    CrudPanel.CrudColumn.choices("状态", "status", 7, "已签", "迟到", "缺勤", "加班", "请假")).withParams(t)));

        // ── 签到管理（LEFT JOIN 防空 emp_id 不可见）
        menus.put("考勤/员工签到", () ->
            FrameworkWindow.INSTANCE.openContentTab("签到记录",
                new CrudPanel("签到记录",
                    "SELECT ci.id, ci.emp_id, e.emp_name, ci.check_time, ci.remark " +
                    "FROM hr_check_in ci LEFT JOIN hr_employees e ON e.id=ci.emp_id " +
                    "WHERE ci.tenant_id=? ORDER BY ci.check_time DESC LIMIT 50",
                    "hr_check_in", false,
                    CrudPanel.CrudColumn.id("ID", "id"),
                    CrudPanel.CrudColumn.text("员工ID", "emp_id", 2),
                    CrudPanel.CrudColumn.readOnly("姓名", "emp_name", 3),
                    CrudPanel.CrudColumn.text("签到时间", "check_time", 4),
                    CrudPanel.CrudColumn.text("备注", "remark", 5)).withParams(t)));

        // ── 签退管理（LEFT JOIN 防空 emp_id 不可见）
        menus.put("考勤/员工签退", () ->
            FrameworkWindow.INSTANCE.openContentTab("签退记录",
                new CrudPanel("签退记录",
                    "SELECT co.id, co.emp_id, e.emp_name, co.check_time, co.remark " +
                    "FROM hr_check_out co LEFT JOIN hr_employees e ON e.id=co.emp_id " +
                    "WHERE co.tenant_id=? ORDER BY co.check_time DESC LIMIT 50",
                    "hr_check_out", false,
                    CrudPanel.CrudColumn.id("ID", "id"),
                    CrudPanel.CrudColumn.text("员工ID", "emp_id", 2),
                    CrudPanel.CrudColumn.readOnly("姓名", "emp_name", 3),
                    CrudPanel.CrudColumn.text("签退时间", "check_time", 4),
                    CrudPanel.CrudColumn.text("备注", "remark", 5)).withParams(t)));

        // ── 考勤汇总
        menus.put("考勤/考勤汇总", () ->
            FrameworkWindow.INSTANCE.openContentTab("考勤汇总",
                new CrudPanel("考勤汇总",
                    "SELECT s.id, e.emp_name, s.att_date, s.check_in, s.check_out, s.status " +
                    "FROM hr_attendance_summary s JOIN hr_employees e ON e.id=s.emp_id " +
                    "WHERE s.tenant_id=? ORDER BY s.att_date DESC LIMIT 100",
                    "hr_attendance_summary", true,
                    CrudPanel.CrudColumn.id("ID", "id"),
                    CrudPanel.CrudColumn.readOnly("姓名", "emp_name", 2),
                    CrudPanel.CrudColumn.readOnly("日期", "att_date", 3),
                    CrudPanel.CrudColumn.readOnly("签到", "check_in", 4),
                    CrudPanel.CrudColumn.readOnly("签退", "check_out", 5),
                    CrudPanel.CrudColumn.readOnly("状态", "status", 6)).withParams(t)));

        // 部门人数（只读 — 聚合计算）
        menus.put("统计/部门人数", () ->
            FrameworkWindow.INSTANCE.openContentTab("部门人数统计",
                new CrudPanel("部门人数统计",
                    "SELECT department, COUNT(*) as emp_count " +
                    "FROM hr_employees WHERE tenant_id=? AND status='ACTIVE' " +
                    "GROUP BY department ORDER BY emp_count DESC",
                    "hr_employees", true,
                    CrudPanel.CrudColumn.readOnly("部门", "department", 1),
                    CrudPanel.CrudColumn.readOnly("人数", "emp_count", 2)).withParams(t)));

        // 考勤统计（只读 — 聚合计算）
        menus.put("统计/考勤统计", () ->
            FrameworkWindow.INSTANCE.openContentTab("考勤统计",
                new CrudPanel("考勤统计",
                    "SELECT a.status, COUNT(*) as cnt " +
                    "FROM hr_attendance a JOIN hr_employees e ON e.id=a.emp_id " +
                    "WHERE a.tenant_id=? " +
                    "GROUP BY a.status ORDER BY cnt DESC",
                    "hr_attendance", true,
                    CrudPanel.CrudColumn.readOnly("状态", "status", 1),
                    CrudPanel.CrudColumn.readOnly("次数", "cnt", 2)).withParams(t)));

        return menus;
    }

    @Override
    public Map<String, JPanel> getPanelContributions() {
        return Map.of("人力资源概览", new HRPanel());
    }

    @Override
    public List<Action> getToolbarActions() {
        return List.of(
            new AbstractAction("👤 新员工") {
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    String t = TenantContext.getCurrentTenantId();
                    FrameworkWindow.INSTANCE.openContentTab("员工管理",
                        new CrudPanel("员工管理",
                            "SELECT id, emp_code, emp_name, gender, department, position, hire_date, status " +
                            "FROM hr_employees WHERE tenant_id=? ORDER BY hire_date DESC",
                            "hr_employees", false,
                            CrudPanel.CrudColumn.id("ID", "id"),
                            CrudPanel.CrudColumn.text("工号", "emp_code", 2),
                            CrudPanel.CrudColumn.text("姓名", "emp_name", 3),
                            CrudPanel.CrudColumn.text("性别", "gender", 4),
                            CrudPanel.CrudColumn.text("部门", "department", 5),
                            CrudPanel.CrudColumn.text("职位", "position", 6),
                            CrudPanel.CrudColumn.text("入职日期", "hire_date", 7),
                            CrudPanel.CrudColumn.text("状态", "status", 8)).withParams(t));
                }
            },
            new AbstractAction("📅 考勤") {
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    String t = TenantContext.getCurrentTenantId();
                    FrameworkWindow.INSTANCE.openContentTab("考勤记录",
                        new CrudPanel("考勤记录",
                            "SELECT a.id, e.emp_name, a.att_date, a.check_in, a.check_out, a.status " +
                            "FROM hr_attendance a JOIN hr_employees e ON e.id=a.emp_id " +
                            "WHERE a.tenant_id=? ORDER BY a.att_date DESC LIMIT 50",
                            "hr_attendance", false,
                            CrudPanel.CrudColumn.id("ID", "id"),
                            CrudPanel.CrudColumn.readOnly("姓名", "emp_name", 2),
                            CrudPanel.CrudColumn.text("日期", "att_date", 3),
                            CrudPanel.CrudColumn.text("签到", "check_in", 4),
                            CrudPanel.CrudColumn.text("签退", "check_out", 5),
                            CrudPanel.CrudColumn.text("状态", "status", 6)).withParams(t));
                }
            }
        );
    }

    // ── 权限 ──

    @Override
    public List<ModulePermission> getDefinedPermissions() {
        return List.of(
            new ModulePermission("hr:employee:read", "查看员工", "查看员工信息"),
            new ModulePermission("hr:employee:write", "管理员工", "创建/修改员工信息"),
            new ModulePermission("hr:attendance:read", "查看考勤", "查看考勤记录"),
            new ModulePermission("hr:attendance:write", "管理考勤", "处理考勤数据"),
            new ModulePermission("hr:payroll:execute", "执行薪资", "执行薪资核算"),
            new ModulePermission("hr:recruitment:manage", "管理招聘", "管理招聘流程")
        ).stream().map(p -> p.withModuleId(getId())).toList();
    }

    @Override
    public List<Class<?>> getProvidedServices() {
        return List.of(HRService.class);
    }

    // ═══════════════════════════════════════════
    //  员工操作（编辑 / 离职处理）
    // ═══════════════════════════════════════════

    private void editEmployee(CrudPanel panel) {
        String id = panel.getSelectedId();
        String name = panel.getSelectedValue(2);
        if (id == null) { JOptionPane.showMessageDialog(null, "请先选择一名员工"); return; }
        // 直接在原面板上弹出编辑对话框
        panel.openEditDialog(id, name);
    }

    private void resignEmployee(CrudPanel panel, String tenantId) {
        String empId = panel.getSelectedId();
        String name = panel.getSelectedValue(2);
        String currentStatus = panel.getSelectedValue(7);
        if (empId == null) { JOptionPane.showMessageDialog(null, "请先选择一名员工"); return; }
        if (!"ACTIVE".equals(currentStatus)) {
            JOptionPane.showMessageDialog(null, "该员工已不是在职状态（当前: " + currentStatus + "）");
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(null,
            "确认将「" + name + "」设置为离职？\n" +
            "操作后：\n" +
            "  · 员工状态 → RESIGNED\n" +
            "  · ERP 账号将同步禁用\n\n" +
            "确认离职？",
            "离职处理", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;

        try {
            // 1. 员工状态置为离职
            String db = null;
            com.erp.db.DatabaseManager.getInstance().executeUpdate(
                "UPDATE hr_employees SET status='RESIGNED' WHERE id=? AND tenant_id=?",
                empId, tenantId);

            // 2. 查关联的 user_id
            String userId = com.erp.db.DatabaseManager.getInstance().executeQuery(
                "SELECT user_id FROM hr_employees WHERE id=? AND tenant_id=?",
                rs -> rs.next() ? rs.getString("user_id") : null, empId, tenantId);

            // 3. 如有关联账号 → 禁用
            if (userId != null && !userId.isEmpty()) {
                com.erp.db.DatabaseManager.getInstance().executeUpdate(
                    "UPDATE erp_users SET status='DISABLED' WHERE id=? AND tenant_id=?", userId, tenantId);
                JOptionPane.showMessageDialog(null,
                    "✅ 「" + name + "」已离职，关联账号已禁用。");
            } else {
                JOptionPane.showMessageDialog(null,
                    "✅ 「" + name + "」已标记为离职。");
            }
            panel.refresh();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "操作失败: " + e.getMessage());
        }
    }
}
