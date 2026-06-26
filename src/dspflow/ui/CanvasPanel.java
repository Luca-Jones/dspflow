package dspflow.ui;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.*;
import java.util.List;
import javax.swing.*;

import dspflow.model.*;
import dspflow.model.blocks.ScopeSink;
import dspflow.model.blocks.SpectrumSink;
import dspflow.model.blocks.StickyNote;

/**
 * The schematic editor.
 *
 * Interactions:
 *   - pick a block in the palette, click the canvas to place (Shift = stamp several)
 *   - drag from a port to another port to wire (input/output in either order)
 *   - left-drag a block to move (snaps to grid); left-drag empty space = rubber-band select
 *   - right/middle-drag = pan; Ctrl + wheel = zoom; wheel = scroll
 *   - Delete removes selection; Esc cancels wiring/placement
 *   - double-click: properties (sinks open their viewer; use right-click > Properties)
 */
public class CanvasPanel extends JPanel {
    public final Diagram diagram;
    private final MainFrame frame;

    private double scale = 1.0, tx = 40, ty = 40;
    private String placing = null;
    private Port wireFrom = null;
    private Port hoverPort = null;
    private Point2D mouseModel = new Point2D.Double();
    private final Set<Object> selection = new LinkedHashSet<>();

    private Point lastScreen;
    private boolean panning = false, panned = false;
    private boolean movingBlocks = false;
    private Point2D pressModel;
    private final Map<Block, Point> moveStart = new HashMap<>();
    private final Map<Wire, List<Point>> wireWaypointStart = new HashMap<>();
    private Rectangle2D rubber = null;

    // Wire segment dragging state
    private Wire draggingWire = null;
    private int draggingSegment = -1;  // index of segment being dragged
    private boolean segmentHorizontal = false;
    private Point2D segmentDragStart = null;

    // Resize state for sticky notes
    private Block resizingBlock = null;
    private int resizeStartW, resizeStartH;

    // Clipboard for copy/paste
    private static class ClipboardEntry {
        String type;
        int dx, dy;  // offset from first block
        int w, h;
        int rotation;
        boolean flipH, flipV;
        Map<String, String> params;
    }
    private static class ClipboardWire {
        int srcIdx, dstIdx;
        String srcPort, dstPort;
        List<Point> waypoints;
    }
    private List<ClipboardEntry> clipboard = new ArrayList<>();
    private List<ClipboardWire> clipboardWires = new ArrayList<>();

    /** All canvas colors, gathered so light/dark switch cleanly. */
    static final class Theme {
        Color bg, grid, wire, sel, border;
        Color glyph, caption, label, busLabel, netLabel;       // text on/near blocks
        Color blockFill, scopeFill, sourceFill, clockFill;     // fillFor()
        Color noteFill, noteBorder, noteText;                  // sticky notes
        Color portInput, portOutput, portCE, portFloat;        // ports
        Color shadow;                                          // drop shadow (with alpha)
    }

    private static final Theme LIGHT = new Theme();
    private static final Theme DARK = new Theme();
    static {
        LIGHT.bg = new Color(0xF6F7F9);
        LIGHT.grid = new Color(0xE2E5EA);
        LIGHT.wire = new Color(0x4A7A6F);
        LIGHT.sel = new Color(0xE8861A);
        LIGHT.border = new Color(0x7A8290);
        LIGHT.glyph = new Color(0x2B3340);
        LIGHT.caption = new Color(0x6B7280);
        LIGHT.label = new Color(0x6B7280);
        LIGHT.busLabel = new Color(0x9AA0AA);
        LIGHT.netLabel = new Color(0x5A6A7A);
        LIGHT.blockFill = Color.WHITE;
        LIGHT.scopeFill = new Color(0xE9F1FB);
        LIGHT.sourceFill = new Color(0xFDF6E3);
        LIGHT.clockFill = new Color(0xF3EAF8);
        LIGHT.noteFill = new Color(0xFFF9C4);
        LIGHT.noteBorder = new Color(0xF9A825);
        LIGHT.noteText = new Color(0x5D4037);
        LIGHT.portInput = new Color(0x3B82F6);
        LIGHT.portOutput = new Color(0x22C55E);
        LIGHT.portCE = new Color(0x8E5BAE);
        LIGHT.portFloat = new Color(0xD05050);
        LIGHT.shadow = new Color(0, 0, 0, 18);

        DARK.bg = new Color(0x1E2127);
        DARK.grid = new Color(0x363B44);
        DARK.wire = new Color(0x5FAE9C);
        DARK.sel = new Color(0xF0A030);
        DARK.border = new Color(0x8A93A3);
        DARK.glyph = new Color(0xE6E9EF);
        DARK.caption = new Color(0x9AA3B2);
        DARK.label = new Color(0x9AA3B2);
        DARK.busLabel = new Color(0x7A828F);
        DARK.netLabel = new Color(0x9FB0C0);
        DARK.blockFill = new Color(0x2A2E36);
        DARK.scopeFill = new Color(0x24323F);
        DARK.sourceFill = new Color(0x37331F);
        DARK.clockFill = new Color(0x33293A);
        DARK.noteFill = new Color(0x4A4220);
        DARK.noteBorder = new Color(0xC9981F);
        DARK.noteText = new Color(0xE8D8B0);
        DARK.portInput = new Color(0x5B9BF6);
        DARK.portOutput = new Color(0x3FD06B);
        DARK.portCE = new Color(0xAE7BCE);
        DARK.portFloat = new Color(0xE07070);
        DARK.shadow = new Color(0, 0, 0, 60);
    }

    private Theme theme = LIGHT;

    public boolean isDarkMode() { return theme == DARK; }

    public void setDarkMode(boolean dark) {
        theme = dark ? DARK : LIGHT;
        setBackground(theme.bg);
        repaint();
    }

    // Display toggles
    private boolean showNetNames = false;
    private boolean showBlockNames = false;
    private boolean showBitWidths = true;

    public void setShowNetNames(boolean v) { showNetNames = v; repaint(); }
    public void setShowBlockNames(boolean v) { showBlockNames = v; repaint(); }
    public void setShowBitWidths(boolean v) { showBitWidths = v; repaint(); }

    public CanvasPanel(MainFrame frame, Diagram diagram) {
        this.frame = frame;
        this.diagram = diagram;
        setBackground(theme.bg);
        setFocusable(true);

        MouseAdapter ma = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { CanvasPanel.this.mousePressed(e); }
            @Override public void mouseReleased(MouseEvent e) { CanvasPanel.this.mouseReleased(e); }
            @Override public void mouseDragged(MouseEvent e) { CanvasPanel.this.mouseDragged(e); }
            @Override public void mouseMoved(MouseEvent e) { CanvasPanel.this.mouseMoved(e); }
            @Override public void mouseClicked(MouseEvent e) { CanvasPanel.this.mouseClicked(e); }
            @Override public void mouseWheelMoved(MouseWheelEvent e) { CanvasPanel.this.wheel(e); }
        };
        addMouseListener(ma);
        addMouseMotionListener(ma);
        addMouseWheelListener(ma);

