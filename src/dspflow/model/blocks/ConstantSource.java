package dspflow.model.blocks;

import dspflow.model.Block;
import dspflow.model.Port;

public class ConstantSource extends Block {
    private final Port out;

    public ConstantSource() {
        params.put("value", "1024");
        params.put("width", "16");
        out = out("out");
        paramsChanged();
    }

    @Override public String type() { return "Constant"; }
    @Override public String glyph() { return ps("value", "?"); }

    @Override public void paramsChanged() {
        out.width = Math.max(1, pi("width", 16));
    }

    @Override public void evaluate(long t) {
        out.value = wrap(pl("value", 0), out.width);
    }
}
