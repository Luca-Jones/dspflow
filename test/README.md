# Engine tests

Pure-Java assertion harness for the simulation engine. No JUnit, no Maven —
matches the project's zero-dependency `javac` build (`build.sh`).

A single runnable class, `dspflow.engine.EngineTest`, asserts and exits
non-zero on any failure.

## Run

    ./test.sh

or manually:

    mkdir -p out-test
    javac -d out-test $(find src test -name '*.java' ! -path '*/ui/*' ! -name 'Main.java')
    java -cp out-test dspflow.engine.EngineTest

The UI (Swing) and `Main` are excluded — engine tests don't need them.

## Coverage

- `Block.wrap` two's-complement sign extension (overflow, width 0/1/64)
- topological ordering of combinational blocks
- algebraic-loop detection (self-loop and multi-block comb cycle)
- delay-broken feedback loop is legal
- two-phase evaluate/clockEdge sequencing (one-tick register lag, accumulator)
- block behaviors: Sine, Shift, Sum, Mult, Clock + clock-enable gating,
  Decimator, Interpolator, Impulse (one-shot + periodic)
