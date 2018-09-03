package bi.two.ts;

import bi.two.chart.ITickData;
import bi.two.chart.TickData;
import bi.two.util.Log;

/** join ticks; provides average price (do not count tick volume) */
public class TickJoinerTimesSeriesData extends BaseTimesSeriesData {
    private ITickData m_lastTick;

    private final long m_size;
    private long m_first;
    private long m_last;
    private long m_end;
    private float m_summ;
    private int m_count;
    private ITickData m_firstTick;
    private int m_joinedCount;
    private int m_reportedCount;

    protected static void log(String s) { Log.log(s); }

    public TickJoinerTimesSeriesData(ITimesSeriesData tsd, long size) {
        super(tsd);
        m_size = size;
    }

    @Override protected void notifyListeners(boolean changed) {
        boolean myChanged = false;
        if (changed) {
            ITimesSeriesData parent = getParent();
            ITickData latestTick = parent.getLatestTick();

            float price = latestTick.getClosePrice();
            long timestamp = latestTick.getTimestamp();

            if (timestamp < m_end) {
                m_last = timestamp;
                m_summ += price;
                m_count++;
            } else {
                if (m_count > 1) { // reportTick
                    long avdTimestamp = (m_first + m_last) / 2; // mid time
                    float avgPrice = m_summ / m_count;
                    TickData avgTickData = new TickData(avdTimestamp, avgPrice);
                    m_lastTick = avgTickData;
                    myChanged = true;
                    m_joinedCount += m_count;
                    m_reportedCount++;
                } else if (m_count == 1) {
                    m_lastTick = m_firstTick;
                    myChanged = true;
                    m_joinedCount += 1;
                    m_reportedCount++;
                }

                m_firstTick = latestTick;
                m_first = timestamp;
                m_last = timestamp;
                m_summ = price;
                m_count = 1;
                m_end = timestamp + m_size;
            }
        }
        // todo: need notify if not changed ?
        if (myChanged) {
            super.notifyListeners(true);
        }
    }

    @Override public ITickData getLatestTick() {
        return m_lastTick;
    }

    @Override public void notifyNoMoreTicks() {
        super.notifyNoMoreTicks();
        log("TickJoinerTs[" + m_size + "ms]: reportedCount=" + m_reportedCount + "; joinedCount=" + m_joinedCount + "; rate=" + (((float) m_joinedCount) / m_reportedCount));
    }
}
