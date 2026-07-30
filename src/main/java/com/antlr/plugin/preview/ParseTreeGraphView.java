package com.antlr.plugin.preview;

import com.antlr.plugin.parsing.PreviewInterpreterRuleContext;
import com.intellij.ui.DarculaColors;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.Magnificator;
import org.antlr.v4.gui.TreeTextProvider;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.antlr.v4.runtime.tree.Tree;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Custom parse-tree canvas replacing {@code org.antlr.v4.gui.TreeViewer}.
 * Top-down box layout with scale, theme colors, and PNG/SVG export support.
 */
public class ParseTreeGraphView extends JPanel implements Magnificator {
    private static final int ARC = 8;
    private static final int PAD_X = 8;
    private static final int PAD_Y = 4;
    private static final int H_GAP = 16;
    private static final int V_GAP = 28;
    private static final double SCALE_MIN = 0.1;
    private static final double SCALE_MAX = 2.5;

    private Tree root;
    private TreeTextProvider textProvider = new DefaultRuleTreeTextProvider(Collections.emptyList());
    private double scale = 1.0;
    private final boolean highlightUnreachedNodes;
    private Color highlightedBoxColor = new JBColor(new Color(0xE0E0E0), new Color(0x3A5F3A));
    private final Set<Tree> highlightedNodes = Collections.newSetFromMap(new IdentityHashMap<>());

    private LayoutNode layoutRoot;
    private int contentWidth;
    private int contentHeight;
    private final Font nodeFont = new Font(Font.SANS_SERIF, Font.PLAIN, 12);

    public final ScaleModel scaleModel = new ScaleModel(1000);

    public ParseTreeGraphView(@Nullable List<String> ruleNames, @Nullable Tree tree, boolean highlightUnreachedNodes) {
        this.highlightUnreachedNodes = highlightUnreachedNodes;
        setOpaque(true);
        setBackground(JBColor.WHITE);
        if (ruleNames != null) {
            setRuleNames(ruleNames);
        }
        putClientProperty(Magnificator.CLIENT_PROPERTY_KEY, this);
        if (tree != null) {
            setTree(tree);
        }
    }

    public void setTree(@Nullable Tree root) {
        this.root = root;
        highlightedNodes.clear();
        rebuildLayout();
        revalidate();
        repaint();
    }

    public void setRuleNames(List<String> ruleNames) {
        this.textProvider = new DefaultRuleTreeTextProvider(ruleNames);
        rebuildLayout();
        revalidate();
        repaint();
    }

    public void setTreeTextProvider(TreeTextProvider provider) {
        this.textProvider = provider != null ? provider : new DefaultRuleTreeTextProvider(Collections.emptyList());
        rebuildLayout();
        revalidate();
        repaint();
    }

    public boolean hasTree() {
        return root != null && layoutRoot != null;
    }

    public double getScale() {
        return scale;
    }

    public void setScale(double scale) {
        scaleModel.setDoubleValue(scale);
    }

    private void applyScale(double scale) {
        this.scale = clamp(scale);
        revalidate();
        repaint();
    }

    public void setHighlightedBoxColor(Color color) {
        this.highlightedBoxColor = color;
        repaint();
    }

    public void addHighlightedNodes(Collection<? extends Tree> nodes) {
        if (nodes == null) {
            return;
        }
        for (Tree n : nodes) {
            if (n != null) {
                highlightedNodes.add(n);
            }
        }
        repaint();
    }

    /**
     * Hit-test a view-space point (component coordinates, including scale) against node boxes.
     * Prefers deeper (child) nodes when bounds overlap.
     */
    @Nullable
    public Tree getTreeAt(Point viewPoint) {
        if (!hasTree() || viewPoint == null) {
            return null;
        }
        double lx = viewPoint.x / scale;
        double ly = viewPoint.y / scale;
        return findTreeAt(layoutRoot, lx, ly);
    }

    /** Mark a single node as selected (replaces prior selection highlight). */
    public void setSelectedNode(@Nullable Tree tree) {
        highlightedNodes.clear();
        if (tree != null) {
            highlightedNodes.add(tree);
        }
        repaint();
    }

