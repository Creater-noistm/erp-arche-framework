package com.erp;

import com.erp.kernel.KernelConfig;
import com.erp.kernel.MicroKernel;
import com.erp.modules.finance.FinanceModule;
import com.erp.modules.inventory.InventoryModule;
import com.erp.modules.hr.HRModule;
import com.erp.modules.purchase.PurchaseModule;
import com.erp.modules.tech.TechModule;
import com.erp.config.UpdateChecker;
import com.erp.db.DatabaseManager;
import com.erp.tenant.LoginSession;
import com.erp.tenant.TenantContext;
import com.erp.ui.LoginDialog;
import com.erp.ui.FrameworkWindow;

import javax.swing.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

/**
 * ═══════════════════════════════════════════════════════════════
 * ErpLauncher — 始祖ERP框架的启动入口
 *
 * 启动流程：
 *   1. 解析命令行参数 / 配置文件
 *   2. 初始化内核配置
 *   3. 启动微内核 (MicroKernel.boot)
 *   4. 初始化多租户上下文
 *   5. 创建默认租户和管理员用户
 *   6. 部署内置示例模块（生产环境从模块路径扫描）
 *   7. 启动 Swing GUI
 *   8. 注册关闭钩子
 * ═══════════════════════════════════════════════════════════════
 */
public class ErpLauncher {

    private static final Logger log = LoggerFactory.getLogger(ErpLauncher.class);

    public static void main(String[] args) {
        log.info("☰☰☰ 始祖ERP (Arche ERP Framework) 启动中 ☰☰☰");

        try {
            // 1. 加载配置
            KernelConfig config = loadConfig(args);

            // 2. 设置 FlatLaf 现代主题
            try {
                UIManager.setLookAndFeel(new com.formdev.flatlaf.FlatLightLaf());
            } catch (Exception e) {
                log.warn("FlatLaf not available: {}", e.getMessage());
            }

            // 3. 检查更新（后台，失败不影响启动）
            try { UpdateChecker.checkAndUpdate(); }
            catch (Exception ex) { log.warn("更新检查失败: {}", ex.getMessage()); }

            // 4. 初始化 MySQL 数据库连接
            try {
                DatabaseManager.init();
                log.info("✓ MySQL connected");
            } catch (RuntimeException ex) {
                log.error("数据库连接失败: {}", ex.getMessage());
                JOptionPane.showMessageDialog(null,
                    "⚠ 主节点不在线\n\n" +
                    "无法连接到数据库服务器。\n" +
                    "请确认主节点电脑已开机且 MySQL 正在运行。\n\n" +
                    "配置地址: " + com.erp.config.ConfigManager.getDbHost() + ":" +
                    com.erp.config.ConfigManager.getDbPort(),
                    "连接失败", JOptionPane.ERROR_MESSAGE);
                System.exit(1);
                return;
            }

            // 5. 显示登录对话框（模态）
            LoginDialog loginDialog = new LoginDialog();
            loginDialog.setVisible(true);

            if (!loginDialog.isLoginSuccess()) {
                log.warn("用户取消了登录");
                System.exit(0);
                return;
            }

            // 从 LoginDialog 取得会话信息（跨线程安全）
            LoginSession session = loginDialog.getLoginSession();
            // 在主线程重新设置 TenantContext
            TenantContext.setLoginSession(session);

            log.info("✓ 登录成功 — 用户: {} @ {}",
                session.username(), session.tenantName());

            // 5. 初始化租户上下文
            TenantContext.initialize(session.tenantId());

            // 6. 启动微内核
            MicroKernel kernel = MicroKernel.boot(config);
            log.info("✓ MicroKernel booted — state: {}", kernel.getState());

            // 7. 部署示例模块（生产环境应从模块目录动态扫描）
            deployDemoModules(kernel);

            // 8. 启动 Swing GUI
            SwingUtilities.invokeLater(() -> {
                try {
                    System.setProperty("awt.useSystemAAFontSettings", "on");
                    System.setProperty("swing.aatext", "true");

                    FrameworkWindow window = new FrameworkWindow(kernel, session);
                    window.setVisible(true);
                    log.info("✓ GUI initialized — FrameworkWindow displayed");

                } catch (Exception e) {
                    log.error("Failed to initialize GUI", e);
                    JOptionPane.showMessageDialog(null,
                        "GUI初始化失败: " + e.getMessage(),
                        "启动错误", JOptionPane.ERROR_MESSAGE);
                }
            });

            // 9. 注册关闭钩子
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("☰ Shutdown hook triggered");
                kernel.shutdown();
                DatabaseManager.getInstance().shutdown();
                log.info("☰ System shutdown complete");
            }));

            log.info("☰☰☰ 始祖ERP框架启动完成 ☰☰☰");
            log.info("  内核状态: {}", kernel.getState());
            log.info("  已部署模块: {}", kernel.getModuleManager().getActiveModuleCount());
            log.info("  当前公司: {} | 用户: {}",
                session.tenantName(),
                session.displayName());

        } catch (Exception e) {
            log.error("Fatal error during startup", e);
            JOptionPane.showMessageDialog(null,
                "<html><h3>始祖ERP启动失败</h3><p>" + e.getMessage() + "</p></html>",
                "严重错误", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }

    // ── 模块部署 ──

    /**
     * 部署示例模块。
     * 生产环境应实现 ModuleLoader 从指定目录动态扫描 JAR 文件。
     */
    private static void deployDemoModules(MicroKernel kernel) {
        log.info("Deploying demo modules...");

        // 注意：部署顺序隐含依赖关系
        // Inventory 无依赖，先部署
        kernel.deployModule(new InventoryModule());

        // Finance 可选依赖 Inventory
        kernel.deployModule(new FinanceModule());

        // HR 无依赖
        kernel.deployModule(new HRModule());

        // 采购管理
        kernel.deployModule(new PurchaseModule());

        // 技术管理
        kernel.deployModule(new TechModule());

        int active = kernel.getModuleManager().getActiveModuleCount();
        log.info("✓ {} modules deployed and active", active);

        // 打印模块列表
        kernel.getModuleManager().getAllModules().forEach(m ->
            log.info("   • {} v{} [{}]", m.getName(), m.getVersion(), m.getState()));
    }

    // ── 配置加载 ──

    private static KernelConfig loadConfig(String[] args) throws IOException {
        // 优先从参数指定的配置文件加载
        for (int i = 0; i < args.length - 1; i++) {
            if ("--config".equals(args[i]) || "-c".equals(args[i])) {
                String configPath = args[i + 1];
                File configFile = new File(configPath);
                if (configFile.exists()) {
                    log.info("Loading config from: {}", configPath);
                    return KernelConfig.load(configPath);
                }
                log.warn("Config file not found: {}", configPath);
            }
        }

        // 其次在 classpath 或工作目录下查找
        File localConfig = new File("erp-config.properties");
        if (localConfig.exists()) {
            log.info("Loading config from: {}", localConfig.getAbsolutePath());
            return KernelConfig.load(localConfig.getAbsolutePath());
        }

        // 默认配置
        log.info("Using default kernel configuration");
        return new KernelConfig();
    }

}
