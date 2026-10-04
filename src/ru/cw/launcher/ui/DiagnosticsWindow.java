package ru.cw.launcher.ui;

import ru.cw.launcher.core.LauncherApp;
import ru.cw.launcher.core.Operation;
import ru.cw.launcher.diagnostics.DiagnosticsManager;
import ru.cw.launcher.minecraft.JavaManager;
import ru.cw.launcher.model.ProgressListener;
import ru.cw.launcher.util.Utils;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;
import java.util.List;

/** Реальная диагностика окружения и экспорт отчёта без секретов. */
public final class DiagnosticsWindow {

    private DiagnosticsWindow() {
    }

    public static void show(Window owner, LauncherApp app) {
        JDialog d = new JDialog(owner, "Диагностика", Dialog.ModalityType.MODELESS);
        d.setSize(760, 560);
        d.setLocationRelativeTo(owner);
        d.getContentPane().setBackground(Theme.PANEL);
        d.setLayout(new BorderLayout(0, 8));

        DefaultListModel<DiagnosticsManager.Check> model = new DefaultListModel<>();
        JList<DiagnosticsManager.Check> list = new JList<>(model);
        list.setCellRenderer((jl, value, index, selected, focus) -> {
            String line = value.title() + " — " + value.value();
            if (value.hint() != null) line += "  (" + value.hint() + ")";
            JLabel l = new JLabel(line);
            l.setOpaque(true);
            l.setFont(Theme.BODY);
            l.setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));
            Color fg = switch (value.level()) {
                case OK -> Theme.GREEN;
                case WARN -> Theme.AMBER;
                case ERROR -> Theme.RED;
                default -> Theme.TEXT;
            };
            l.setForeground(fg);
            l.setBackground(selected ? Theme.PANEL_2 : Theme.FIELD);
            return l;
        });
        JLabel status = Theme.muted("Нажмите «Проверить», чтобы собрать фактическое состояние.");
        d.add(status, BorderLayout.NORTH);
        JScrollPane listScroll = new JScrollPane(list);
        Theme.style(listScroll);
        d.add(listScroll, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        buttons.setBackground(Theme.PANEL);
        CutButton run = new CutButton("Проверить", CutButton.Accent.PRIMARY, false);
        CutButton fix = new CutButton("Исправить выбранное", CutButton.Accent.NEUTRAL, false);
        CutButton verify = new CutButton("Проверить сборку", CutButton.Accent.NEUTRAL, false);
        CutButton report = new CutButton("Создать отчёт", CutButton.Accent.NEUTRAL, false);
        CutButton close = new CutButton("Закрыть", CutButton.Accent.GHOST, false);

        run.addActionListener(e -> app.io().submit(() -> {
            SwingUtilities.invokeLater(() -> status.setText("Идёт проверка…"));
            List<DiagnosticsManager.Check> checks = app.diagnostics().run(app.cfg, app.mc(), app.fabric(),
                    app.java(), app.mods(), app.accounts(), new ProgressListener() {
                        @Override
                        public void stage(String title) {
                            SwingUtilities.invokeLater(() -> status.setText(title));
                        }

                        @Override
                        public void progress(long done, long total, String text) {
                            SwingUtilities.invokeLater(() -> status.setText(text));
                        }
                    });
            SwingUtilities.invokeLater(() -> {
                model.clear();
                int bad = 0;
                for (DiagnosticsManager.Check c : checks) {
                    model.addElement(c);
                    if (c.level() == DiagnosticsManager.Level.ERROR) bad++;
                }
                status.setText(bad == 0 ? "Критичных ошибок не найдено." : "Ошибок: " + bad + ".");
            });
        }));
        fix.addActionListener(e -> {
            DiagnosticsManager.Check c = list.getSelectedValue();
            if (c == null || !c.fixable()) {
                Dialogs.info(d, "Выберите пункт, у которого есть способ исправления.");
                return;
            }
            applyFix(d, app, c);
        });
        verify.addActionListener(e -> {
            d.dispose();
            Operation op = app.verifyOperation();
            op.onSuccess(() -> SwingUtilities.invokeLater(() ->
                    Dialogs.info(owner, "Проверка сборки завершена без ошибок.")));
            op.onError(t -> SwingUtilities.invokeLater(() ->
                    Dialogs.error(owner, Operation.friendly(t), null, () -> app.submit("Проверка", app.verifyOperation()))));
            if (!app.submit("Проверка сборки", op)) {
                Dialogs.info(owner, "Сейчас уже выполняется другая операция.");
            }
        });
        report.addActionListener(e -> app.io().submit(() -> {
            try {
                if (app.diagnostics().checks().isEmpty()) {
                    app.diagnostics().run(app.cfg, app.mc(), app.fabric(), app.java(), app.mods(),
                            app.accounts(), ProgressListener.NOOP);
                }
                Path zip = app.diagnostics().writeReportZip(app.cfg, app.mc(), app.fabric(), app.java(), app.mods());
                SwingUtilities.invokeLater(() -> {
                    Dialogs.info(d, "Отчёт создан: " + zip + "\nПароли и токены в него не входят.");
                    Utils.openFolder(zip.getParent());
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> Dialogs.error(d,
                        "Не удалось создать отчёт.", ru.cw.launcher.util.Log.reason(ex), null));
            }
        }));
        close.addActionListener(e -> d.dispose());
        buttons.add(run);
        buttons.add(fix);
        buttons.add(verify);
        buttons.add(report);
        buttons.add(close);
        d.add(buttons, BorderLayout.SOUTH);
        WindowBackground.dim(d);
        d.setVisible(true);
        run.doClick();
    }

    private static void applyFix(Component parent, LauncherApp app, DiagnosticsManager.Check c) {
        String fix = c.fixAction() == null ? "" : c.fixAction();
        if (fix.startsWith("Установить Java")) {
            int major = JavaManager.requiredMajor(app.cfg.minecraftVersion);
            Operation op = new Operation("Установка Java", app.state())
                    .runningAs(ru.cw.launcher.model.GameState.INSTALLING)
                    .finishAs(ru.cw.launcher.model.GameState.READY);
            for (Operation.Task t : app.javaInstallTasks(major)) op.add(t.title, t.weight, t.body);
            if (!app.submit("Java", op)) Dialogs.info(parent, "Сейчас уже выполняется другая операция.");
            return;
        }
        if (fix.contains("сборк") || fix.contains("Исправить") || fix.contains("мод")) {
            app.updateBuild();
            return;
        }
        app.install();
    }
}
