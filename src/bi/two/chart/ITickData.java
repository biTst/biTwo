package bi.two.chart;

public interface ITickData {
    long getTimestamp();
    long getBarSize();
    float getMinPrice();
    float getMaxPrice();

    TickPainter getTickPainter();
    
    ITickData getOlderTick();
    void setOlderTick(ITickData last);

    boolean isValid();
}
