package dspflow.engine;

import java.util.*;
import dspflow.engine.Simulator.SimException;
import dspflow.model.*;
import dspflow.model.blocks.*;

/**
 * Zero-dependency self-test harness for the DSPFlow simulation engine.
 * Pure Java SE: a runnable main that asserts. Matches the project's
 * no-Maven/no-JUnit build (compiled by build.sh-style javac).
 *
 * Run:  see test/README.md
 */
public class EngineTest {

    // ---- tiny assertion kit --------------------------------------------
    static int passed = 0;
    static final List<String> failures = new ArrayList<>();

    static void check(boolean cond, String what) {
        if (cond) { passed++; }
        else failures.add(what);
    }

    static void eq(long got, long want, String what) {
        check(got == want, what + " (got " + got + ", want " + want + ")");
    }

    static void expectSim(Runnable r, String contains, String what) {
        try { r.run(); failures.add(what + " (expected SimException, none thrown)"); }
        catch (RuntimeException e) {
            Throwable c = e.getCause() != null ? e.getCause() : e;
            check(c instanceof SimException && c.getMessage().contains(contains),
                  what + " (got: " + c.getClass().getSimpleName() + " " + c.getMessage() + ")");
        }
    }

    // wrap run() so it can be used in a lambda
    static void run(Diagram d, long ticks) {
        try { Simulator.run(d, ticks); }
        catch (SimException e) { throw new RuntimeException(e); }
    }

    // ---- helpers -------------------------------------------------------
    static Block add(Diagram d, Block b) { d.add(b); return b; }
    static void wire(Diagram d, Block s, String so, Block dd, String di) {
        Port src = d.findPort(s, so, false);
        Port dst = d.findPort(dd, di, true);
        if (src == null || dst == null) throw new IllegalStateException("bad wire " + so + "->" + di);
        d.connect(src, dst);
    }
    static long outVal(Block b) { return b.outputs.get(0).value; }

    // ===================================================================
    public static void main(String[] args) {
        testWrap();
        testTopoOrder();
        testSelfAlgebraicLoop();
        testFeedbackLoopAlgebraic();
        testFeedbackLoopWithDelayOk();
        testTwoPhaseDelayRegister();
        testSineSource();
        testShiftBlock();
        testSumBlock();
        testMultBlock();
        testClockSourceAndCE();
        testDecimator();
        testInterpolator();
        testImpulse();
        testSignExtend();
        testGaussianNoise();
        testWhiteNoise();
        testPinkNoise();

        System.out.println("\n" + passed + " checks passed, " + failures.size() + " failed.");
        for (String f : failures) System.out.println("  FAIL: " + f);
        if (!failures.isEmpty()) System.exit(1);
        System.out.println("OK");
    }

    // ---- Block.wrap (two's-complement sign extension) ------------------
    static void testWrap() {
        eq(Block.wrap(0xFFFF, 16), -1, "wrap 0xFFFF/16 -> -1");
        eq(Block.wrap(0x8000, 16), -32768, "wrap 0x8000/16 -> -32768");
        eq(Block.wrap(0x7FFF, 16), 32767, "wrap max positive 16");
        eq(Block.wrap(32768, 16), -32768, "wrap overflow 16");
        eq(Block.wrap(5, 4), 5, "wrap 5/4 in range");
        eq(Block.wrap(8, 4), -8, "wrap 8/4 -> -8");
        eq(Block.wrap(-1, 4), -1, "wrap -1/4 -> -1");
        eq(Block.wrap(0, 0), 0, "wrap width 0 -> 0");
        eq(Block.wrap(Long.MAX_VALUE, 64), Long.MAX_VALUE, "wrap width>=64 passthrough");
        eq(Block.wrap(1, 1), -1, "wrap 1/1 -> -1 (sign bit)");
    }

