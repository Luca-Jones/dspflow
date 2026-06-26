package dspflow.model.blocks;

import dspflow.model.Block;
import dspflow.model.Port;

/**
 * Quantized sine/cosine source: round(A * sin(2*pi*n/period + phase)).
 * `period` is in samples and may be fractional. The sample counter n
 * advances when the optional clock-enable is high (sample-and-hold
 * otherwise), so the source can live in a slow clock domain.
 */
public class SineSource extends Block {
    private final Port out;
    private final Port ce;
    private long n;

    public SineSource() {
        params.put("amplitude", "1000");
        params.put("period", "32");
        params.put("phase_deg", "0");
        params.put("cosine", "0");
        params.put("width", "16");
        // Base clock rate in Hz; lets the dialog show an absolute signal
        // frequency. ponytail: per-block, no model has a global rate.
        params.put("sample_rate_hz", "48000");
        ce = in("ce");
        out = out("out");
        paramsChanged();
    }

    @Override public String type() { return "Sine"; }
    @Override public String glyph() { return pi("cosine", 0) != 0 ? "cos" : "sin"; }

    @Override public void paramsChanged() {
        out.width = Math.max(1, pi("width", 16));
    }

    @Override public void reset() { n = 0; }

    @Override public void evaluate(long t) {
        double period = Math.max(1e-9, pd("period", 32));
        double a = pd("amplitude", 1000);
        double arg = 2 * Math.PI * n / period + Math.toRadians(pd("phase_deg", 0));
        double v = pi("cosine", 0) != 0 ? Math.cos(arg) : Math.sin(arg);
        out.value = wrap(Math.round(a * v), out.width);
    }

    @Override public void clockEdge(long t) {
        if (ceHigh(ce)) n++;
    }
}
