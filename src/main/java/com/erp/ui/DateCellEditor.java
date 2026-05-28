package com.erp.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;

/**
 * 日期选择表格编辑器 — 嵌入 JTable 的简易日历弹窗。
 * 选中日期写入单元格，选择"清除"写入"永久"。
 */
class DateCellEditor extends AbstractCellEditor implements TableCellEditor {

    private static final long serialVersionUID = 1L;

    private final JTextField field = new JTextField();

    DateCellEditor() {
        field.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        field.setEditable(false);
        field.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                showDatePicker();
            }
        });
    }

    @Override
    public Component getTableCellEditorComponent(JTable table, Object value,
            boolean isSelected, int row, int col) {
        field.setText(value != null ? value.toString() : "");
        SwingUtilities.invokeLater(this::showDatePicker);
        return field;
    }

    @Override
    public Object getCellEditorValue() {
        return field.getText();
    }

    private void showDatePicker() {
        JDialog dlg = new JDialog(SwingUtilities.windowForComponent(field),
            "选择日期", Dialog.ModalityType.APPLICATION_MODAL);
        dlg.setSize(260, 240);
        dlg.setLocationRelativeTo(field);

        JPanel cal = new JPanel(new BorderLayout(4, 4));
        cal.setBorder(new EmptyBorder(8, 8, 8, 8));

        LocalDate today = LocalDate.now();
        LocalDate current;
        try {
            current = LocalDate.parse(field.getText());
        } catch (Exception ex) {
            current = today;
        }

        JPanel header = new JPanel(new BorderLayout());
        LocalDate[] curRef = {current};
        JLabel monthLabel = new JLabel(current.getYear() + "年 " + current.getMonthValue() + "月");
        monthLabel.setFont(new Font("微软雅黑", Font.BOLD, 13));
        JButton prev = new JButton("◀");
        prev.setFocusable(false);
        JButton next = new JButton("▶");
        next.setFocusable(false);
        JButton clear = new JButton("清除");
        header.add(prev, BorderLayout.WEST);
        header.add(monthLabel, BorderLayout.CENTER);
        header.add(next, BorderLayout.EAST);

        JPanel grid = new JPanel(new GridLayout(0, 7, 2, 2));
        String[] dayNames = {"一", "二", "三", "四", "五", "六", "日"};
        for (String d : dayNames) {
            grid.add(new JLabel(d, SwingConstants.CENTER));
        }
        LocalDate firstDay = curRef[0].withDayOfMonth(1);
        int startDow = firstDay.getDayOfWeek().getValue();
        int daysInMonth = firstDay.lengthOfMonth();
        for (int i = 1; i < startDow; i++) {
            grid.add(new JLabel());
        }
        LocalDate[] selected = {current};
        for (int d = 1; d <= daysInMonth; d++) {
            final LocalDate day = firstDay.withDayOfMonth(d);
            JButton btn = new JButton(String.valueOf(d));
            btn.setFont(new Font("微软雅黑", Font.PLAIN, 11));
            btn.setMargin(new Insets(0, 0, 0, 0));
            btn.setFocusable(false);
            if (day.equals(today)) {
                btn.setForeground(new Color(25, 118, 210));
            }
            btn.addActionListener(ev -> {
                selected[0] = day;
                dlg.dispose();
            });
            grid.add(btn);
        }

        prev.addActionListener(e -> {
            curRef[0] = curRef[0].minusMonths(1);
            dlg.dispose();
            showDatePicker();
        });
        next.addActionListener(e -> {
            curRef[0] = curRef[0].plusMonths(1);
            dlg.dispose();
            showDatePicker();
        });
        clear.addActionListener(e -> {
            selected[0] = null;
            dlg.dispose();
        });

        cal.add(header, BorderLayout.NORTH);
        cal.add(grid, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout());
        bottom.add(clear);
        cal.add(bottom, BorderLayout.SOUTH);

        dlg.add(cal);
        dlg.setVisible(true);

        if (selected[0] != null) {
            field.setText(selected[0].toString());
        } else {
            field.setText("永久");
        }
        stopCellEditing();
    }
}