    // ---- topological ordering of combinational blocks ------------------
    // Chain: Const -> Shift -> Shift -> Sum.  Each comb block must appear
    // after its driver in evalOrder.
    static void testTopoOrder() {
        Diagram d = new Diagram();
        Block c = add(d, new ConstantSource());
        Block s1 = add(d, new ShiftBlock());
        Block s2 = add(d, new ShiftBlock());
        Block sum = add(d, new SumBlock());
        wire(d, c, "out", s1, "in");
        wire(d, s1, "out", s2, "in");
        wire(d, s2, "out", sum, "in1");
        wire(d, c, "out", sum, "in2");
        d.bindDrivers();
        try {
            List<Block> order = Simulator.evalOrder(d);
            int iS1 = order.indexOf(s1), iS2 = order.indexOf(s2), iSum = order.indexOf(sum);
            check(iS1 >= 0 && iS2 >= 0 && iSum >= 0, "topo: all comb blocks present");
            check(iS1 < iS2, "topo: s1 before s2");
            check(iS2 < iSum, "topo: s2 before sum");
            // Const is sequential-side (non-combinational) so it sits in the
            // registered prefix; comb blocks come after.
        } catch (SimException e) { failures.add("topo threw: " + e.getMessage()); }
    }

    // ---- a combinational block wired to itself = algebraic loop --------
    static void testSelfAlgebraicLoop() {
        Diagram d = new Diagram();
        Block sum = add(d, new SumBlock());
        wire(d, sum, "out", sum, "in1");
        d.bindDrivers();
        expectSim(() -> { try { Simulator.evalOrder(d); }
                          catch (SimException e) { throw new RuntimeException(e); } },
                  "Algebraic loop", "self-loop detected");
    }

    // ---- comb -> comb -> comb cycle (no register) = algebraic loop -----
    static void testFeedbackLoopAlgebraic() {
        Diagram d = new Diagram();
        Block s1 = add(d, new ShiftBlock());
        Block s2 = add(d, new SumBlock());
        wire(d, s1, "out", s2, "in1");
        wire(d, s2, "out", s1, "in"); // feedback, no delay
        d.bindDrivers();
        expectSim(() -> run(d, 1), "Algebraic loop", "comb feedback cycle detected");
    }

    // ---- same loop but with a Delay in it = legal, breaks the cycle ----
    static void testFeedbackLoopWithDelayOk() {
        Diagram d = new Diagram();
        Block sum = add(d, new SumBlock());
        Block del = add(d, new DelayBlock());
        wire(d, sum, "out", del, "in");
        wire(d, del, "out", sum, "in1"); // feedback through register: OK
        // in2 floats (0)
        d.bindDrivers();
        try {
            Simulator.evalOrder(d); // must not throw
            check(true, "delay-broken loop has valid eval order");
        } catch (SimException e) { failures.add("delay loop threw: " + e.getMessage()); }
        run(d, 5); // must run without error
        check(true, "delay loop simulates");
    }

    // ---- two-phase: Delay outputs OLD state during evaluate, latches new
    // in clockEdge. An accumulator (Sum += Delay, Delay <- Sum) must lag
    // by exactly one tick.
    static void testTwoPhaseDelayRegister() {
        // Const(5) + Delay(out) -> Delay(in). Accumulator: 0,5,10,15,...
        Diagram d = new Diagram();
        Block c = add(d, new ConstantSource());
        c.params.put("value", "5"); c.paramsChanged();
        Block sum = add(d, new SumBlock());
        Block del = add(d, new DelayBlock());
        wire(d, c, "out", sum, "in1");
        wire(d, del, "out", sum, "in2");
        wire(d, sum, "out", del, "in");
        d.bindDrivers();
        long[] seen = new long[6];
        try {
            List<Block> order = Simulator.evalOrder(d);
            for (Block b : d.blocks) b.reset();
            for (long t = 0; t < 6; t++) {
                for (Block b : order) b.evaluate(t);
                seen[(int) t] = outVal(sum);
                for (Block b : d.blocks) b.clockEdge(t);
            }
        } catch (SimException e) { failures.add("accumulator threw: " + e.getMessage()); }
        // tick0: delay=0 -> sum=5 ; tick1: delay=5 -> sum=10 ; ...
        eq(seen[0], 5, "accum t0");
        eq(seen[1], 10, "accum t1");
        eq(seen[2], 15, "accum t2");
        eq(seen[5], 30, "accum t5");
    }

