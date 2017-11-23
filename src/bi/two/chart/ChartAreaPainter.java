package bi.two.chart;

import bi.two.ts.TimesSeriesData;

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
//                    System.out.println("crossX=" + crossX + " => index = " + highlightTickIndex);
                }

                ITickData prevTick = null;
                int size = ticks.size();

                if (highlightTickIndex >= 1) {
                    int crossX = crossPoint.x;

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
}
