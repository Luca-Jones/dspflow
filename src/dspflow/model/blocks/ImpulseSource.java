package dspflow.model.blocks;

import dspflow.model.Block;
import dspflow.model.Port;

/**
 * Impulse / pulse-train source. Emits `amplitude` at sample index `delay`,
 * repeating every `period` samples (period = 0 means one-shot). The sample
 * index advances when the optional clock-enable is high, so a gated impulse
 * source runs in a slower clock domain (output is held between enables).
 */
public class ImpulseSource extends Block {
    private final Port out;
    private final Port ce;
    private long n;

    public ImpulseSource() {
        params.put("amplitude", "1024");
        params.put("delay", "0");
        params.put("period", "0");
        params.put("width", "16");
        ce = in("ce");
        out = out("out");
        paramsChanged();
    }

    @Override public String type() { return "Impulse"; }
    @Override public String glyph() { return "\u03B4[n]"; }

    @Override public void paramsChanged() {
        out.width = Math.max(1, pi("width", 16));
    }

    @Override public void reset() { n = 0; }

    @Override public void evaluate(long t) {
        long amp = pl("amplitude", 1024);
        long delay = pl("delay", 0);
        long period = pl("period", 0);
        boolean hit = period > 0
                ? (n >= delay && (n - delay) % period == 0)
                : (n == delay);
        out.value = wrap(hit ? amp : 0, out.width);
    }

    @Override public void clockEdge(long t) {
        if (ceHigh(ce)) n++;
    }
}
