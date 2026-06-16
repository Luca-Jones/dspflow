# DSPFlow

A small Simulink-style block-diagram editor and simulator for **fixed-point,
multirate DSP**, written in plain Java SE (Swing). No Java dependencies, no build
system — just `javac`.

scope/spectrum windows pop open.

## Build & run

Requires JDK 11 or newer (`javac` on your PATH).

    ./build.sh        # Linux / macOS -- compiles and builds dspflow.jar
    ./run.sh          # launches the GUI
    ./run.sh examples/integrator.dsp   # open a diagram on startup

`build.sh` produces a runnable `dspflow.jar`, so you can also launch with:

    java -jar dspflow.jar [file.dsp]

On Windows use `build.bat` / `run.bat`.

There is no external dependency of any kind; you can also build from an IDE by
importing `src/` as a plain Java project with `dspflow.Main` as the main class.

## Using the editor

- **Place blocks**: click a palette button, then click the canvas. Hold
  **Shift** while clicking to stamp several copies. Press **Esc** or click
  *Select* to return to the selection tool.
- **Wire**: drag from an output port (right edge) to an input port. The `ce`
  port on the bottom edge of Delay/Impulse/Sine accepts a Clock signal.
  Inputs that still need a wire show a red ring.
- **Pan/zoom**: drag empty canvas with the right or middle button (or just
  drag empty space), scroll wheel to zoom. View > Reset view to recenter.
- **Edit**: double-click a block for its properties, or right-click for a
  menu. Double-click a Scope/Spectrum to (re)open its matplotlib plot.
  **Delete** removes
  the selection.
- **Run**: set the tick count in the toolbar and press Run (Ctrl+R).

## Viewing results (matplotlib)

Press **Run** and DSPFlow opens an interactive matplotlib window for every
Scope and Spectrum in the diagram — all at once. A Scope shows all of its
channels overlaid on a single plot; a Spectrum shows FFT magnitude in dB.
These are real matplotlib windows, so you get pan, zoom, and Save built in.

How it works: Java does all the DSP, writes every sink's samples to a
temporary JSON file, then runs the bundled `plot.py` through the system
`python3`. That one Python process owns all the windows and stays up until you
close them; the editor remains usable meanwhile. Nothing is linked into the
JVM, so the jar stays a plain jar. The only requirement is `python3` with
`matplotlib` on your PATH; if it's missing, you get a clear message and the
rest of the app still works. (You can also double-click a sink, or right-click
> Plot results, to re-open the windows after a run.)

- Override the interpreter with `-Ddspflow.python=/path/to/python` if `python3`
  (or `python` on Windows) isn't the one you want:
  `java -Ddspflow.python=python3.12 -jar dspflow.jar`
- The FFT is computed in Java, so `plot.py` only needs matplotlib (no numpy).

## Simulation semantics (read this once)

DSPFlow models a synchronous fixed-point design the way an FPGA would:

- **One base clock.** The simulator advances in *ticks*. Every tick, all
  combinational blocks (Sum, Mult, Shift, Interp) are evaluated in
  topological order, then all registered blocks (Delay, Decim, sources)
  latch on the clock edge.
- **Slower rates are clock enables.** A `Clock` block outputs a 1 once every
  `divide` ticks (with an optional `phase` offset). Wire it into a `ce` input
  and that block only updates on enabled ticks — exactly like a clock-enable
  in RTL. Unconnected `ce` means "always enabled".
- **Signals are signed integers.** Every value is a two's-complement integer
  wrapped to the block's output `width` (hardware-style overflow, no
  saturation). A Sum with width 20 wraps at ±2^19, like real hardware.
- **Feedback is fine — through a register.** Any loop must contain at least
  one Delay (or other registered block). A purely combinational loop is an
  algebraic loop and the simulator refuses to run, naming the blocks
  involved.
- **Scopes sample every base tick**, so a decimated signal appears as a
  staircase — which is exactly what the hardware signal does.

### The classic integrator

`Impulse -> Sum(+,+) -> out`, with `Sum.out -> Delay -> Sum.in2`. The delay
breaks the loop; an impulse of 1024 makes the output step to 1024 and hold.
See `examples/integrator.dsp`.

## Block reference

| Block    | Ports                | Parameters | Notes |
|----------|----------------------|------------|-------|
| Const    | out                  | value, width | DC level |
| Impulse  | ce? / out            | amplitude, delay, period (0 = one-shot), width | counts enabled ticks |
| Sine     | ce? / out            | amplitude, period (ticks), phase_deg, cosine (0/1), width | period counted in *enabled* ticks |
| Clock    | out                  | divide, phase | 1-tick-wide enable pulse, 1 bit |
| Delay    | in, ce? / out        | depth, width | z^-depth shift register |
| Sum      | in1..inN / out       | signs (e.g. `++-`), width | sign string sets port count |
| Mult     | in1, in2 / out       | shift_right, width | full product then >> shift_right |
| Shift    | in / out             | shift (+left/-right), width | arithmetic shift, free power-of-2 gain |
| Decim    | in / out             | factor, phase | registered keep-1-of-M, holds between samples |
| Interp   | in / out             | factor, phase | zero-stuffer (combinational) |
| Scope    | in1..inN             | channels (1-8) | time-domain viewer |
| Spectrum | in                   | fft (16-65536, pow2), hann (0/1) | dB magnitude of last `fft` samples |

`ce?` = optional clock-enable input on the bottom edge.

## File format (`.dsp`)

Plain text, easy to diff and generate:

    DSPFLOW 1
    BLOCK <Type> <id> <x> <y>
    P <key>=<value>          (one per parameter, attaches to last BLOCK)
    WIRE <srcId> <srcPort> <dstId> <dstPort>

See the `examples/` folder: `integrator.dsp`, `multirate.dsp` (sine ->
decimate -> interpolate, with a spectrum showing the imaging),
`clocked_delay.dsp` (Clock driving a Delay's `ce`).

## Extending with your own blocks

1. Subclass `dspflow.model.Block` (look at `ShiftBlock` for a minimal
   combinational block, `DelayBlock` for a registered one). Put parameters in
   the `params` map in the constructor; the property dialog is generated
   automatically.
   - Combinational: do the work in `evaluate(t)`, leave `clockEdge` empty.
   - Registered: output *state* in `evaluate(t)`, update state in
     `clockEdge(t)` (gate on `ceHigh()` if you add a `ce` port).
   - Always wrap outputs with `wrap(value, width)`.
2. Register it in `BlockLibrary` (add to `TYPES` and the `create()` switch).

That's all — it appears in the palette, saves/loads, and simulates.

## Notes

- Memory: a Scope stores `channels × ticks` longs; 10M ticks × 2 channels is
  ~160 MB. The Run field caps at 50M ticks.
- The engine is headless-friendly: `Diagram.load(file)` +
  `Simulator.run(diagram, ticks)` work without any GUI, handy for unit tests.
