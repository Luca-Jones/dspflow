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
        if (sc.data.isEmpty()) return;
        int channels = sc.inputs.size();
        StringBuilder sb = new StringBuilder(8192);
        sb.append("tick");
        for (int ch = 0; ch < channels; ch++)
            sb.append(',').append(csvField(sc.inputs.get(ch).name));
        sb.append('\n');
        for (int i = 0; i < sc.data.size(); i++) {
            sb.append(i);
            long[] row = sc.data.get(i);
            for (int ch = 0; ch < channels; ch++)
                sb.append(',').append(row[ch]);
            sb.append('\n');
        }
        write(dir, sc.label(), sb.toString());
    }

    private static void writeSpectrumCsv(Path dir, SpectrumSink sp) throws IOException {
        double[][] xy = sp.spectrumDb();
        if (xy == null) return;
        double[] x = xy[0], y = xy[1];
        StringBuilder sb = new StringBuilder(8192);
        sb.append("freq,magnitude_db\n");
        for (int i = 0; i < x.length; i++)
            sb.append(num(x[i])).append(',').append(num(y[i])).append('\n');
        write(dir, sp.label(), sb.toString());
    }

    private static void write(Path dir, String label, String contents) throws IOException {
        Path out = dir.resolve(sanitize(label) + ".csv");
        Files.write(out, contents.getBytes(StandardCharsets.UTF_8));
    }

    /** A finite number as plain text, or empty for NaN/Inf. */
    private static String num(double v) {
        return (Double.isNaN(v) || Double.isInfinite(v)) ? "" : Double.toString(v);
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
        int channels = sc.inputs.size();
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        kv(sb, "kind", "scope").append(',');
        kv(sb, "title", sc.label()).append(',');
        kv(sb, "xlabel", "tick").append(',');
        kv(sb, "ylabel", "value").append(',');
        sb.append("\"series\":[");
        for (int ch = 0; ch < channels; ch++) {
            if (ch > 0) sb.append(',');
            sb.append('{');
            kv(sb, "name", sc.inputs.get(ch).name).append(',');
            sb.append("\"y\":[");
            for (int i = 0; i < sc.data.size(); i++) {
                if (i > 0) sb.append(',');
                sb.append(sc.data.get(i)[ch]);
            }
            sb.append("]}");
        }
        sb.append("]}");
        return sb.toString();
    }

    private static String spectrumFigure(SpectrumSink sp) {
        double[][] xy = sp.spectrumDb();
        if (xy == null) return null;
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        kv(sb, "kind", "spectrum").append(',');
        kv(sb, "title", sp.label()).append(',');
        kv(sb, "xlabel", "normalized frequency").append(',');
        kv(sb, "ylabel", "magnitude (dB)").append(',');
        doubleArray(sb, "x", xy[0]).append(',');
        doubleArray(sb, "y", xy[1]);
        sb.append('}');
        return sb.toString();
    }

    private static StringBuilder kv(StringBuilder sb, String k, String v) {
        return sb.append('"').append(k).append("\":\"").append(escape(v)).append('"');
    }

    private static StringBuilder doubleArray(StringBuilder sb, String k, double[] a) {
        sb.append('"').append(k).append("\":[");
        for (int i = 0; i < a.length; i++) {
            if (i > 0) sb.append(',');
            double v = a[i];
            if (Double.isNaN(v) || Double.isInfinite(v)) sb.append('0');
            else sb.append(v);
        }
        return sb.append(']');
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
