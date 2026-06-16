package dspflow.model.blocks;

import java.util.Arrays;
import dspflow.model.Block;
import dspflow.model.Port;

/**
 * A z^-depth register chain - a variable-width flip-flop bank with a
 * clock-enable input. On each tick where `ce` is high (or unconnected),
 * the chain shifts and captures the input. The output is registered, so
 * a Delay legally breaks any feedback loop.
 */
public class DelayBlock extends Block {
    private final Port din;
    private final Port ce;
    private final Port q;
    private long[] reg = new long[1];

    public DelayBlock() {
        params.put("width", "16");
        params.put("depth", "1");
        din = in("in");
        ce = in("ce");
        q = out("out");
        paramsChanged();
    }

    @Override public String type() { return "Delay"; }
    @Override public String glyph() { return "z\u207B" + sup(Math.max(1, pi("depth", 1))); }

    private static String sup(int v) {
        String d = Integer.toString(v), s = "";
        String sups = "\u2070\u00B9\u00B2\u00B3\u2074\u2075\u2076\u2077\u2078\u2079";
        for (char c : d.toCharArray()) s += sups.charAt(c - '0');
        return s;
    }

    @Override public void paramsChanged() {
        int depth = Math.max(1, pi("depth", 1));
        if (reg.length != depth) reg = new long[depth];
        q.width = Math.max(1, pi("width", 16));
    }

    @Override public void reset() { Arrays.fill(reg, 0); }

    @Override public void evaluate(long t) {
        q.value = wrap(reg[reg.length - 1], q.width);
    }

    @Override public void clockEdge(long t) {
        if (!ceHigh(ce)) return;
        for (int i = reg.length - 1; i > 0; i--) reg[i] = reg[i - 1];
        reg[0] = wrap(inVal(0), q.width);
    }
}
