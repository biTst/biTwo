package bi.two.ts.join;

import bi.two.chart.ITickData;
import bi.two.ts.ITimesSeriesData;
import bi.two.ts.TicksTimesSeriesData;

public abstract class BaseTickJoiner extends TicksTimesSeriesData<ITickData> {
    protected final long m_size;
    protected final boolean m_collectTicks;

    BaseTickJoiner(ITimesSeriesData parent, long size, boolean collectTicks) {
        super(parent);
        m_size = size;
        m_collectTicks = collectTicks;
    }
}
