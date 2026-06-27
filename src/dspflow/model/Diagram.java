package dspflow.model;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** The model: a set of blocks plus the wires between their ports. */
public class Diagram {
    public final List<Block> blocks = new ArrayList<>();
    public final List<Wire> wires = new ArrayList<>();
    public int nextId = 1;

    /** Project-level absolute clock frequency (Hz). One source of truth for
     *  all blocks; the Sine dialog uses it to convert period <-> frequency. */
    public long clockHz = 10_000_000;

    public void add(Block b) {
        b.id = nextId++;
        b.displayNum = nextDisplayNum(b.type());
        blocks.add(b);
    }

    /** Next per-type display index. Monotonic within a type for the session:
     *  one past the highest displayNum currently in use, so deleting a block
     *  never renumbers the survivors (no confusing relabels on the canvas). */
    private int nextDisplayNum(String type) {
        int max = 0;
        for (Block b : blocks)
            if (b.type().equals(type)) max = Math.max(max, b.displayNum);
        return max + 1;
    }

    /** Test hook for Block.main self-check. */
    int nextDisplayNumCheck(String type) { return nextDisplayNum(type); }

    public void remove(Block b) {
        blocks.remove(b);
        wires.removeIf(w -> w.src.block == b || w.dst.block == b);
    }

    public void remove(Wire w) {
        wires.remove(w);
    }

    /**
     * Connect two ports (in either order). Exactly one must be an output and
     * one an input. An input may only have one driver; a new wire replaces
     * the old one. Returns true on success.
     */
    public boolean connect(Port a, Port b) {
        Port src = a.input ? b : a;
        Port dst = a.input ? a : b;
        if (src.input || !dst.input) return false;
        wires.removeIf(w -> w.dst == dst);
        wires.add(new Wire(src, dst));
        return true;
    }

    /** Drop wires whose ports no longer exist (after a port-count change). */
    public void pruneWires() {
        wires.removeIf(w -> !w.src.block.outputs.contains(w.src)
                         || !w.dst.block.inputs.contains(w.dst));
    }

    /** Resolve every input port's driver from the wire list. */
    public void bindDrivers() {
        for (Block b : blocks)
            for (Port p : b.inputs) p.driver = null;
        for (Wire w : wires) w.dst.driver = w.src;
    }

    public Port findPort(Block b, String name, boolean input) {
        for (Port p : input ? b.inputs : b.outputs)
            if (p.name.equals(name)) return p;
        return null;
    }

    // ---- persistence (simple line-based text format) -------------------

    public void save(File f) throws IOException {
        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(
                new FileOutputStream(f), StandardCharsets.UTF_8))) {
            pw.println("DSPFLOW 1");
            pw.println("CLOCK " + clockHz);
            for (Block b : blocks) {
                pw.println("BLOCK " + b.type() + " " + b.id + " " + b.x + " " + b.y
                        + " " + b.w + " " + b.h
                        + " " + b.rotation + " " + (b.flipH ? 1 : 0) + " " + (b.flipV ? 1 : 0));
                for (Map.Entry<String, String> e : b.params.entrySet())
                    pw.println("P " + e.getKey() + "=" + e.getValue());
            }
            for (Wire w : wires) {
                pw.println("WIRE " + w.src.block.id + " " + w.src.name + " "
                                   + w.dst.block.id + " " + w.dst.name);
                for (java.awt.Point p : w.waypoints)
                    pw.println("W " + p.x + " " + p.y);
            }
        }
    }

    public static Diagram load(File f) throws IOException {
        Diagram d = new Diagram();
        Map<Integer, Block> byId = new HashMap<>();
        List<String[]> wireLines = new ArrayList<>();
        Block cur = null;
        int maxId = 0;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                new FileInputStream(f), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#") || line.startsWith("DSPFLOW")) continue;
                if (line.startsWith("CLOCK ")) {
                    // ponytail: old files lack this line and keep the 10MHz default.
                    try { d.clockHz = Long.parseLong(line.substring(6).trim()); }
                    catch (NumberFormatException ignore) {}
                    continue;
                }
                if (line.startsWith("BLOCK ")) {
                    String[] t = line.split("\\s+");
                    cur = BlockLibrary.create(t[1]);
                    if (cur == null) throw new IOException("Unknown block type: " + t[1]);
                    cur.id = Integer.parseInt(t[2]);
                    cur.x = Integer.parseInt(t[3]);
                    cur.y = Integer.parseInt(t[4]);
                    // Parse size, rotation, flip (optional, for backwards compatibility)
                    // New format: BLOCK type id x y w h rot flipH flipV (10 tokens)
                    // Old format: BLOCK type id x y rot flipH flipV (8 tokens)
                    if (t.length >= 10) {
                        // New format with w/h
                        cur.w = Integer.parseInt(t[5]);
                        cur.h = Integer.parseInt(t[6]);
                        cur.rotation = Integer.parseInt(t[7]) % 4;
                        cur.flipH = Integer.parseInt(t[8]) != 0;
                        cur.flipV = Integer.parseInt(t[9]) != 0;
                    } else {
                        // Old format without w/h
                        if (t.length > 5) cur.rotation = Integer.parseInt(t[5]) % 4;
                        if (t.length > 6) cur.flipH = Integer.parseInt(t[6]) != 0;
                        if (t.length > 7) cur.flipV = Integer.parseInt(t[7]) != 0;
                    }
                    d.blocks.add(cur);
                    byId.put(cur.id, cur);
                    maxId = Math.max(maxId, cur.id);
                } else if (line.startsWith("P ")) {
                    if (cur == null) continue;
                    String kv = line.substring(2);
                    int eq = kv.indexOf('=');
                    if (eq > 0) cur.params.put(kv.substring(0, eq).trim(), kv.substring(eq + 1).trim());
                } else if (line.startsWith("WIRE ") || line.startsWith("W ")) {
                    wireLines.add(line.split("\\s+"));
                }
            }
        }
        // displayNum isn't serialized; recompute per-type by block order so
        // labels come out contiguous (Sine 1, Sine 2, ...) regardless of ids.
        Map<String, Integer> typeCount = new HashMap<>();
        for (Block b : d.blocks)
            b.displayNum = typeCount.merge(b.type(), 1, Integer::sum);
        for (Block b : d.blocks) b.paramsChanged();
        Wire curWire = null;
        for (String[] t : wireLines) {
            if (t[0].equals("WIRE")) {
                Block sb = byId.get(Integer.parseInt(t[1]));
                Block db = byId.get(Integer.parseInt(t[3]));
                if (sb == null || db == null) { curWire = null; continue; }
                Port src = d.findPort(sb, t[2], false);
                Port dst = d.findPort(db, t[4], true);
                if (src != null && dst != null) {
                    curWire = new Wire(src, dst);
                    d.wires.add(curWire);
                } else {
                    curWire = null;
                }
            } else if (t[0].equals("W") && curWire != null) {
                curWire.waypoints.add(new java.awt.Point(
                    Integer.parseInt(t[1]), Integer.parseInt(t[2])));
            }
        }
        d.nextId = maxId + 1;
        return d;
    }
}
