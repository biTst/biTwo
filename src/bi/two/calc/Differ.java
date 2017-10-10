package bi.two.calc;

import bi.two.chart.ITickData;
import bi.two.chart.TickData;
import bi.two.ts.BaseTimesSeriesData;
import bi.two.ts.ITimesSeriesData;

public class Differ extends BaseTimesSeriesData<ITickData> {
    private final ITimesSeriesData<ITickData> m_one;
    private final ITimesSeriesData<ITickData> m_two;
    private boolean m_dirty;
    private TickData m_tickData;

    public Differ(ITimesSeriesData<ITickData> one, ITimesSeriesData<ITickData> two) {
        super(null);
        m_one = one;
        m_two = two;

        one.getActive().addListener(this);
        two.getActive().addListener(this);
    }

    @Override public void onChanged(ITimesSeriesData ts, boolean changed) {
        if (changed) {
            m_dirty = true;
        }
        super.onChanged(this, changed); // notifyListeners
    }

    @Override public ITickData getLatestTick() {
        if (m_dirty) {
            ITickData latestOne = m_one.getLatestTick();
            if(latestOne != null) {
                ITickData latestTwo = m_two.getLatestTick();
                if(latestTwo != null) {
                    float one = latestOne.getClosePrice();
                    float two = latestTwo.getClosePrice();
                    float diff = one - two;
                    long timestamp = Math.max(latestOne.getTimestamp(), latestTwo.getTimestamp());
                    m_tickData = new TickData(timestamp, diff);
                    m_dirty = false;
                }
            }
        }
        return m_tickData;
    }
}