        InputMap im = getInputMap(WHEN_FOCUSED);
        ActionMap am = getActionMap();
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "del");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0), "del");
        am.put("del", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { deleteSelection(); }
        });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "esc");
        am.put("esc", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                wireFrom = null;
                frame.clearPlacing();
                repaint();
            }
        });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK), "copy");
        am.put("copy", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { copySelection(); }
        });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK), "paste");
        am.put("paste", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { paste(); }
        });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_R, 0), "rotate");
        am.put("rotate", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { rotateSelection(); }
        });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_H, 0), "flipH");
        am.put("flipH", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { flipSelectionH(); }
        });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_V, 0), "flipV");
        am.put("flipV", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { flipSelectionV(); }
        });
    }

    // ---- coordinate helpers --------------------------------------------

    private Point2D toModel(Point p) {
        return new Point2D.Double((p.x - tx) / scale, (p.y - ty) / scale);
    }

    /** Spacing of the dot grid; everything (blocks, waypoints, route corners) snaps to it. */
    static final int GRID_STEP = 20;

    private static int snap(double v) { return (int) Math.round(v / GRID_STEP) * GRID_STEP; }

    public void setPlacing(String type) {
        placing = type;
        setCursor(Cursor.getPredefinedCursor(
                type != null ? Cursor.CROSSHAIR_CURSOR : Cursor.DEFAULT_CURSOR));
    }

    public void resetView() { scale = 1; tx = 40; ty = 40; repaint(); }

    public void clearSelection() { selection.clear(); repaint(); }

    // ---- hit tests ------------------------------------------------------

    public Point portPos(Port p) {
        Block b = p.block;
        // Determine base edge: 0=left, 1=top, 2=right, 3=bottom
        int edge;
        int idx, count;
        if (p.input) {
            if (p.isCE()) {
                edge = 3; // CE always starts at bottom
                idx = 0; count = 1;
            } else {
                edge = 0; // inputs start at left
                List<Port> ins = new ArrayList<>();
                for (Port q : b.inputs) if (!q.isCE()) ins.add(q);
                idx = ins.indexOf(p);
                count = ins.size();
            }
        } else {
            edge = 2; // outputs start at right
            idx = b.outputs.indexOf(p);
            count = b.outputs.size();
        }

        // Apply rotation (clockwise)
        edge = (edge + b.rotation) % 4;

        // Apply horizontal flip (swaps left/right)
        if (b.flipH) {
            if (edge == 0) edge = 2;
            else if (edge == 2) edge = 0;
        }

        // Apply vertical flip (swaps top/bottom)
        if (b.flipV) {
            if (edge == 1) edge = 3;
            else if (edge == 3) edge = 1;
        }

        // Compute position on edge
        return posOnEdge(b, edge, idx, count);
    }

    private Point posOnEdge(Block b, int edge, int idx, int count) {
        int frac = (idx + 1) * (edge % 2 == 0 ? b.h : b.w) / (count + 1);
        switch (edge) {
            case 0: return new Point(b.x, b.y + frac);           // left
            case 1: return new Point(b.x + frac, b.y);           // top
            case 2: return new Point(b.x + b.w, b.y + frac);     // right
            case 3: return new Point(b.x + frac, b.y + b.h);     // bottom
            default: return new Point(b.x, b.y);
        }
    }

    /** Get the edge a port is on (0=left, 1=top, 2=right, 3=bottom) after rotation/flip. */
    private int portEdge(Port p) {
        Block b = p.block;
        int edge;
        if (p.input) {
            edge = p.isCE() ? 3 : 0;
        } else {
            edge = 2;
        }
        edge = (edge + b.rotation) % 4;
        if (b.flipH) {
            if (edge == 0) edge = 2;
            else if (edge == 2) edge = 0;
        }
        if (b.flipV) {
            if (edge == 1) edge = 3;
            else if (edge == 3) edge = 1;
        }
        return edge;
    }

    private Port portAt(double mx, double my) {
        double r = Math.max(7, 7 / scale);
        for (int i = diagram.blocks.size() - 1; i >= 0; i--) {
            Block b = diagram.blocks.get(i);
            for (Port p : b.inputs) if (portPos(p).distance(mx, my) <= r) return p;
            for (Port p : b.outputs) if (portPos(p).distance(mx, my) <= r) return p;
        }
        return null;
    }

    private Block blockAt(double mx, double my) {
        for (int i = diagram.blocks.size() - 1; i >= 0; i--) {
            Block b = diagram.blocks.get(i);
            if (mx >= b.x && mx <= b.x + b.w && my >= b.y && my <= b.y + b.h) return b;
        }
        return null;
    }

    private static final int RESIZE_HANDLE = 12;

    private Block resizeHandleAt(double mx, double my) {
        for (int i = diagram.blocks.size() - 1; i >= 0; i--) {
            Block b = diagram.blocks.get(i);
            if (!(b instanceof StickyNote)) continue;
            // Check if in bottom-right corner resize area
            double hx = b.x + b.w;
            double hy = b.y + b.h;
            if (mx >= hx - RESIZE_HANDLE && mx <= hx && my >= hy - RESIZE_HANDLE && my <= hy) {
                return b;
            }
        }
        return null;
    }

    private Wire wireAt(double mx, double my) {
        double tol = Math.max(5, 5 / scale);
        for (Wire w : diagram.wires) {
            List<Point2D> pts = route(w);
            for (int i = 0; i + 1 < pts.size(); i++) {
                if (Line2D.ptSegDist(pts.get(i).getX(), pts.get(i).getY(),
                        pts.get(i + 1).getX(), pts.get(i + 1).getY(), mx, my) <= tol)
                    return w;
            }
        }
        return null;
    }

    private boolean wireIntersectsRect(Wire w, Rectangle2D r) {
        List<Point2D> pts = route(w);
        for (int i = 0; i + 1 < pts.size(); i++) {
            if (r.intersectsLine(pts.get(i).getX(), pts.get(i).getY(),
                    pts.get(i + 1).getX(), pts.get(i + 1).getY()))
                return true;
        }
        return false;
    }

    // ---- wire routing (simple orthogonal) --------------------------------

    private static final int STUB = 24;

    /**
     * Full drawn polyline: [srcPort, srcStub, corners..., dstStub, dstPort].
     * Stubs always run STUB px straight out of each port (along the port edge
     * axis), so a wire leaves/enters an arrowhead in the direction it faces and
     * the first turn is STUB px away. The interior corners are exactly the
     * waypoints for custom routes (kept normalized via normalizeWaypoints).
     */
    private List<Point2D> route(Wire w) {
        Point a0 = portPos(w.src);
        Point b0 = portPos(w.dst);
        Point2D a1 = stubPoint(w.src);
        Point2D b1 = stubPoint(w.dst);
        boolean srcH = (portEdge(w.src) % 2) == 0;  // edges 0/2 = horizontal axis
        boolean dstH = (portEdge(w.dst) % 2) == 0;

        List<Point2D> anchors = new ArrayList<>();
        anchors.add(a1);
        if (w.hasCustomRoute())
            for (Point p : w.waypoints) anchors.add(new Point2D.Double(p.x, p.y));
        else
            anchors.addAll(autoCorners(w));
        anchors.add(b1);

        List<Point2D> chain = orthogonalize(anchors, srcH, dstH);

        List<Point2D> out = new ArrayList<>();
        out.add(new Point2D.Double(a0.x, a0.y));
        out.addAll(chain);
        out.add(new Point2D.Double(b0.x, b0.y));
        return simplify(out);
    }

    /**
     * Collapse coincident points and merge collinear runs in an orthogonal
     * polyline. Removes the zero-length and redundant segments produced when
     * stub/corner anchors coincide (e.g. same-facing ports where the meet
     * point lands on a stub) -- those caused overlapping draws, spurious
     * draggable segments, and a degenerate arrowhead direction when the last
     * two points collapsed onto each other.
     */
    private List<Point2D> simplify(List<Point2D> pts) {
        List<Point2D> out = new ArrayList<>();
        for (Point2D p : pts) {
            // skip a point coincident with the previous one
            if (!out.isEmpty() && near(out.get(out.size() - 1), p)) continue;
            // drop the middle of three collinear points
            if (out.size() >= 2) {
                Point2D a = out.get(out.size() - 2), b = out.get(out.size() - 1);
                if (collinear(a, b, p)) out.remove(out.size() - 1);
            }
            out.add(p);
        }
        if (out.size() < 2) out.add(pts.get(pts.size() - 1));  // keep at least 2 pts
        return out;
    }

    private static boolean near(Point2D a, Point2D b) {
        return Math.abs(a.getX() - b.getX()) < 0.5 && Math.abs(a.getY() - b.getY()) < 0.5;
    }

    private static boolean collinear(Point2D a, Point2D b, Point2D c) {
        boolean hor = Math.abs(a.getY() - b.getY()) < 0.5 && Math.abs(b.getY() - c.getY()) < 0.5;
        boolean ver = Math.abs(a.getX() - b.getX()) < 0.5 && Math.abs(b.getX() - c.getX()) < 0.5;
        return hor || ver;
    }

    /**
     * Connect anchors (stub..waypoints..stub) into an orthogonal polyline,
     * inserting an L-corner between any two anchors that don't already share an
     * axis. The corner adjacent to a stub is oriented so the segment touching
     * the stub runs along the stub's axis (so the wire exits/enters the port
     * straight). Returns all points from the first anchor to the last.
     */
    private List<Point2D> orthogonalize(List<Point2D> anchors, boolean srcH, boolean dstH) {
        List<Point2D> out = new ArrayList<>();
        out.add(anchors.get(0));
        int n = anchors.size();
        for (int i = 1; i < n; i++) {
            Point2D prev = out.get(out.size() - 1);
            Point2D cur = anchors.get(i);
            boolean aligned = prev.getX() == cur.getX() || prev.getY() == cur.getY();
            if (!aligned) {
                boolean horizFirst;
                if (i == 1) horizFirst = srcH;          // leave src along stub axis
                else if (i == n - 1) horizFirst = !dstH; // enter dst along stub axis
                else horizFirst = Math.abs(cur.getX() - prev.getX())
                        >= Math.abs(cur.getY() - prev.getY());
                Point2D corner = horizFirst
                        ? new Point2D.Double(cur.getX(), prev.getY())
                        : new Point2D.Double(prev.getX(), cur.getY());
                out.add(corner);
            }
            out.add(cur);
        }
        return out;
    }

    /**
     * Interior corner points of the auto-route (between the two stubs), snapped
     * to the dot grid so auto-routed wires run dot-to-dot. orthogonalize() only
     * inserts L-corners whose coordinates are copied from these (already snapped)
     * neighbours, so the whole drawn polyline stays on the grid.
     */
    private List<Point2D> autoCorners(Wire w) {
        List<Point2D> pts = routeAuto(w);
        List<Point2D> corners = new ArrayList<>();
        for (Point2D p : pts.subList(2, pts.size() - 2))
            corners.add(new Point2D.Double(snap(p.getX()), snap(p.getY())));
        return corners;
    }

    /** Get stub point extending outward from port based on its edge. */
    private Point2D stubPoint(Port p) {
        Point pos = portPos(p);
        int edge = portEdge(p);
        switch (edge) {
            case 0: return new Point2D.Double(pos.x - STUB, pos.y);  // left
            case 1: return new Point2D.Double(pos.x, pos.y - STUB);  // top
            case 2: return new Point2D.Double(pos.x + STUB, pos.y);  // right
            case 3: return new Point2D.Double(pos.x, pos.y + STUB);  // bottom
            default: return new Point2D.Double(pos.x, pos.y);
        }
    }

    /** Auto-route based on port edges, avoiding backtracking and dst block. */
    private List<Point2D> routeAuto(Wire w) {
        Point a0 = portPos(w.src);
        Point b0 = portPos(w.dst);
        Point2D a1 = stubPoint(w.src);
        Point2D b1 = stubPoint(w.dst);

        int srcEdge = portEdge(w.src);
        int dstEdge = portEdge(w.dst);
        Block dstBlock = w.dst.block;

        // Outward direction of each stub (pointing away from its own port).
        // The wire must leave the src stub this way and approach the dst stub
        // from this side, so a segment never reverses into a stub (no left->right
        // or up->down without a perpendicular run between).
        int sox = (srcEdge == 2) ? 1 : (srcEdge == 0) ? -1 : 0;
        int soy = (srcEdge == 3) ? 1 : (srcEdge == 1) ? -1 : 0;
        int dox = (dstEdge == 2) ? 1 : (dstEdge == 0) ? -1 : 0;
        int doy = (dstEdge == 3) ? 1 : (dstEdge == 1) ? -1 : 0;

        boolean srcHoriz = (sox != 0);
        boolean dstHoriz = (dox != 0);

        List<Point2D> pts = new ArrayList<>();
        pts.add(new Point2D.Double(a0.x, a0.y));
        pts.add(a1);

        // Clearance around destination block
        int margin = STUB + 4;
        double dstLeft = dstBlock.x - margin;
        double dstRight = dstBlock.x + dstBlock.w + margin;
        double dstTop = dstBlock.y - margin;
        double dstBottom = dstBlock.y + dstBlock.h + margin;
        double dstCenterX = dstBlock.x + dstBlock.w / 2.0;
        double dstCenterY = dstBlock.y + dstBlock.h / 2.0;

        double ax = a1.getX(), ay = a1.getY();
        double bx = b1.getX(), by = b1.getY();

        if (srcHoriz && dstHoriz) {
            if (sox == dox) {
                // Both stubs face the same way: meet just past the outer port,
                // then drop straight in (no doubling back into the arrowhead).
                double ex = (sox > 0) ? Math.max(ax, bx) : Math.min(ax, bx);
                pts.add(new Point2D.Double(ex, ay));
                pts.add(new Point2D.Double(ex, by));
            } else if ((sox > 0 && bx >= ax) || (sox < 0 && bx <= ax)) {
                // Facing each other with room between: Z through the midpoint.
                double midx = (ax + bx) / 2;
                pts.add(new Point2D.Double(midx, ay));
                pts.add(new Point2D.Double(midx, by));
            } else {
                // Facing apart / overlapping: loop around the dst block.
                double goY = (ay < dstCenterY) ? dstTop : dstBottom;
                double extX = (sox > 0) ? Math.max(ax, dstRight) : Math.min(ax, dstLeft);
                pts.add(new Point2D.Double(extX, ay));
                pts.add(new Point2D.Double(extX, goY));
                pts.add(new Point2D.Double(bx, goY));
            }
        } else if (!srcHoriz && !dstHoriz) {
            if (soy == doy) {
                double ey = (soy > 0) ? Math.max(ay, by) : Math.min(ay, by);
                pts.add(new Point2D.Double(ax, ey));
                pts.add(new Point2D.Double(bx, ey));
            } else if ((soy > 0 && by >= ay) || (soy < 0 && by <= ay)) {
                double midy = (ay + by) / 2;
                pts.add(new Point2D.Double(ax, midy));
                pts.add(new Point2D.Double(bx, midy));
            } else {
                double goX = (ax < dstCenterX) ? dstLeft : dstRight;
                double extY = (soy > 0) ? Math.max(ay, dstBottom) : Math.min(ay, dstTop);
                pts.add(new Point2D.Double(ax, extY));
                pts.add(new Point2D.Double(goX, extY));
                pts.add(new Point2D.Double(goX, by));
            }
        } else if (srcHoriz) {
            // Src horizontal, dst vertical. Single corner (bx,ay) works only if
            // it leaves src forward in x and reaches b1 from its outward side.
            boolean xOk = (sox > 0 && bx >= ax) || (sox < 0 && bx <= ax);
            boolean yOk = (ay - by) * doy >= 0;
            if (xOk && yOk) {
                pts.add(new Point2D.Double(bx, ay));
            } else {
                // Detour to the port's outward side, then drop straight into b1.
                double goY = (doy < 0) ? dstTop : dstBottom;
                double extX = (sox > 0) ? Math.max(ax, dstRight) : Math.min(ax, dstLeft);
                pts.add(new Point2D.Double(extX, ay));
                pts.add(new Point2D.Double(extX, goY));
                pts.add(new Point2D.Double(bx, goY));
            }
        } else {
            // Src vertical, dst horizontal. Corner (ax,by).
            boolean yOk = (soy > 0 && by >= ay) || (soy < 0 && by <= ay);
            boolean xOk = (ax - bx) * dox >= 0;
            if (yOk && xOk) {
                pts.add(new Point2D.Double(ax, by));
            } else {
                double goX = (dox < 0) ? dstLeft : dstRight;
                double extY = (soy > 0) ? Math.max(ay, dstBottom) : Math.min(ay, dstTop);
                pts.add(new Point2D.Double(ax, extY));
                pts.add(new Point2D.Double(goX, extY));
                pts.add(new Point2D.Double(goX, by));
            }
        }

        pts.add(b1);
        pts.add(new Point2D.Double(b0.x, b0.y));
        return pts;
    }

    /** Convert auto-route to explicit waypoints for editing. */
    private void bakeWaypoints(Wire w) {
        if (w.hasCustomRoute()) return;
        for (Point2D p : autoCorners(w))
            w.waypoints.add(new Point(snap((int) p.getX()), snap((int) p.getY())));
        normalizeWaypoints(w);
    }

    /** Find which segment of a wire is at given point. Returns -1 if none. */
    private int segmentAt(Wire w, double mx, double my) {
        double tol = Math.max(5, 5 / scale);
        List<Point2D> pts = route(w);
        for (int i = 0; i + 1 < pts.size(); i++) {
            if (Line2D.ptSegDist(pts.get(i).getX(), pts.get(i).getY(),
                    pts.get(i + 1).getX(), pts.get(i + 1).getY(), mx, my) <= tol)
                return i;
        }
        return -1;
    }

    /** Check if segment is horizontal (vs vertical). */
    private boolean isHorizontal(List<Point2D> pts, int seg) {
        if (seg < 0 || seg + 1 >= pts.size()) return false;
        double dy = Math.abs(pts.get(seg + 1).getY() - pts.get(seg).getY());
        double dx = Math.abs(pts.get(seg + 1).getX() - pts.get(seg).getX());
        return dx > dy;
    }

    // ---- mouse logic ------------------------------------------------------

    private void mousePressed(MouseEvent e) {
        requestFocusInWindow();
        Point2D m = toModel(e.getPoint());
        mouseModel = m;
        lastScreen = e.getPoint();
        panned = false;

        boolean left = SwingUtilities.isLeftMouseButton(e);
        boolean right = SwingUtilities.isRightMouseButton(e);
        boolean middle = SwingUtilities.isMiddleMouseButton(e);

        // Middle always pans
        if (middle) {
            panning = true;
            return;
        }

        // Placing mode: left click places block
        if (placing != null && left) {
            Block b = BlockLibrary.create(placing);
            if (b != null) {
                b.x = snap(m.getX() - b.w / 2.0);
                b.y = snap(m.getY() - b.h / 2.0);
                diagram.add(b);
                frame.setStatus("Placed " + b.label() + ".");
            }
            if (!e.isShiftDown()) frame.clearPlacing();
            repaint();
            return;
        }

        // Left click on port: start wiring
        Port p = portAt(m.getX(), m.getY());
        if (p != null && left) {
            wireFrom = p;
            repaint();
            return;
        }

        // Left click on wire: drag segment or select
        Wire w = wireAt(m.getX(), m.getY());
        if (w != null && left) {
            // Alt+click: reset to auto-routing
            if (e.isAltDown()) {
                w.clearWaypoints();
                selection.clear();
                selection.add(w);
                frame.setStatus("Wire reset to auto-routing.");
                repaint();
                return;
            }
            int seg = segmentAt(w, m.getX(), m.getY());
            if (seg >= 0 && !e.isControlDown()) {
                // Start segment drag - first bake waypoints if auto-routed
                bakeWaypoints(w);
                List<Point2D> pts = route(w);
                draggingWire = w;
                draggingSegment = seg;
                segmentHorizontal = isHorizontal(pts, seg);
                segmentDragStart = m;
                selection.clear();
                selection.add(w);
                repaint();
                return;
            }
            if (!e.isControlDown()) selection.clear();
            selection.add(w);
            repaint();
            return;
        }

        // Left click on resize handle (sticky notes)
        Block resizeTarget = resizeHandleAt(m.getX(), m.getY());
        if (resizeTarget != null && left) {
            resizingBlock = resizeTarget;
            resizeStartW = resizeTarget.w;
            resizeStartH = resizeTarget.h;
            pressModel = m;
            selection.clear();
            selection.add(resizeTarget);
            repaint();
            return;
        }

        // Left click on block: select/drag
        Block b = blockAt(m.getX(), m.getY());
        if (b != null && left) {
            if (e.isControlDown()) {
                if (!selection.remove(b)) selection.add(b);
            } else if (!selection.contains(b)) {
                selection.clear();
                selection.add(b);
            }
            movingBlocks = true;
            pressModel = m;
            moveStart.clear();
            wireWaypointStart.clear();
            Set<Block> selectedBlocks = new HashSet<>();
            for (Object o : selection) {
                if (o instanceof Block) {
                    Block blk = (Block) o;
                    moveStart.put(blk, new Point(blk.x, blk.y));
                    selectedBlocks.add(blk);
                }
            }
            // Track waypoints of wires fully within selection
            for (Wire wire : diagram.wires) {
                if (selectedBlocks.contains(wire.src.block) && selectedBlocks.contains(wire.dst.block)) {
                    List<Point> copy = new ArrayList<>();
                    for (Point pt : wire.waypoints) copy.add(new Point(pt));
                    wireWaypointStart.put(wire, copy);
                }
            }
            repaint();
            return;
        }

        // Left on empty: clear selection and pan
        if (left) {
            if (!e.isControlDown()) selection.clear();
            panning = true;
            repaint();
            return;
        }

        // Right on empty: rubber-band selection (context menu on release if no drag)
        if (right) {
            if (!e.isControlDown()) selection.clear();
            pressModel = m;
            rubber = new Rectangle2D.Double(m.getX(), m.getY(), 0, 0);
            repaint();
        }
    }

    private void mouseDragged(MouseEvent e) {
        Point2D m = toModel(e.getPoint());
        mouseModel = m;
        if (panning) {
            tx += e.getX() - lastScreen.x;
            ty += e.getY() - lastScreen.y;
            lastScreen = e.getPoint();
            panned = true;
            repaint();
            return;
        }
        if (wireFrom != null) {
            hoverPort = portAt(m.getX(), m.getY());
            repaint();
            return;
        }
        if (movingBlocks) {
            double dx = m.getX() - pressModel.getX();
            double dy = m.getY() - pressModel.getY();
            for (Map.Entry<Block, Point> en : moveStart.entrySet()) {
                en.getKey().x = snap(en.getValue().x + dx);
                en.getKey().y = snap(en.getValue().y + dy);
            }
            // Move wire waypoints with blocks
            for (Map.Entry<Wire, List<Point>> en : wireWaypointStart.entrySet()) {
                Wire w = en.getKey();
                List<Point> orig = en.getValue();
                w.waypoints.clear();
                for (Point p : orig) {
                    w.waypoints.add(new Point(snap(p.x + dx), snap(p.y + dy)));
                }
            }
            repaint();
            return;
        }
        if (resizingBlock != null) {
            double dx = m.getX() - pressModel.getX();
            double dy = m.getY() - pressModel.getY();
            resizingBlock.w = Math.max(60, snap(resizeStartW + dx));
            resizingBlock.h = Math.max(40, snap(resizeStartH + dy));
            repaint();
            return;
        }
        if (draggingWire != null && draggingSegment >= 0) {
            // Move segment by adjusting waypoints.
            // Route layout: [port, stub, wp0, wp1, ..., wpN, stub, port].
            // Segment s connects route[s] to route[s+1], so the two waypoints it
            // touches are wp[s-2] and wp[s-1] (either may be a fixed stub, out of
            // range, in which case only the in-range waypoint moves).
            List<Point> wps = draggingWire.waypoints;
            int wp1 = draggingSegment - 2;  // first waypoint of segment
            int wp2 = draggingSegment - 1;  // second waypoint of segment

            if (segmentHorizontal) {
                // Horizontal segment: moving changes Y
                int newY = snap((int) m.getY());
                if (wp1 >= 0 && wp1 < wps.size()) wps.get(wp1).y = newY;
                if (wp2 >= 0 && wp2 < wps.size()) wps.get(wp2).y = newY;
            } else {
                // Vertical segment: moving changes X
                int newX = snap((int) m.getX());
                if (wp1 >= 0 && wp1 < wps.size()) wps.get(wp1).x = newX;
                if (wp2 >= 0 && wp2 < wps.size()) wps.get(wp2).x = newX;
            }
            repaint();
            return;
        }
        if (rubber != null) {
            rubber.setFrameFromDiagonal(pressModel, m);
            repaint();
        }
    }

    private void mouseReleased(MouseEvent e) {
        Point2D m = toModel(e.getPoint());
        if (panning) {
            panning = false;
            return;
        }
        // Right click: context menu only if no rubber-band drag occurred
        if (SwingUtilities.isRightMouseButton(e) && draggingWire == null && wireFrom == null) {
            boolean rubberUsed = rubber != null && (rubber.getWidth() > 3 || rubber.getHeight() > 3);
            if (!rubberUsed) {
                rubber = null;
                showPopup(e, m);
                return;
            }
        }
        if (wireFrom != null) {
            Port p2 = portAt(m.getX(), m.getY());
            if (p2 != null && p2 != wireFrom) {
                if (diagram.connect(wireFrom, p2))
                    frame.setStatus("Connected " + (wireFrom.input ? p2 : wireFrom)
                            + " \u2192 " + (wireFrom.input ? wireFrom : p2));
                else
                    frame.setStatus("Cannot connect: needs one output and one input.");
            }
            wireFrom = null;
            hoverPort = null;
            repaint();
            return;
        }
        if (rubber != null) {
            for (Block b : diagram.blocks)
                if (rubber.intersects(b.x, b.y, b.w, b.h)) selection.add(b);
            for (Wire w : diagram.wires)
                if (wireIntersectsRect(w, rubber)) selection.add(w);
            rubber = null;
            repaint();
        }
        if (draggingWire != null) {
            // Snap waypoints back onto the actual corners of the drawn route.
            normalizeWaypoints(draggingWire);
            draggingWire = null;
            draggingSegment = -1;
            repaint();
        }
        movingBlocks = false;
        resizingBlock = null;
    }

    /**
     * Re-derive waypoints so they equal the actual corners of the drawn route:
     * one waypoint per direction change, none along a straight run. Drops
     * collinear points and captures any corner orthogonalize() had to insert,
     * keeping the draggable handles aligned with the visible bends.
     */
    private void normalizeWaypoints(Wire w) {
        if (!w.hasCustomRoute()) return;
        List<Point2D> pts = route(w);  // [port, stub, ...corners..., stub, port]
        List<Point> corners = new ArrayList<>();
        for (int i = 2; i < pts.size() - 2; i++) {
            Point2D prev = pts.get(i - 1), cur = pts.get(i), next = pts.get(i + 1);
            boolean sameX = prev.getX() == cur.getX() && cur.getX() == next.getX();
            boolean sameY = prev.getY() == cur.getY() && cur.getY() == next.getY();
            if (sameX || sameY) continue;  // not a corner, skip
            corners.add(new Point(snap((int) Math.round(cur.getX())),
                    snap((int) Math.round(cur.getY()))));
        }
        w.waypoints.clear();
        w.waypoints.addAll(corners);
    }

    private void mouseMoved(MouseEvent e) {
        Point2D m = toModel(e.getPoint());
        mouseModel = m;
        Port p = portAt(m.getX(), m.getY());
        Block resizeTarget = resizeHandleAt(m.getX(), m.getY());
        if (p != hoverPort || resizeTarget != null) {
            hoverPort = p;
            Cursor c;
            if (resizeTarget != null) c = Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR);
            else if (p != null) c = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
            else if (placing != null) c = Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR);
            else c = Cursor.getDefaultCursor();
            setCursor(c);
            repaint();
        }
    }

    private void mouseClicked(MouseEvent e) {
        if (e.getClickCount() != 2 || !SwingUtilities.isLeftMouseButton(e)) return;
        Point2D m = toModel(e.getPoint());
        Block b = blockAt(m.getX(), m.getY());
        if (b == null) return;
        PropertyDialog.edit(frame, b, diagram, this);
    }

    private void wheel(MouseWheelEvent e) {
        if (e.isControlDown()) {
            double f = Math.pow(1.12, -e.getPreciseWheelRotation());
            double ns = Math.max(0.2, Math.min(4.0, scale * f));
            f = ns / scale;
            tx = e.getX() - f * (e.getX() - tx);
            ty = e.getY() - f * (e.getY() - ty);
            scale = ns;
            frame.setZoomLabel((int) Math.round(scale * 100) + "%");
        } else if (e.isShiftDown()) {
            tx -= e.getPreciseWheelRotation() * 40;
        } else {
            ty -= e.getPreciseWheelRotation() * 40;
        }
        repaint();
    }

    private void showPopup(MouseEvent e, Point2D m) {
        Block b = blockAt(m.getX(), m.getY());
        Wire w = b == null ? wireAt(m.getX(), m.getY()) : null;
        JPopupMenu menu = new JPopupMenu();
        if (b != null) {
            if (!selection.contains(b)) { selection.clear(); selection.add(b); repaint(); }
            JMenuItem props = new JMenuItem("Properties\u2026");
            props.addActionListener(a -> PropertyDialog.edit(frame, b, diagram, this));
            menu.add(props);
            if (b instanceof ScopeSink || b instanceof SpectrumSink) {
                JMenuItem v = new JMenuItem("Plot results");
                v.addActionListener(a -> frame.showPlots());
                menu.add(v);
            }
            menu.addSeparator();
            JMenuItem rotate = new JMenuItem("Rotate (R)");
            rotate.addActionListener(a -> { b.rotateCW(); clearWiresForBlock(b); repaint(); });
            menu.add(rotate);
            JMenuItem flipH = new JMenuItem("Flip horizontal (H)");
            flipH.addActionListener(a -> { b.flipHorizontal(); clearWiresForBlock(b); repaint(); });
            menu.add(flipH);
            JMenuItem flipV = new JMenuItem("Flip vertical (V)");
            flipV.addActionListener(a -> { b.flipVertical(); clearWiresForBlock(b); repaint(); });
            menu.add(flipV);
            menu.addSeparator();
            JMenuItem del = new JMenuItem("Delete");
            del.addActionListener(a -> { diagram.remove(b); selection.remove(b); repaint(); });
            menu.add(del);
        } else if (w != null) {
            if (w.hasCustomRoute()) {
                JMenuItem reset = new JMenuItem("Reset routing");
                reset.addActionListener(a -> { w.clearWaypoints(); repaint(); });
                menu.add(reset);
            }
            JMenuItem del = new JMenuItem("Delete wire");
            del.addActionListener(a -> { diagram.remove(w); selection.remove(w); repaint(); });
            menu.add(del);
        } else {
            JMenuItem rv = new JMenuItem("Reset view");
            rv.addActionListener(a -> resetView());
            menu.add(rv);
        }
        menu.show(this, e.getX(), e.getY());
    }

    private void deleteSelection() {
        for (Object o : new ArrayList<>(selection)) {
            if (o instanceof Block) diagram.remove((Block) o);
            else if (o instanceof Wire) diagram.remove((Wire) o);
        }
        selection.clear();
        repaint();
    }

    private void rotateSelection() {
        boolean any = false;
        for (Object o : selection) {
            if (o instanceof Block) {
                Block b = (Block) o;
                b.rotateCW();
                clearWiresForBlock(b);
                any = true;
            }
        }
        if (any) {
            frame.setStatus("Rotated 90° clockwise. (R=rotate, H=flipH, V=flipV)");
            repaint();
        }
    }

    private void flipSelectionH() {
        boolean any = false;
        for (Object o : selection) {
            if (o instanceof Block) {
                Block b = (Block) o;
                b.flipHorizontal();
                clearWiresForBlock(b);
                any = true;
            }
        }
        if (any) {
            frame.setStatus("Flipped horizontally. (R=rotate, H=flipH, V=flipV)");
            repaint();
        }
    }

    private void flipSelectionV() {
        boolean any = false;
        for (Object o : selection) {
            if (o instanceof Block) {
                Block b = (Block) o;
                b.flipVertical();
                clearWiresForBlock(b);
                any = true;
            }
        }
        if (any) {
            frame.setStatus("Flipped vertically. (R=rotate, H=flipH, V=flipV)");
            repaint();
        }
    }

    private void clearWiresForBlock(Block b) {
        for (Wire w : diagram.wires) {
            if (w.src.block == b || w.dst.block == b) {
                w.clearWaypoints();
            }
        }
    }

    private void copySelection() {
        // Collect selected blocks
        List<Block> blocks = new ArrayList<>();
        for (Object o : selection)
            if (o instanceof Block) blocks.add((Block) o);
        if (blocks.isEmpty()) {
            frame.setStatus("Nothing to copy.");
            return;
        }

        // Use first block as reference point
        int refX = blocks.get(0).x, refY = blocks.get(0).y;

        clipboard.clear();
        clipboardWires.clear();

        // Store block data
        Map<Block, Integer> blockIndex = new HashMap<>();
        for (int i = 0; i < blocks.size(); i++) {
            Block b = blocks.get(i);
            blockIndex.put(b, i);
            ClipboardEntry entry = new ClipboardEntry();
            entry.type = b.type();
            entry.dx = b.x - refX;
            entry.dy = b.y - refY;
            entry.w = b.w;
            entry.h = b.h;
            entry.rotation = b.rotation;
            entry.flipH = b.flipH;
            entry.flipV = b.flipV;
            entry.params = new LinkedHashMap<>(b.params);
            clipboard.add(entry);
        }

        // Store wires between copied blocks
        for (Wire w : diagram.wires) {
            Integer srcIdx = blockIndex.get(w.src.block);
            Integer dstIdx = blockIndex.get(w.dst.block);
            if (srcIdx != null && dstIdx != null) {
                ClipboardWire cw = new ClipboardWire();
                cw.srcIdx = srcIdx;
                cw.dstIdx = dstIdx;
                cw.srcPort = w.src.name;
                cw.dstPort = w.dst.name;
                cw.waypoints = new ArrayList<>();
                for (Point p : w.waypoints)
                    cw.waypoints.add(new Point(p.x - refX, p.y - refY));
                clipboardWires.add(cw);
            }
        }

        frame.setStatus("Copied " + blocks.size() + " block(s).");
    }

    private void paste() {
        if (clipboard.isEmpty()) {
            frame.setStatus("Clipboard empty.");
            return;
        }

        // Paste at mouse position (snapped) or offset from original
        int baseX = snap((int) mouseModel.getX());
        int baseY = snap((int) mouseModel.getY());

        // Create new blocks
        List<Block> newBlocks = new ArrayList<>();
        for (ClipboardEntry entry : clipboard) {
            Block b = BlockLibrary.create(entry.type);
            if (b == null) continue;
            b.x = snap(baseX + entry.dx);
            b.y = snap(baseY + entry.dy);
            b.w = entry.w;
            b.h = entry.h;
            b.rotation = entry.rotation;
            b.flipH = entry.flipH;
            b.flipV = entry.flipV;
            b.params.putAll(entry.params);
            b.paramsChanged();
            diagram.add(b);
            newBlocks.add(b);
        }

        // Recreate wires
        List<Wire> newWires = new ArrayList<>();
        for (ClipboardWire cw : clipboardWires) {
            if (cw.srcIdx >= newBlocks.size() || cw.dstIdx >= newBlocks.size()) continue;
            Block srcBlock = newBlocks.get(cw.srcIdx);
            Block dstBlock = newBlocks.get(cw.dstIdx);
            Port src = diagram.findPort(srcBlock, cw.srcPort, false);
            Port dst = diagram.findPort(dstBlock, cw.dstPort, true);
            if (src != null && dst != null) {
                Wire w = new Wire(src, dst);
                for (Point p : cw.waypoints)
                    w.waypoints.add(new Point(snap(baseX + p.x), snap(baseY + p.y)));
                diagram.wires.add(w);
                newWires.add(w);
            }
        }

        // Select pasted blocks and wires
        selection.clear();
        selection.addAll(newBlocks);
        selection.addAll(newWires);

        frame.setStatus("Pasted " + newBlocks.size() + " block(s).");
        repaint();
    }

    // ---- painting -----------------------------------------------------------

    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.translate(tx, ty);
        g2.scale(scale, scale);

        paintGrid(g2);
        for (Wire w : diagram.wires) paintWire(g2, w);
        if (wireFrom != null) paintPendingWire(g2);
        for (Block b : diagram.blocks) paintBlock(g2, b);

        if (rubber != null) {
            g2.setColor(theme.sel);
            g2.setStroke(new BasicStroke((float) (1 / scale), BasicStroke.CAP_BUTT,
                    BasicStroke.JOIN_MITER, 10, new float[]{4, 4}, 0));
            g2.draw(rubber);
        }
        g2.dispose();
    }

    private void paintGrid(Graphics2D g2) {
        Rectangle2D vis = new Rectangle2D.Double(-tx / scale, -ty / scale,
                getWidth() / scale, getHeight() / scale);
        g2.setColor(theme.grid);
        int step = 20;
        double d = 1.6;  // dot diameter (model units)
        int x0 = (int) Math.floor(vis.getMinX() / step) * step;
        int y0 = (int) Math.floor(vis.getMinY() / step) * step;
        for (int x = x0; x < vis.getMaxX(); x += step)
            for (int y = y0; y < vis.getMaxY(); y += step)
                g2.fill(new Ellipse2D.Double(x - d / 2, y - d / 2, d, d));
    }

    private void paintWire(Graphics2D g2, Wire w) {
        boolean sel = selection.contains(w);
        g2.setColor(sel ? theme.sel : theme.wire);
        g2.setStroke(new BasicStroke(sel ? 2.4f : 1.8f,
                BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        List<Point2D> pts = route(w);
        Path2D path = new Path2D.Double();
        path.moveTo(pts.get(0).getX(), pts.get(0).getY());
        for (int i = 1; i < pts.size(); i++)
            path.lineTo(pts.get(i).getX(), pts.get(i).getY());
        g2.draw(path);

        // arrowhead at destination
        Point2D b0 = pts.get(pts.size() - 1);
        Point2D b1 = pts.get(pts.size() - 2);
        double ang = Math.atan2(b0.getY() - b1.getY(), b0.getX() - b1.getX());
        double arrowLen = 18;
        double arrowAngle = 0.4;
        Path2D ah = new Path2D.Double();
        ah.moveTo(b0.getX(), b0.getY());
        ah.lineTo(b0.getX() - arrowLen * Math.cos(ang - arrowAngle), b0.getY() - arrowLen * Math.sin(ang - arrowAngle));
        ah.lineTo(b0.getX() - arrowLen * Math.cos(ang + arrowAngle), b0.getY() - arrowLen * Math.sin(ang + arrowAngle));
        ah.closePath();
        g2.fill(ah);

        // bus width label near the source
        if (showBitWidths) {
            g2.setFont(g2.getFont().deriveFont(9f));
            g2.setColor(theme.busLabel);
            Point sp = portPos(w.src);
            g2.drawString(w.src.width + "b", sp.x + 4, sp.y - 4);
        }

        // net name at wire midpoint
        if (showNetNames) {
            g2.setFont(g2.getFont().deriveFont(9f));
            g2.setColor(theme.netLabel);
            String netName = w.src.block.label() + "." + w.src.name;
            // Find midpoint of wire
            int midIdx = pts.size() / 2;
            Point2D mid = pts.get(midIdx);
            g2.drawString(netName, (int) mid.getX() + 3, (int) mid.getY() - 3);
        }

        // Draw waypoint handles when selected and has custom routing
        if (sel && w.hasCustomRoute()) {
            g2.setColor(theme.sel);
            for (Point wp : w.waypoints) {
                g2.fillRect(wp.x - 3, wp.y - 3, 6, 6);
            }
        }
    }

    private void paintPendingWire(Graphics2D g2) {
        Point a = portPos(wireFrom);
        g2.setColor(theme.sel);
        g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                10, new float[]{5, 4}, 0));
        g2.draw(new Line2D.Double(a.x, a.y, mouseModel.getX(), mouseModel.getY()));
    }

    private Color fillFor(Block b) {
        String t = b.type();
        if (t.equals("Scope") || t.equals("Spectrum")) return theme.scopeFill;
        if (t.equals("Constant") || t.equals("Impulse") || t.equals("Sine")) return theme.sourceFill;
        if (t.equals("Clock")) return theme.clockFill;
        return theme.blockFill;
    }

    private void paintBlock(Graphics2D g2, Block b) {
        boolean sel = selection.contains(b);

        // Sticky notes get special rendering
        if (b instanceof StickyNote) {
            paintStickyNote(g2, (StickyNote) b, sel);
            return;
        }

        Shape rr = new RoundRectangle2D.Double(b.x, b.y, b.w, b.h, 10, 10);
        g2.setColor(theme.shadow);
        g2.fill(new RoundRectangle2D.Double(b.x + 2, b.y + 3, b.w, b.h, 10, 10));
        g2.setColor(fillFor(b));
        g2.fill(rr);
        g2.setColor(sel ? theme.sel : theme.border);
        g2.setStroke(new BasicStroke(sel ? 2.2f : 1.2f));
        g2.draw(rr);

        // glyph
        g2.setColor(theme.glyph);
        g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
        String gl = b.glyph();
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(gl, b.x + (b.w - fm.stringWidth(gl)) / 2,
                b.y + (b.h + fm.getAscent() - fm.getDescent()) / 2);

        // caption (block name)
        if (showBlockNames) {
            g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
            g2.setColor(theme.caption);
            String lab = b.label();
            fm = g2.getFontMetrics();
            g2.drawString(lab, b.x + (b.w - fm.stringWidth(lab)) / 2, b.y + b.h + 13);
        }

        // ports
        for (Port p : b.inputs) paintPort(g2, p, b);
        for (Port p : b.outputs) paintPort(g2, p, b);
    }

    private void paintStickyNote(Graphics2D g2, StickyNote note, boolean sel) {
        // Shadow
        g2.setColor(theme.shadow);
        g2.fill(new Rectangle2D.Double(note.x + 3, note.y + 3, note.w, note.h));

        // Fill
        g2.setColor(theme.noteFill);
        g2.fill(new Rectangle2D.Double(note.x, note.y, note.w, note.h));

        // Border
        g2.setColor(sel ? theme.sel : theme.noteBorder);
        g2.setStroke(new BasicStroke(sel ? 2f : 1f));
        g2.draw(new Rectangle2D.Double(note.x, note.y, note.w, note.h));

        // Text with word wrap
        String text = note.text();
        if (!text.isEmpty()) {
            g2.setColor(theme.noteText);
            g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
            FontMetrics fm = g2.getFontMetrics();
            int lineH = fm.getHeight();
            int pad = 6;
            int maxW = note.w - pad * 2;
            int y = note.y + pad + fm.getAscent();
            int maxY = note.y + note.h - pad;

            for (String line : text.split("\n")) {
                // Word wrap each paragraph
                if (line.isEmpty()) {
                    y += lineH;
                    if (y > maxY) break;
                    continue;
                }
                String[] words = line.split(" ");
                StringBuilder current = new StringBuilder();
                for (String word : words) {
                    String test = current.length() == 0 ? word : current + " " + word;
                    if (fm.stringWidth(test) <= maxW) {
                        current = new StringBuilder(test);
                    } else {
                        if (current.length() > 0) {
                            g2.drawString(current.toString(), note.x + pad, y);
                            y += lineH;
                            if (y > maxY) break;
                        }
                        current = new StringBuilder(word);
                    }
                }
                if (current.length() > 0 && y <= maxY) {
                    g2.drawString(current.toString(), note.x + pad, y);
                    y += lineH;
                }
                if (y > maxY) break;
            }
        }

        // Resize handle (bottom-right corner triangle)
        int hx = note.x + note.w;
        int hy = note.y + note.h;
        g2.setColor(theme.noteBorder);
        int[] xpts = { hx - RESIZE_HANDLE, hx, hx };
        int[] ypts = { hy, hy - RESIZE_HANDLE, hy };
        g2.fillPolygon(xpts, ypts, 3);
    }

    private void paintPort(Graphics2D g2, Port p, Block b) {
        Point pos = portPos(p);
        boolean hot = p == hoverPort || p == wireFrom;
        int r = hot ? 5 : 4;

        if (p.isCE()) {
            // CE port: purple filled circle
            g2.setColor(hot ? theme.sel : theme.portCE);
            g2.fillOval(pos.x - r, pos.y - r, 2 * r, 2 * r);
            g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 8));
            g2.drawString("ce", pos.x + 5, pos.y + 8);
        } else if (p.input) {
            // Input port: blue hollow circle
            g2.setColor(hot ? theme.sel : theme.portInput);
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawOval(pos.x - r, pos.y - r, 2 * r, 2 * r);
            // Show signs for Sum block inputs
            if (b.type().equals("Sum")) {
                String signs = b.ps("signs", "++").replaceAll("[^+\\-]", "");
                int idx = b.inputs.indexOf(p);
                if (idx >= 0 && idx < signs.length()) {
                    g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
                    g2.setColor(signs.charAt(idx) == '-' ? new Color(0xDC2626) : new Color(0x16A34A));
                    g2.drawString(String.valueOf(signs.charAt(idx)), pos.x + 8, pos.y + 5);
                }
            }
            // Show port names for multi-input blocks (Scope, Spectrum)
            else if (hasMultipleInputs(b)) {
                g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 9));
                g2.setColor(theme.label);
                g2.drawString(p.name, pos.x + 10, pos.y + 4);
            }
        } else {
            // Output port: green filled circle
            g2.setColor(hot ? theme.sel : theme.portOutput);
            g2.fillOval(pos.x - r, pos.y - r, 2 * r, 2 * r);
        }

        // floating (unwired, non-CE) inputs get a subtle warning ring
        if (p.input && !p.isCE() && p.driver == null && !isWired(p)) {
            g2.setColor(theme.portFloat);
            g2.setStroke(new BasicStroke(1f));
            g2.drawOval(pos.x - 7, pos.y - 7, 14, 14);
        }
    }

    private boolean hasMultipleInputs(Block b) {
        int count = 0;
        for (Port p : b.inputs) if (!p.isCE()) count++;
        return count > 1;
    }

    private boolean isWired(Port p) {
        for (Wire w : diagram.wires) if (w.dst == p) return true;
        return false;
    }
}
