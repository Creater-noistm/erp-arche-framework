package com.erp.db;

import com.erp.config.ConfigManager;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;

/**
 * MySQL 数据库管理器 — 连接池版。
 *
 * 所有数据库操作都通过 HikariCP 连接池执行，
 * 支持高并发场景，所有数据读写直连 MySQL。
 */
public class DatabaseManager {

    private static final Logger log = LoggerFactory.getLogger(DatabaseManager.class);

    private static DatabaseManager instance;
    private HikariDataSource dataSource;

    // 连接配置已移至 ConfigManager

    private DatabaseManager(String url, String user, String password) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(user);
        config.setPassword(password);
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");

        // 连接池配置（高并发调优）
        config.setMaximumPoolSize(10);       // 最大连接数
        config.setMinimumIdle(3);            // 最小空闲
        config.setConnectionTimeout(5000);   // 连不上时最多等5秒
        config.setIdleTimeout(300000);       // 空闲5分钟后回收
        config.setMaxLifetime(600000);       // 每个连接最长生存10分钟
        config.setPoolName("ErpPool");

        // 确保每次拿到的连接是最新的
        config.setConnectionTestQuery("SELECT 1");
        config.setAutoCommit(true);

        dataSource = new HikariDataSource(config);
    }

    public static synchronized void init() {
        if (instance != null) return;
        String url  = ConfigManager.getJdbcUrl();
        String user = ConfigManager.getDbUser();
        String pass = ConfigManager.getDbPass();
        instance = new DatabaseManager(url, user, pass);

        // 验证连接池
        try (Connection conn = instance.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            log.info("✅ ERP-Pool connected: {} v{} @ {}", meta.getDatabaseProductName(),
                meta.getDatabaseProductVersion(), ConfigManager.getDbHost());
        } catch (SQLException e) {
            log.error("❌ 无法连接到主节点 MySQL ({}:{}): {}",
                ConfigManager.getDbHost(), ConfigManager.getDbPort(), e.getMessage());
            instance = null;
            throw new RuntimeException("主节点不在线 — 数据库连接失败: " + e.getMessage(), e);
        }

        instance.verifyTables();
    }

    public static synchronized void init(String url, String user, String password) {
        if (instance != null) return;
        instance = new DatabaseManager(url, user, password);
        try (Connection conn = instance.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            log.info("MySQL connected: {} @ {}", meta.getDatabaseProductName(), url);
        } catch (SQLException e) {
            throw new RuntimeException("MySQL连接失败", e);
        }
    }

    public static DatabaseManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("数据库未初始化，请先调用 DatabaseManager.init()");
        }
        return instance;
    }

    /** 从连接池获取连接（用完必须 close） */
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    /** 关闭连接池（应用退出时） */
    public synchronized void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            log.info("ERP-Pool closed");
        }
    }

    private void verifyTables() {
        String[] requiredTables = {"erp_tenants", "erp_users", "inv_products", "fin_account_subjects", "hr_employees"};
        try (Connection conn = getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            for (String table : requiredTables) {
                try (ResultSet rs = meta.getTables("erp", null, table, null)) {
                    if (!rs.next()) {
                        log.warn("⚠ 缺少核心表 '{}'，请执行建表脚本", table);
                    }
                }
            }
            log.info("数据库架构验证完毕");
        } catch (SQLException e) {
            log.error("验证表结构失败", e);
        }
    }

    // ── 通用查询辅助 ──

    /** 执行 INSERT/UPDATE/DELETE，返回影响行数 */
    public int executeUpdate(String sql, Object... params) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            return ps.executeUpdate();
        } catch (SQLException e) {
            log.error("SQL 执行失败: {}", sql, e);
            throw new RuntimeException("数据库操作失败: " + e.getMessage(), e);
        }
    }

    /** 执行查询，返回 ResultSet 的处理结果 */
    public <T> T executeQuery(String sql, ResultSetHandler<T> handler, Object... params) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return handler.handle(rs);
            }
        } catch (SQLException e) {
            log.error("SQL 查询失败: {}", sql, e);
            throw new RuntimeException("数据库查询失败: " + e.getMessage(), e);
        }
    }

    // ── 事务（级联操作） ──

    /** 在同一个事务中执行多条 SQL，全成功 COMMIT，任何一条失败 ROLLBACK */
    public void runTransaction(TransactionalBlock block) {
        Connection conn = null;
        try {
            conn = getConnection();
            conn.setAutoCommit(false);
            block.execute(conn);
            conn.commit();
        } catch (Exception e) {
            log.error("事务失败，已回滚", e);
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException se) { /* ignore */ }
            }
            throw new RuntimeException("事务执行失败: " + e.getMessage(), e);
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException ignored) {}
            }
        }
    }

    @FunctionalInterface
    public interface TransactionalBlock {
        void execute(Connection conn) throws SQLException;
    }

    @FunctionalInterface
    public interface ResultSetHandler<T> {
        T handle(ResultSet rs) throws SQLException;
    }
}
