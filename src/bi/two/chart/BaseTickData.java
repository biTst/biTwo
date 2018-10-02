package bi.two.chart;

import bi.two.util.Utils;

public class BaseTickData implements ITickData {
    long m_timestamp;
    float m_price;

    public BaseTickData() {}

    public BaseTickData(long timestamp, float price) {
        init(timestamp, price);
    }

    public BaseTickData(BaseTickData tickData) {
        init(tickData);
    }

    public BaseTickData(ITickData tickData) {
        init(tickData);
    }

    public void init(ITickData tickData) {
        init(tickData.getTimestamp(), tickData.getClosePrice());
    }

    public void init(long timestamp, float price) {
        m_timestamp = timestamp;
        m_price = price;
    }

    @Override public long getTimestamp() { return m_timestamp; }
    @Override public float getClosePrice() { return m_price; }
    @Override public float getMinPrice() { return m_price; }
    @Override public float getMaxPrice() { return m_price; }
    @Override public TickPainter getTickPainter() {
        return TickPainter.TICK;
    }
    @Override public ITickData getOlderTick() { return null; }
    @Override public long getBarSize() { return 0; }

    public void setTimestamp(long timestamp) { m_timestamp = timestamp; }
    public void setOlderTick(ITickData olderTick) { /*noop*/ }

    @Override public boolean isValid() { return (m_price != Utils.INVALID_PRICE) && (m_price > 0); }

    @Override public void onTimeShift(long shift) {
        m_timestamp += shift;
    }

    @Override public String toString() { return getName() + "[" + getParams() + "]"; }

    protected String getParams() {
        return "time=" + m_timestamp + "; price=" + m_price;
    }

    protected String getName() { return "BaseTickData"; }
}