    // ---- SineSource: counter advances in clockEdge (sampled in evaluate) -
    static void testSineSource() {
        SineSource s = new SineSource();
        s.params.put("amplitude", "1000");
        s.params.put("period", "4"); // n=0,1,2,3 -> sin(0,pi/2,pi,3pi/2)
        s.params.put("phase_deg", "0");
        s.paramsChanged();
        s.reset();
        long[] v = new long[4];
        for (long t = 0; t < 4; t++) { s.evaluate(t); v[(int) t] = s.outputs.get(0).value; s.clockEdge(t); }
        eq(v[0], 0, "sine n=0 -> 0");
        eq(v[1], 1000, "sine n=1 -> +A");
        eq(v[2], 0, "sine n=2 -> 0");
        eq(v[3], -1000, "sine n=3 -> -A");
        // cosine variant
        SineSource cs = new SineSource();
        cs.params.put("amplitude", "1000"); cs.params.put("period", "4");
        cs.params.put("cosine", "1"); cs.paramsChanged(); cs.reset();
        cs.evaluate(0); eq(cs.outputs.get(0).value, 1000, "cosine n=0 -> +A");
    }

    // ---- ShiftBlock: arithmetic left/right shift + wrap ----------------
    static void testShiftBlock() {
        eq(shiftOf(3, 2, 16), 12, "shift left 3<<2");
        eq(shiftOf(-8, -1, 16), -4, "shift right arithmetic -8>>1");
        eq(shiftOf(7, -1, 16), 3, "shift right 7>>1");
        // overflow wraps: 0x4000 << 1 = 0x8000 -> -32768 in 16-bit
        eq(shiftOf(0x4000, 1, 16), -32768, "shift left overflow wraps");
    }
    static long shiftOf(long in, int shift, int width) {
        ShiftBlock b = new ShiftBlock();
        b.params.put("shift", "" + shift); b.params.put("width", "" + width); b.paramsChanged();
        Block drv = new ConstantSource(); drv.params.put("value", "" + in); drv.params.put("width", "32"); drv.paramsChanged();
        drv.id = 1; b.id = 2;
        // hand-wire driver into input 0
        b.inputs.get(0).driver = drv.outputs.get(0);
        drv.evaluate(0); b.evaluate(0);
        return b.outputs.get(0).value;
    }

    // ---- SumBlock: signs, variable inputs, wrap ------------------------
    static void testSumBlock() {
        SumBlock b = new SumBlock();
        b.params.put("signs", "+-"); b.paramsChanged();
        check(b.inputs.size() == 2, "sum +- has 2 inputs");
        drive(b, 0, 100); drive(b, 1, 30);
        b.evaluate(0);
        eq(b.outputs.get(0).value, 70, "sum 100 - 30 = 70");

        SumBlock b3 = new SumBlock();
        b3.params.put("signs", "+++"); b3.paramsChanged();
        check(b3.inputs.size() == 3, "sum +++ has 3 inputs");
        drive(b3, 0, 10); drive(b3, 1, 20); drive(b3, 2, 30);
        b3.evaluate(0);
        eq(b3.outputs.get(0).value, 60, "sum 10+20+30 = 60");
    }

    // ---- MultBlock: product, shift_right, wrap -------------------------
    static void testMultBlock() {
        MultBlock b = new MultBlock();
        b.params.put("width", "32"); b.params.put("shift_right", "0"); b.paramsChanged();
        drive(b, 0, 7); drive(b, 1, 6);
        b.evaluate(0);
        eq(b.outputs.get(0).value, 42, "mult 7*6 = 42");

        MultBlock bs = new MultBlock();
        bs.params.put("width", "32"); bs.params.put("shift_right", "4"); bs.paramsChanged();
        drive(bs, 0, 256); drive(bs, 1, 256); // 65536 >> 4 = 4096
        bs.evaluate(0);
        eq(bs.outputs.get(0).value, 4096, "mult 256*256>>4 = 4096");
    }