    /**
     * Select the deepest parse-tree node covering {@code offset} and scroll it into view
     * (Scroll from Source for the Parse tree tab).
     */
    public void selectNodeAtOffset(int offset) {
        if (!(root instanceof ParseTree) || layoutRoot == null) {
            return;
        }
        Tree hit = findNodeAtOffset(root, offset);
        if (hit == null) {
            return;
        }
        setSelectedNode(hit);
        LayoutNode layout = findLayoutNode(layoutRoot, hit);
        if (layout != null) {
            Rectangle viewRect = new Rectangle(
                    (int) Math.floor(layout.x * scale),
                    (int) Math.floor(layout.y * scale),
                    Math.max(1, (int) Math.ceil(layout.boxW * scale)),
                    Math.max(1, (int) Math.ceil(layout.boxH * scale)));
            scrollRectToVisible(viewRect);
        }
    }

    @Nullable
    private static Tree findNodeAtOffset(Tree tree, int offset) {
        if (tree instanceof ParserRuleContext ctx) {
            if (!inBounds(ctx, offset)) {
                return null;
            }
            for (int i = 0; i < tree.getChildCount(); i++) {
                Tree childHit = findNodeAtOffset(tree.getChild(i), offset);
                if (childHit != null) {
                    return childHit;
                }
            }
            return tree;
        }
        if (tree instanceof TerminalNode terminal) {
            Token symbol = terminal.getSymbol();
            if (symbol != null
                    && symbol.getStartIndex() <= offset
                    && symbol.getStopIndex() >= offset) {
                return tree;
            }
        }
        return null;
    }

    private static boolean inBounds(ParserRuleContext ctx, int offset) {
        Token start = ctx.getStart();
        Token stop = ctx.getStop();
        return start != null && stop != null
                && start.getStartIndex() <= offset
                && stop.getStopIndex() >= offset;
    }

