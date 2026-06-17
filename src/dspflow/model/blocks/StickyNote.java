package dspflow.model.blocks;

import dspflow.model.Block;

/**
 * A resizable text annotation block with no ports.
 * Does not participate in simulation.
 */
public class StickyNote extends Block {

    public StickyNote() {
        w = 120;
        h = 80;
        params.put("text", "");
    }

    @Override public String type() { return "Note"; }
    @Override public String glyph() { return ""; }
    @Override public String label() { return ""; }  // no caption below

    @Override public boolean combinational() { return false; }
    @Override public void evaluate(long t) {}  // no-op
    @Override public void reset() {}

    public String text() {
        return params.getOrDefault("text", "");
    }
}
