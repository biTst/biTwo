package bi.two.chart;

public interface ITickData {
    long getTimestamp();
    float getPrice();
    float getMinPrice();
    float getMaxPrice();

    long getBarSize();

    TickPainter getTickPainter();
    
    ITickData getOlderTick();
    void setOlderTick(ITickData last);

    boolean isValid();
}
