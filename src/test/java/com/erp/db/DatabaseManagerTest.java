package com.erp.db;

import org.junit.jupiter.api.*;

import java.sql.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DatabaseManager 核心测试 — 连接池 / 事务提交 / 事务回滚。
 * 使用 H2 内存数据库，无需外部依赖。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DatabaseManagerTest {

    private static DatabaseManager db;

    @BeforeAll
    static void setUp() {
        DatabaseManager.init("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1", "sa", "");
        db = DatabaseManager.getInstance();

        // 创建测试表
        db.executeUpdate("""
            CREATE TABLE IF NOT EXISTS test_accounts (
                id INT AUTO_INCREMENT PRIMARY KEY,
                name VARCHAR(100),
                balance DECIMAL(15,2)
            )
        """);
    }

    @AfterAll
    static void tearDown() {
        if (db != null) {
            db.shutdown();
        }
    }

    @BeforeEach
    void prepareData() {
        db.executeUpdate("DELETE FROM test_accounts");
        db.executeUpdate("INSERT INTO test_accounts (name, balance) VALUES ('Alice', 1000.00)");
        db.executeUpdate("INSERT INTO test_accounts (name, balance) VALUES ('Bob', 500.00)");
    }

    // ── 连接池 ──

    @Test
    @DisplayName("HikariCP 连接池正常工作")
    void connectionPoolWorks() {
        try (Connection conn = db.getConnection()) {
            assertNotNull(conn);
            assertFalse(conn.isClosed());
        } catch (SQLException e) {
            fail("连接获取失败: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("executeQuery 返回正确结果")
    void queryReturnsCorrectResult() {
        String name = db.executeQuery(
            "SELECT name FROM test_accounts WHERE balance = 1000.00",
            rs -> rs.next() ? rs.getString("name") : null
        );
        assertEquals("Alice", name);
    }

    @Test
    @DisplayName("executeUpdate 返回影响行数")
    void updateReturnsAffectedRows() {
        int rows = db.executeUpdate("UPDATE test_accounts SET balance = 999 WHERE name = 'Alice'");
        assertEquals(1, rows);
    }

    // ── 事务 ──

    @Test
    @DisplayName("事务 — 成功提交后数据持久化")
    void transactionCommitPersistsData() {
        db.runTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE test_accounts SET balance = balance - 100 WHERE name = 'Alice'")) {
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE test_accounts SET balance = balance + 100 WHERE name = 'Bob'")) {
                ps.executeUpdate();
            }
        });

        Double aliceBalance = db.executeQuery(
            "SELECT balance FROM test_accounts WHERE name = 'Alice'",
            rs -> rs.next() ? rs.getDouble("balance") : null
        );
        Double bobBalance = db.executeQuery(
            "SELECT balance FROM test_accounts WHERE name = 'Bob'",
            rs -> rs.next() ? rs.getDouble("balance") : null
        );

        assertEquals(900.00, aliceBalance, 0.01, "Alice 余额应为 900");
        assertEquals(600.00, bobBalance, 0.01, "Bob 余额应为 600");
    }

    @Test
    @DisplayName("事务 — 异常时自动回滚")
    void transactionRollbackOnError() {
        assertThrows(RuntimeException.class, () -> {
            db.runTransaction(conn -> {
                try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE test_accounts SET balance = balance - 100 WHERE name = 'Alice'")) {
                    ps.executeUpdate();
                }
                // 故意抛异常触发回滚
                throw new SQLException("模拟业务异常");
            });
        });

        // 验证余额未变化
        Double aliceBalance = db.executeQuery(
            "SELECT balance FROM test_accounts WHERE name = 'Alice'",
            rs -> rs.next() ? rs.getDouble("balance") : null
        );
        assertEquals(1000.00, aliceBalance, 0.01, "Alice 余额应保持不变（事务已回滚）");
    }

    @Test
    @DisplayName("事务 — 多条 SQL 原子性")
    void transactionAtomicity() {
        // 先删 Bob，然后尝试对不存在的 Charlie 操作导致失败
        assertThrows(RuntimeException.class, () -> {
            db.runTransaction(conn -> {
                try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM test_accounts WHERE name = 'Bob'")) {
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE test_accounts SET balance = balance + 100 WHERE name = 'Charlie'")) {
                    // Charlie 不存在，update 影响 0 行但不抛异常
                    // 手动抛异常模拟业务校验失败
                    if (ps.executeUpdate() == 0) {
                        throw new SQLException("Charlie 不存在");
                    }
                }
            });
        });

        // Bob 应仍然存在（事务回滚）
        String bobName = db.executeQuery(
            "SELECT name FROM test_accounts WHERE name = 'Bob'",
            rs -> rs.next() ? rs.getString("name") : null
        );
        assertNotNull(bobName, "Bob 应仍存在（事务回滚）");
    }

    // ── 异常处理 ──

    @Test
    @DisplayName("错误 SQL 抛出 RuntimeException")
    void invalidSqlThrowsException() {
        assertThrows(RuntimeException.class, () -> {
            db.executeUpdate("INSERT INTO non_existent_table VALUES (1)");
        });
    }

    @Test
    @DisplayName("executeQuery 传 null SQL 抛异常")
    void nullSqlQueryThrows() {
        assertThrows(RuntimeException.class, () -> {
            db.executeQuery(null, rs -> "result");
        });
    }
}
