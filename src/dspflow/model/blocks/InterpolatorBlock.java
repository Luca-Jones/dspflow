package dspflow.model.blocks;

import dspflow.model.Block;
import dspflow.model.Port;

/**
 * Interpolator (zero-stuffer): passes the input through on one tick out of
 * every `factor` (at tick % factor == phase) and outputs 0 otherwise,
 * producing the classic up-sampled stream with spectral images. Follow it
 * with your interpolation filter. Combinational.
 */
public class InterpolatorBlock extends Block {
    private final Port out;

    public InterpolatorBlock() {
        params.put("factor", "2");
        params.put("phase", "0");
        params.put("width", "16");
        in("in");
        out = out("out");
        paramsChanged();
    }

    @Override public String type() { return "Interp"; }
    @Override public String glyph() { return "\u2191" + Math.max(1, pi("factor", 2)); }
    @Override public boolean combinational() { return true; }

    @Override public void paramsChanged() {
        out.width = Math.max(1, pi("width", 16));
    }

    @Override public void evaluate(long t) {
        long l = Math.max(1, pl("factor", 2));
        long ph = ((pl("phase", 0) % l) + l) % l;
        out.value = (t % l == ph) ? wrap(inVal(0), out.width) : 0;
    }
}
