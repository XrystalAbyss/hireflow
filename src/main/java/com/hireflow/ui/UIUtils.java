package com.hireflow.ui;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Styling, fonts, colors, and UI component factory utilities for HireFlow Swing desktop.
 */
public final class UIUtils {

    // Palette
    public static final Color BG_MAIN = new Color(248, 250, 252);
    public static final Color BG_SIDEBAR = new Color(15, 23, 42);
    public static final Color BG_SIDEBAR_HOVER = new Color(30, 41, 59);
    public static final Color BG_SIDEBAR_ACTIVE = new Color(51, 65, 85);
    public static final Color CARD_BG = Color.WHITE;
    public static final Color BORDER_COLOR = new Color(226, 232, 240);

    public static final Color PRIMARY = new Color(37, 99, 235);
    public static final Color PRIMARY_HOVER = new Color(29, 78, 216);
    public static final Color SUCCESS = new Color(16, 185, 129);
    public static final Color SUCCESS_HOVER = new Color(5, 150, 105);
    public static final Color DANGER = new Color(239, 68, 68);
    public static final Color DANGER_HOVER = new Color(220, 38, 38);
    public static final Color WARNING = new Color(245, 158, 11);
    public static final Color INFO = new Color(14, 165, 233);

    public static final Color TEXT_PRIMARY = new Color(15, 23, 42);
    public static final Color TEXT_SECONDARY = new Color(71, 85, 105);
    public static final Color TEXT_MUTED = new Color(148, 163, 184);

    // Fonts
    public static final Font FONT_HEADER = new Font("Segoe UI", Font.BOLD, 20);
    public static final Font FONT_SUBHEADER = new Font("Segoe UI", Font.BOLD, 15);
    public static final Font FONT_BOLD = new Font("Segoe UI", Font.BOLD, 13);
    public static final Font FONT_REGULAR = new Font("Segoe UI", Font.PLAIN, 13);
    public static final Font FONT_SMALL = new Font("Segoe UI", Font.PLAIN, 11);
    public static final Font FONT_STAT_NUM = new Font("Segoe UI", Font.BOLD, 26);

    private UIUtils() {}

