package ru.cw.launcher.ui;

import ru.cw.launcher.accounts.Account;
import ru.cw.launcher.util.Log;
import ru.cw.launcher.util.Paths;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;

/** Выбор папки игры и, со второго окна, временного ника. */
public final class DevLaunchDialog {

    public static final class Choice {
        public final Path dir;
        /** Пусто, если запускать текущий аккаунт. */
        public final String nick;

        Choice(Path dir, String nick) {
            this.dir = dir;
            this.nick = nick;
        }
    }

    private DevLaunchDialog() {
    }

    /** null — отмена. */
    public static Choice ask(Component parent, boolean askNick) {
        JDialog d = new JDialog(SwingUtilities.getWindowAncestor(parent), "CWLauncher",
                Dialog.ModalityType.APPLICATION_MODAL);
        d.setUndecorated(true);
        JPanel body = new JPanel(new BorderLayout(0, 12));
        body.setBackground(Theme.PANEL);
        body.setBorder(BorderFactory.createCompoundBorder(
                new Dialogs.CutBorder(Theme.INFO, 10), BorderFactory.createEmptyBorder(18, 20, 16, 20)));

        JLabel title = new JLabel(Lang.t("dev_title"));
        title.setForeground(Theme.TEXT);
        title.setFont(Theme.H3);
        JLabel hint = new JLabel("<html><div style='width:420px'>" + esc(Lang.t("dev_pick")) + "</div></html>");
        hint.setForeground(Theme.MUTED);
        hint.setFont(Theme.BODY);
        JPanel head = new JPanel(new BorderLayout(0, 6));
        head.setOpaque(false);
        head.add(title, BorderLayout.NORTH);
        head.add(hint, BorderLayout.CENTER);
        body.add(head, BorderLayout.NORTH);

        DefaultListModel<String> model = new DefaultListModel<>();
        model.addElement("");
        for (String name : Paths.playInstanceNames()) model.addElement(name);
        JList<String> list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setSelectedIndex(0);
        list.setVisibleRowCount(6);
        list.setBackground(Theme.FIELD);
        list.setForeground(Theme.TEXT);
        list.setFont(Theme.BODY);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> src, Object value, int index,
                                                          boolean selected, boolean focus) {
                String text = value == null || value.toString().isEmpty() ? Lang.t("dev_main") : value.toString();
                JLabel row = (JLabel) super.getListCellRendererComponent(src, text, index, selected, focus);
                row.setOpaque(true);
                row.setBackground(selected ? Theme.PANEL_2 : Theme.FIELD);
                row.setForeground(Theme.TEXT);
                return row;
            }
        });
        JScrollPane scroll = new JScrollPane(list);
        scroll.setPreferredSize(new Dimension(420, 140));
        scroll.setBorder(new Dialogs.CutBorder(Theme.LINE, 6));

        JTextField name = Theme.field("", 16);
        CutButton create = new CutButton(Lang.t("dev_create"), CutButton.Accent.NEUTRAL);
        JLabel status = new JLabel(" ");
        status.setForeground(new Color(0xE0524D));
        status.setFont(Theme.SMALL);
        create.addActionListener(e -> {
            String safe = Paths.playFolderName(name.getText());
            if (safe.isEmpty()) {
                status.setText(Lang.t("dev_bad_name"));
                return;
            }
            try {
                Paths.createPlayInstance(safe);
                if (!model.contains(safe)) model.addElement(safe);
                list.setSelectedValue(safe, true);
                name.setText("");
                status.setText(" ");
                Log.info("Папка игры: " + safe);
            } catch (Exception ex) {
                status.setText(Log.reason(ex));
            }
        });
        JPanel createRow = new JPanel(new BorderLayout(8, 0));
        createRow.setOpaque(false);
        createRow.add(name, BorderLayout.CENTER);
        createRow.add(create, BorderLayout.EAST);

        JPanel center = new JPanel(new BorderLayout(0, 8));
        center.setOpaque(false);
        center.add(scroll, BorderLayout.CENTER);
        JTextField nick = Theme.field("", 16);
        JPanel stack = new JPanel(new BorderLayout(0, 8));
        stack.setOpaque(false);
        stack.add(createRow, BorderLayout.NORTH);
        if (askNick) {
            JLabel nickLabel = new JLabel("<html><div style='width:420px'>"
                    + esc(Lang.t("dev_nick_h")) + "</div></html>");
            nickLabel.setForeground(Theme.MUTED);
            nickLabel.setFont(Theme.SMALL);
            JPanel nickBox = new JPanel(new BorderLayout(0, 4));
            nickBox.setOpaque(false);
            JLabel nickTitle = new JLabel(Lang.t("dev_nick"));
            nickTitle.setForeground(Theme.TEXT);
            nickTitle.setFont(Theme.BODY);
            nickBox.add(nickTitle, BorderLayout.NORTH);
            nickBox.add(nick, BorderLayout.CENTER);
            nickBox.add(nickLabel, BorderLayout.SOUTH);
            stack.add(nickBox, BorderLayout.CENTER);
        }
        stack.add(status, BorderLayout.SOUTH);
        center.add(stack, BorderLayout.SOUTH);
        body.add(center, BorderLayout.CENTER);

        final Choice[] result = {null};
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        CutButton cancel = new CutButton(Lang.t("cancel"), CutButton.Accent.GHOST);
        cancel.addActionListener(e -> d.dispose());
        CutButton go = new CutButton(Lang.t("dev_go"), CutButton.Accent.PRIMARY);
        go.addActionListener(e -> {
            String chosenNick = null;
            if (askNick) {
                String typed = nick.getText() == null ? "" : nick.getText().trim();
                if (!typed.isEmpty() && !Account.validNick(typed)) {
                    status.setText(Lang.t("dev_bad_nick"));
                    return;
                }
                chosenNick = typed.isEmpty() ? null : typed;
            }
            result[0] = new Choice(folderOf(list.getSelectedValue()), chosenNick);
            d.dispose();
        });
        if (askNick) {
            CutButton skip = new CutButton(Lang.t("dev_skip"), CutButton.Accent.NEUTRAL);
            skip.addActionListener(e -> {
                result[0] = new Choice(folderOf(list.getSelectedValue()), null);
                d.dispose();
            });
            buttons.add(skip);
        }
        buttons.add(go);
        buttons.add(cancel);
        body.add(buttons, BorderLayout.SOUTH);

        d.setContentPane(body);
        WindowBackground.dim(d);
        d.pack();
        d.setLocationRelativeTo(parent);
        d.setVisible(true);
        return result[0];
    }

    private static Path folderOf(String selected) {
        if (selected == null || selected.isEmpty()) return Paths.gameDir();
        return Paths.playInstance(selected);
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
