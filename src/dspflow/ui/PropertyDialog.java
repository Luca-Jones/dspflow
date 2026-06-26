package dspflow.ui;

import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import javax.swing.*;

import dspflow.model.Block;
import dspflow.model.Diagram;
import dspflow.model.blocks.StickyNote;

/**
 * Generic parameter editor: shows one text field per entry in the block's
 * param map, so new blocks get an editor for free. An optional "name" row
 * lets you caption the block.
 */
public class PropertyDialog {

    private static String displayName(String key) {
        switch (key) {
            case "width": return "bus width";
            case "fft_size": return "FFT size";
            case "hann": return "Hann Windowed";
            case "period": return "period (samples)";
            case "sample_rate_hz": return "clock freq (Hz)";
            default: return key;
        }
    }

    // Explicit set, not value-sniffing: width/seed/period are also "0"/"1"-storable
    // but are numeric, not boolean. Only keys that are semantically on/off belong here.
    private static final Set<String> BOOL_KEYS = Set.of("hann", "cosine", "signed");

    /** Params edited as an on/off checkbox rather than a text field. */
    private static boolean isBool(String key) {
        return BOOL_KEYS.contains(key);
    }

    /** Parse a field's text as a double, falling back on malformed input. */
    private static double parse(String s, double def) {
        try { return Double.parseDouble(s.trim()); }
        catch (NumberFormatException e) { return def; }
    }

    /** A stored param string is "true" when non-empty and not "0". */
    private static boolean boolValue(String v) {
        String s = v == null ? "" : v.trim();
        return !s.isEmpty() && !s.equals("0");
    }

    public static void edit(Window owner, Block b, Diagram d, CanvasPanel canvas) {
        // Sticky notes get a simpler text-only editor
        if (b instanceof StickyNote) {
            editStickyNote(owner, (StickyNote) b, canvas);
            return;
        }

        JDialog dlg = new JDialog(owner, b.type() + " " + b.id + " properties",
                Dialog.ModalityType.APPLICATION_MODAL);
        JPanel grid = new JPanel(new GridBagLayout());
        grid.setBorder(BorderFactory.createEmptyBorder(12, 14, 8, 14));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;

        Map<String, JTextField> fields = new LinkedHashMap<>();
        Map<String, JCheckBox> checks = new LinkedHashMap<>();
        int row = 0;

        c.gridx = 0; c.gridy = row; c.weightx = 0;
        grid.add(new JLabel("name"), c);
        JTextField nameField = new JTextField(b.params.getOrDefault("name", ""), 12);
        c.gridx = 1; c.weightx = 1;
        grid.add(nameField, c);
        row++;

        for (Map.Entry<String, String> e : b.params.entrySet()) {
            if (e.getKey().equals("name")) continue;
            // Boolean params render as a checkbox spanning both columns.
            if (isBool(e.getKey())) {
                JCheckBox cb = new JCheckBox(displayName(e.getKey()),
                        boolValue(e.getValue()));
                checks.put(e.getKey(), cb);
                c.gridx = 0; c.gridy = row; c.weightx = 1; c.gridwidth = 2;
                grid.add(cb, c);
                c.gridwidth = 1;
                row++;
                continue;
            }
            c.gridx = 0; c.gridy = row; c.weightx = 0;
            grid.add(new JLabel(displayName(e.getKey())), c);
            JTextField f = new JTextField(e.getValue(), 12);
            fields.put(e.getKey(), f);
            c.gridx = 1; c.weightx = 1;
            grid.add(f, c);
            row++;
        }

        // Sine: live read-out of the absolute signal frequency.
        // f = sample_rate / period (Hz); normalized = 1/period cycles/sample.
        if (b.type().equals("Sine")) {
            JLabel readout = new JLabel();
            Runnable update = () -> {
                double period = parse(fields.get("period").getText(), 1);
                double rate = parse(fields.get("sample_rate_hz").getText(), 0);
                if (period <= 0) period = 1e-9;
                readout.setText(String.format(
                        "signal freq: %.4g Hz   (%.4g cycles/sample)",
                        rate / period, 1.0 / period));
            };
            update.run();
            javax.swing.event.DocumentListener dl = new javax.swing.event.DocumentListener() {
                public void insertUpdate(javax.swing.event.DocumentEvent e) { update.run(); }
                public void removeUpdate(javax.swing.event.DocumentEvent e) { update.run(); }
                public void changedUpdate(javax.swing.event.DocumentEvent e) { update.run(); }
            };
            fields.get("period").getDocument().addDocumentListener(dl);
            fields.get("sample_rate_hz").getDocument().addDocumentListener(dl);
            c.gridx = 0; c.gridy = row; c.weightx = 1; c.gridwidth = 2;
            grid.add(readout, c);
            c.gridwidth = 1;
            row++;
        }

        JButton ok = new JButton("OK");
        JButton cancel = new JButton("Cancel");
        ok.addActionListener(a -> {
            String nm = nameField.getText().trim();
            if (nm.isEmpty()) b.params.remove("name");
            else b.params.put("name", nm);
            for (Map.Entry<String, JTextField> e : fields.entrySet())
                b.params.put(e.getKey(), e.getValue().getText().trim());
            for (Map.Entry<String, JCheckBox> e : checks.entrySet())
                b.params.put(e.getKey(), e.getValue().isSelected() ? "1" : "0");
            b.paramsChanged();
            d.pruneWires();
            canvas.repaint();
            dlg.dispose();
        });
        cancel.addActionListener(a -> dlg.dispose());

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btns.add(cancel);
        btns.add(ok);

        dlg.setLayout(new BorderLayout());
        dlg.add(grid, BorderLayout.CENTER);
        dlg.add(btns, BorderLayout.SOUTH);
        dlg.getRootPane().setDefaultButton(ok);
        dlg.pack();
        dlg.setLocationRelativeTo(owner);
        dlg.setVisible(true);
    }

    private static void editStickyNote(Window owner, StickyNote note, CanvasPanel canvas) {
        JDialog dlg = new JDialog(owner, "Edit Note",
                Dialog.ModalityType.APPLICATION_MODAL);

        JTextArea textArea = new JTextArea(note.text(), 8, 25);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        JScrollPane scroll = new JScrollPane(textArea);
        scroll.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));

        JButton ok = new JButton("OK");
        JButton cancel = new JButton("Cancel");
        ok.addActionListener(a -> {
            note.params.put("text", textArea.getText());
            canvas.repaint();
            dlg.dispose();
        });
        cancel.addActionListener(a -> dlg.dispose());

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btns.add(cancel);
        btns.add(ok);

        dlg.setLayout(new BorderLayout());
        dlg.add(scroll, BorderLayout.CENTER);
        dlg.add(btns, BorderLayout.SOUTH);
        dlg.getRootPane().setDefaultButton(ok);
        dlg.pack();
        dlg.setLocationRelativeTo(owner);
        dlg.setVisible(true);
    }
}
