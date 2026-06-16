package dspflow.model.blocks;

import dspflow.model.Block;
import dspflow.model.Port;

/**
 * Arithmetic bit shifter for power-of-two gain. `shift` > 0 shifts left
 * (multiply by 2^shift), `shift` < 0 shifts right (divide, sign-extending).
 * The result wraps to the output width.
 */
public class ShiftBlock extends Block {
    private final Port out;

    public ShiftBlock() {
        params.put("shift", "1");
        params.put("width", "16");
        in("in");
        out = out("out");
        paramsChanged();
    }

    @Override public String type() { return "Shift"; }

    @Override public String glyph() {
        int s = pi("shift", 1);
        return s >= 0 ? "\u00AB " + s : "\u00BB " + (-s);
    }

    @Override public boolean combinational() { return true; }

    @Override public void paramsChanged() {
        out.width = Math.max(1, pi("width", 16));
    }

    @Override public void evaluate(long t) {
        int s = pi("shift", 1);
        long v = inVal(0);
        v = s >= 0 ? (v << Math.min(s, 62)) : (v >> Math.min(-s, 62));
        out.value = wrap(v, out.width);
    }
}
