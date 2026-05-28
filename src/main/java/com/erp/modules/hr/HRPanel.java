package com.erp.modules.hr;

import com.erp.db.DatabaseManager;
import com.erp.tenant.TenantContext;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.labels.StandardPieSectionLabelGenerator;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PiePlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.renderer.category.LineAndShapeRenderer;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.general.DefaultPieDataset;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.text.DecimalFormat;

/**
* 人力资源概览面板 — 部门柱状图 + 出勤饼图 + 月度趋势折线图。
*/
public class HRPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    private final String tenant;

    // ── 中文字体 ──
    private static final Font FONT_TITLE  = new Font("微软雅黑", Font.BOLD, 14);
    private static final Font FONT_LABEL  = new Font("微软雅黑", Font.PLAIN, 11);
    private static final Font FONT_TICK   = new Font("微软雅黑", Font.PLAIN, 10);
    private static final Font FONT_CARD   = new Font("微软雅黑", Font.PLAIN, 11);
    private static final Font FONT_CARD_V = new Font("微软雅黑", Font.BOLD, 16);

    // ── 现代色板 ──
    private static final Color C_BAR    = new Color(66, 133, 244);
    private static final Color C_NORMAL = new Color(52, 168, 83);
    private static final Color C_LATE   = new Color(251, 188, 4);
    private static final Color C_EARLY  = new Color(234, 134, 46);
    private static final Color C_ABSENT = new Color(234, 67, 53);
    private static final Color C_LINE   = new Color(15, 157, 88);
    private static final Color C_CARD_BG = Color.WHITE;

    public HRPanel() {
        String t = TenantContext.getCurrentTenantId();
        tenant = (t != null) ? t : "t-arche";

        setLayout(new BorderLayout(10, 10));
        setBorder(new EmptyBorder(16, 16, 16, 16));

        JLabel title = new JLabel("人力资源概览 Dashboard");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18));
        add(title, BorderLayout.NORTH);

        JPanel body = new JPanel(new GridBagLayout());
        body.setOpaque(false);
        GridBagConstraints g = new GridBagConstraints();
        g.fill = GridBagConstraints.BOTH;
        g.insets = new Insets(4, 0, 4, 0);

        g.gridx = 0; g.gridy = 0; g.weightx = 1; g.weighty = 0;
        body.add(buildMetricCards(), g);

        g.gridy = 1; g.weighty = 1;
        body.add(buildChartRow(), g);

        g.gridy = 2; g.weighty = 0.6;
        body.add(buildTrendChart(), g);

        add(body, BorderLayout.CENTER);
    }

    // ═══════════════════════════════════════════
    //  指标卡片
    // ═══════════════════════════════════════════

    private JPanel buildMetricCards() {
        int empCount = queryInt("SELECT COUNT(*) FROM hr_employees WHERE tenant_id=? AND status='ACTIVE'");
        int newEmp   = queryInt("SELECT COUNT(*) FROM hr_employees WHERE tenant_id=? AND status='ACTIVE' AND hire_date >= DATE_SUB(CURDATE(), INTERVAL 1 MONTH)");
        int resigned = queryInt("SELECT COUNT(*) FROM hr_employees WHERE tenant_id=? AND status='RESIGNED' AND created_at >= DATE_SUB(CURDATE(), INTERVAL 1 MONTH)");
        double turnover = (empCount + resigned) > 0 ? (double) resigned / (empCount + resigned) * 100 : 0;

        JPanel row = new JPanel(new GridLayout(1, 4, 12, 0));
        row.setOpaque(false);
        row.add(metricCard("👨‍💼 在职员工", empCount + " 人", new Color(50, 120, 200)));
        row.add(metricCard("📅 本月入职", newEmp + " 人", new Color(80, 170, 80)));
        row.add(metricCard("🚪 本月离职", resigned + " 人", new Color(200, 80, 80)));
        row.add(metricCard("📊 离职率", String.format("%.1f%%", turnover), new Color(200, 130, 50)));
        return row;
    }

    private JPanel metricCard(String label, String value, Color accent) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(228, 228, 228), 1),
            new EmptyBorder(12, 16, 12, 16)));
        card.setBackground(C_CARD_BG);
        JLabel tl = new JLabel(label);
        tl.setFont(FONT_CARD); tl.setForeground(new Color(140, 140, 140));
        JLabel vl = new JLabel(value);
        vl.setFont(FONT_CARD_V); vl.setForeground(accent);
        card.add(tl, BorderLayout.NORTH);
        card.add(vl, BorderLayout.CENTER);
        return card;
    }

    // ═══════════════════════════════════════════
    //  图表行
    // ═══════════════════════════════════════════

    private JPanel buildChartRow() {
        JPanel row = new JPanel(new GridLayout(1, 2, 12, 0));
        row.setOpaque(false);
        row.add(buildDeptBarChart());
        row.add(buildAttendancePieChart());
        return row;
    }

    /** 部门人数柱状图 */
    private ChartPanel buildDeptBarChart() {
        DefaultCategoryDataset ds = new DefaultCategoryDataset();
        boolean hasData = false;
        try {
            hasData = DatabaseManager.getInstance().executeQuery(
                "SELECT department, COUNT(*) FROM hr_employees WHERE tenant_id=? AND status='ACTIVE' GROUP BY department ORDER BY COUNT(*) DESC",
                rs -> {
                    boolean any = false;
                    while (rs.next()) { ds.addValue(rs.getInt(2), "人数", rs.getString(1)); any = true; }
                    return any;
                }, tenant);
        } catch (Exception ignored) {}

        if (!hasData) ds.addValue(0, "人数", "暂无数据");

        JFreeChart chart = ChartFactory.createBarChart(
            "部门人数分布", null, "人数", ds,
            PlotOrientation.VERTICAL, false, true, false);
        applyStyle(chart);

        CategoryPlot plot = chart.getCategoryPlot();
        BarRenderer r = (BarRenderer) plot.getRenderer();
        r.setSeriesPaint(0, C_BAR);
        r.setShadowVisible(false);
        r.setMaximumBarWidth(0.10);

        return wrap(chart);
    }

    /** 出勤状况饼图 */
    private ChartPanel buildAttendancePieChart() {
        DefaultPieDataset<String> ds = new DefaultPieDataset<>();
        boolean hasData = false;
        try {
            hasData = DatabaseManager.getInstance().executeQuery(
                "SELECT status, COUNT(*) FROM hr_attendance WHERE tenant_id=? AND att_date >= DATE_SUB(CURDATE(), INTERVAL 1 MONTH) GROUP BY status",
                rs -> {
                    boolean any = false;
                    while (rs.next()) {
                        String label = switch (rs.getString(1)) {
                            case "NORMAL" -> "正常出勤";
                            case "LATE"   -> "迟到";
                            case "EARLY"  -> "早退";
                            case "ABSENT" -> "缺勤";
                            default -> rs.getString(1);
                        };
                        ds.setValue(label, rs.getInt(2));
                        any = true;
                    }
                    return any;
                }, tenant);
        } catch (Exception ignored) {}

        if (!hasData) ds.setValue("暂无数据", 1);

        JFreeChart chart = ChartFactory.createPieChart(
            "本月出勤状况", ds, true, true, false);
        applyStyle(chart);

        PiePlot<?> plot = (PiePlot<?>) chart.getPlot();
        plot.setSectionPaint("正常出勤", C_NORMAL);
        plot.setSectionPaint("迟到", C_LATE);
        plot.setSectionPaint("早退", C_EARLY);
        plot.setSectionPaint("缺勤", C_ABSENT);
        plot.setSectionPaint("暂无数据", new Color(200, 200, 200));
        plot.setLabelGenerator(new StandardPieSectionLabelGenerator(
            "{0}: {1} ({2})", new DecimalFormat("0"), new DecimalFormat("0.0%")));
        plot.setSimpleLabels(true);

        return wrap(chart);
    }

    /** 月度出勤率趋势折线图 */
    private ChartPanel buildTrendChart() {
        DefaultCategoryDataset ds = new DefaultCategoryDataset();
        boolean hasData = false;
        try {
            hasData = DatabaseManager.getInstance().executeQuery(
                "SELECT DATE_FORMAT(att_date,'%Y-%m') AS m, COUNT(*) AS t, " +
                "SUM(CASE WHEN status IN ('NORMAL','LATE','EARLY') THEN 1 ELSE 0 END) AS p " +
                "FROM hr_attendance WHERE tenant_id=? AND att_date >= DATE_SUB(CURDATE(),INTERVAL 6 MONTH) " +
                "GROUP BY m ORDER BY m",
                rs -> {
                    boolean any = false;
                    while (rs.next()) {
                        int total = rs.getInt("t"), present = rs.getInt("p");
                        ds.addValue(total > 0 ? (double) present / total * 100 : 0, "出勤率 %", rs.getString("m"));
                        any = true;
                    }
                    return any;
                }, tenant);
        } catch (Exception ignored) {}

        if (!hasData) ds.addValue(0, "出勤率 %", "无数据");

        JFreeChart chart = ChartFactory.createLineChart(
            "月度出勤率趋势", "月份", "出勤率 %", ds,
            PlotOrientation.VERTICAL, false, true, false);
        applyStyle(chart);

        CategoryPlot plot = chart.getCategoryPlot();
        LineAndShapeRenderer r = (LineAndShapeRenderer) plot.getRenderer();
        r.setSeriesPaint(0, C_LINE);
        r.setSeriesStroke(0, new BasicStroke(2.5f));
        r.setDefaultShapesVisible(true);
        r.setDefaultShape(new java.awt.geom.Ellipse2D.Double(-3.5, -3.5, 7, 7));

        return wrap(chart);
    }

    // ═══════════════════════════════════════════
    //  辅助
    // ═══════════════════════════════════════════

    private int queryInt(String sql) {
        try { return DatabaseManager.getInstance().executeQuery(sql, rs -> rs.next() ? rs.getInt(1) : 0, tenant); }
        catch (Exception e) { return 0; }
    }

    /** 给 ChartPanel 包一层白底圆角边框 */
    private ChartPanel wrap(JFreeChart chart) {
        ChartPanel cp = new ChartPanel(chart);
        cp.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(228, 228, 228), 1),
            new EmptyBorder(8, 8, 8, 8)));
        cp.setBackground(Color.WHITE);
        cp.setPreferredSize(new Dimension(0, 200));
        return cp;
    }

    /** 统一样式 — 中文字体 + 现代扁平 */
    private void applyStyle(JFreeChart chart) {
        chart.setBackgroundPaint(Color.WHITE);
        chart.getTitle().setFont(FONT_TITLE);
        chart.getTitle().setPaint(new Color(50, 50, 50));
        chart.removeLegend();

        if (chart.getPlot() instanceof PiePlot pp) {
            pp.setBackgroundPaint(Color.WHITE);
            pp.setOutlineVisible(false);
            pp.setLabelFont(FONT_LABEL);
            pp.setShadowPaint(null);
            pp.setSectionOutlinesVisible(false);
            return;
        }
        if (chart.getPlot() instanceof CategoryPlot cp) {
            cp.setBackgroundPaint(Color.WHITE);
            cp.setRangeGridlinePaint(new Color(235, 235, 235));
            cp.setDomainGridlinesVisible(false);
            cp.setOutlineVisible(false);
            cp.getDomainAxis().setLabelFont(FONT_LABEL);
            cp.getDomainAxis().setTickLabelFont(FONT_TICK);
            cp.getRangeAxis().setLabelFont(FONT_LABEL);
            cp.getRangeAxis().setTickLabelFont(FONT_TICK);
        }
    }
}
