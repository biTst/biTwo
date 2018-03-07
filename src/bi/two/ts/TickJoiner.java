package bi.two.ts;

import bi.two.chart.TickData;

public class TickJoiner {
    private final TimesSeriesData<TickData> m_tsd;
    private final long m_size;
    private long m_first;
    private long m_last;
    private long m_end;
    private float m_summ;
    private int m_count;
    private TickData m_firstTick;
    private int m_joinedCount;
    private int m_reportedCount;

    public TickJoiner(TimesSeriesData<TickData> tsd, long size) {
        m_tsd = tsd;
        m_size = size;
    }

    public void addNewestTick(TickData tickData) {
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

    private void reportTick() {
        if (m_count > 1) {
            long avdTimestamp = (m_first + m_last) / 2;
            float avgPrice = m_summ / m_count;
            TickData avgTickData = new TickData(avdTimestamp, avgPrice);
            m_tsd.addNewestTick(avgTickData);
            m_joinedCount += m_count;
            m_reportedCount++;
        } else if (m_count == 1) {
            m_tsd.addNewestTick(m_firstTick);
            m_joinedCount += 1;
            m_reportedCount++;
        }
    }

    public void finish() {
        reportTick();
        System.out.println("TickJoiner[" + m_size + "ms]: reportedCount=" + m_reportedCount + "; joinedCount=" + m_joinedCount + "; rate=" + (((float) m_joinedCount) / m_reportedCount));
    }
}
