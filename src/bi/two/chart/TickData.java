package bi.two.chart;

import bi.two.util.Utils;

public class TickData implements ITickData {
    private long m_timestamp;
    private float m_price;
    private ITickData m_olderTick;

    public TickData(long timestamp, float price) {
        init(timestamp, price);
    }

    public void init(long timestamp, float price) {
        m_timestamp = timestamp;
        m_price = price;
    }

    @Override public long getTimestamp() { return m_timestamp; }
    @Override public float getMinPrice() { return m_price; }
    @Override public float getMaxPrice() { return m_price; }
    @Override public TickPainter getTickPainter() {
        return TickPainter.TRADE;
    }
    @Override public ITickData getOlderTick() { return m_olderTick; }

    @Override public long getBarSize() { return 0; }

    public float getPrice() { return m_price; }

    public void setOlderTick(ITickData olderTick) { m_olderTick = olderTick; }

    @Override public boolean isValid() { return (m_price != Utils.INVALID_PRICE) && (m_price > 0); }
}
