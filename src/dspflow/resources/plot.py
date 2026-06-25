#!/usr/bin/env python3
"""DSPFlow figure viewer.

Reads one JSON job describing every sink in the diagram and opens an
interactive matplotlib window per sink, then blocks in plt.show() until the
user closes them. Java records the raw signals; this script plots them and
computes the spectrum FFT (numpy).

Dependencies: matplotlib and numpy.

Usage:  python3 plot.py <job.json>

Job format:
  {"figures":[
     {"kind":"scope","title":"Scope 4","xlabel":"tick","ylabel":"value",
      "series":[{"name":"in1","y":[...]}, ...]},
     {"kind":"spectrum","title":"Spectrum 5","xlabel":"normalized frequency",
      "ylabel":"magnitude (dB)","fft_size":1024,"hann":true,
      "series":[{"name":"in1","y":[...]}, ...]}
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

    import matplotlib
    import matplotlib.pyplot as plt

    if matplotlib.get_backend().lower() == "agg":
        sys.stderr.write(
            "No GUI backend available (matplotlib is using non-interactive 'agg'),\n"
            "so plot windows cannot open. Install a GUI backend, e.g.:\n"
            "    pip install PyQt6\n")
        return 1

    figures = job.get("figures", [])
    if not figures:
        sys.stderr.write("no figures in job\n")
        return 1

    for spec in figures:
        kind = spec.get("kind", "scope")
        fig, ax = plt.subplots(figsize=(10, 5))
        fig.canvas.manager.set_window_title(spec.get("title", "DSPFlow"))

        if kind == "spectrum":
            import numpy as np
            n = int(spec.get("fft_size", 1024))
            win = np.hanning(n) if spec.get("hann", True) else np.ones(n)
            series = spec.get("series", [])
            for s in series:
                y = np.asarray(s.get("y", []), dtype=float)
                # take the last n samples, right-aligned in an n-wide buffer
                # (zero-padded in front when fewer than n are available)
                seg = y[-n:]
                buf = np.zeros(n)
                buf[n - len(seg):] = seg
                spectrum = np.fft.rfft(buf * win)
                # Amplitude spectrum: scale so a pure tone reads its true
                # amplitude (e.g. a 1000-amplitude sine -> 20*log10(1000)=60 dB)
                # independent of N. Divide by the window's coherent sum (= N for
                # a rectangular window) and double the single-sided bins, except
                # DC and Nyquist which have no negative-frequency twin.
                mag = np.abs(spectrum) * (2.0 / np.sum(win))
                mag[0] /= 2.0
                if n % 2 == 0:
                    mag[-1] /= 2.0
                # No peak-normalization, no floor clamp; empty bins are -inf
                # and simply aren't drawn.
                with np.errstate(divide="ignore"):
                    db = 20.0 * np.log10(mag)
                freq = np.linspace(0.0, 0.5, len(db))
                # Markers so a lone finite bin (a coherent tone whose energy
                # sits in a single bin, with -inf empty neighbours) is still
                # visible -- a bare line can't draw an isolated point.
                ax.plot(freq, db, linewidth=1.0, marker=".", markersize=3,
                        label=s.get("name", ""))
            if len(series) > 1:
                ax.legend(loc="upper right", fontsize="small")
            ax.set_xlim(0, 0.5)
        else:  # scope: overlay every channel on one axes
            series = spec.get("series", [])
            for i, s in enumerate(series):
                y = s["y"]
                color = "C%d" % (i % 10)
                # Stem plot: each tick is a discrete sample, so zero-stuffed
                # (interpolated) channels show their zeros as bare baseline
                # stems rather than being hidden by a connecting line.
                markerline, stemlines, baseline = ax.stem(
                    range(len(y)), y, linefmt=color, markerfmt="o",
                    basefmt="black", label=s.get("name", ""))
                markerline.set_markersize(3)
                markerline.set_color(color)
                stemlines.set_linewidth(1.0)
                baseline.set_linewidth(0.5)
                baseline.set_alpha(0.5)
            if len(series) > 1:
                ax.legend(loc="upper right", fontsize="small")

        ax.set_title(spec.get("title", "DSPFlow"))
        ax.set_xlabel(spec.get("xlabel", ""))
        ax.set_ylabel(spec.get("ylabel", ""))
        ax.grid(True, alpha=0.3)
        fig.tight_layout()

    plt.show()  # opens all figures at once, blocks until closed
    return 0


if __name__ == "__main__":
    sys.exit(main())
