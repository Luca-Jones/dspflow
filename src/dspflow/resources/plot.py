#!/usr/bin/env python3
"""DSPFlow figure viewer.

Reads one JSON job describing every sink in the diagram and opens an
interactive matplotlib window per sink, then blocks in plt.show() until the
user closes them. Java does all the DSP; this script only plots.

Dependency-light: matplotlib only, no numpy.

Usage:  python3 plot.py <job.json>

Job format:
  {"figures":[
     {"kind":"scope","title":"Scope 4","xlabel":"tick","ylabel":"value",
      "series":[{"name":"in1","y":[...]}, ...]},
     {"kind":"spectrum","title":"Spectrum 5","xlabel":"normalized frequency",
      "ylabel":"magnitude (dB)","x":[...],"y":[...]}
  ]}
"""
import json
import sys


def main():
    if len(sys.argv) != 2:
        sys.stderr.write("usage: plot.py <job.json>\n")
        return 2

    with open(sys.argv[1], "r") as fh:
        job = json.load(fh)

    import matplotlib.pyplot as plt

    figures = job.get("figures", [])
    if not figures:
        sys.stderr.write("no figures in job\n")
        return 1

    for spec in figures:
        kind = spec.get("kind", "scope")
        fig, ax = plt.subplots(figsize=(10, 5))
        fig.canvas.manager.set_window_title(spec.get("title", "DSPFlow"))

        if kind == "spectrum":
            x, y = spec["x"], spec["y"]
            ax.plot(x, y, linewidth=1.0)
            ax.set_xlim(0, x[-1] if x else 0.5)
        else:  # scope: overlay every channel on one axes
            for s in spec.get("series", []):
                y = s["y"]
                # steps-post mirrors the zero-order hold of the hardware
                # signal, so slow (clock-enabled) channels read as staircases.
                ax.plot(range(len(y)), y, drawstyle="steps-post",
                        linewidth=1.0, label=s.get("name", ""))
            if len(spec.get("series", [])) > 1:
                ax.legend(loc="upper right", fontsize="small")
            ax.axhline(0, color="black", linewidth=0.5, alpha=0.5)

        ax.set_title(spec.get("title", "DSPFlow"))
        ax.set_xlabel(spec.get("xlabel", ""))
        ax.set_ylabel(spec.get("ylabel", ""))
        ax.grid(True, alpha=0.3)
        fig.tight_layout()

    plt.show()  # opens all figures at once, blocks until closed
    return 0


if __name__ == "__main__":
    sys.exit(main())
