package bi.two.chart;

public class ChartAreaData {
    private final String m_name;
    private ITicksData m_ticksData;

    public ChartAreaData(String name) {
        m_name = name;
    }

    public String getName() { return m_name; }
    public ITicksData getTicksData() { return m_ticksData; }

    public void setTicksData(ITicksData ticksData) { m_ticksData = ticksData; }

    public int getPriceAxeWidth() {
        return 70;
    }
}
