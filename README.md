# DSPFlow

A small Simulink-style block-diagram editor and simulator for **fixed-point,
multirate DSP**, written in plain Java SE (Swing).

## Dependencies

- **JDK 11+** (`javac` on PATH)
- **Python 3 with matplotlib** (for plotting)

### Option 1: Global install

```bash
pip install matplotlib
```

### Option 2: Virtual environment

```bash
python3 -m venv .venv
source .venv/bin/activate   # Windows: .venv\Scripts\activate
pip install matplotlib
```

Run the app while venv is activated.

## Build

Use the provided build script to compile the java source files:

```bash
./build.sh
```

The resulting jar file can be run with:

```bash
java -jar dspflow.jar
```

Or with a diagram file:
```bash
java -jar dspflow.jar examples/integrator.dsp
```
