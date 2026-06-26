package dspflow.model.blocks;

import dspflow.model.Block;
import dspflow.model.Port;
import java.util.Random;

/**
 * Gaussian (normal) white-noise source: round(mean + stdev * N(0,1)),
 * one fresh sample per tick. The optional clock-enable gates new draws:
 * when low the previous sample is held (so the source can live in a slow
 * clock domain). Uses java.util.Random.nextGaussian(). `seed` makes runs
 * reproducible; the default seed 1 gives a fixed stream.
 */
public class GaussianNoiseSource extends Block {
    private final Port out;
    private final Port ce;
    private Random rng;
    private long held;
    private boolean primed;

    public GaussianNoiseSource() {
        params.put("mean", "0");
        params.put("stdev", "1000");
        params.put("seed", "1");
        params.put("width", "16");
        ce = in("ce");
        out = out("out");
        paramsChanged();
    }

    @Override public String type() { return "Gauss"; }
    @Override public String glyph() { return "Nμσ"; }

    @Override public void paramsChanged() {
        out.width = Math.max(1, pi("width", 16));
    }

    @Override public void reset() { rng = new Random(pl("seed", 1)); held = 0; primed = false; }

    @Override public void evaluate(long t) {
        if (rng == null) reset();
        if (!primed || ceHigh(ce)) {  // first tick always draws
            double v = pd("mean", 0) + pd("stdev", 1000) * rng.nextGaussian();
            held = wrap(Math.round(v), out.width);
            primed = true;
        }
        out.value = held;
    }
}
