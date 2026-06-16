package dspflow.model.blocks;

import dspflow.model.Block;

/**
 * Spectrum view: records every tick; the viewer takes the last `fft_size`
 * samples (rounded to a power of two), optionally applies a Hann window,
 * and plots the FFT magnitude in dB against normalized frequency.
 */
public class SpectrumSink extends Block {
    public long[] data = new long[0];
    private int count;

    public SpectrumSink() {
        params.put("fft_size", "1024");
        params.put("hann", "1");
        in("in");
        paramsChanged();
    }

    @Override public String type() { return "Spectrum"; }
    @Override public String glyph() { return "FFT"; }

    @Override public void reset() { data = new long[0]; count = 0; }

    @Override public void evaluate(long t) {}

    @Override public void clockEdge(long t) {
        if (count == data.length) {
            long[] nd = new long[Math.max(1024, data.length * 2)];
            System.arraycopy(data, 0, nd, 0, count);
            data = nd;
        }
        data[count++] = inVal(0);
    }

    public int sampleCount() { return count; }

    /** Power-of-two FFT size from params, clamped to [16, 65536]. */
    public int fftSize() {
        int n = Math.max(16, Math.min(65536, pi("fft_size", 1024)));
        return Integer.highestOneBit(n);
    }

    /**
     * Computes the magnitude spectrum (dB, relative to the strongest bin)
     * of the last fftSize() samples. Returns {x, y} where x is normalized
     * frequency 0..0.5 and y is dB clamped to a -120 dB floor, or null if
     * no samples have been recorded. Shared by the Swing panel and the
     * matplotlib exporter so both agree exactly.
     */
    public double[][] spectrumDb() {
        if (count == 0) return null;
        int n = fftSize();
        double[] re = new double[n];
        double[] im = new double[n];
        int avail = Math.min(count, n);
        boolean hann = pi("hann", 1) != 0;
        for (int i = 0; i < avail; i++) {
            double v = data[count - avail + i];
            int k = n - avail + i; // right-align, zero-pad in front
            double w = hann ? 0.5 - 0.5 * Math.cos(2 * Math.PI * k / (n - 1)) : 1.0;
            re[k] = v * w;
        }
        dspflow.engine.FFT.fft(re, im);

        int bins = n / 2 + 1;
        double[] x = new double[bins];
        double[] y = new double[bins];
        double peak = -1e300;
        for (int k = 0; k < bins; k++) {
            double mag = Math.hypot(re[k], im[k]);
            y[k] = 20 * Math.log10(mag + 1e-12);
            peak = Math.max(peak, y[k]);
        }
        double floor = -120;
        for (int k = 0; k < bins; k++) {
            y[k] = Math.max(floor, y[k] - peak); // normalize to peak = 0 dB
            x[k] = 0.5 * k / (bins - 1);
        }
        return new double[][] { x, y };
    }
}
