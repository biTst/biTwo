package bi.two.ts;

import bi.two.chart.TickData;

//=============================================================================================
public class TicksTimesSeriesDataToDelete extends TicksTimesSeriesData<TickData> {
    private final boolean m_collectTicks;

    public TicksTimesSeriesDataToDelete(boolean collectTicks) {
        super(null);
        m_collectTicks = collectTicks;
    }

    @Override public void addNewestTick(TickData tickData) {
        if (m_collectTicks) {
            super.addNewestTick(tickData);
        } else {
            m_ticks.set(0, tickData); // always update only last tick
            m_newestTick = tickData;
            notifyListeners(true);
        }
    }
}
