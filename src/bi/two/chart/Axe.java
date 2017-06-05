package bi.two.chart;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.text.NumberFormat;

public class Axe {
    public static final int AXE_MARKER_WIDTH = 10;

    private boolean m_isInitialized;
    private double m_srcMin;
    private double m_srcMax;
    private int m_dstMin;
    private int m_dstMax;
    private double m_scale;
    private int m_size;
    private double m_diff; // src min<->max

    public Axe() {
        m_isInitialized = false;
    }

    public Axe(float srcMin, float srcMax, int dstMin, int dstMax) {
        init(srcMin, srcMax, dstMin, dstMax);
    }

    public boolean isInitialized() { return m_isInitialized; }
    public double getScale() { return m_scale; }
    public double getSrcMin() { return m_srcMin; }
    public int getDstMin() { return m_dstMin; }

    public void init(double srcMin, double srcMax, int dstMin, int dstMax) {
        m_srcMin = srcMin;
        m_srcMax = srcMax;
        m_dstMin = dstMin;
        m_dstMax = dstMax;
        updateInt();
        m_isInitialized = true;
    }

    protected void updateInt() {
        m_size = m_dstMax - m_dstMin;
        m_diff = m_srcMax - m_srcMin;
        m_scale = (m_srcMax - m_srcMin) / (m_dstMax - m_dstMin);
    }

    public void resize(int xDelta) {
        m_dstMax += xDelta;
        m_srcMin -= (xDelta * m_scale);
        updateInt();
    }

    public double translateDouble(double value) {
        double dstOffset = getDstOffset(value);
        return m_dstMin + dstOffset;
    }


    public int translateInt(double value) {
        double ret = translateDouble(value);
        return (int) ret;
    }

    public double getDstOffset(double value) {
        double srcOffset = value - m_srcMin;
        double ret = srcOffset / m_scale;
        return ret;
    }

    public double translateReverse(int dest) {
        int dstOffset = dest - m_dstMin;
        double value = getValue(dstOffset);
        return value;
    }

    private double getValue(int dstOffset) {
        double offset = dstOffset * m_scale;
        double value = offset + m_srcMin;
        return value;
    }


    public int paintYAxe(Graphics g, int right, Color color) {
        g.setColor(color);

        int fontHeight = g.getFont().getSize();
        int halfFontHeight = fontHeight / 2;

        int maxLabelsCount = Math.abs(m_size) * 3 / fontHeight / 4;
        double maxLabelsStep = m_diff / maxLabelsCount;
        double log = Math.log10(maxLabelsStep);
        int floor = (int) Math.floor(log);
        int points = Math.max(0, -floor);
        double pow = Math.pow(10, floor);
        double mant = maxLabelsStep / pow;
        int stepMant;
        if (mant == 1) {
            stepMant = 1;
        } else if (mant <= 2) {
            stepMant = 2;
        } else if (mant <= 5) {
            stepMant = 5;
        } else {
            stepMant = 1;
            floor++;
            pow = Math.pow(10, floor);
        }
        double step = stepMant * pow;

        double minLabel = Math.floor(m_srcMin / step) * step;
        double maxLabel = Math.ceil(m_srcMax / step) * step;

        NumberFormat nf = NumberFormat.getInstance();
        nf.setMaximumFractionDigits(points);
        nf.setMinimumFractionDigits(points);

        FontMetrics fontMetrics = g.getFontMetrics();
        int maxWidth = 10;
        for (double y = minLabel; y <= maxLabel; y += step) {
            String str = nf.format(y);
            Rectangle2D bounds = fontMetrics.getStringBounds(str, g);
            int stringWidth = (int) Math.ceil(bounds.getWidth());
            maxWidth = Math.max(maxWidth, stringWidth);
        }

        int x = right - maxWidth;

        for (double val = minLabel; val <= maxLabel; val += step) {
            String str = nf.format(val);
            int y = translateInt(val);  // getPointReverse(val);
            g.drawString(str, x, y + halfFontHeight);
            g.drawLine(x - 2, y, x - AXE_MARKER_WIDTH, y);
        }

//        g.drawString("h" + height, x, fontHeight * 2);
//        g.drawString("m" + maxLabelsCount, x, fontHeight * 3);
//        g.drawString("d" + diff, x, fontHeight * 4);
//        g.drawString("m" + maxLabelsStep, x, fontHeight * 5);
//        g.drawString("l" + log, x, fontHeight * 6);
//        g.drawString("f" + floor, x, fontHeight * 7);
//        g.drawString("p" + pow, x, fontHeight * 8);
//        g.drawString("m" + mant, x, fontHeight * 9);
//        g.drawString("s" + stepMant, x, fontHeight * 11);
//        g.drawString("f" + floor, x, fontHeight * 12);
//        g.drawString("p" + pow, x, fontHeight * 13);
//        g.drawString("s" + step, x, fontHeight * 14);
//        g.drawString("p" + points, x, fontHeight * 15);
//
//        g.drawString("ma" + maxLabel, x, fontHeight * 17);
//        g.drawString("mi" + minLabel, x, fontHeight * 18);

        return maxWidth + AXE_MARKER_WIDTH + 2;
    }

    public void zoom(boolean in) {
        double newDiff = m_diff * (in ? 1.1f : 0.9f);
        double newSrcMin = m_srcMax - newDiff;
        m_srcMin = newSrcMin;
        updateInt();
    }
}
