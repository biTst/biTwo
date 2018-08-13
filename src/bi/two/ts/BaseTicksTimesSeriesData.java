package bi.two.ts;

import bi.two.chart.ITickData;
import bi.two.chart.ITicksData;

/** TS which do not collects ticks */
public abstract class BaseTicksTimesSeriesData<T extends ITickData>
        extends BaseTimesSeriesData
        implements ITicksData {
    protected T m_newestTick;

    public BaseTicksTimesSeriesData(ITimesSeriesData parent) {
        super(parent);
    }

    abstract public void addNewestTick(T tickData);
    abstract public void addOlderTick(T tickData);

    @Override public T getLatestTick() { return m_newestTick; }
}