    // ---- ClockSource divides; CE gates a sequential source -------------
    static void testClockSourceAndCE() {
        ClockSource ck = new ClockSource();
        ck.params.put("divide", "3"); ck.params.put("phase", "0"); ck.paramsChanged();
        long[] e = new long[6];
        for (long t = 0; t < 6; t++) { ck.evaluate(t); e[(int) t] = ck.outputs.get(0).value; }
        long[] want = {1, 0, 0, 1, 0, 0};
        for (int i = 0; i < 6; i++) eq(e[i], want[i], "clock /3 tick " + i);

        // Impulse with CE wired to a /2 clock: counter only advances on enable
        Diagram d = new Diagram();
        Block clk = add(d, new ClockSource()); clk.params.put("divide", "2"); clk.paramsChanged();
        Block imp = add(d, new ImpulseSource()); imp.params.put("delay", "1"); imp.params.put("amplitude", "100"); imp.paramsChanged();
        wire(d, clk, "ce", imp, "ce");
        d.bindDrivers();
        // n advances only when ce high (ticks 0,2,4,...). Impulse fires at n==1.
        // n: t0->eval n=0, ce@t0=1 so clockEdge n->1; t1 ce=0 n stays 1 (FIRE); ...
        long[] out = new long[6];
        try {
            List<Block> order = Simulator.evalOrder(d);
            for (Block bk : d.blocks) bk.reset();
            for (long t = 0; t < 6; t++) {
                for (Block bk : order) bk.evaluate(t);
                out[(int) t] = imp.outputs.get(0).value;
                for (Block bk : d.blocks) bk.clockEdge(t);
            }
        } catch (SimException ex) { failures.add("ce impulse threw: " + ex.getMessage()); }
        eq(out[0], 0, "gated impulse t0 n=0");
        eq(out[1], 100, "gated impulse t1 n=1 fires (held)");
        eq(out[2], 100, "gated impulse t2 n still 1 (ce low t1)");
    }

    // ---- Decimator: registered sample-and-hold every `factor` ----------
    static void testDecimator() {
        // Drive with a ramp via Sine? simpler: Impulse train. Use Const that we
        // change is awkward; use a counter built from Delay+Sum accumulator.
        // Easiest: drive decim from a per-tick-known source = ClockSource? value 0/1.
        // Use an accumulator to make a ramp 0,1,2,... feeding the decimator.
        Diagram d = new Diagram();
        Block one = add(d, new ConstantSource()); one.params.put("value", "1"); one.paramsChanged();
        Block sum = add(d, new SumBlock());
        Block del = add(d, new DelayBlock());
        Block dec = add(d, new DecimatorBlock());
        dec.params.put("factor", "2"); dec.params.put("phase", "0"); dec.paramsChanged();
        wire(d, one, "out", sum, "in1");
        wire(d, del, "out", sum, "in2");
        wire(d, sum, "out", del, "in");   // accumulator: del = 0,1,2,3,... at evaluate
        wire(d, del, "out", dec, "in");   // decimator samples the delay output
        d.bindDrivers();
        long[] dout = new long[6];
        long[] ramp = new long[6];
        try {
            List<Block> order = Simulator.evalOrder(d);
            for (Block b : d.blocks) b.reset();
            for (long t = 0; t < 6; t++) {
                for (Block b : order) b.evaluate(t);
                ramp[(int) t] = del.outputs.get(0).value;
                dout[(int) t] = dec.outputs.get(0).value;
                for (Block b : d.blocks) b.clockEdge(t);
            }
        } catch (SimException e) { failures.add("decim threw: " + e.getMessage()); }
        // del output (evaluate phase): 0,1,2,3,4,5
        eq(ramp[0], 0, "ramp t0"); eq(ramp[3], 3, "ramp t3");
        // decimator captures input in clockEdge when cnt%2==0 (cnt=0,2,4...),
        // and outputs held value at evaluate (so one tick later it shows up).
        // cnt0(t0): held<-del(0)=0 ; cnt1(t1): hold ; cnt2(t2): held<-del(2)=2 ...
        // dec evaluate at t reads held BEFORE that tick's clockEdge:
        //   t0 held=0, t1 held=0, t2 held=0, t3 held=2, t4 held=2, t5 held=4
        eq(dout[0], 0, "decim t0"); eq(dout[1], 0, "decim t1");
        eq(dout[2], 0, "decim t2"); eq(dout[3], 2, "decim t3");
        eq(dout[5], 4, "decim t5");
    }

