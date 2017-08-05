package bi.two.chart;

public class TickVolumeData extends TickData {
    float m_volume;

    public TickVolumeData(long timestamp, float price, float volume) {
        super(timestamp, price);
        m_volume = volume;
    }

    public float getVolume() { return m_volume; }

    public void init(long timestamp, float price, float volume) {
        init(timestamp, price);
        m_volume = volume;
    }

    @Override public String toString() {
        return "TickVolumeData[time=" + m_timestamp + "; price=" + m_price + "; volume=" + m_volume + "]";
    }
}
