package dspflow.model;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

/**
 * A wire from an output port (src) to an input port (dst).
 * Waypoints define explicit routing path (orthogonal corners).
 * Empty waypoints = auto-route.
 */
public class Wire {
    public final Port src;
    public final Port dst;
    public final List<Point> waypoints = new ArrayList<>();

    public Wire(Port src, Port dst) {
        this.src = src;
        this.dst = dst;
    }

    /** Clear waypoints, revert to auto-routing. */
    public void clearWaypoints() {
        waypoints.clear();
    }

    /** Check if wire has custom routing. */
    public boolean hasCustomRoute() {
        return !waypoints.isEmpty();
    }
}
