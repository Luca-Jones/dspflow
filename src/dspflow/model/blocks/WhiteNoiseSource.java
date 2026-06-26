package dspflow.model.blocks;

import dspflow.model.Block;
import dspflow.model.Port;
import java.util.Random;

/**
 * Uniform white-noise source: a fresh sample drawn uniformly from
 * [-amplitude, +amplitude] each tick. Distinct from the Gaussian source:
 * here every value in the range is equally likely (flat PDF), whereas
 * Gauss is bell-shaped; both are "white" (flat power spectrum). The
 * optional clock-enable gates new draws (hold when low). `seed` makes runs
 * reproducible; the default seed 1 gives a fixed stream.
 */
public class WhiteNoiseSource extends Block {
    private final Port out;
    private final Port ce;
    private Random rng;
    private long held;
    private boolean primed;

    public WhiteNoiseSource() {
        params.put("amplitude", "1000");
        params.put("seed", "1");
        params.put("width", "16");
        ce = in("ce");
        out = out("out");
        paramsChanged();
    }

    @Override public String type() { return "White"; }
    @Override public String glyph() { return "wht"; }

    @Override public void paramsChanged() {
        out.width = Math.max(1, pi("width", 16));
    }

    @Override public void reset() { rng = new Random(pl("seed", 1)); held = 0; primed = false; }

    @Override public void evaluate(long t) {
        if (rng == null) reset();
        if (!primed || ceHigh(ce)) {  // first tick always draws
            double a = pd("amplitude", 1000);
            double v = (2 * rng.nextDouble() - 1) * a;  // uniform in [-a, +a]
            held = wrap(Math.round(v), out.width);
            primed = true;
        }
        out.value = held;
    }
}
