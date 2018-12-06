package bi.two.chart;

public class TickData extends BaseTickData {
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

    @Override public ITickData getOlderTick() { return m_olderTick; }

    public void setOlderTick(ITickData olderTick) { m_olderTick = olderTick; }

    public TickData newTimeShifted(long shift) {
        return  new TickData(getTimestamp() + shift, getClosePrice());
    }
}
