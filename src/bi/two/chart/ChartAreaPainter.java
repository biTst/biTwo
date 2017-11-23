package bi.two.chart;

import bi.two.ts.TimesSeriesData;
import bi.two.util.Utils;

import java.awt.Graphics2D;
import java.awt.Point;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ChartAreaPainter {

    public void paintChartArea(Graphics2D g2, ITicksData ticksData, Axe.AxeLong xAxe, Axe yAxe, long timeMin, long timeMax, Point crossPoint) {

    }

    //-----------------------------------------------------------------
    public static class SplineChartAreaPainter extends ChartAreaPainter {
        private final TimesSeriesData m_ticksTs;

        public SplineChartAreaPainter(TimesSeriesData ticksTs) {
            m_ticksTs = ticksTs;
        }

        @Override public void paintChartArea(Graphics2D g2, ITicksData ticksData, Axe.AxeLong xAxe, Axe yAxe, long timeMin, long timeMax, Point crossPoint) {
            List<? extends ITickData> ticks = m_ticksTs.getTicks();
            synchronized (ticks) {
                if (crossPoint != null) {
                    int crossX = crossPoint.x;
                    int tickIndex3 = findTickIndexFromX(ticks, crossX, xAxe);
                    if (tickIndex3 > 0) {
                        ITickData iTickData = ticks.get(tickIndex3);
                        long timestamp3 = iTickData.getTimestamp();
                        long timestamp2 = timestamp3 - Utils.MIN_IN_MILLIS;
                        int tickIndex2 = findTickIndexFromMillis(ticks, timestamp2);
                        if (tickIndex2 > 0) {
                            long timestamp1 = timestamp2 - Utils.MIN_IN_MILLIS;
                            int tickIndex1 = findTickIndexFromMillis(ticks, timestamp1);
                            if (tickIndex1 > 0) {
                                drawLine(g2, ticks, xAxe, yAxe, tickIndex1, tickIndex2);
                                drawLine(g2, ticks, xAxe, yAxe, tickIndex2, tickIndex3);
                            }
                        }
                    }
                }
            }
        }

        private void drawLine(Graphics2D g2, List<? extends ITickData> ticks, Axe.AxeLong xAxe, Axe yAxe, int tickIndex1, int tickIndex2) {
            ITickData tick1 = ticks.get(tickIndex1);
            ITickData tick2 = ticks.get(tickIndex2);

            long millis1 = tick1.getTimestamp();
            long millis2 = tick2.getTimestamp();

            int x1 = xAxe.translateInt(millis1);
            int x2 = xAxe.translateInt(millis2);

            float value1 = tick1.getClosePrice();
            float value2 = tick2.getClosePrice();

            int y1 = yAxe.translateInt(value1);
            int y2 = yAxe.translateInt(value2);

            g2.drawLine(x1, y1, x2, y2);
        }
    }

    //-----------------------------------------------------------------
    public static class TicksChartAreaPainter extends ChartAreaPainter {
        private final TickPainter m_tickPainter;

        public TicksChartAreaPainter(TickPainter tickPainter) {
            m_tickPainter = tickPainter;
        }

        @Override public void paintChartArea(Graphics2D g2, ITicksData ticksData, Axe.AxeLong xAxe, Axe yAxe, long timeMin, long timeMax, Point crossPoint) {
            List<? extends ITickData> ticks = ticksData.getTicks();
            int highlightTickIndex = -1;
            synchronized (ticks) {
                if (crossPoint != null) {
                    int crossX = crossPoint.x;
                    highlightTickIndex = findTickIndexFromX(ticks, crossX, xAxe);
                }


                ITickData prevTick = null;
                int size = ticks.size();
                for (int i = 0; i < size; i++) {
                    ITickData tick = ticks.get(i);
                    long timestamp = tick.getTimestamp();
                    boolean timeAfterFrame = (timestamp > timeMax);
                    if (!timeAfterFrame) {
                        boolean highlightTick = (i == highlightTickIndex);
                        m_tickPainter.paintTick(g2, tick, prevTick, xAxe, yAxe, highlightTick);
                        boolean timeBeforeFrame = (timestamp < timeMin);
                        if (timeBeforeFrame) {
                            break;
                        }
                    }
                    prevTick = tick;
                }
            }
        }
    }

    private static int findTickIndexFromX(List<? extends ITickData> ticks, int crossX, Axe.AxeLong xAxe) {
        int highlightTickIndex;
        highlightTickIndex = Collections.binarySearch(ticks, null, new Comparator<ITickData>() {
            @Override public int compare(ITickData td1, ITickData td2) {
                long millis = td1.getTimestamp();
                double tickX = xAxe.translateDouble(millis);
                return tickX > crossX
                        ? -1
                        : tickX < crossX
                            ? 1
                            : 0;
            }
        });
        if (highlightTickIndex < 0) {
            highlightTickIndex = -highlightTickIndex - 1;
        }
        if (highlightTickIndex >= 1) {
            ITickData tick1 = ticks.get(highlightTickIndex);
            ITickData tick2 = ticks.get(highlightTickIndex - 1);

            long timestamp1 = tick1.getTimestamp();
            double x1 = xAxe.translateDouble(timestamp1);
            long timestamp2 = tick2.getTimestamp();
            double x2 = xAxe.translateDouble(timestamp2);

            double diff1 = Math.abs(x1 - crossX);
            double diff2 = Math.abs(x2 - crossX);
            if (diff2 < diff1) {
                highlightTickIndex--;
            }
        }
        return highlightTickIndex;
    }

    private static int findTickIndexFromMillis(List<? extends ITickData> ticks, long millis) {
        int highlightTickIndex;
        highlightTickIndex = Collections.binarySearch(ticks, null, new Comparator<ITickData>() {
            @Override public int compare(ITickData td1, ITickData td2) {
                long tickMillis = td1.getTimestamp();
                return tickMillis > millis
                        ? -1
                        : tickMillis < millis
                            ? 1
                            : 0;
            }
        });
        if (highlightTickIndex < 0) {
            highlightTickIndex = -highlightTickIndex - 1;
        }
        if ((highlightTickIndex >= 1) && (highlightTickIndex > ticks.size())) {
            ITickData tick1 = ticks.get(highlightTickIndex);
            ITickData tick2 = ticks.get(highlightTickIndex - 1);

            long timestamp1 = tick1.getTimestamp();
            long timestamp2 = tick2.getTimestamp();

            long diff1 = Math.abs(timestamp1 - millis);
            long diff2 = Math.abs(timestamp2 - millis);
            if (diff2 < diff1) {
                highlightTickIndex--;
            }
        }
        return highlightTickIndex;
    }
}
