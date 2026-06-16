package dspflow.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Base class for every block in a diagram.
 *
 * Simulation contract (see engine.Simulator):
 *  - evaluate(t) computes output port values for tick t. Registered
 *    (sequential) blocks output their *current state* here; combinational
 *    blocks (combinational() == true) compute outputs from current input
 *    values and are evaluated in topological order.
 *  - clockEdge(t) runs after every block has evaluated; sequential blocks
 *    latch their next state here. This two-phase scheme gives correct
 *    register semantics and makes feedback loops work, as long as every
 *    loop contains at least one registered block (Delay / Decimator).
 *
 * All signals are signed two's-complement integers carried in a long and
 * wrapped to the block's output bus width (hardware-style overflow).
 */
public abstract class Block {
    public int id;
    public int x, y, w = 110, h = 60;
    public int rotation = 0;  // 0=0°, 1=90°, 2=180°, 3=270° clockwise
    public boolean flipH = false;  // horizontal flip
    public boolean flipV = false;  // vertical flip
    public final LinkedHashMap<String, String> params = new LinkedHashMap<>();
    public final List<Port> inputs = new ArrayList<>();
    public final List<Port> outputs = new ArrayList<>();

    /** Rotate 90° clockwise. */
    public void rotateCW() { rotation = (rotation + 1) % 4; }

    /** Rotate 90° counter-clockwise. */
    public void rotateCCW() { rotation = (rotation + 3) % 4; }

    /** Toggle horizontal flip. */
    public void flipHorizontal() { flipH = !flipH; }

    /** Toggle vertical flip. */
    public void flipVertical() { flipV = !flipV; }

    /** Type name used in the palette and in saved files. */
    public abstract String type();

    /** Big symbol drawn in the middle of the block. */
    public String glyph() { return type(); }

    /** Caption drawn under the block. */
    public String label() {
        String n = params.get("name");
        return (n != null && !n.isEmpty()) ? n : type() + " " + id;
    }

    /** True if outputs depend combinationally on current-tick inputs. */
    public boolean combinational() { return false; }

    /** Clear all internal state before a simulation run. */
    public void reset() {}

    /** Phase 1: drive output port values for tick t. */
    public abstract void evaluate(long t);

    /** Phase 2: latch state / record samples after all evaluation. */
    public void clockEdge(long t) {}

    /** Re-read params (widths, port counts, state sizes). */
    public void paramsChanged() {}

    // ---- port helpers -------------------------------------------------

    protected Port in(String name) {
        Port p = new Port(this, name, true);
        inputs.add(p);
        return p;
    }

    protected Port out(String name) {
        Port p = new Port(this, name, false);
        outputs.add(p);
        return p;
    }

    /** Value seen at input i (0 if unconnected). */
    public long inVal(int i) {
        Port p = inputs.get(i);
        return p.driver == null ? 0 : p.driver.value;
    }

    /** A clock-enable input counts as high when unconnected. */
    public boolean ceHigh(Port ce) {
        return ce == null || ce.driver == null || ce.driver.value != 0;
    }

    /**
     * Grow/shrink the input list to n ports named prefix1..prefixN,
     * preserving existing Port objects (and therefore wires) where possible.
     * Only used by blocks without a "ce" port.
     */
    protected void setInputs(int n, String prefix) {
        while (inputs.size() > n) inputs.remove(inputs.size() - 1);
        while (inputs.size() < n) in(prefix + (inputs.size() + 1));
        for (int i = 0; i < n; i++) inputs.get(i).name = prefix + (i + 1);
    }

    // ---- parameter helpers --------------------------------------------

    public int pi(String key, int def) {
        try { return Integer.parseInt(params.getOrDefault(key, "" + def).trim()); }
        catch (Exception e) { return def; }
    }

    public long pl(String key, long def) {
        try { return Long.parseLong(params.getOrDefault(key, "" + def).trim()); }
        catch (Exception e) { return def; }
    }

    public double pd(String key, double def) {
        try { return Double.parseDouble(params.getOrDefault(key, "" + def).trim()); }
        catch (Exception e) { return def; }
    }

    public String ps(String key, String def) {
        String v = params.get(key);
        return v == null ? def : v;
    }

    /** Wrap v to a signed two's-complement value of the given bit width. */
    public static long wrap(long v, int width) {
        if (width <= 0) return 0;
        if (width >= 64) return v;
        int s = 64 - width;
        return (v << s) >> s;
    }
}
