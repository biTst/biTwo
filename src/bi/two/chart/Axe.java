package bi.two.chart;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.text.NumberFormat;

public abstract class Axe<Src extends Number> {
    public static final int AXE_MARKER_WIDTH = 10;

    private boolean m_isInitialized;
    private Src m_srcMin;
    private Src m_srcMax;
    private Src m_srcDiff; // src min<->max

    private int m_dstMin;
    private int m_dstMax;
    private int m_dstDiff;

    private double m_scale;
    private NumberFormat m_formatter;

    protected abstract Src newSrc(double value);

    public Axe() {
        m_isInitialized = false;
    }

    public Axe(Src srcMin, Src srcMax, int dstMin, int dstMax) {
        init(srcMin, srcMax, dstMin, dstMax);
    }

    public boolean isInitialized() { return m_isInitialized; }
    public double getScale() { return m_scale; }
    public Src getSrcMin() { return m_srcMin; }
    public int getDstMin() { return m_dstMin; }
    public NumberFormat getFormatter() { return m_formatter; }

    public void init(Src srcMin, Src srcMax, int dstMin, int dstMax) {
        m_srcMin = srcMin;
        m_srcMax = srcMax;
        m_dstMin = dstMin;
        m_dstMax = dstMax;
        updateInt();
        m_isInitialized = true;
    }

    protected void updateInt() {
        m_srcDiff = newSrc(m_srcMax.doubleValue() - m_srcMin.doubleValue());
        m_dstDiff = m_dstMax - m_dstMin;
        m_scale = m_srcDiff.doubleValue() / m_dstDiff;
    }

    public void resize(int xDelta) {
        m_dstMax += xDelta;
        m_srcMin = newSrc(m_srcMin.doubleValue() - (xDelta * m_scale));
        updateInt();
    }

    public void shift(int xDelta) {
        double xDeltaScaled = xDelta * m_scale;
        m_srcMin = newSrc(m_srcMin.doubleValue() - xDeltaScaled);
        m_srcMax = newSrc(m_srcMax.doubleValue() - xDeltaScaled);
        updateInt();
    }

    public void zoom(boolean in, int destZoomPoint) {
        double srcZoomPoint = translateReverse(destZoomPoint);
        float rate = in ? 1.1f : 0.9f;
        double newSrcMin = srcZoomPoint - (srcZoomPoint - m_srcMin.doubleValue()) * rate;
        m_srcMin = newSrc(newSrcMin);

        double newSrcMax = srcZoomPoint + (m_srcMax.doubleValue() - srcZoomPoint) * rate;
        m_srcMax = newSrc(newSrcMax);

        double newSrcDiff = newSrcMax - newSrcMin;
        m_srcDiff = newSrc(newSrcDiff);
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
        double srcOffset = value - m_srcMin.doubleValue();
        double ret = srcOffset / m_scale;
        return ret;
    }

    public double translateReverse(double dest) {
        double dstOffset = dest - m_dstMin;
        double value = getValue(dstOffset);
        return value;
    }

    private double getValue(double dstOffset) {
        double offset = dstOffset * m_scale;
        double value = offset + m_srcMin.doubleValue();
        return value;
    }


    public int paintYAxe(Graphics g, int right, Color color) {
        g.setColor(color);

        int fontHeight = g.getFont().getSize();
        int halfFontHeight = fontHeight / 2;

        int maxLabelsCount = Math.abs(m_dstDiff) * 3 / fontHeight / 4;
        double maxLabelsStep = m_srcDiff.doubleValue() / maxLabelsCount;
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

        double minLabel = Math.floor(m_srcMin.doubleValue() / step) * step;
        double maxLabel = Math.ceil(m_srcMax.doubleValue() / step) * step;

        m_formatter = NumberFormat.getInstance();
        m_formatter.setMaximumFractionDigits(points);
        m_formatter.setMinimumFractionDigits(points);

        FontMetrics fontMetrics = g.getFontMetrics();
        int maxWidth = 10;
        for (double y = minLabel; y <= maxLabel; y += step) {
            String str = m_formatter.format(y);
            Rectangle2D bounds = fontMetrics.getStringBounds(str, g);
            int stringWidth = (int) Math.ceil(bounds.getWidth());
            maxWidth = Math.max(maxWidth, stringWidth);
        }

        int x = right - maxWidth;

        for (double val = minLabel; val <= maxLabel; val += step) {
            String str = m_formatter.format(val);
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


    //--------------------------------------------------------------
    public static class AxeLong extends Axe<Long> {
        @Override protected Long newSrc(double value) {
            return new Long((long) value);
        }
    }


    //--------------------------------------------------------------
    public static class AxeFloat extends Axe<Float> {
        @Override protected Float newSrc(double value) {
            return new Float(value);
        }
    }


    //--------------------------------------------------------------
    public static class AxeDouble extends Axe<Double> {
        @Override protected Double newSrc(double value) {
            return new Double(value);
        }
    }
}
