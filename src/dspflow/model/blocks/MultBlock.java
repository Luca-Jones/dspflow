package dspflow.model.blocks;

import dspflow.model.Block;
import dspflow.model.Port;

/**
 * Combinational multiplier. Computes the full product a*b, arithmetic
 * right-shifts it by `shift_right` (to discard fractional bits in a
 * fixed-point design), then wraps to the output width.
 */
public class MultBlock extends Block {
    private final Port out;

    public MultBlock() {
        params.put("width", "16");
        params.put("shift_right", "0");
        in("a");
        in("b");
        out = out("out");
        paramsChanged();
    }

    @Override public String type() { return "Mult"; }
    @Override public String glyph() { return "\u00D7"; }
    @Override public boolean combinational() { return true; }

    @Override public void paramsChanged() {
        out.width = Math.max(1, pi("width", 16));
    }

    @Override public void evaluate(long t) {
        long p = inVal(0) * inVal(1);
        int sr = Math.max(0, pi("shift_right", 0));
        out.value = wrap(p >> sr, out.width);
    }
}
