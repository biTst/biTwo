package bi.two.ts;

import bi.two.chart.BarData;
import bi.two.chart.ITickData;

public class BarsTimesSeriesData extends TicksTimesSeriesData<BarData> {
    private final long m_barSize;
    BarData m_lastBar;

    public BarsTimesSeriesData(ITimesSeriesData parent, long barSize) {
        super(parent);
        m_barSize = barSize;
    }

    @Override public void onChanged(ITimesSeriesData ts, boolean changed) {
        if (changed) {
            ITickData last = ts.getLatestTick();
            if (last != null) {
                if (m_lastBar == null) {
                    startNewBar(last);
                } else {
                    long timestamp = last.getTimestamp();
                    long barStart = m_lastBar.getTimestamp();
                    long diff = timestamp - barStart;
                    if (diff >= m_barSize) { // close prev bar
                        startNewBar(last);
                    } else {
                        float price = last.getClosePrice();
                        m_lastBar.update(price);
                    }
                }
                super.onChanged(ts, changed);
            }
        }
    }

    @Override public void notifyNoMoreTicks() {
        if (m_lastBar != null) {
            addNewestTick(m_lastBar);
        }
        super.notifyNoMoreTicks();
    }

    private void startNewBar(ITickData last) {
        long timestamp = last.getTimestamp();
        long barStart = (timestamp / m_barSize) * m_barSize;
        float price = last.getClosePrice();
        m_lastBar = new BarData(barStart, m_barSize, price);
        addNewestTick(m_lastBar);
    }
}
