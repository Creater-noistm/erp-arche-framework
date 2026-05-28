package com.erp.config;

import javax.swing.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.Properties;

/**
 * 自动更新检查器 — 启动时从远程 URL 拉取最新版本号，
 * 比对本机版本，有新版本则下载 JAR 并提示重启。
 */
public class UpdateChecker {

    private static final String CUR_VERSION = "1.0.0";  // 用 jar manifest 或文件存实际版本
    private static final String VERSION_FILE = "version.txt"; // 本地记录

    /** 检查更新（在 EDT 之外调用） */
    public static boolean checkAndUpdate() {
        String updateUrl = ConfigManager.getUpdateUrl();
        if (updateUrl.isEmpty()) return false;

        try {
            // 下载远程版本信息
            URL url = new URL(updateUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent", "ERP-Updater/1.0");

            Properties remote = new Properties();
            try (InputStream in = conn.getInputStream()) {
                remote.load(in);
            }

            String remoteVer = remote.getProperty("version", "0");
            String jarUrl    = remote.getProperty("jar_url", "");
            String notes     = remote.getProperty("notes", "新版本可用");

            if (remoteVer.compareTo(getLocalVersion()) <= 0) {
                return false; // 已是最新
            }

            // 询问用户
            int choice = JOptionPane.showConfirmDialog(null,
                "🆕 发现新版本 v" + remoteVer + "\n\n" + notes + "\n\n是否立即更新？",
                "自动更新", JOptionPane.YES_NO_OPTION);
            if (choice != JOptionPane.YES_OPTION) return false;

            // 下载新 JAR
            Path tempJar = Files.createTempFile("erp-update-", ".jar");
            try (InputStream jarIn = new URL(jarUrl).openStream()) {
                Files.copy(jarIn, tempJar, StandardCopyOption.REPLACE_EXISTING);
            }

            // 找到当前 JAR 路径
            Path currentJar = getCurrentJarPath();
            if (currentJar == null) {
                JOptionPane.showMessageDialog(null, "无法定位当前 JAR，请手动替换。");
                return false;
            }

            // 替换（Windows 下可以通过脚本延迟替换）
            Path backup = currentJar.resolveSibling(currentJar.getFileName() + ".old");
            Files.move(currentJar, backup, StandardCopyOption.REPLACE_EXISTING);
            Files.move(tempJar, currentJar, StandardCopyOption.REPLACE_EXISTING);

            // 更新本地版本号
            setLocalVersion(remoteVer);

            JOptionPane.showMessageDialog(null,
                "✅ 更新完成！请重新启动应用。", "更新成功", JOptionPane.INFORMATION_MESSAGE);
            System.exit(0);
            return true;

        } catch (Exception e) {
            System.err.println("[Update] 检查更新失败: " + e.getMessage());
            return false; // 静默失败，不影响正常使用
        }
    }

    /** 定位当前运行的 JAR 文件 */
    private static Path getCurrentJarPath() {
        try {
            String classPath = UpdateChecker.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI().getPath();
            Path path = Paths.get(classPath);
            // 如果从 JAR 运行则返回 JAR 路径，否则可能在 IDE 中
            if (Files.isRegularFile(path) && path.toString().endsWith(".jar")) {
                return path;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static String getLocalVersion() {
        try {
            return Files.readString(Paths.get(VERSION_FILE)).trim();
        } catch (Exception e) {
            return "0";
        }
    }

    private static void setLocalVersion(String v) {
        try {
            Files.writeString(Paths.get(VERSION_FILE), v);
        } catch (Exception ignored) {}
    }
}
