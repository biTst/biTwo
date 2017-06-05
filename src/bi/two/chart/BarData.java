package bi.two.chart;

import bi.two.util.Utils;

public class BarData implements ITickData {

    private final long m_time;
    private final long m_period;
    private final float m_maxPrice;
    private final float m_minPrice;
    private ITickData m_olderBar;

    public BarData(long time, long period, float maxPrice, float minPrice) {
        m_time = time;
        m_period = period;
        m_maxPrice = maxPrice;
        m_minPrice = minPrice;
    }

    public long getPeriod() { return m_period; }
    @Override public long getBarSize() { return m_period; }

    @Override public long getTimestamp() { return m_time; }
    @Override public float getMinPrice() { return m_minPrice; }
    @Override public float getMaxPrice() { return m_maxPrice; }

    @Override public TickPainter getTickPainter() {
        return TickPainter.BAR;
    }
    @Override public ITickData getOlderTick() { return m_olderBar; }

    @Override public void setOlderTick(ITickData older) { m_olderBar = older; }

    @Override public boolean isValid() { return (m_maxPrice != Utils.INVALID_PRICE) && (m_minPrice > 0); }
}
