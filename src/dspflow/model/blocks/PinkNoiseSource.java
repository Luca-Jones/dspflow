package dspflow.model.blocks;

import dspflow.model.Block;
import dspflow.model.Port;
import java.util.Random;

/**
 * Pink (1/f) noise source: white noise filtered to a -3 dB/octave rolloff.
 * Uses Paul Kellet's "economy" pink filter (3 one-pole sections summed) over
 * a uniform white source -- cheap, ~ -10 dB at the band edges, good enough
 * for a flowgraph sim. The optional clock-enable gates new draws (hold when
 * low). `seed` makes runs reproducible; the default seed 1 gives a fixed
 * stream. `amplitude` scales the (roughly unit-variance) filter output.
 *
 * Ref: http://www.firstpr.com.au/dsp/pink-noise/  (Kellet's economy method)
 */
public class PinkNoiseSource extends Block {
    private final Port out;
    private final Port ce;
    private Random rng;
    private double b0, b1, b2;  // filter state
    private long held;
    private boolean primed;

    public PinkNoiseSource() {
        params.put("amplitude", "1000");
        params.put("seed", "1");
        params.put("width", "16");
        ce = in("ce");
        out = out("out");
        paramsChanged();
    }

    @Override public String type() { return "Pink"; }
    @Override public String glyph() { return "1/f"; }

    @Override public void paramsChanged() {
        out.width = Math.max(1, pi("width", 16));
    }

    @Override public void reset() {
        rng = new Random(pl("seed", 1));
        b0 = b1 = b2 = 0;
        held = 0; primed = false;
    }

    @Override public void evaluate(long t) {
        if (rng == null) reset();
        if (!primed || ceHigh(ce)) {  // first tick always draws
            double white = 2 * rng.nextDouble() - 1;  // uniform in [-1, +1]
            b0 = 0.99765 * b0 + white * 0.0990460;
            b1 = 0.96300 * b1 + white * 0.2965164;
            b2 = 0.57000 * b2 + white * 1.0526913;
            double pink = b0 + b1 + b2 + white * 0.1848;
            // normalize: the summed gain is ~3.5, scale back toward unit range
            held = wrap(Math.round(pink / 3.5 * pd("amplitude", 1000)), out.width);
            primed = true;
        }
        out.value = held;
    }
}
