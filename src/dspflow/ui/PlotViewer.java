package dspflow.ui;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;

import dspflow.model.Block;
import dspflow.model.Diagram;
import dspflow.model.Port;
import dspflow.model.blocks.ScopeSink;
import dspflow.model.blocks.SpectrumSink;

/**
 * Displays simulation results in interactive matplotlib windows.
 *
 * <p>Design: Java owns all the DSP. After a run, this serializes every sink's
 * data into a single CSV job and launches the bundled {@code plot.py} via the
 * system {@code python3}. That one Python process opens an interactive window
 * per sink (scope channels overlaid on one axes) and blocks in {@code plt.show()}
 * until the user closes them. Nothing is linked into the JVM, so the jar stays
 * a plain jar; the only runtime requirement is python3 + matplotlib on PATH.
 *
 * <p>{@code plot.py} ships as a classpath resource and is extracted to a temp
 * file on demand, so it works identically from {@code out/} or the jar.
 */
public final class PlotViewer {

    /** Thrown when plotting can't even be launched; message is presentable. */
    public static class PlotException extends Exception {
        public PlotException(String m) { super(m); }
        public PlotException(String m, Throwable c) { super(m, c); }
    }

    private PlotViewer() {}

    /** True if the diagram has at least one sink worth plotting. */
    public static boolean hasPlottableSinks(Diagram d) {
        for (Block b : d.blocks)
            if (b instanceof ScopeSink || b instanceof SpectrumSink) return true;
        return false;
    }

    /**
     * Launches matplotlib to show every sink in {@code diagram}. Returns
     * immediately; the Python process owns its windows and exits when the user
     * closes them. A monitor thread reports a clean error if Python fails to
     * start or dies early (e.g. matplotlib missing).
     *
     * @param onError called (off the EDT) with a user-presentable message if
     *                the Python process fails to launch or exits non-zero
     * @throws PlotException if the job can't be prepared at all
     */
    public static void show(Diagram diagram, java.util.function.Consumer<String> onError)
            throws PlotException {
        String job = buildJob(diagram);
        if (job == null)
            throw new PlotException("Nothing to plot - no scopes or spectrums, or no data. Press Run first.");

        // Dump every sink's raw data to CSV in the working dir before plotting,
        // so users get the numbers regardless of whether matplotlib runs.
        dumpCsv(diagram);

        Path scriptPath;
        Path jobPath;
        try {
            scriptPath = extractScript();
            jobPath = Files.createTempFile("dspflow_job", ".csv");
            Files.write(jobPath, job.getBytes(StandardCharsets.UTF_8));
            jobPath.toFile().deleteOnExit();
        } catch (IOException e) {
            throw new PlotException("Could not prepare plot job: " + e.getMessage(), e);
        }

        String python = pythonCommand();
        ProcessBuilder pb = new ProcessBuilder(python, scriptPath.toString(), jobPath.toString());
        pb.redirectErrorStream(true);

        final Process proc;
        try {
            proc = pb.start();
        } catch (IOException e) {
            throw new PlotException(
                    "Could not run '" + python + "'. Is Python 3 installed and on your PATH?\n"
                    + "Tip: launch with -Ddspflow.python=/path/to/python to override.\n"
                    + e.getMessage(), e);
        }

        // Watch the process without blocking the GUI; only surface failures.
        Thread monitor = new Thread(() -> {
            String output;
            int code;
            try {
                output = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                code = proc.waitFor();
            } catch (Exception ex) {
                return;
            } finally {
                try { Files.deleteIfExists(jobPath); } catch (IOException ignored) {}
            }
            if (code != 0 && onError != null) {
                String hint = output.contains("matplotlib")
                        ? "\n\nIs matplotlib installed?  Try:  " + python + " -m pip install matplotlib"
                        : "";
                onError.accept("matplotlib failed (exit " + code + "):\n" + output.trim() + hint);
            }
        }, "dspflow-plot-monitor");
        monitor.setDaemon(true);
        monitor.start();
    }

    /** Allow overriding the interpreter via -Ddspflow.python=... ; else python3. */
    private static String pythonCommand() {
        String p = System.getProperty("dspflow.python");
        if (p != null && !p.isBlank()) return p;
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("win") ? "python" : "python3";
    }

    private static Path extractScript() throws IOException {
        try (InputStream in = PlotViewer.class.getResourceAsStream("/dspflow/resources/plot.py")) {
            if (in == null) throw new IOException("bundled plot.py resource is missing");
            Path tmp = Files.createTempFile("dspflow_plot", ".py");
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            tmp.toFile().deleteOnExit();
            return tmp;
        }
    }

    // ---- CSV dump (one file per sink, written to the working directory) ------

    /**
     * Writes one CSV per sink (scope or spectrum) into the process working
     * directory, named after the sink. Best-effort: a write failure for one
     * sink is reported on stderr and never blocks plotting.
     */
    private static void dumpCsv(Diagram diagram) {
        Path dir = Paths.get("").toAbsolutePath();
        for (Block b : diagram.blocks) {
            try {
                if (b instanceof ScopeSink sc) writeScopeCsv(dir, sc);
                else if (b instanceof SpectrumSink sp) writeSpectrumCsv(dir, sp);
            } catch (IOException e) {
                System.err.println("dspflow: could not write CSV for " + b.label()
                        + ": " + e.getMessage());
            }
        }
    }

