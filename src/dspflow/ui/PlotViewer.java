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
 * data into a single JSON job and launches the bundled {@code plot.py} via the
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
        String json = buildJob(diagram);
        if (json == null)
            throw new PlotException("Nothing to plot - no scopes or spectrums, or no data. Press Run first.");

        // Dump every sink's raw data to CSV in the working dir before plotting,
        // so users get the numbers regardless of whether matplotlib runs.
        dumpCsv(diagram);

        Path scriptPath;
        Path jobPath;
        try {
            scriptPath = extractScript();
            jobPath = Files.createTempFile("dspflow_job", ".json");
            Files.write(jobPath, json.getBytes(StandardCharsets.UTF_8));
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

    // ---- JSON job construction (hand-rolled; we only emit what we control) ----

    /** Builds the multi-figure job, or null if there is nothing with data. */
    private static String buildJob(Diagram diagram) {
        StringBuilder sb = new StringBuilder(8192);
        sb.append("{\"figures\":[");
        boolean any = false;
        for (Block b : diagram.blocks) {
            String fig = null;
            if (b instanceof ScopeSink sc) fig = scopeFigure(sc);
            else if (b instanceof SpectrumSink sp) fig = spectrumFigure(sp);
            if (fig == null) continue;
            if (any) sb.append(',');
            sb.append(fig);
            any = true;
        }
        sb.append("]}");
        return any ? sb.toString() : null;
    }

    private static String scopeFigure(ScopeSink sc) {
        if (sc.data.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        kv(sb, "kind", "scope").append(',');
        kv(sb, "title", sc.label()).append(',');
        kv(sb, "xlabel", "tick").append(',');
        kv(sb, "ylabel", "value").append(',');
        seriesArray(sb, sc.inputs, sc.data);
        sb.append('}');
        return sb.toString();
    }

    /**
     * The spectrum figure ships raw per-channel time samples plus the FFT
     * parameters; the Python viewer (numpy) computes the transform and the dB
     * magnitude. This keeps all the DSP-vs-plotting boundary in one place.
     */
    private static String spectrumFigure(SpectrumSink sp) {
        if (sp.data.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        kv(sb, "kind", "spectrum").append(',');
        kv(sb, "title", sp.label()).append(',');
        kv(sb, "xlabel", "normalized frequency").append(',');
        kv(sb, "ylabel", "magnitude (dB)").append(',');
        sb.append("\"fft_size\":").append(sp.fftSize()).append(',');
        sb.append("\"hann\":").append(sp.hann()).append(',');
        seriesArray(sb, sp.inputs, sp.data);
        sb.append('}');
        return sb.toString();
    }

    /** Emit "series":[{name,y:[...]}, ...] for one channel per port. */
    private static StringBuilder seriesArray(StringBuilder sb,
            List<Port> ports, List<long[]> rows) {
        sb.append("\"series\":[");
        for (int ch = 0; ch < ports.size(); ch++) {
            if (ch > 0) sb.append(',');
            sb.append('{');
            kv(sb, "name", seriesName(ports.get(ch))).append(',');
            sb.append("\"y\":[");
            for (int i = 0; i < rows.size(); i++) {
                if (i > 0) sb.append(',');
                sb.append(rows.get(i)[ch]);
            }
            sb.append("]}");
        }
        return sb.append(']');
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

    private static StringBuilder kv(StringBuilder sb, String k, String v) {
        return sb.append('"').append(k).append("\":\"").append(escape(v)).append('"');
    }

    private static String escape(String s) {
        StringBuilder b = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n");  break;
                case '\r': b.append("\\r");  break;
                case '\t': b.append("\\t");  break;
                default:
                    if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
            }
        }
        return b.toString();
    }
}
