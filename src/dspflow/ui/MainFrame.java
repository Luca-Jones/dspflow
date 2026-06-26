package dspflow.ui;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.File;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;

import dspflow.engine.Simulator;
import dspflow.model.Block;
import dspflow.model.BlockLibrary;
import dspflow.model.Diagram;
import dspflow.model.blocks.ScopeSink;
import dspflow.model.blocks.SpectrumSink;

public class MainFrame extends JFrame {

    private Diagram diagram = new Diagram();
    private CanvasPanel canvas;
    private JTextField ticksField;
    private JLabel status;
    private JLabel zoomLabel;
    private JToggleButton selectBtn;
    private ButtonGroup paletteGroup;
    private File currentFile;
    private boolean darkMode = false;  // durable pref, re-applied when canvas is recreated

    public MainFrame() {
        super("DSPFlow");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1180, 760);
        setLocationByPlatform(true);

        canvas = new CanvasPanel(this, diagram);

        setJMenuBar(buildMenuBar());
        add(buildToolbar(), BorderLayout.NORTH);
        add(buildPalette(), BorderLayout.WEST);
        add(canvas, BorderLayout.CENTER);

        status = new JLabel(" Pick a block, click the canvas to place. Drag port-to-port to wire."
                + " Right-drag pans, Ctrl+wheel zooms, Del deletes, Esc cancels.");
        status.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        add(status, BorderLayout.SOUTH);
    }

    // ---- UI construction ------------------------------------------------

    private JMenuBar buildMenuBar() {
        JMenuBar mb = new JMenuBar();

        JMenu file = new JMenu("File");
        file.add(item("New", KeyEvent.VK_N, e -> newDiagram()));
        file.add(item("Open\u2026", KeyEvent.VK_O, e -> open()));
        file.add(item("Save", KeyEvent.VK_S, e -> save(false)));
        file.add(item("Save As\u2026", 0, e -> save(true)));
        file.addSeparator();
        file.add(item("Exit", 0, e -> dispose()));
        mb.add(file);

        JMenu sim = new JMenu("Simulate");
        sim.add(item("Run", KeyEvent.VK_R, e -> runSim()));
        mb.add(sim);

        JMenu view = new JMenu("View");
        view.add(item("Reset view", 0, e -> { canvas.resetView(); setZoomLabel("100%"); }));
        view.addSeparator();
        JCheckBoxMenuItem bitWidths = new JCheckBoxMenuItem("Bit widths", true);
        bitWidths.addActionListener(e -> canvas.setShowBitWidths(bitWidths.isSelected()));
        view.add(bitWidths);
        JCheckBoxMenuItem blockNames = new JCheckBoxMenuItem("Block names", false);
        blockNames.addActionListener(e -> canvas.setShowBlockNames(blockNames.isSelected()));
        view.add(blockNames);
        JCheckBoxMenuItem netNames = new JCheckBoxMenuItem("Net names", false);
        netNames.addActionListener(e -> canvas.setShowNetNames(netNames.isSelected()));
        view.add(netNames);
        view.addSeparator();
        JCheckBoxMenuItem darkMode = new JCheckBoxMenuItem("Dark mode", false);
        darkMode.addActionListener(e -> { this.darkMode = darkMode.isSelected(); canvas.setDarkMode(this.darkMode); });
        view.add(darkMode);
        mb.add(view);

        JMenu help = new JMenu("Help");
        help.add(item("Semantics\u2026", 0, e -> showSemantics()));
        help.add(item("About\u2026", 0, e -> JOptionPane.showMessageDialog(this,
                "DSPFlow - a small block-diagram sandbox for multirate fixed-point DSP.\n"
              + "Plain Java SE / Swing, no dependencies.",
                "About DSPFlow", JOptionPane.INFORMATION_MESSAGE)));
        mb.add(help);
        return mb;
    }

    private JMenuItem item(String name, int key, java.awt.event.ActionListener a) {
        JMenuItem it = new JMenuItem(name);
        if (key != 0) it.setAccelerator(KeyStroke.getKeyStroke(key,
                Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        it.addActionListener(a);
        return it;
    }

    private JToolBar buildToolbar() {
        JToolBar tb = new JToolBar();
        tb.setFloatable(false);
        tb.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));

        tb.add(new JLabel("Ticks: "));
        ticksField = new JTextField("1024", 7);
        ticksField.setMaximumSize(new Dimension(90, 28));
        ticksField.addActionListener(e -> runSim());
        tb.add(ticksField);
        tb.addSeparator();

        JButton run = new JButton("\u25B6 Run");
        run.setToolTipText("Reset all state and simulate the given number of base ticks (Ctrl+R)");
        run.addActionListener(e -> runSim());
        tb.add(run);
        tb.addSeparator();

        zoomLabel = new JLabel("100%");
        tb.add(Box.createHorizontalGlue());
        tb.add(zoomLabel);
        return tb;
    }

    private JComponent buildPalette() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(BorderFactory.createEmptyBorder(8, 6, 8, 6));
        paletteGroup = new ButtonGroup();

        selectBtn = paletteButton("\u2196 Select", null);
        selectBtn.setSelected(true);
        p.add(selectBtn);
        p.add(Box.createVerticalStrut(8));

        // Grouping for the known types (visual ordering only). Any block that is
        // registered in BlockLibrary.TYPES but not listed here is appended to a
        // trailing "Other" group, so new block types appear in the palette
        // automatically without having to edit this list.
        String[][] groups = {
            {"Constant", "Impulse", "Sine", "Clock", "Gauss", "White", "Pink"},
            {"Delay", "Sum", "Mult", "Shift", "SignExt"},
            {"Decim", "Interp"},
            {"Scope", "Spectrum"},
            {"Note"}
        };
        java.util.Set<String> grouped = new java.util.LinkedHashSet<>();
        for (String[] grp : groups) for (String t : grp) grouped.add(t);

        for (String[] grp : groups) {
            for (String t : grp)
                if (java.util.Arrays.asList(BlockLibrary.TYPES).contains(t))
                    p.add(paletteButton(t, t));
            p.add(Box.createVerticalStrut(8));
        }
        boolean anyOther = false;
        for (String t : BlockLibrary.TYPES) {
            if (grouped.contains(t)) continue;
            p.add(paletteButton(t, t));
            anyOther = true;
        }
        if (anyOther) p.add(Box.createVerticalStrut(8));
        p.add(Box.createVerticalGlue());

        JScrollPane sp = new JScrollPane(p, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sp.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, new Color(0xD7DBE0)));
        return sp;
    }

    private JToggleButton paletteButton(String label, String type) {
        JToggleButton b = new JToggleButton(label);
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
        b.setMaximumSize(new Dimension(110, 30));
        b.setFocusPainted(false);
        if (type != null) b.setToolTipText(BlockLibrary.describe(type));
        paletteGroup.add(b);
        b.addActionListener(e -> {
            canvas.setPlacing(type);
            if (type != null) setStatus("Click the canvas to place a " + type
                    + " block (hold Shift to place several).");
        });
        return b;
    }

    // ---- actions ----------------------------------------------------------

    public void clearPlacing() {
        selectBtn.setSelected(true);
        canvas.setPlacing(null);
    }

    public void setStatus(String s) { status.setText(" " + s); }

    public void setZoomLabel(String s) { zoomLabel.setText(s); }

    private void runSim() {
        long n;
        try {
            n = Long.parseLong(ticksField.getText().trim());
            if (n <= 0 || n > 50_000_000L) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Ticks must be a positive integer (max 50,000,000).",
                    "Invalid tick count", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            long t0 = System.nanoTime();
            Simulator.run(diagram, n);
            long ms = (System.nanoTime() - t0) / 1_000_000;
            setStatus("Ran " + n + " ticks in " + ms + " ms.");
            canvas.repaint();
            showPlots();
        } catch (Simulator.SimException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(),
                    "Simulation error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Opens interactive matplotlib windows for every sink with data. */
    public void showPlots() {
        if (!PlotViewer.hasPlottableSinks(diagram)) return;
        final JFrame owner = this;
        try {
            PlotViewer.show(diagram, msg ->
                SwingUtilities.invokeLater(() -> {
                    setStatus("Plotting failed.");
                    JOptionPane.showMessageDialog(owner, msg,
                            "matplotlib error", JOptionPane.ERROR_MESSAGE);
                }));
        } catch (PlotViewer.PlotException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(),
                    "Could not plot", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void newDiagram() {
        replaceDiagram(new Diagram(), null);
        setStatus("New diagram.");
    }

    private void open() {
        JFileChooser fc = chooser();
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        openFile(fc.getSelectedFile());
    }

    /** Loads a diagram file; used by File &gt; Open and the command line. */
    public void openFile(File f) {
        try {
            replaceDiagram(Diagram.load(f), f);
            setStatus("Loaded " + f.getName() + ".");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Could not load file:\n" + ex,
                    "Open failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void save(boolean as) {
        File f = currentFile;
        if (as || f == null) {
            JFileChooser fc = chooser();
            if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
            f = fc.getSelectedFile();
            if (!f.getName().contains(".")) f = new File(f.getPath() + ".dsp");
        }
        try {
            diagram.save(f);
            currentFile = f;
            setTitle("DSPFlow - " + f.getName());
            setStatus("Saved " + f.getName() + ".");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Could not save file:\n" + ex,
                    "Save failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private JFileChooser chooser() {
        JFileChooser fc = new JFileChooser(currentFile != null ? currentFile.getParentFile()
                : new File("examples").isDirectory() ? new File("examples") : null);
        fc.setFileFilter(new FileNameExtensionFilter("DSPFlow diagrams (*.dsp)", "dsp"));
        return fc;
    }

    private void replaceDiagram(Diagram d, File f) {
        diagram = d;
        currentFile = f;
        setTitle(f == null ? "DSPFlow" : "DSPFlow - " + f.getName());
        remove(canvas);
        canvas = new CanvasPanel(this, diagram);
        canvas.setDarkMode(darkMode);  // respect pref across file open (fresh canvas defaults to light)
        add(canvas, BorderLayout.CENTER);
        revalidate();
        canvas.repaint();
        clearPlacing();
    }

    private void showSemantics() {
        JTextArea ta = new JTextArea(
            "Simulation model\n"
          + "----------------\n"
          + "Time advances in base ticks (the fastest clock in the design).\n"
          + "Each tick has two phases:\n"
          + "  1. evaluate - registered blocks (Delay, Decim, sources) output their\n"
          + "     state; combinational blocks (Sum, Mult, Shift, Interp) then compute\n"
          + "     in dependency order, exactly like logic settling between clock edges.\n"
          + "  2. clock edge - registers latch their inputs, sinks record.\n\n"
          + "Feedback is allowed as long as every loop contains a Delay (or Decim);\n"
          + "a purely combinational loop is rejected as an algebraic loop.\n\n"
          + "Values are signed two's-complement integers that wrap at each block's\n"
          + "output width - overflow behaves like real hardware.\n\n"
          + "Multirate: wire a Clock block into a 'ce' (clock-enable) input to put a\n"
          + "Delay or source in a slower clock domain - the FPGA single-clock +\n"
          + "clock-enable style. Decim keeps 1 of M samples (registered, holds its\n"
          + "output); Interp zero-stuffs by L. Scopes sample every base tick, so slow\n"
          + "signals appear as staircases.", 24, 76);
        ta.setEditable(false);
        ta.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JOptionPane.showMessageDialog(this, new JScrollPane(ta),
                "DSPFlow semantics", JOptionPane.PLAIN_MESSAGE);
    }
}