    private static void writeScopeCsv(Path dir, ScopeSink sc) throws IOException {
        writeSamplesCsv(dir, sc.label(), sc.inputs, sc.data);
    }

    /**
     * Spectrum CSV holds the raw time-domain samples (the input to the FFT),
     * since the transform itself now happens in the Python viewer.
     */
    private static void writeSpectrumCsv(Path dir, SpectrumSink sp) throws IOException {
        writeSamplesCsv(dir, sp.label(), sp.inputs, sp.data);
    }

    /** One column per channel, one row per tick. */
    private static void writeSamplesCsv(Path dir, String label,
            List<Port> ports, List<long[]> rows) throws IOException {
        if (rows.isEmpty()) return;
        int channels = ports.size();
        StringBuilder sb = new StringBuilder(8192);
        sb.append("tick");
        for (int ch = 0; ch < channels; ch++)
            sb.append(',').append(csvField(ports.get(ch).name));
        sb.append('\n');
        for (int i = 0; i < rows.size(); i++) {
            sb.append(i);
            long[] row = rows.get(i);
            for (int ch = 0; ch < channels; ch++)
                sb.append(',').append(row[ch]);
            sb.append('\n');
        }
        write(dir, label, sb.toString());
    }

    private static void write(Path dir, String label, String contents) throws IOException {
        Path out = dir.resolve(sanitize(label) + ".csv");
        Files.write(out, contents.getBytes(StandardCharsets.UTF_8));
    }

    /** Quote a header field only if it contains CSV-special characters. */
    private static String csvField(String s) {
        if (s.indexOf(',') < 0 && s.indexOf('"') < 0 && s.indexOf('\n') < 0) return s;
        return '"' + s.replace("\"", "\"\"") + '"';
    }

    /** Turn a sink label into a safe filename stem. */
    private static String sanitize(String label) {
        StringBuilder b = new StringBuilder(label.length());
        for (int i = 0; i < label.length(); i++) {
            char c = label.charAt(i);
            b.append(Character.isLetterOrDigit(c) || c == '-' || c == '.' ? c : '_');
        }
        String s = b.toString();
        return s.isEmpty() ? "sink" : s;
    }

    // ---- CSV job construction (one block per figure; format documented in plot.py) ----

    /**
     * Builds the multi-figure job as CSV, or null if there is nothing with
     * data. Each figure is a block of {@code # key,value} metadata lines
     * followed by a {@code tick,<series>...} data table; blocks are separated
     * by a blank line. See plot.py for the exact format.
     */
    private static String buildJob(Diagram diagram) {
        StringBuilder sb = new StringBuilder(8192);
        boolean any = false;
        for (Block b : diagram.blocks) {
            boolean wrote = false;
            if (b instanceof ScopeSink sc) wrote = scopeFigure(sb, any, sc);
            else if (b instanceof SpectrumSink sp) wrote = spectrumFigure(sb, any, sp);
            any |= wrote;
        }
        return any ? sb.toString() : null;
    }

    private static boolean scopeFigure(StringBuilder sb, boolean precededByFigure, ScopeSink sc) {
        if (sc.data.isEmpty()) return false;
        if (precededByFigure) sb.append('\n');
        meta(sb, "kind", "scope");
        meta(sb, "title", sc.label());
        meta(sb, "xlabel", "tick");
        meta(sb, "ylabel", "value");
        seriesTable(sb, sc.inputs, sc.data);
        return true;
    }

    /**
     * The spectrum figure ships raw per-channel time samples plus the FFT
     * parameters; the Python viewer (numpy) computes the transform and the dB
     * magnitude. This keeps all the DSP-vs-plotting boundary in one place.
     */
    private static boolean spectrumFigure(StringBuilder sb, boolean precededByFigure, SpectrumSink sp) {
        if (sp.data.isEmpty()) return false;
        if (precededByFigure) sb.append('\n');
        meta(sb, "kind", "spectrum");
        meta(sb, "title", sp.label());
        meta(sb, "xlabel", "normalized frequency");
        meta(sb, "ylabel", "magnitude (dB)");
        meta(sb, "fft_size", Integer.toString(sp.fftSize()));
        meta(sb, "hann", Boolean.toString(sp.hann()));
        seriesTable(sb, sp.inputs, sp.data);
        return true;
    }

    /** Header row "tick,<name>,..." then one data row per tick. */
    private static void seriesTable(StringBuilder sb, List<Port> ports, List<long[]> rows) {
        sb.append("tick");
        for (Port p : ports)
            sb.append(',').append(csvField(seriesName(p)));
        sb.append('\n');
        for (int i = 0; i < rows.size(); i++) {
            sb.append(i);
            long[] row = rows.get(i);
            for (int ch = 0; ch < ports.size(); ch++)
                sb.append(',').append(row[ch]);
            sb.append('\n');
        }
    }

    /**
     * Label a sink input by the output port driving it (e.g. "my_block_1.out"),
     * since the sink's own port names are just "in1", "in2". Falls back to the
     * sink port name when the input is unconnected.
     */
    private static String seriesName(Port p) {
        Port d = p.driver;
        return d == null ? p.name : d.block.label() + "." + d.name;
    }

    /** Emit a "# key,value" metadata line, quoting the value if needed. */
    private static void meta(StringBuilder sb, String k, String v) {
        sb.append("# ").append(k).append(',').append(csvField(v)).append('\n');
    }
}
