package dspflow.model.blocks;

import java.util.ArrayList;
import dspflow.model.Block;

/**
 * Spectrum view. Records one or more input channels on every base tick, the
 * same way the Scope does. The FFT itself is computed in the Python viewer
 * (numpy); Java only ships the raw time-domain samples plus the FFT
 * parameters. The viewer takes the last {@code fft_size} samples per channel
 * (rounded to a power of two), optionally applies a Hann window, and plots
 * magnitude in dB against normalized frequency.
 */
public class SpectrumSink extends Block {
    /** One row per tick; row[i] is channel i. Read by the viewer. */
    public final ArrayList<long[]> data = new ArrayList<>();

    public SpectrumSink() {
        params.put("channels", "2");
        params.put("fft_size", "1024");
        params.put("hann", "0");
        paramsChanged();
    }

    @Override public String type() { return "Spectrum"; }
    @Override public String glyph() { return "FFT"; }

    @Override public void paramsChanged() {
        int n = Math.max(1, Math.min(8, pi("channels", 2)));
        setInputs(n, "in");
        h = Math.max(60, n * 22 + 16);
    }

    @Override public void reset() { data.clear(); }

    @Override public void evaluate(long t) {}

    @Override public void clockEdge(long t) {
        long[] row = new long[inputs.size()];
        for (int i = 0; i < row.length; i++) row[i] = inVal(i);
        data.add(row);
    }

    /** Power-of-two FFT size from params, clamped to [16, 65536]. */
    public int fftSize() {
        int n = Math.max(16, Math.min(65536, pi("fft_size", 1024)));
        return Integer.highestOneBit(n);
    }

    /** Whether the viewer should apply a Hann window before the FFT. */
    public boolean hann() { return pi("hann", 1) != 0; }
}
