package dspflow.engine;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import dspflow.model.Block;
import dspflow.model.Diagram;
import dspflow.model.Port;
import dspflow.model.blocks.ScopeSink;

/**
 * Golden-file test runner. Pure Java SE, no Swing, no deps.
 *
 * For each test/golden/<name>.dsp it: loads the diagram, runs the headless
 * Simulator, dumps each Scope sink to CSV (same format as the UI's
 * PlotViewer: header "tick,&lt;port&gt;,...", one row per tick), and diffs
 * the result against test/golden/<name>.expected.csv. First differing line
 * fails loudly.
 *
 * Tick count: a "# TICKS n" comment line in the .dsp (Diagram.load skips
 * '#' lines, so it stays invisible to the model). Default 32 if absent.
 *
 * Exactly one Scope sink per golden .dsp (keeps the expected file to one
 * CSV). ponytail: multi-sink would need named files; add only if a case needs it.
 *
 * Run: ./test.sh   (which also runs this after EngineTest)
 */
public class GoldenTest {

    static final int DEFAULT_TICKS = 32;

    public static void main(String[] args) throws Exception {
        // "gen <name>": print the CSV the engine produces for one case (used
        // once to snapshot a new golden; redirect into <name>.expected.csv).
        if (args.length == 2 && args[0].equals("gen")) {
            System.out.print(runToCsv(Paths.get("test", "golden", args[1] + ".dsp").toFile()));
            return;
        }
        Path dir = Paths.get("test", "golden");
        if (!Files.isDirectory(dir)) { System.out.println("no test/golden dir, skipping"); return; }
        List<Path> cases;
        try (var s = Files.list(dir)) {
            cases = s.filter(p -> p.toString().endsWith(".dsp")).sorted().toList();
        }
        if (cases.isEmpty()) { System.out.println("no golden .dsp cases found"); return; }

        int failed = 0;
        for (Path dsp : cases) {
            String name = dsp.getFileName().toString().replaceFirst("\\.dsp$", "");
            Path exp = dir.resolve(name + ".expected.csv");
            try {
                String got = runToCsv(dsp.toFile());
                String want = Files.readString(exp, StandardCharsets.UTF_8);
                String diff = firstDiff(want, got);
                if (diff == null) System.out.println("PASS golden: " + name);
                else { System.out.println("FAIL golden: " + name + "\n" + diff); failed++; }
            } catch (NoSuchFileException e) {
                System.out.println("FAIL golden: " + name + " (missing expected: " + exp + ")");
                failed++;
            } catch (Exception e) {
                System.out.println("FAIL golden: " + name + " (" + e + ")");
                failed++;
            }
        }
        if (failed > 0) { System.out.println(failed + " golden case(s) failed."); System.exit(1); }
        System.out.println("golden OK");
    }

    /** Load, run for the case's tick count, return the single Scope sink's CSV. */
    static String runToCsv(File dsp) throws Exception {
        Diagram d = Diagram.load(dsp);
        long ticks = readTicks(dsp);
        Simulator.run(d, ticks);
        List<ScopeSink> scopes = new ArrayList<>();
        for (Block b : d.blocks) if (b instanceof ScopeSink sc) scopes.add(sc);
        if (scopes.size() != 1)
            throw new IllegalStateException("expected exactly one Scope sink, found " + scopes.size());
        return toCsv(scopes.get(0));
    }

    /** Parse "# TICKS n" comment; default DEFAULT_TICKS. */
    static long readTicks(File dsp) throws IOException {
        for (String line : Files.readAllLines(dsp.toPath(), StandardCharsets.UTF_8)) {
            String t = line.trim();
            if (t.toUpperCase(Locale.ROOT).startsWith("# TICKS")) {
                String[] parts = t.split("\\s+");
                if (parts.length >= 3) return Long.parseLong(parts[2]);
            }
        }
        return DEFAULT_TICKS;
    }

    /** Mirror of PlotViewer.writeSamplesCsv: "tick,<port>,..." then one row/tick. */
    static String toCsv(ScopeSink sc) {
        List<Port> ports = sc.inputs;
        List<long[]> rows = sc.data;
        StringBuilder sb = new StringBuilder();
        sb.append("tick");
        for (Port p : ports) sb.append(',').append(p.name);
        sb.append('\n');
        for (int i = 0; i < rows.size(); i++) {
            sb.append(i);
            long[] row = rows.get(i);
            for (int ch = 0; ch < ports.size(); ch++) sb.append(',').append(row[ch]);
            sb.append('\n');
        }
        return sb.toString();
    }

    /** Returns null if equal, else a human-readable first-difference report. */
    static String firstDiff(String want, String got) {
        String[] w = want.split("\n", -1), g = got.split("\n", -1);
        int n = Math.max(w.length, g.length);
        for (int i = 0; i < n; i++) {
            String wl = i < w.length ? w[i] : "<EOF>";
            String gl = i < g.length ? g[i] : "<EOF>";
            if (!wl.equals(gl))
                return "  first diff at line " + (i + 1) + ":\n    expected: " + wl + "\n    got:      " + gl;
        }
        return null;
    }
}
