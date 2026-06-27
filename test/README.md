# DSPFlow tests

Pure Java SE, no JUnit, no Swing. Run everything with:

    ./test.sh

It compiles `src` + `test` (excluding the Swing UI) and runs:

- `dspflow.engine.EngineTest`  — block-level unit asserts (if present on this branch)
- `dspflow.engine.GoldenTest`  — `.dsp` diagram golden-file tests (below)

## Golden tests

A golden test is a `.dsp` diagram run headlessly through `Simulator.run`, with
the Scope sink's per-tick output dumped to CSV and diffed against a checked-in
expected CSV. The CSV format mirrors the UI's `PlotViewer`: a `tick,<port>,...`
header then one row per tick.

Each case is a pair in `test/golden/`:

    <name>.dsp            the diagram
    <name>.expected.csv   the golden output

Rules:

- The diagram must contain **exactly one `Scope` sink**. Its inputs become the
  CSV columns. (Wire whatever you want to measure into the Scope.)
- Tick count comes from a `# TICKS n` comment line in the `.dsp`. It's a `#`
  comment, so the loader ignores it — it only controls the test. Default is
  **32** ticks if the line is absent.
- Use deterministic blocks (Constant, Impulse, Sine, Clock, Delay, Sum, Shift,
  Mult, Decim, Interp). Noise sources are seeded but skip them here.

### Adding a new golden case (the AI-automatable part)

1. Build the diagram in the app (or hand-write the `.dsp`) and save it as
   `test/golden/<name>.dsp`. Add a `# TICKS n` line for the run length and
   wire the signal(s) under test into a single Scope.

2. Snapshot the engine's output as the golden:

       javac -d out-test $(find src test -name '*.java' ! -path '*/ui/*' ! -name 'Main.java')
       java -cp out-test dspflow.engine.GoldenTest gen <name> > test/golden/<name>.expected.csv

3. **Eyeball a couple of values** in the generated CSV to confirm they're what
   you expect — `gen` snapshots whatever the engine does, so a bug would be
   frozen in as "correct" otherwise.

4. Run `./test.sh`. The new case is picked up automatically (every `*.dsp` in
   `test/golden/` is run). A mismatch prints the first differing line and exits
   non-zero.

### Current cases

| case          | what it checks                                                      |
|---------------|---------------------------------------------------------------------|
| `constant`    | Constant(7) reads 7 every tick                                      |
| `impulse`     | pulse train: amplitude 100 at ticks 2 and 6 (delay 2, period 4)     |
| `accumulator` | Const(1)+Delay feedback Sum = ramp 1,2,3,... ; ch2 stays 1          |
