package bi.two.chart;

public class TradeTickData extends TickVolumeData {
    private final long m_tradeId;

    public long getTradeId() { return m_tradeId; }

    public TradeTickData(long tradeId, long timestamp, float price, float volume) {
        super(timestamp, price, volume);
        m_tradeId = tradeId;
    }

    @Override public int hashCode() {
        return (int)m_tradeId;
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TradeTickData that = (TradeTickData) o;
        return m_tradeId == that.m_tradeId;
    }

    @Override public String toString() {
        return "TradeTickData[tradeId=" + m_tradeId + "; time=" + m_timestamp + "; price=" + m_price + "; volume=" + m_volume + "]";
    }
}
