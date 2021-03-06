package bi.two.calc;

import bi.two.chart.ITickData;
import bi.two.chart.TickData;
import bi.two.ts.BaseTimesSeriesData;
import bi.two.ts.ITimesSeriesData;

import java.util.List;

//----------------------------------------------------------
public class Average extends BaseTimesSeriesData<ITickData> {
    private final List<BaseTimesSeriesData> m_tss;
    private boolean m_dirty;
    private TickData m_tick;

    public Average(List<BaseTimesSeriesData> tss, ITimesSeriesData baseDs) {
        super(null);
        m_tss = tss;
        for (ITimesSeriesData<ITickData> next : tss) {
            next.getActive().addListener(this);
        }
        setParent(baseDs); // subscribe to list first - will be called onChanged() and set as dirty ONLY
    }

    @Override public void onChanged(ITimesSeriesData ts, boolean changed) {
        if (ts == getParent()) {
            super.onChanged(ts, changed); // forward onChanged() only for parent ds. other should only reset changed state
        } else {
            if (changed) {
                m_dirty = true;
            }
        }
    }

    @Override public ITickData getLatestTick() {
        if (m_dirty) {
            boolean allDone = true;
            float sum = 0;
            for (ITimesSeriesData<ITickData> next : m_tss) {
                ITickData lastTick = next.getLatestTick();
                if (lastTick != null) {
                    float value = lastTick.getClosePrice();
                    sum += value;
                } else {
                    allDone = false;
                    break; // not ready yet
                }
            }
            if (allDone) {
                ITickData latestTick = getParent().getLatestTick();
                if (latestTick != null) {
                    float average = sum / m_tss.size();
                    long timestamp = latestTick.getTimestamp();
                    m_tick = new TickData(timestamp, average);
                    m_dirty = false;
                }
            }
        }
        return m_tick;
    }

    @Override public void onTimeShift(long shift) {
        if (m_tick != null) {
            m_tick = m_tick.newTimeShifted(shift);
        }
        // todo: call super only
        notifyOnTimeShift(shift);
    }
}
