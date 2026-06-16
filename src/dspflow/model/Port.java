package dspflow.model;

/**
 * A connection point on a block. Output ports carry a driven value and a bus
 * width; input ports are resolved to a driving output port ("driver") before
 * simulation by Diagram.bindDrivers().
 */
public class Port {
    public final Block block;
    public String name;
    public final boolean input;
    /** Bus width in bits (meaningful for output ports). */
    public int width = 16;
    /** Current driven value (meaningful for output ports). */
    public long value;
    /** Resolved driving output port (meaningful for input ports). */
    public Port driver;

    public Port(Block block, String name, boolean input) {
        this.block = block;
        this.name = name;
        this.input = input;
    }

    /** Clock-enable ports render on the bottom edge and may be left unconnected. */
    public boolean isCE() {
        return input && name.equals("ce");
    }

    @Override
    public String toString() {
        return block.type() + "#" + block.id + "." + name;
    }
}
