package dspflow.model.blocks;

import dspflow.model.Block;
import dspflow.model.Port;

/**
 * Combinational add/subtract junction. The `signs` parameter is a string
 * of '+' and '-' characters, one per input (e.g. "+-" or "+++").
 * The result wraps to the output width, like a hardware adder.
 */
public class SumBlock extends Block {
    private final Port out;

    public SumBlock() {
        params.put("signs", "++");
        params.put("width", "16");
        out = out("out");
        paramsChanged();
    }

    @Override public String type() { return "Sum"; }
    @Override public String glyph() { return "\u03A3"; }
    @Override public boolean combinational() { return true; }

    private String signs() {
        String s = ps("signs", "++").replaceAll("[^+\\-]", "");
        return s.length() < 2 ? "++" : s;
    }

    @Override public void paramsChanged() {
        String s = signs();
        setInputs(s.length(), "in");
        h = Math.max(60, s.length() * 22 + 16);
        out.width = Math.max(1, pi("width", 16));
    }

    @Override public void evaluate(long t) {
        String s = signs();
        long acc = 0;
        for (int i = 0; i < inputs.size(); i++)
            acc += s.charAt(i) == '-' ? -inVal(i) : inVal(i);
        out.value = wrap(acc, out.width);
    }
}
