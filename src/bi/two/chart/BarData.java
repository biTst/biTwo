package bi.two.chart;

public class BarData extends TickData {
    private final long m_barSize;
    float m_open;
    float m_high;
    float m_low;
    float m_close;

    @Override public float getClosePrice() { return m_close; }
    @Override public float getMinPrice() { return m_low; }
    @Override public float getMaxPrice() { return m_high; }
    @Override public long getBarSize() { return m_barSize; }

    public BarData(long timestamp, long barSize, float price) {
        super(timestamp, price);
        m_barSize = barSize;
        m_open = price;
        m_high = price;
        m_low = price;
        m_close = price;
    }

    public void update(float price) {
        m_high = (m_high >= price) ? m_high : price;  //  Math.max(m_high, price);
        m_low = (m_low <= price) ? m_low : price;     //  Math.min(m_low, price);
        m_close = price;
        m_price = price;
    }


    @Override protected String getParams() {
        return super.getParams()
                + "; open=" + m_open
                + "; high=" + m_high
                + "; low=" + m_low
                + "; close=" + m_close ;
    }

    @Override protected String getName() { return "BarData"; }
}
