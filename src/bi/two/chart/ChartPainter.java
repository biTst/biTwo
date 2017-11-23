package bi.two.chart;

import bi.two.util.Utils;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

public class ChartPainter {
    private static final Date SHARED_DATE = new Date();
    public static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("y MM dd HH:mm:ss.S z");
    {
        DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT"));
    }

    public ChartPainter() {
    }

    public void paintChart(Graphics2D g2, ChartSetting chartSetting, ChartPaintSetting cps, ChartData chartData, Point crossPoint, Point selectPoint) {
        List<ChartAreaSettings> chartAreasSettings = chartSetting.getChartAreasSettings();

        // calc maxPriceAxeWidth and init xAxe
        Axe.AxeLong xAxe = cps.getXAxe();
        boolean tryInitAxe = !xAxe.isInitialized();
        long minTimestamp = Long.MAX_VALUE;
        long maxTimestamp = 0;
        int maxPriceAxeWidth = 1;
        for (ChartAreaSettings cas : chartAreasSettings) {
            List<ChartAreaLayerSettings> layers = cas.getLayers();
            for (ChartAreaLayerSettings lcas : layers) {
                String name = lcas.getName();
                ChartAreaData cad = chartData.getChartAreaData(name);
                if (cad != null) {
                    ITicksData ticksData = cad.getTicksData();
                    if (ticksData != null) {
                        if (tryInitAxe) {
                            List<? extends ITickData> ticks = ticksData.getTicks();
                            if (ticks != null) {
                                int size = ticks.size();
                                if (size > 0) {
                                    long oldestTimestamp = ticks.get(size - 1).getTimestamp();
                                    minTimestamp = Math.min(minTimestamp, oldestTimestamp);
                                    long newestTimestamp = ticks.get(0).getTimestamp();
                                    maxTimestamp = Math.max(maxTimestamp, newestTimestamp);
                                }
                            }
                        }
                        int width = cad.getPriceAxeWidth();
                        maxPriceAxeWidth = Math.max(maxPriceAxeWidth, width);
                    }
                }
            }
        }
        cps.setPriceAxeWidth(maxPriceAxeWidth);

        int width = cps.getWidth();
        int height = cps.getHeight();

        if (tryInitAxe) {
            if (maxTimestamp != 0) {
                int dstMax = width - maxPriceAxeWidth - 1;
                xAxe.init(minTimestamp, maxTimestamp, 0, dstMax);
            }
        }

        if(!xAxe.isInitialized()) {
            return;
        }

        paintTimeAxe(g2, cps);

        int timeAxeHeight = cps.getTimeAxeHeight();
        int chartsHeight = height - timeAxeHeight;

        // paint areas
        for (ChartAreaSettings cas : chartAreasSettings) {
            int paintLeft = (int) (cas.getLeft() * width);
            int paintWidth = (int) (cas.getWidth() * width);
            int paintTop = (int) (cas.getTop() * chartsHeight);
            int paintHeight = (int) (cas.getHeight() * chartsHeight);

            ChartAreaPaintSetting caps = new ChartAreaPaintSetting(paintLeft, paintWidth, paintTop, paintHeight);

            long timeMin = (long) xAxe.translateReverse(0);
            int priceAxeWidth = cps.getPriceAxeWidth();
            int timeAxeWidth = width - priceAxeWidth - 1;
            long timeMax = (long) xAxe.translateReverse(timeAxeWidth);

            float minPrice = Float.POSITIVE_INFINITY;
            float maxPrice = Float.NEGATIVE_INFINITY;
            List<ChartAreaLayerSettings> layers = cas.getLayers();
            for (ChartAreaLayerSettings layer : layers) {
                String name = layer.getName();
                ChartAreaData cad = chartData.getChartAreaData(name);
                if (cad != null) {
                    ITicksData ticksData = cad.getTicksData();
                    if (ticksData != null) {
                        List<? extends ITickData> ticks = ticksData.getTicks();
                        if (ticks != null) {
                            synchronized (ticks) {
                                for (ITickData tick : ticks) {
                                    long timestamp = tick.getTimestamp();
                                    if ((timestamp >= timeMin) && (timestamp <= timeMax)) { // fit horizontally ?
                                        float min = tick.getMinPrice();
                                        float max = tick.getMaxPrice();

                                        if ((min != Utils.INVALID_PRICE) && !Float.isInfinite(min) && !Float.isInfinite(max)) {
                                            maxPrice = Math.max(maxPrice, max);
                                            minPrice = Math.min(minPrice, min);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (!Float.isInfinite(minPrice) && !Float.isInfinite(maxPrice)) {
                float diff = maxPrice - minPrice;
                float extra = diff * 0.05f;
                minPrice -= extra;
                maxPrice += extra;
            }
            caps.initYAxe(minPrice, maxPrice);

            paintChartArea(g2, cas, cps, caps, chartData, crossPoint);
        }

        if (crossPoint != null) {
            if (selectPoint != null) {
                paintLine(g2, selectPoint, crossPoint);
            }
            paintCross(g2, crossPoint, width, height);
        }
    }

    private void paintLine(Graphics2D g2, Point selectPoint, Point crossPoint) {
        int x1 = (int) selectPoint.getX();
        int y1 = (int) selectPoint.getY();
        int x2 = (int) crossPoint.getX();
        int y2 = (int) crossPoint.getY();

        g2.setColor(Color.LIGHT_GRAY);
        g2.drawLine(x1, y1, x2, y2);
    }

    private void paintCross(Graphics2D g2, Point crossPoint, int width, int height) {
        int x = (int) crossPoint.getX();
        int y = (int) crossPoint.getY();

        g2.setColor(Color.LIGHT_GRAY);
        g2.drawLine(x, 0, x, height);
        g2.drawLine(0, y, width, y);
    }

    private void paintChartArea(Graphics2D g2, ChartAreaSettings cas, ChartPaintSetting cps, ChartAreaPaintSetting caps,
                                ChartData chartData, Point crossPoint) {
        int paintLeft = caps.getPaintLeft();
        int paintWidth = caps.getPaintWidth();
        int paintTop = caps.getPaintTop();
        int paintHeight = caps.getPaintHeight();

        int paintBottom = paintTop + paintHeight;

        Color color = cas.getColor();
        g2.setColor(color);

        g2.drawRect(paintLeft, paintTop, paintWidth - 1, paintHeight - 1);
        int fontHeight = g2.getFontMetrics().getHeight();
        g2.drawString(cas.getName(), paintLeft + 10, paintTop + fontHeight);

        // paint PriceAxe
        int paintRight = paintLeft + paintWidth - 1;
        int priceAxeWidth = cps.getPriceAxeWidth();
        int priceRight = paintRight - priceAxeWidth;

        g2.setColor(Color.PINK);
        g2.drawRect(priceRight, paintTop + 1, priceAxeWidth, paintHeight - 3);

        g2.clipRect(priceRight, paintTop + 1, priceAxeWidth, paintHeight - 3);
        Axe yAxe = caps.getYAxe();
        yAxe.paintYAxe(g2, paintRight, Color.PINK);
        g2.setClip(null);

        // paint zero line
        double horizont = cas.getHorizontalLineValue();
        int zero = yAxe.translateInt(horizont);
        if ((zero > paintTop) && (zero < paintBottom)) {
            g2.setColor(Color.GRAY);
            g2.drawRect(paintLeft, zero, paintWidth - priceAxeWidth, zero);
        }

        Axe.AxeLong xAxe = cps.getXAxe();
        if(xAxe.isInitialized()) {
            long timeMin = (long) xAxe.translateReverse(0);
            long timeMax = (long) xAxe.translateReverse(priceRight);

            // paint layers
            List<ChartAreaLayerSettings> layers = cas.getLayers();
            for (ChartAreaLayerSettings ls : layers) {
                String name = ls.getName();
                ChartAreaData cad = chartData.getChartAreaData(name);
                if (cad != null) {
                    ITicksData ticksData = cad.getTicksData();
                    if (ticksData != null) {
                        Color layerColor = ls.getColor();
                        g2.setColor(layerColor);

                        ChartAreaPainter chartAreaPainter = ls.getChartAreaPainter();
                        chartAreaPainter.paintChartArea(g2, ticksData, xAxe, yAxe, timeMin, timeMax, crossPoint);
                    }
                }
            }
        }
    }
    
    private void paintTimeAxe(Graphics2D g2, ChartPaintSetting cps) {
        int width = cps.getWidth();
        int height = cps.getHeight();
        int timeAxeHeight = cps.getTimeAxeHeight();
        int priceAxeWidth = cps.getPriceAxeWidth();

        g2.setColor(Color.MAGENTA);
        int timeAxeY = height - timeAxeHeight;
        int timeAxeWidth = width - priceAxeWidth - 1;
        g2.drawRect(0, timeAxeY, timeAxeWidth, timeAxeHeight - 1);

        int textY = height - 2;

        Axe.AxeLong timeAxe = cps.getXAxe();
        if(!timeAxe.isInitialized()) {
            return;
        }

        long timeMin = (long) timeAxe.translateReverse(0);
        long timeMax = (long) timeAxe.translateReverse(timeAxeWidth);

        FontMetrics fontMetrics = g2.getFontMetrics();

        List<TimeAxeMarker> markers = new ArrayList<TimeAxeMarker>();

        for (TimeAxeLevel level : TimeAxeLevel.values()) {
            long start = level.roundUp(timeMin);
            int startX = timeAxe.translateInt(start);
            long end = level.roundDown(timeMax);
            int endX = timeAxe.translateInt(end);
//            int dstMin = timeAxe.getDstMin();
//            double srcMin = timeAxe.getSrcMin();
            boolean startMarkerOnScreen = (startX >= 0) && (startX <= timeAxeWidth);
            boolean endMarkerOnScreen = (endX >= 0) && (endX <= timeAxeWidth);
            long timeGap = end - start;
            long period = level.getPeriod();
            int marketsSteps = (int) (timeGap/ period);
            if (startMarkerOnScreen || endMarkerOnScreen || (marketsSteps > 1)) {
                if(!startMarkerOnScreen) {
                    start += period;
                }
                if(!endMarkerOnScreen) {
                    end -= period;
                }
                int startSize = markers.size();
                int markersAdded = 0;
                boolean fineGrainedSkipped = false;
                for (long time = start; time <= end; time = level.add(time)) {
                    int markerX = timeAxe.translateInt(time);
                    String markerLabel = level.format(time);
                    int markerLabelWidth = fontMetrics.stringWidth(markerLabel);

                    boolean canFit = true;
                    for (TimeAxeMarker marker : markers) {
                        int xDiff = Math.abs(marker.getMarkerX() - markerX);
                        int gap = xDiff * 2 - marker.getWidth() - markerLabelWidth;
                        if (gap < 4) { // can NOT fit
                            fineGrainedSkipped |= (time == marker.getMarkerTime());
                            canFit = false;
                            break;
                        }
                    }
                    if (canFit) {
                        TimeAxeMarker timeAxeMarker = new TimeAxeMarker(time, markerX, markerLabelWidth, markerLabel);
                        markers.add(timeAxeMarker);
                        markersAdded++;
                    }
                }
                if ((startSize > 0) && (markersAdded == 0) && !fineGrainedSkipped) { // no more markers can fit
                    break;
                }
            }
        }

        for (TimeAxeMarker marker : markers) {
            int markerX = marker.getMarkerX();
            g2.drawString(marker.getLabel(), markerX - marker.getWidth() / 2, textY);
            g2.drawLine(markerX, timeAxeY, markerX, timeAxeY - 3);
        }

        int labelsY = timeAxeY - 2;

        String timeMinStr = formatDate(timeMin);
        g2.drawString(timeMinStr, 2, labelsY);
        String timeMaxStr = formatDate(timeMax);
        g2.drawString(timeMaxStr, timeAxeWidth - fontMetrics.stringWidth(timeMaxStr), labelsY);
    }

    private static synchronized String formatDate(long time) {
        SHARED_DATE.setTime(time);
        String str = DATE_FORMAT.format(SHARED_DATE);
        return str;
    }



    // --------------------------------------------------
    public static class TimeAxeMarker {
        private final long m_markerTime;
        private final int m_markerX;
        private final int m_width;
        private final String m_label;

        public long getMarkerTime() { return m_markerTime; }
        public int getMarkerX() { return m_markerX; }
        public int getWidth() { return m_width; }
        public String getLabel() { return m_label; }

        public TimeAxeMarker(long markerTime, int markerX, int width, String label) {
            m_markerTime = markerTime;
            m_markerX = markerX;
            m_width = width;
            m_label = label;
        }
    }

}
