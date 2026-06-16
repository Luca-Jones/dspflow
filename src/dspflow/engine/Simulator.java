package dspflow.engine;

import java.util.*;
import dspflow.model.Block;
import dspflow.model.Diagram;
import dspflow.model.Port;
import dspflow.model.Wire;

/**
 * Two-phase, single-base-tick simulator.
 *
 * Per tick:
 *   1. evaluate(t) on every block. Registered blocks (Delay, Decim, sources)
 *      output their state and are evaluated first in any order; combinational
 *      blocks (Sum, Mult, Shift, Interp) are evaluated in topological order
 *      of the combinational dependency graph.
 *   2. clockEdge(t) on every block: registers latch, sinks record.
 *
 * Feedback is legal as long as every loop passes through at least one
 * registered block; a purely combinational loop is reported as an
 * algebraic loop error.
 */
public class Simulator {

    public static class SimException extends Exception {
        public SimException(String msg) { super(msg); }
    }

    /** Reset everything and run for the given number of base ticks. */
    public static void run(Diagram d, long ticks) throws SimException {
        d.bindDrivers();
        List<Block> order = evalOrder(d);
        for (Block b : d.blocks) b.reset();
        List<Block> all = d.blocks;
        for (long t = 0; t < ticks; t++) {
            for (Block b : order) b.evaluate(t);
            for (Block b : all) b.clockEdge(t);
        }
    }

    /** Registered blocks first, then combinational blocks topologically sorted. */
    static List<Block> evalOrder(Diagram d) throws SimException {
        List<Block> order = new ArrayList<>();
        List<Block> comb = new ArrayList<>();
        for (Block b : d.blocks)
            (b.combinational() ? comb : order).add(b);

        // adjacency among combinational blocks
        Map<Block, List<Block>> adj = new HashMap<>();
        Map<Block, Integer> indeg = new HashMap<>();
        for (Block b : comb) { adj.put(b, new ArrayList<>()); indeg.put(b, 0); }
        for (Wire w : d.wires) {
            Block s = w.src.block, t = w.dst.block;
            if (s.combinational() && t.combinational() && s != t) {
                adj.get(s).add(t);
                indeg.merge(t, 1, Integer::sum);
            } else if (s == t && s.combinational()) {
                throw new SimException("Algebraic loop on " + s.label()
                        + " - insert a Delay in the loop.");
            }
        }

        Deque<Block> q = new ArrayDeque<>();
        for (Block b : comb) if (indeg.get(b) == 0) q.add(b);
        int done = 0;
        while (!q.isEmpty()) {
            Block b = q.poll();
            order.add(b);
            done++;
            for (Block n : adj.get(b))
                if (indeg.merge(n, -1, Integer::sum) == 0) q.add(n);
        }
        if (done < comb.size()) {
            StringBuilder sb = new StringBuilder();
            for (Block b : comb)
                if (indeg.get(b) > 0) sb.append(sb.length() > 0 ? ", " : "").append(b.label());
            throw new SimException("Algebraic loop detected (no register in feedback path).\n"
                    + "Insert a Delay block in the loop. Blocks involved: " + sb);
        }
        return order;
    }

    /** For decorating the canvas: input ports left unconnected (excluding CE). */
    public static List<Port> floatingInputs(Diagram d) {
        d.bindDrivers();
        List<Port> r = new ArrayList<>();
        for (Block b : d.blocks)
            for (Port p : b.inputs)
                if (p.driver == null && !p.isCE()) r.add(p);
        return r;
    }
}
