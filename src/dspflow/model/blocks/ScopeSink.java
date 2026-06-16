package dspflow.model.blocks;

import java.util.ArrayList;
import dspflow.model.Block;

/**
 * Time-domain scope. Records every input channel on every base tick
 * (so signals held by slow clock enables show as staircases - useful
 * when eyeballing multirate behavior). Double-click the block to open
 * its viewer window.
 */
public class ScopeSink extends Block {
    /** One row per tick; row[i] is channel i. Read by the viewer. */
    public final ArrayList<long[]> data = new ArrayList<>();

    public ScopeSink() {
        params.put("channels", "2");
        paramsChanged();
    }

    @Override public String type() { return "Scope"; }
    @Override public String glyph() { return "\u223F"; }

    @Override public void paramsChanged() {
        int n = Math.max(1, Math.min(8, pi("channels", 2)));
        setInputs(n, "in");
        h = Math.max(60, n * 22 + 16);
    }

    @Override public void reset() { data.clear(); }

    @Override public void evaluate(long t) {}

    @Override public void clockEdge(long t) {
        long[] row = new long[inputs.size()];
        for (int i = 0; i < row.length; i++) row[i] = inVal(i);
        data.add(row);
    }
}
