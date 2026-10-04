package ru.cw.launcher.ui;

import ru.cw.launcher.core.LauncherApp;
import ru.cw.launcher.core.Operation;
import ru.cw.launcher.util.Paths;

import javax.swing.*;
import java.awt.*;

/**
 * Восстановление сборки: заново готовит Minecraft, Fabric и моды.
 * Миры, скриншоты, ресурспаки и сохранения не удаляются.
 */
public class RepairWindow extends JDialog {

    public RepairWindow(Window owner, LauncherApp app) {
        super(owner, "Восстановление сборки", Dialog.ModalityType.APPLICATION_MODAL);
        getContentPane().setBackground(Theme.PANEL);
        setLayout(new BorderLayout(0, 12));
        JLabel text = new JLabel("<html><div style='width:460px'>"
                + "<b>Проблема сборки.</b><br><br>"
                + (app.state().detail() == null ? "" : app.state().detail())
                + "<br><br>Восстановление заново скачает повреждённые компоненты Minecraft и Fabric "
                + "и заново установит ZIP модов после проверки modsVersion.txt. "
                + "Каталоги миров, screenshots, resourcepacks и сохранений не удаляются."
                + "<br><br>Экземпляр: " + Paths.instance(app.cfg.instanceId())
                + "</div></html>");
        text.setForeground(Theme.TEXT);
        text.setFont(Theme.BODY);
        text.setBorder(BorderFactory.createEmptyBorder(16, 16, 8, 16));
        add(text, BorderLayout.CENTER);
        WindowBackground.dim(this);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        buttons.setOpaque(false);
        CutButton repair = new CutButton("Восстановить", CutButton.Accent.PRIMARY, false);
        CutButton verify = new CutButton("Проверить сборку", CutButton.Accent.NEUTRAL, false);
        CutButton diag = new CutButton("Диагностика", CutButton.Accent.NEUTRAL, false);
        CutButton close = new CutButton("Отмена", CutButton.Accent.GHOST, false);
        repair.addActionListener(e -> {
            dispose();
            app.repair();
        });
        verify.addActionListener(e -> {
            dispose();
            Operation op = app.verifyOperation();
            op.onError(t -> SwingUtilities.invokeLater(() ->
                    Dialogs.error(owner, Operation.friendly(t), null, null)));
            if (!app.submit("Проверка сборки", op)) {
                Dialogs.info(owner, "Сейчас уже выполняется другая операция.");
            }
        });
        diag.addActionListener(e -> {
            dispose();
            DiagnosticsWindow.show(owner, app);
        });
        close.addActionListener(e -> dispose());
        buttons.add(verify);
        buttons.add(diag);
        buttons.add(repair);
        buttons.add(close);
        add(buttons, BorderLayout.SOUTH);
        pack();
        setLocationRelativeTo(owner);
    }
}
