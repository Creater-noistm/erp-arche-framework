package com.erp.config;

import java.io.*;
import java.nio.file.*;
import java.util.Properties;

/**
 * 配置管理器 — 从 app.properties 读取数据库连接和更新参数。
 *
 * 如果文件不存在，自动用默认值生成一份。
 *
 * 配置项说明：
 *   db.host   = MySQL 主机地址（局域网 IP 或 Tailscale 虚拟 IP）
 *   db.port   = MySQL 端口（默认 3306）
 *   db.name   = 数据库名（默认 erp）
 *   db.user   = 数据库用户（默认 root）
 *   db.pass   = 数据库密码
 *   update.url = 版本检查地址（可选，留空则不自动更新）
 */
public class ConfigManager {
    private static final String FILE_NAME = "app.properties";
    private static Properties props = new Properties();

    // ── 默认值 ──
    private static final String DEF_HOST  = "127.0.0.1";
    private static final String DEF_PORT  = "3306";
    private static final String DEF_DB    = "erp";
    private static final String DEF_USER  = "root";
    private static final String DEF_PASS  = "erp2024";

    static { load(); }

    /** 加载或创建配置文件（优先 JAR 所在目录，其次当前工作目录） */
    private static void load() {
        // 先尝试 JAR 所在目录
        Path jarDir = getJarDirectory();
        if (jarDir != null) {
            Path path = jarDir.resolve(FILE_NAME);
            if (Files.exists(path)) {
                try (InputStream in = Files.newInputStream(path)) {
                    props.load(in);
                } catch (IOException e) {
                    System.err.println("[Config] 读取配置失败: " + e.getMessage());
                }
            }
        }
        // 再尝试当前工作目录
        if (props.isEmpty()) {
            Path path = Paths.get(FILE_NAME);
            if (Files.exists(path)) {
                try (InputStream in = Files.newInputStream(path)) {
                    props.load(in);
                } catch (IOException e) {
                    System.err.println("[Config] 读取配置失败: " + e.getMessage());
                }
            }
        }
        // 如果还不存在，用默认值生成
        if (props.isEmpty()) {
            setDefaults();
            save();
        }
    }

    /** 获取当前 JAR 文件所在目录 */
    private static Path getJarDirectory() {
        try {
            String cp = ConfigManager.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI().getPath();
            if (cp != null && cp.matches("/[A-Za-z]:/.*")) cp = cp.substring(1);
            Path p = Paths.get(cp);
            if (Files.isRegularFile(p)) p = p.getParent(); // JAR → 目录
            return p;
        } catch (Exception e) { return null; }
    }

    /** 写入默认配置 */
    private static void setDefaults() {
        props.setProperty("db.host", DEF_HOST);
        props.setProperty("db.port", DEF_PORT);
        props.setProperty("db.name", DEF_DB);
        props.setProperty("db.user", DEF_USER);
        props.setProperty("db.pass", DEF_PASS);
        props.setProperty("update.url", "");
    }

    /** 保存到文件（优先写 JAR 目录，其次当前目录） */
    public static void save() {
        Path dir = getJarDirectory();
        Path path = (dir != null ? dir : Paths.get("")).resolve(FILE_NAME);
        try (OutputStream out = Files.newOutputStream(path)) {
            props.store(out, "ERP Config");
        } catch (IOException e) {
            System.err.println("[Config] 写入配置失败: " + e.getMessage());
        }
    }

    // ── Getters（环境变量优先级 > 配置文件 > 默认值）──

    public static String getDbHost()   { return envOr("ERP_DB_HOST",   props.getProperty("db.host", DEF_HOST)); }
    public static String getDbPort()   { return envOr("ERP_DB_PORT",   props.getProperty("db.port", DEF_PORT)); }
    public static String getDbName()   { return envOr("ERP_DB_NAME",   props.getProperty("db.name", DEF_DB)); }
    public static String getDbUser()   { return envOr("ERP_DB_USER",   props.getProperty("db.user", DEF_USER)); }
    public static String getDbPass()   { return envOr("ERP_DB_PASS",   props.getProperty("db.pass", DEF_PASS)); }
    public static String getUpdateUrl(){ return envOr("ERP_UPDATE_URL", props.getProperty("update.url", "").trim()); }

    private static String envOr(String key, String fallback) {
        String val = System.getenv(key);
        return (val != null && !val.isEmpty()) ? val : fallback;
    }

    /** 完整 JDBC URL */
    public static String getJdbcUrl() {
        return "jdbc:mysql://" + getDbHost() + ":" + getDbPort() + "/" + getDbName()
            + "?useSSL=false&allowPublicKeyRetrieval=true"
            + "&serverTimezone=Asia/Shanghai&characterEncoding=UTF-8"
            + "&connectTimeout=5000&socketTimeout=10000";
    }

    // ── Setters（供 UI 修改） ──

    public static void setDbHost(String v) { props.setProperty("db.host", v); save(); }
    public static void setDbPort(String v) { props.setProperty("db.port", v); save(); }
    public static void setUpdateUrl(String v) { props.setProperty("update.url", v); save(); }
}
