package bi.two.calc;

import bi.two.algo.BarSplitter;
import bi.two.chart.ITickData;
import bi.two.chart.TickData;
import bi.two.ts.BaseTimesSeriesData;
import bi.two.ts.ITimesSeriesData;

public abstract class TicksBufferBased<R>
        extends BaseTimesSeriesData<ITickData>
        implements BarSplitter.BarHolder.ITicksProcessor<R> {
    final BarSplitter m_splitter;
    private boolean m_initialized;
    private boolean m_dirty;
    private boolean m_filled;
    private TickData m_tickData;

    protected abstract float calcTickValue(R ret);

    public TicksBufferBased(ITimesSeriesData<ITickData> tsd, long period) {
        m_splitter = new BarSplitter(tsd, 1, period);
        setParent(m_splitter);
    }

    @Override public ITickData getLatestTick() {
        if (m_filled) {
            if (m_dirty) {
                R ret = m_splitter.m_newestBar.iterateTicks(this);
                if (ret == null) {
                    return null; // can be when not enough ticks in bar to calc regression  - should be at lest 2 ticks
                }
                float price = calcTickValue(ret);
                if (!Float.isNaN(price)) {
                    long timestamp = m_parent.getLatestTick().getTimestamp();
                    m_tickData = new TickData(timestamp, price);
                } else {
                    return null; // can be when not enough ticks in bar to calc regression  - should be at lest 2 ticks
                }
                m_dirty = false;
            }
            return m_tickData;
        }
        return null; // not yet filled
    }

    @Override public void onChanged(ITimesSeriesData ts, boolean changed) {
        boolean iAmChanged = false;
        if (changed) {
            if (!m_initialized) {
                BarSplitter.BarHolder newestBar = m_splitter.m_newestBar;
                if (newestBar != null) {
                    m_initialized = true;
                    newestBar.addBarHolderListener(new BarSplitter.BarHolder.IBarHolderListener() {
                        @Override public void onTickEnter(ITickData tickData) {}
                        @Override public void onTickExit(ITickData tickData) {
                            m_filled = true;
                            m_splitter.m_newestBar.removeBarHolderListener(this);
                        }
                    });
                } else {
                    return; // not initialized
                }
            }
            m_dirty = m_filled;
            iAmChanged = m_dirty;
        }
        super.onChanged(this, iAmChanged); // notifyListeners
    }

    @Override public void onTimeShift(long shift) {
        if (m_tickData != null) {
            m_tickData = m_tickData.newTimeShifted(shift);
        }
        notifyOnTimeShift(shift);
    }
}
