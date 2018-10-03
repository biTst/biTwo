package bi.two.ts;

import bi.two.chart.ITickData;
import bi.two.util.Log;

/** join ticks; provides average price (do not count tick volume) */
public class TickJoinerTimesSeriesData extends BaseTimesSeriesData<ITickData> {
    private ITickData m_lastTick;

    private final long m_size;
//    private long m_first;
//    private long m_last;
    private long m_end;
//    private float m_summ;
    private int m_count;
    private ITickData m_lastJoinTick;
    private int m_joinedCount;
    private int m_reportedCount;
    private float m_lastPrice;

    protected static void log(String s) { Log.log(s); }

    public TickJoinerTimesSeriesData(ITimesSeriesData tsd, long size) {
        super(tsd);
        m_size = size;
    }

    @Override public void onChanged(ITimesSeriesData ts, boolean changed) {
        if (changed) {
            ITimesSeriesData parent = m_parent; // todo: inline
            ITickData parentTick = parent.getLatestTick();

            float price = parentTick.getClosePrice();
            long timestamp = parentTick.getTimestamp();

            if (timestamp < m_end) {
//                m_last = timestamp;
//                m_summ += price;
                m_count++;
            } else {
                if (m_count > 1) { // reportTick

                    // todo: make separate class for avg joiner
                    // report avg price and time
//                    long tickTimestamp = (m_first + m_last) / 2; // mid time
//                    float tickPrice = m_summ / m_count;
//                    TickData tickData = new TickData(tickTimestamp, tickPrice);
//                    m_lastTick = tickData;

                    // report last price and time
                    m_lastTick = m_lastJoinTick;
                    notifyListeners(true);
                    m_joinedCount += m_count;
                    m_reportedCount++;
                } else if (m_count == 1) {
                    m_lastTick = m_lastJoinTick;
                    notifyListeners(true);
                    m_joinedCount += 1;
                    m_reportedCount++;
                }

//                m_first = timestamp;
//                m_last = timestamp;
//                m_summ = price;
                m_count = 1;
                m_end = timestamp + m_size;
            }
            m_lastJoinTick = parentTick;
            m_lastPrice = price;
        }
    }

    @Override public ITickData getLatestTick() {
        return m_lastTick;
    }

    @Override public void notifyNoMoreTicks() {
        // todo: notify last not reported tick here
        super.notifyNoMoreTicks();
        log("TickJoinerTs[" + m_size + "ms]: reportedCount=" + m_reportedCount + "; joinedCount=" + m_joinedCount + "; rate=" + (((float) m_joinedCount) / m_reportedCount));
    }
}
