package bi.two.chart;

import java.awt.Graphics2D;
import java.util.List;

public class ChartAreaPainter {

    public void paintChartArea(Graphics2D g2, ITicksData ticksData, Axe.AxeLong xAxe, Axe yAxe, long timeMin, long timeMax) {

    }

    //-----------------------------------------------------------------
    public static class TicksChartAreaPainter extends ChartAreaPainter {
        private final TickPainter m_tickPainter;

        public TicksChartAreaPainter(TickPainter tickPainter) {
            m_tickPainter = tickPainter;
        }

        @Override public void paintChartArea(Graphics2D g2, ITicksData ticksData, Axe.AxeLong xAxe, Axe yAxe, long timeMin, long timeMax) {
            List<? extends ITickData> ticks = ticksData.getTicks();
            synchronized (ticks) {
                ITickData prevTick = null;
                for (ITickData tick : ticks) {
                    long timestamp = tick.getTimestamp();
                    boolean timeAfterFrame = (timestamp > timeMax);
                    if (timeAfterFrame) {
                        // just skip pain
                    } else {
                        m_tickPainter.paintTick(g2, tick, prevTick, xAxe, yAxe);
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
