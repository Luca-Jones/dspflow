package dspflow.model;

import dspflow.model.blocks.*;

/**
 * Factory for block types. To add a new block: write a subclass of Block,
 * then register its type name here (and it will appear in the palette).
 */
public class BlockLibrary {

    public static final String[] TYPES = {
        "Constant", "Impulse", "Sine", "Clock",
        "Delay", "Sum", "Mult", "Shift",
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
            case "Delay":    return new DelayBlock();
            case "Sum":      return new SumBlock();
            case "Mult":     return new MultBlock();
            case "Shift":    return new ShiftBlock();
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
            case "Delay":    return "z^-k register chain with clock-enable input";
            case "Sum":      return "Add/subtract junction (signs configurable)";
            case "Mult":     return "Multiplier with post-product right shift";
            case "Shift":    return "Arithmetic bit shifter (power-of-two gain)";
            case "Decim":    return "Decimator: keep 1 of M samples (registered)";
            case "Interp":   return "Interpolator: zero-stuff by L";
            case "Scope":    return "Time-domain scope (samples every tick)";
            case "Spectrum": return "FFT magnitude view";
            case "Note":     return "Resizable text annotation";
            default:         return type;
        }
    }
}