    @Nullable
    private static LayoutNode findLayoutNode(LayoutNode node, Tree tree) {
        if (node.tree == tree) {
            return node;
        }
        for (LayoutNode child : node.children) {
            LayoutNode found = findLayoutNode(child, tree);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    @Nullable
    private static Tree findTreeAt(LayoutNode node, double x, double y) {
        for (int i = node.children.size() - 1; i >= 0; i--) {
            Tree hit = findTreeAt(node.children.get(i), x, y);
            if (hit != null) {
                return hit;
            }
        }
        if (x >= node.x && x < node.x + node.boxW && y >= node.y && y < node.y + node.boxH) {
            return node.tree;
        }
        return null;
    }

    @Override
    public Point magnify(double magnification, Point at) {
        setScale(getScale() * magnification);
        return at;
    }

    @Override
    public Dimension getPreferredSize() {
        if (!hasTree()) {
            return new Dimension(200, 100);
        }
        return new Dimension(Math.max(1, (int) Math.ceil(contentWidth * scale) + 20),
                Math.max(1, (int) Math.ceil(contentHeight * scale) + 20));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (!hasTree()) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.scale(scale, scale);
            paintEdges(g2, layoutRoot);
            paintNodes(g2, layoutRoot);
        } finally {
            g2.dispose();
        }
    }

    /** Write a lightweight SVG of the current layout (no Batik). */
    public void exportSvg(java.io.File file, boolean transparentBackground) throws IOException {
        if (!hasTree()) {
            return;
        }
        int w = Math.max(1, (int) Math.ceil(contentWidth * scale) + 20);
        int h = Math.max(1, (int) Math.ceil(contentHeight * scale) + 20);
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(w)
                .append("\" height=\"").append(h).append("\" viewBox=\"0 0 ")
                .append(w).append(' ').append(h).append("\">\n");
        if (!transparentBackground) {
            sb.append("<rect width=\"100%\" height=\"100%\" fill=\"white\"/>\n");
        }
        appendSvgEdges(sb, layoutRoot);
        appendSvgNodes(sb, layoutRoot);
        sb.append("</svg>\n");
        try (Writer out = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
            out.write(sb.toString());
        }
    }

    private void rebuildLayout() {
        layoutRoot = null;
        contentWidth = 0;
        contentHeight = 0;
        if (root == null) {
            return;
        }
        FontMetrics fm = getFontMetrics(nodeFont);
        layoutRoot = measure(root, fm);
        assignPositions(layoutRoot, 10, 10);
        contentWidth = (int) Math.ceil(layoutRoot.subtreeWidth + 20);
        contentHeight = maxBottom(layoutRoot) + 20;
    }

    private LayoutNode measure(Tree tree, FontMetrics fm) {
        String text = safeText(tree);
        String[] lines = text.split("\n", -1);
        int textW = 0;
        for (String line : lines) {
            textW = Math.max(textW, fm.stringWidth(line));
        }
        int boxW = textW + PAD_X * 2;
        int boxH = lines.length * fm.getHeight() + PAD_Y * 2;

        LayoutNode node = new LayoutNode(tree, text, lines, boxW, boxH);
        if (tree.getChildCount() == 0) {
            node.subtreeWidth = boxW;
            return node;
        }
        int childrenWidth = 0;
        for (int i = 0; i < tree.getChildCount(); i++) {
            LayoutNode child = measure(tree.getChild(i), fm);
            node.children.add(child);
            childrenWidth += child.subtreeWidth;
            if (i > 0) {
                childrenWidth += H_GAP;
            }
        }
        node.subtreeWidth = Math.max(boxW, childrenWidth);
        return node;
    }

    private void assignPositions(LayoutNode node, double left, double top) {
        node.x = left + (node.subtreeWidth - node.boxW) / 2.0;
        node.y = top;
        if (node.children.isEmpty()) {
            return;
        }
        double childTop = top + node.boxH + V_GAP;
        double cursor = left + (node.subtreeWidth - childrenSpan(node)) / 2.0;
        for (LayoutNode child : node.children) {
            assignPositions(child, cursor, childTop);
            cursor += child.subtreeWidth + H_GAP;
        }
    }

    private static double childrenSpan(LayoutNode node) {
        double w = 0;
        for (int i = 0; i < node.children.size(); i++) {
            w += node.children.get(i).subtreeWidth;
            if (i > 0) {
                w += H_GAP;
            }
        }
        return w;
    }

    private static int maxBottom(LayoutNode node) {
        int bottom = (int) Math.ceil(node.y + node.boxH);
        for (LayoutNode child : node.children) {
            bottom = Math.max(bottom, maxBottom(child));
        }
        return bottom;
    }

    private void paintEdges(Graphics2D g2, LayoutNode node) {
        g2.setColor(JBColor.GRAY);
        g2.setStroke(new BasicStroke(1f));
        double parentCx = node.x + node.boxW / 2.0;
        double parentBy = node.y + node.boxH;
        for (LayoutNode child : node.children) {
            double childCx = child.x + child.boxW / 2.0;
            double childTy = child.y;
            double midY = (parentBy + childTy) / 2.0;
            g2.drawLine((int) parentCx, (int) parentBy, (int) parentCx, (int) midY);
            g2.drawLine((int) parentCx, (int) midY, (int) childCx, (int) midY);
            g2.drawLine((int) childCx, (int) midY, (int) childCx, (int) childTy);
            paintEdges(g2, child);
        }
    }

    private void paintNodes(Graphics2D g2, LayoutNode node) {
        paintBox(g2, node);
        for (LayoutNode child : node.children) {
            paintNodes(g2, child);
        }
    }

    private void paintBox(Graphics2D g2, LayoutNode node) {
        Tree tree = node.tree;
        boolean failed = false;
        if (tree instanceof ParserRuleContext ctx) {
            failed = ctx.exception != null && ctx.stop != null && ctx.stop.getTokenIndex() < ctx.start.getTokenIndex();
        }
        boolean error = tree instanceof ErrorNode || failed;
        boolean highlighted = highlightedNodes.contains(tree);
        boolean unreached = highlightUnreachedNodes
                && tree instanceof PreviewInterpreterRuleContext ctx
                && !ctx.reached;

        RoundRectangle2D.Double box = new RoundRectangle2D.Double(node.x, node.y, node.boxW, node.boxH, ARC, ARC);

        if (highlighted || error) {
            g2.setColor(error ? DarculaColors.RED : highlightedBoxColor);
            g2.fill(box);
        } else {
            g2.setColor(new JBColor(new Color(0xF5F5F5), new Color(0x3C3F41)));
            g2.fill(box);
        }

        g2.setColor(JBColor.GRAY);
        g2.draw(box);

        if (unreached) {
            g2.setColor(JBColor.ORANGE);
            g2.draw(new RoundRectangle2D.Double(node.x, node.y, node.boxW - 1, node.boxH - 1, ARC, ARC));
        }

        g2.setFont(nodeFont);
        g2.setColor(error ? new JBColor(Color.DARK_GRAY, Color.LIGHT_GRAY) : JBColor.BLACK);
        FontMetrics fm = g2.getFontMetrics();
        int tx = (int) node.x + PAD_X;
        int ty = (int) node.y + PAD_Y + fm.getAscent();
        for (String line : node.lines) {
            g2.drawString(line, tx, ty);
            ty += fm.getHeight();
        }
    }

    private void appendSvgEdges(StringBuilder sb, LayoutNode node) {
        for (LayoutNode child : node.children) {
            double parentCx = (node.x + node.boxW / 2.0) * scale;
            double parentBy = (node.y + node.boxH) * scale;
            double childCx = (child.x + child.boxW / 2.0) * scale;
            double childTy = child.y * scale;
            double midY = (parentBy + childTy) / 2.0;
            sb.append("<polyline fill=\"none\" stroke=\"#888\" stroke-width=\"1\" points=\"")
                    .append(fmt(parentCx)).append(',').append(fmt(parentBy)).append(' ')
                    .append(fmt(parentCx)).append(',').append(fmt(midY)).append(' ')
                    .append(fmt(childCx)).append(',').append(fmt(midY)).append(' ')
                    .append(fmt(childCx)).append(',').append(fmt(childTy))
                    .append("\"/>\n");
            appendSvgEdges(sb, child);
        }
    }

    private void appendSvgNodes(StringBuilder sb, LayoutNode node) {
        Tree tree = node.tree;
        boolean failed = false;
        if (tree instanceof ParserRuleContext ctx) {
            failed = ctx.exception != null && ctx.stop != null && ctx.stop.getTokenIndex() < ctx.start.getTokenIndex();
        }
        boolean error = tree instanceof ErrorNode || failed;
        boolean highlighted = highlightedNodes.contains(tree);
        String fill = error ? "#ff6b6b" : (highlighted ? "#e0e0e0" : "#f5f5f5");
        double x = node.x * scale;
        double y = node.y * scale;
        double w = node.boxW * scale;
        double h = node.boxH * scale;
        sb.append("<rect x=\"").append(fmt(x)).append("\" y=\"").append(fmt(y))
                .append("\" width=\"").append(fmt(w)).append("\" height=\"").append(fmt(h))
                .append("\" rx=\"4\" fill=\"").append(fill).append("\" stroke=\"#888\"/>\n");
        double fontSize = 12 * scale;
        double ty = y + PAD_Y * scale + fontSize;
        for (String line : node.lines) {
            sb.append("<text x=\"").append(fmt(x + PAD_X * scale)).append("\" y=\"").append(fmt(ty))
                    .append("\" font-family=\"sans-serif\" font-size=\"").append(fmt(fontSize))
                    .append("\" fill=\"#000\">").append(escapeXml(line)).append("</text>\n");
            ty += fontSize * 1.2;
        }
        for (LayoutNode child : node.children) {
            appendSvgNodes(sb, child);
        }
    }

    private String safeText(Tree tree) {
        try {
            String t = textProvider.getText(tree);
            return t != null ? t : String.valueOf(tree);
        } catch (Exception e) {
            return String.valueOf(tree);
        }
    }

    private static String fmt(double v) {
        return String.format(java.util.Locale.US, "%.2f", v);
    }

    private static String escapeXml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static double clamp(double val) {
        if (val <= SCALE_MIN) return SCALE_MIN;
        if (val >= SCALE_MAX) return SCALE_MAX;
        return val;
    }

    private static final class LayoutNode {
        final Tree tree;
        final String text;
        final String[] lines;
        final int boxW;
        final int boxH;
        final List<LayoutNode> children = new ArrayList<>();
        double subtreeWidth;
        double x;
        double y;

        LayoutNode(Tree tree, String text, String[] lines, int boxW, int boxH) {
            this.tree = tree;
            this.text = text;
            this.lines = lines;
            this.boxW = boxW;
            this.boxH = boxH;
        }
    }

    public class ScaleModel extends DefaultBoundedRangeModel {
        ScaleModel(int ticks) {
            super(ticks / 2, 0, 1, ticks);
        }

        int range() {
            return getMaximum() - getMinimum();
        }

        double i2dTranslate(double val) {
            return val + (SCALE_MIN - (double) getMinimum());
        }

        double i2dScale(double val) {
            return val * ((SCALE_MAX - SCALE_MIN) / ((double) range()));
        }

        double d2iTranslate(double val) {
            return val + (((double) getMinimum()) - SCALE_MIN);
        }

        double d2iScale(double val) {
            return val * ((double) range()) / (SCALE_MAX - SCALE_MIN);
        }

        int computeIntValue(double doubleValue) {
            return Math.round((float) d2iTranslate(d2iScale(doubleValue)));
        }

        double computeDoubleValue() {
            return i2dScale(i2dTranslate(getValue()));
        }

        @Override
        public void setValue(int i) {
            super.setValue(i);
            ParseTreeGraphView.this.applyScale(computeDoubleValue());
        }

        public void setDoubleValue(double value) {
            setValue(computeIntValue(clamp(value)));
        }
    }
}
