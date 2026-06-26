#!/usr/bin/env python3
"""DSPFlow figure viewer.

Reads one CSV job describing every sink in the diagram and opens an
interactive matplotlib window per sink, then blocks in plt.show() until the
user closes them. Java records the raw signals; this script plots them and
computes the spectrum FFT (numpy).

Dependencies: matplotlib and numpy.

Usage:  python3 plot.py <job.csv>

Job format (plain CSV, no JSON):
  Each figure is one block. A block is a run of "# key,value" metadata lines
  followed by a data table. Blocks are separated by a blank line.

    # kind,scope
    # title,Scope 4
    # xlabel,tick
    # ylabel,value
    tick,src_1.out,src_2.out
    0,3,7
    1,4,8

  The table's first row is the header: column 0 is "tick" (ignored), the rest
  name one series each. Every following row is one tick; column i (i>0) is
  that series' sample. Spectrum blocks add two extra metadata lines:

    # fft_size,1024
    # hann,true

  Standard CSV quoting applies to metadata values and series names.
"""
import csv
import sys


def parse_jobs(path):
    """Return [(meta dict, header list|None, rows list-of-lists), ...] per figure."""
    figures = []
    meta, header, rows = {}, None, []

    def flush():
        nonlocal meta, header, rows
        if meta or header:
            figures.append((meta, header, rows))
        meta, header, rows = {}, None, []

    with open(path, newline="") as fh:
        for row in csv.reader(fh):
            if not row:  # blank line separates figures
                flush()
            elif row[0].startswith("#"):  # "# key" in col 0, value in col 1
                meta[row[0].lstrip("# ").strip()] = row[1] if len(row) > 1 else ""
            elif header is None:
                header = row
            else:
                rows.append(row)
    flush()
    return figures


def columns(header, rows):
    """Return [(name, [float,...]), ...] for each series column (skip tick)."""
    return [(header[i], [float(r[i]) for r in rows]) for i in range(1, len(header))]


def main():
    if len(sys.argv) != 2:
        sys.stderr.write("usage: plot.py <job.csv>\n")
        return 2

    figures = parse_jobs(sys.argv[1])

    import matplotlib
    import matplotlib.pyplot as plt

    if matplotlib.get_backend().lower() == "agg":
        sys.stderr.write(
            "No GUI backend available (matplotlib is using non-interactive 'agg'),\n"
            "so plot windows cannot open. Install a GUI backend, e.g.:\n"
            "    pip install PyQt6\n")
        return 1

    if not figures:
        sys.stderr.write("no figures in job\n")
        return 1

    for meta, header, rows in figures:
        kind = meta.get("kind", "scope")
        title = meta.get("title", "DSPFlow")
        series = columns(header, rows) if header else []
        fig, ax = plt.subplots(figsize=(10, 5))
        fig.canvas.manager.set_window_title(title)

        if kind == "spectrum":
            import numpy as np
            n = int(meta.get("fft_size", 1024))
            win = np.hanning(n) if meta.get("hann", "true") == "true" else np.ones(n)
            for name, y in series:
                y = np.asarray(y, dtype=float)
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
                        label=name)
            if len(series) > 1:
                ax.legend(loc="upper right", fontsize="small")
            ax.set_xlim(0, 0.5)
        else:  # scope: overlay every channel on one axes
            for i, (name, y) in enumerate(series):
                color = "C%d" % (i % 10)
                # Stem plot: each tick is a discrete sample, so zero-stuffed
                # (interpolated) channels show their zeros as bare baseline
                # stems rather than being hidden by a connecting line.
                markerline, stemlines, baseline = ax.stem(
                    range(len(y)), y, linefmt=color, markerfmt="o",
                    basefmt="black", label=name)
                markerline.set_markersize(3)
                markerline.set_color(color)
                stemlines.set_linewidth(1.0)
                baseline.set_linewidth(0.5)
                baseline.set_alpha(0.5)
            if len(series) > 1:
                ax.legend(loc="upper right", fontsize="small")

        ax.set_title(title)
        ax.set_xlabel(meta.get("xlabel", ""))
        ax.set_ylabel(meta.get("ylabel", ""))
        ax.grid(True, alpha=0.3)
        fig.tight_layout()

    plt.show()  # opens all figures at once, blocks until closed
    return 0


if __name__ == "__main__":
    sys.exit(main())