    public static JButton createButton(String text, Color bg, Color hoverBg, Color fg) {
        JButton btn = new JButton(text);
        btn.setFont(FONT_BOLD);
        btn.setBackground(bg);
        btn.setForeground(fg);
        btn.setFocusPainted(false);
        btn.setBorder(new CompoundBorder(new LineBorder(bg.darker(), 1, true), new EmptyBorder(8, 16, 8, 16)));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setOpaque(true);

        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (btn.isEnabled()) btn.setBackground(hoverBg);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (btn.isEnabled()) btn.setBackground(bg);
            }
        });

        return btn;
    }

    public static JButton createPrimaryButton(String text) {
        return createButton(text, PRIMARY, PRIMARY_HOVER, Color.WHITE);
    }

    public static JButton createSuccessButton(String text) {
        return createButton(text, SUCCESS, SUCCESS_HOVER, Color.WHITE);
    }

    public static JButton createDangerButton(String text) {
        return createButton(text, DANGER, DANGER_HOVER, Color.WHITE);
    }

    public static JButton createSecondaryButton(String text) {
        JButton btn = createButton(text, new Color(241, 245, 249), new Color(226, 232, 240), TEXT_PRIMARY);
        btn.setBorder(new CompoundBorder(new LineBorder(BORDER_COLOR, 1, true), new EmptyBorder(8, 16, 8, 16)));
        return btn;
    }

    public static JTextField createTextField(int columns) {
        JTextField tf = new JTextField(columns);
        tf.setFont(FONT_REGULAR);
        tf.setForeground(TEXT_PRIMARY);
        tf.setBackground(Color.WHITE);
        tf.setBorder(new CompoundBorder(new LineBorder(BORDER_COLOR, 1, true), new EmptyBorder(6, 10, 6, 10)));
        return tf;
    }

    public static <T> JComboBox<T> createComboBox(T[] items) {
        JComboBox<T> combo = new JComboBox<>(items);
        combo.setFont(FONT_REGULAR);
        combo.setBackground(Color.WHITE);
        combo.setForeground(TEXT_PRIMARY);
        combo.setBorder(new LineBorder(BORDER_COLOR, 1, true));
        return combo;
    }

    public static void styleTable(JTable table) {
        table.setFont(FONT_REGULAR);
        table.setRowHeight(32);
        table.setGridColor(BORDER_COLOR);
        table.setShowVerticalLines(false);
        table.setShowHorizontalLines(true);
        table.setSelectionBackground(new Color(238, 242, 255));
        table.setSelectionForeground(PRIMARY);

        JTableHeader header = table.getTableHeader();
        header.setFont(FONT_BOLD);
        header.setBackground(new Color(241, 245, 249));
        header.setForeground(TEXT_SECONDARY);
        header.setBorder(new LineBorder(BORDER_COLOR, 1));
        header.setPreferredSize(new Dimension(header.getWidth(), 36));

        // Default cell renderer with left padding
        DefaultTableCellRenderer renderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                setBorder(new EmptyBorder(0, 10, 0, 10));
                if (!isSelected) {
                    c.setBackground(row % 2 == 0 ? Color.WHITE : new Color(249, 250, 251));
                }
                return c;
            }
        };
        table.setDefaultRenderer(Object.class, renderer);
    }

    public static JPanel createCard(String title, Component content) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(CARD_BG);
        card.setBorder(new CompoundBorder(
                new LineBorder(BORDER_COLOR, 1, true),
                new EmptyBorder(16, 18, 16, 18)
        ));

        if (title != null && !title.isEmpty()) {
            JLabel titleLabel = new JLabel(title);
            titleLabel.setFont(FONT_SUBHEADER);
            titleLabel.setForeground(TEXT_PRIMARY);
            titleLabel.setBorder(new EmptyBorder(0, 0, 12, 0));
            card.add(titleLabel, BorderLayout.NORTH);
        }

        if (content != null) {
            card.add(content, BorderLayout.CENTER);
        }

        return card;
    }

    public static JPanel createStatCard(String labelText, String valueText, Color accentColor) {
        JPanel card = new JPanel(new BorderLayout(5, 5));
        card.setBackground(CARD_BG);
        card.setBorder(new CompoundBorder(
                new CompoundBorder(
                        new LineBorder(BORDER_COLOR, 1, true),
                        BorderFactory.createMatteBorder(0, 5, 0, 0, accentColor)
                ),
                new EmptyBorder(14, 16, 14, 16)
        ));

        JLabel title = new JLabel(labelText.toUpperCase());
        title.setFont(FONT_SMALL);
        title.setForeground(TEXT_MUTED);

        JLabel val = new JLabel(valueText);
        val.setFont(FONT_STAT_NUM);
        val.setForeground(TEXT_PRIMARY);

        card.add(title, BorderLayout.NORTH);
        card.add(val, BorderLayout.CENTER);

        return card;
    }

    public static void showSuccess(Component parent, String message, String title) {
        JOptionPane.showMessageDialog(parent, message, title, JOptionPane.INFORMATION_MESSAGE);
    }

    public static void showError(Component parent, String message, String title) {
        String sanitized = sanitizeDisplayMessage(message);
        JOptionPane.showMessageDialog(parent, sanitized, title, JOptionPane.ERROR_MESSAGE);
    }

    public static void showWarning(Component parent, String message, String title) {
        String sanitized = sanitizeDisplayMessage(message);
        JOptionPane.showMessageDialog(parent, sanitized, title, JOptionPane.WARNING_MESSAGE);
    }

    public static String sanitizeDisplayMessage(String input) {
        if (input == null) return "An unexpected error occurred.";
        return input.replaceAll("(?i)(password|pwd|secret)=[^;\\s&]+", "$1=******")
                .replaceAll("://([^:]+):([^@]+)@", "://$1:******@");
    }
}
