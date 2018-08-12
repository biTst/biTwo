package bi.two.ts;

import bi.two.chart.ITickData;

import java.util.List;

public class NoTicksTimesSeriesData<T extends ITickData> extends BaseTicksTimesSeriesData<T> {
    public NoTicksTimesSeriesData(ITimesSeriesData parent) {
        super(parent);
    }

    @Override public void addNewestTick(T tickData) {
        m_newestTick = tickData;
        notifyListeners(true);
    }

    @Override public void addOlderTick(T tickData) {
        m_newestTick = tickData;
        notifyListeners(true);
    }

    @Override public List getTicks() {
        throw new RuntimeException("should not be called");
    }
}
