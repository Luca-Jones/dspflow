package dspflow.ui;

import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;
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
            case "hann": return "Hann window";
            default: return key;
        }
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
        int row = 0;

        c.gridx = 0; c.gridy = row; c.weightx = 0;
        grid.add(new JLabel("name"), c);
        JTextField nameField = new JTextField(b.params.getOrDefault("name", ""), 12);
        c.gridx = 1; c.weightx = 1;
        grid.add(nameField, c);
        row++;

        for (Map.Entry<String, String> e : b.params.entrySet()) {
            if (e.getKey().equals("name")) continue;
            c.gridx = 0; c.gridy = row; c.weightx = 0;
            grid.add(new JLabel(displayName(e.getKey())), c);
            JTextField f = new JTextField(e.getValue(), 12);
            fields.put(e.getKey(), f);
            c.gridx = 1; c.weightx = 1;
            grid.add(f, c);
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
