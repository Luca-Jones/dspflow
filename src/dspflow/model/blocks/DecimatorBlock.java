package dspflow.model.blocks;

import dspflow.model.Block;
import dspflow.model.Port;

/**
 * Decimator: a register clocked at 1/`factor` of the base rate. It captures
 * the input every `factor` ticks (at tick % factor == phase) and holds it
 * otherwise. Being registered, it also breaks feedback loops.
 *
 * Note: the output is still observed every base tick (sample-and-hold);
 * follow it with blocks gated by a matching Clock if you want a true
 * slow-domain chain.
 */
public class DecimatorBlock extends Block {
    private final Port out;
    private long held;
    private long cnt;

    public DecimatorBlock() {
        params.put("factor", "2");
        params.put("phase", "0");
        params.put("width", "16");
        in("in");
        out = out("out");
        paramsChanged();
    }

    @Override public String type() { return "Decim"; }
    @Override public String glyph() { return "\u2193" + Math.max(1, pi("factor", 2)); }

    @Override public void paramsChanged() {
        out.width = Math.max(1, pi("width", 16));
    }

    @Override public void reset() { held = 0; cnt = 0; }

    @Override public void evaluate(long t) {
        out.value = wrap(held, out.width);
    }

    @Override public void clockEdge(long t) {
        long m = Math.max(1, pl("factor", 2));
        long ph = ((pl("phase", 0) % m) + m) % m;
        if (cnt % m == ph) held = inVal(0);
        cnt++;
    }
}
