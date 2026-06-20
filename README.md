# DSPFlow

A small Simulink-style block-diagram editor and simulator for **fixed-point,
multirate DSP**. The gui and simulation enginer are written in Java and 
the plots are generated with the matplotlib Python library.

## Dependencies

- **JDK 11+**
- **Python 3 with matplotlib and numpy**

### Option 1: Global install

```bash
pip install matplotlib numpy
```

### Option 2: Virtual environment

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install matplotlib numpy
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
