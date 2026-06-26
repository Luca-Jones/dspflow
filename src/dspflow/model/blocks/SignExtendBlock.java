package dspflow.model.blocks;

import dspflow.model.Block;
import dspflow.model.Port;

/**
 * Bus resize / sign-extension. Reinterprets the input as a `from` bit-wide
 * two's-complement value, then re-presents it on a `width` bit-wide bus.
 * When `width` > `from` this is a true sign extension (the input sign bit
 * fills the new high bits); set `signed` to 0 for zero-extension instead.
 * Narrowing (`width` < `from`) simply truncates/wraps to the new width.
 * Combinational. Glyph shows the resize as "[MSB:LSB]" of the output bus.
 */
public class SignExtendBlock extends Block {
    private final Port out;

    public SignExtendBlock() {
        params.put("from", "8");
        params.put("width", "16");
        params.put("signed", "1");
        in("in");
        out = out("out");
        paramsChanged();
    }

    @Override public String type() { return "SignExt"; }

    @Override public String glyph() {
        int w = Math.max(1, pi("width", 16));
        return "[" + (w - 1) + ":0]";
    }

    @Override public boolean combinational() { return true; }

    @Override public void paramsChanged() {
        out.width = Math.max(1, pi("width", 16));
    }

    @Override public void evaluate(long t) {
        int from = Math.max(1, pi("from", 8));
        long v = inVal(0);
        // Interpret the input in its source width: signed -> sign-extend,
        // unsigned -> mask to the low `from` bits (zero-extend).
        v = pi("signed", 1) != 0 ? wrap(v, from) : (v & ((from >= 64) ? -1L : ((1L << from) - 1)));
        out.value = wrap(v, out.width);
    }
}
