package dspflow;

import dspflow.ui.MainFrame;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/** Entry point for DSPFlow. */
public final class Main {
    private Main() {}

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // fall back to default look and feel
            }
            MainFrame frame = new MainFrame();
            frame.setVisible(true);
            if (args.length > 0) {
                frame.openFile(new java.io.File(args[0]));
            }
        });
    }
}
