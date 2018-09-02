package bi.two.ts;

import bi.two.chart.ITickData;
import bi.two.chart.TickData;
import bi.two.util.Log;

public class TickJoiner extends TicksTimesSeriesData {
    private final long m_size;
    private long m_first;
    private long m_last;
    private long m_end;
    private float m_summ;
    private int m_count;
    private ITickData m_firstTick;
    private int m_joinedCount;
    private int m_reportedCount;

    private ITickData m_latestTick;

    private static void console(String s) { Log.console(s); }

    @Override public ITickData getLatestTick() { return m_latestTick; }

    public TickJoiner(ITimesSeriesData tsd, long size) {
        super(tsd);
        m_size = size;
    }

    @Override public void onChanged(ITimesSeriesData ts, boolean changed) {
        if(changed) {
            ITimesSeriesData parent = getParent();
            ITickData tickData = parent.getLatestTick();
            float price = tickData.getClosePrice();
            long timestamp = tickData.getTimestamp();

            if (timestamp < m_end) {
                m_last = timestamp;
                m_summ += price;
                m_count++;
            } else {
                reportTick();
                m_firstTick = tickData;
                m_first = timestamp;
                m_last = timestamp;
                m_summ = price;
                m_count = 1;
                m_end = timestamp + m_size;
            }
        }
        super.onChanged(ts, changed);
    }

    private void reportTick() {
        if (m_count > 1) {
            long avdTimestamp = (m_first + m_last) / 2;
            float avgPrice = m_summ / m_count;
            TickData avgTickData = new TickData(avdTimestamp, avgPrice);
            m_latestTick = avgTickData;
            notifyListeners(true);
            m_joinedCount += m_count;
            m_reportedCount++;
        } else if (m_count == 1) {
            m_latestTick = m_firstTick;
            notifyListeners(true);
            m_joinedCount += 1;
            m_reportedCount++;
        }
    }

    @Override public void notifyNoMoreTicks() {
        reportTick();
        console("TickJoiner[" + m_size + "ms]: reportedCount=" + m_reportedCount + "; joinedCount=" + m_joinedCount + "; rate=" + (((float) m_joinedCount) / m_reportedCount));
        super.notifyNoMoreTicks();
    }
}
