package bi.two.chart;

import bi.two.util.Utils;

public class TickData implements ITickData {
    long m_timestamp;
    float m_price;
    private ITickData m_olderTick;

    public TickData() {}

    public TickData(long timestamp, float price) {
        init(timestamp, price);
    }

    public TickData(TickData tickData) {
        init(tickData);
        m_olderTick = tickData.m_olderTick;
    }

    public TickData(ITickData tickData) {
        init(tickData);
    }

    public void init(ITickData tickData) {
        init(tickData.getTimestamp(), tickData.getPrice());
    }

    public void init(long timestamp, float price) {
        m_timestamp = timestamp;
        m_price = price;
    }

    @Override public long getTimestamp() { return m_timestamp; }
    @Override public float getPrice() { return m_price; }
    @Override public float getMinPrice() { return m_price; }
    @Override public float getMaxPrice() { return m_price; }
    @Override public TickPainter getTickPainter() {
        return TickPainter.TICK;
    }
    @Override public ITickData getOlderTick() { return m_olderTick; }

    @Override public long getBarSize() { return 0; }


    public void setOlderTick(ITickData olderTick) { m_olderTick = olderTick; }

    @Override public boolean isValid() { return (m_price != Utils.INVALID_PRICE) && (m_price > 0); }

    @Override public String toString() {
        return "TickVolumeData[time=" + m_timestamp + "; price=" + m_price + "]";
    }
}
