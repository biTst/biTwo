package bi.two.ts;

import bi.two.chart.ITickData;

import static bi.two.util.Log.console;

public class CloseTickJoiner extends TicksTimesSeriesData<ITickData> {
    private final long m_size;
    private long m_end;
    private int m_count;
    private int m_joinedCount;
    private int m_reportedCount;

    private ITickData m_lastParentTick;

    private ITickData m_latestTick; // value

    @Override public ITickData getLatestTick() { return m_latestTick; }

    public CloseTickJoiner(ITimesSeriesData tsd, long size) {
        super(tsd);
        m_size = size;
    }

    @Override public void onChanged(ITimesSeriesData ts, boolean changed) {
        if (changed) {
            ITickData tickData = m_parent.getLatestTick();
            long timestamp = tickData.getTimestamp();
            if (timestamp < m_end) {
                m_count++;
            } else {
                if (m_count > 0) {
                    reportTick();
                }
                m_count = 1;
                m_end = timestamp + m_size;
            }
            m_lastParentTick = tickData;
        }
    }

    private void reportTick() {
        m_latestTick = m_lastParentTick;
        notifyListeners(true);
        m_joinedCount += m_count;
        m_reportedCount++;
    }

    @Override public void notifyNoMoreTicks() {
        reportTick();
        console("CloseTickJoiner[" + m_size + "ms]: reportedCount=" + m_reportedCount + "; joinedCount=" + m_joinedCount + "; rate=" + (((float) m_joinedCount) / m_reportedCount));
        super.notifyNoMoreTicks();
    }
}
