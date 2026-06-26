package dspflow.model;

import dspflow.model.blocks.*;

/**
 * Factory for block types. To add a new block: write a subclass of Block,
 * then register its type name here (and it will appear in the palette).
 */
public class BlockLibrary {

    public static final String[] TYPES = {
        "Constant", "Impulse", "Sine", "Clock",
        "Gauss", "White", "Pink",
        "Delay", "Sum", "Mult", "Shift", "SignExt",
        "Decim", "Interp",
        "Scope", "Spectrum",
        "Note"
    };

    public static Block create(String type) {
        switch (type) {
            case "Constant": return new ConstantSource();
            case "Impulse":  return new ImpulseSource();
            case "Sine":     return new SineSource();
            case "Clock":    return new ClockSource();
            case "Gauss":    return new GaussianNoiseSource();
            case "White":    return new WhiteNoiseSource();
            case "Pink":     return new PinkNoiseSource();
            case "Delay":    return new DelayBlock();
            case "Sum":      return new SumBlock();
            case "Mult":     return new MultBlock();
            case "Shift":    return new ShiftBlock();
            case "SignExt":  return new SignExtendBlock();
            case "Decim":    return new DecimatorBlock();
            case "Interp":   return new InterpolatorBlock();
            case "Scope":    return new ScopeSink();
            case "Spectrum": return new SpectrumSink();
            case "Note":     return new StickyNote();
            default:         return null;
        }
    }

    /** Short tooltip shown in the palette. */
    public static String describe(String type) {
        switch (type) {
            case "Constant": return "Constant value source";
            case "Impulse":  return "Unit impulse / pulse train source";
            case "Sine":     return "Quantized sine/cosine source";
            case "Clock":    return "Clock-enable pulse generator (1 every N ticks)";
            case "Gauss":    return "Gaussian (normal) white noise: mean + stdev*N(0,1)";
            case "White":    return "Uniform white noise in [-amplitude, +amplitude] (flat PDF)";
            case "Pink":     return "Pink 1/f noise (Kellet economy filter over white)";
            case "Delay":    return "z^-k register chain with clock-enable input";
            case "Sum":      return "Add/subtract junction (signs configurable)";
            case "Mult":     return "Multiplier with post-product right shift";
            case "Shift":    return "Arithmetic bit shifter (power-of-two gain)";
            case "SignExt":  return "Bus resize: sign- or zero-extend from N bits to width";
            case "Decim":    return "Decimator: keep 1 of M samples (registered)";
            case "Interp":   return "Interpolator: zero-stuff by L";
            case "Scope":    return "Time-domain scope (samples every tick)";
            case "Spectrum": return "FFT magnitude view";
            case "Note":     return "Resizable text annotation";
            default:         return type;
        }
    }
}