    // ---- Interpolator: passes input on phase tick, 0 otherwise ---------
    static void testInterpolator() {
        InterpolatorBlock b = new InterpolatorBlock();
        b.params.put("factor", "3"); b.params.put("phase", "0"); b.paramsChanged();
        drive(b, 0, 77);
        long[] out = new long[6];
        for (long t = 0; t < 6; t++) { b.evaluate(t); out[(int) t] = b.outputs.get(0).value; }
        long[] want = {77, 0, 0, 77, 0, 0};
        for (int i = 0; i < 6; i++) eq(out[i], want[i], "interp /3 tick " + i);
    }

    // ---- Impulse one-shot and periodic ---------------------------------
    static void testImpulse() {
        ImpulseSource one = new ImpulseSource();
        one.params.put("amplitude", "50"); one.params.put("delay", "2"); one.params.put("period", "0");
        one.paramsChanged(); one.reset();
        long[] v = new long[5];
        for (long t = 0; t < 5; t++) { one.evaluate(t); v[(int) t] = one.outputs.get(0).value; one.clockEdge(t); }
        long[] want = {0, 0, 50, 0, 0};
        for (int i = 0; i < 5; i++) eq(v[i], want[i], "impulse one-shot tick " + i);

        ImpulseSource per = new ImpulseSource();
        per.params.put("amplitude", "9"); per.params.put("delay", "1"); per.params.put("period", "3");
        per.paramsChanged(); per.reset();
        long[] p = new long[7];
        for (long t = 0; t < 7; t++) { per.evaluate(t); p[(int) t] = per.outputs.get(0).value; per.clockEdge(t); }
        long[] wantP = {0, 9, 0, 0, 9, 0, 0};
        for (int i = 0; i < 7; i++) eq(p[i], wantP[i], "impulse period tick " + i);
    }

    // ---- SignExtendBlock: signed/zero extend + truncate ----------------
    static void testSignExtend() {
        // 0xFF read as 8-bit signed = -1, extended to 16 bits stays -1.
        eq(signExt(0xFF, 8, 16, true), -1, "signext 0xFF/8->16 signed = -1");
        // same byte, zero-extended = 255.
        eq(signExt(0xFF, 8, 16, false), 255, "signext 0xFF/8->16 unsigned = 255");
        // positive value passes through unchanged.
        eq(signExt(0x40, 8, 16, true), 64, "signext 0x40/8->16 = 64");
        // narrowing wraps: 0x1FF into 8 bits signed = -1.
        eq(signExt(0x1FF, 16, 8, true), -1, "signext narrow 0x1FF->8 = -1");
    }
    static long signExt(long in, int from, int width, boolean signed) {
        SignExtendBlock b = new SignExtendBlock();
        b.params.put("from", "" + from); b.params.put("width", "" + width);
        b.params.put("signed", signed ? "1" : "0"); b.paramsChanged();
        drive(b, 0, in);
        b.evaluate(0);
        return b.outputs.get(0).value;
    }

