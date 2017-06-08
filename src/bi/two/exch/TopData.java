package bi.two.exch;

// last known exch prices
public class TopData {
    public double m_bid; // ASK > BID
    public double m_ask;
    public double m_last;

    public TopData(TopData topData) {
        this(topData.m_bid, topData.m_ask, topData.m_last);
    }

    public TopData(double bid, double ask) {
        this(bid, ask, 0);
    }

    public TopData(String bid, String ask, String last) {
        this(Double.parseDouble(bid), Double.parseDouble(ask), Double.parseDouble(last));
    }

    public TopData(double bid, double ask, double last) {
        init(bid, ask, last);
    }

    public void init(double bid, double ask, double last) {
        m_bid = bid;
        m_ask = ask;
        m_last = last;
    }

    @Override public String toString() {
        return "TopData[" + "bid=" + m_bid + ", ask=" + m_ask + ", last=" + m_last + ']';
    }
}
