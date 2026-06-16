package dspflow.model.blocks;

import dspflow.model.Block;
import dspflow.model.Port;

/**
 * Clock-enable generator: outputs 1 for one base tick out of every
 * `divide` ticks (at tick % divide == phase), 0 otherwise. Wire it into
 * the `ce` input of a Delay or source to put that block in a slower
 * clock domain - the FPGA "single clock + clock enables" style.
 */
public class ClockSource extends Block {
    private final Port out;

    public ClockSource() {
        params.put("divide", "2");
        params.put("phase", "0");
        out = out("ce");
        out.width = 1;
        paramsChanged();
    }

    @Override public String type() { return "Clock"; }
    @Override public String glyph() { return "/" + Math.max(1, pi("divide", 2)); }

    @Override public void evaluate(long t) {
        long div = Math.max(1, pl("divide", 2));
        long ph = ((pl("phase", 0) % div) + div) % div;
        out.value = (t % div == ph) ? 1 : 0;
    }
}