    // ---- GaussianNoise: reproducible + mean/stdev within tolerance -----
    static void testGaussianNoise() {
        GaussianNoiseSource g = new GaussianNoiseSource();
        g.params.put("mean", "0"); g.params.put("stdev", "1000");
        g.params.put("seed", "42"); g.params.put("width", "32"); g.paramsChanged();
        int N = 20000;
        double[] s = sampleStats(g, N);
        // sample mean should be near 0 (a few sigma/sqrt(N) ~ a few * 7)
        check(Math.abs(s[0]) < 60, "gauss mean ~0 (got " + s[0] + ")");
        // sample stdev should be near 1000 (within ~3%)
        check(Math.abs(s[1] - 1000) < 30, "gauss stdev ~1000 (got " + s[1] + ")");

        // reproducibility: same seed -> identical first sample
        GaussianNoiseSource a = new GaussianNoiseSource(); a.params.put("seed", "7"); a.params.put("width", "32"); a.paramsChanged(); a.reset();
        GaussianNoiseSource b = new GaussianNoiseSource(); b.params.put("seed", "7"); b.params.put("width", "32"); b.paramsChanged(); b.reset();
        a.evaluate(0); b.evaluate(0);
        eq(a.outputs.get(0).value, b.outputs.get(0).value, "gauss same seed reproducible");
    }

    // ---- WhiteNoise: uniform range + reproducible ----------------------
    static void testWhiteNoise() {
        WhiteNoiseSource w = new WhiteNoiseSource();
        w.params.put("amplitude", "1000"); w.params.put("seed", "3"); w.params.put("width", "32"); w.paramsChanged();
        w.reset();
        long max = 0;
        for (long t = 0; t < 5000; t++) { w.evaluate(t); max = Math.max(max, Math.abs(w.outputs.get(0).value)); }
        check(max <= 1000, "white stays within +/-amplitude (max " + max + ")");
        check(max > 800, "white spans most of its range (max " + max + ")");
        // uniform mean ~0
        WhiteNoiseSource w2 = new WhiteNoiseSource();
        w2.params.put("amplitude", "1000"); w2.params.put("seed", "3"); w2.params.put("width", "32"); w2.paramsChanged();
        double[] s = sampleStats(w2, 20000);
        check(Math.abs(s[0]) < 30, "white mean ~0 (got " + s[0] + ")");
    }

    // ---- PinkNoise: produces nonzero, bounded, reproducible output -----
    static void testPinkNoise() {
        PinkNoiseSource p = new PinkNoiseSource();
        p.params.put("amplitude", "1000"); p.params.put("seed", "5"); p.params.put("width", "32"); p.paramsChanged();
        p.reset();
        boolean anyNonZero = false;
        for (long t = 0; t < 2000; t++) { p.evaluate(t); if (p.outputs.get(0).value != 0) anyNonZero = true; }
        check(anyNonZero, "pink produces nonzero output");
        // reproducibility
        PinkNoiseSource a = new PinkNoiseSource(); a.params.put("seed", "9"); a.params.put("width", "32"); a.paramsChanged(); a.reset();
        PinkNoiseSource b = new PinkNoiseSource(); b.params.put("seed", "9"); b.params.put("width", "32"); b.paramsChanged(); b.reset();
        long va = 0, vb = 0;
        for (long t = 0; t < 10; t++) { a.evaluate(t); b.evaluate(t); va = a.outputs.get(0).value; vb = b.outputs.get(0).value; }
        eq(va, vb, "pink same seed reproducible");
    }

    // mean and (population) stdev of N evaluated samples (CE unconnected = high)
    static double[] sampleStats(Block src, int n) {
        src.reset();
        double sum = 0, sumSq = 0;
        for (long t = 0; t < n; t++) {
            src.evaluate(t);
            double v = src.outputs.get(0).value;
            sum += v; sumSq += v * v;
        }
        double mean = sum / n;
        double var = sumSq / n - mean * mean;
        return new double[] { mean, Math.sqrt(var) };
    }

    // hand-wire a constant driver onto input i of a combinational block
    static void drive(Block b, int i, long value) {
        ConstantSource c = new ConstantSource();
        c.params.put("value", "" + value); c.params.put("width", "32"); c.paramsChanged();
        c.evaluate(0);
        b.inputs.get(i).driver = c.outputs.get(0);
    }
}
