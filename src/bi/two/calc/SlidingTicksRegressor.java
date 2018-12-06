package bi.two.calc;

import bi.two.algo.BarSplitter;
import bi.two.chart.ITickData;
import bi.two.chart.TickData;
import bi.two.ts.BaseTimesSeriesData;
import bi.two.ts.ITimesSeriesData;
import org.apache.commons.math3.stat.regression.SimpleRegression;

public class SlidingTicksRegressor extends BaseTimesSeriesData<ITickData> {
    private final boolean m_collectValues;
    final SimpleRegression m_simpleRegression = new SimpleRegression(true);
    private final BarSplitter m_splitter;
    private long m_firstTimestamp;
    private long m_lastTimestamp;
    private boolean m_initialized;
    private boolean m_filled;
    private TickData m_lastTick;
    private TickData m_sharedTick = new TickData();

    public SlidingTicksRegressor(ITimesSeriesData<ITickData> tsd, long period) {
        this(tsd, period, true);
    }

    public SlidingTicksRegressor(ITimesSeriesData tsd, long period, boolean collectValues) {
        this(tsd, period, collectValues, true);
    }

    public SlidingTicksRegressor(ITimesSeriesData tsd, long period, boolean collectValues, boolean cloneTicks) {
        m_collectValues = collectValues;
        m_splitter = new BarSplitter(tsd, 1, period, cloneTicks);
        setParent(m_splitter);
    }

    @Override public void onChanged(ITimesSeriesData ts, boolean changed) {
        if (!m_initialized && changed) {
            // onChanged() called after first tick is already processed
            BarSplitter.BarHolder newestBar = m_splitter.m_newestBar;
            if (newestBar != null) {
                BarSplitter.TickNode latestTickNode = newestBar.getLatestTick();
                ITickData olderTick = latestTickNode.m_param;
                addTick(olderTick); //

                newestBar.addBarHolderListener(new BarSplitter.BarHolder.IBarHolderListener() {
                    @Override public void onTickEnter(ITickData tickData) {
                        addTick(tickData);
                    }

                    @Override public void onTickExit(ITickData tickData) {
                        m_filled = true;

                        float price = tickData.getClosePrice();
                        long timestamp = tickData.getTimestamp();
                        m_simpleRegression.removeData(timestamp - m_firstTimestamp, price);
                        m_lastTick = null; // mark as dirty
                    }
                });
                m_initialized = true;
            } else {
                return; // not initialized
            }
        }
        notifyListeners(changed);
    }

    private void addTick(ITickData tickData) {
        float price = tickData.getClosePrice();
        long timestamp = tickData.getTimestamp();
        if (m_firstTimestamp == 0) {
            m_firstTimestamp = timestamp;
        }
        m_lastTimestamp = timestamp;
        m_simpleRegression.addData(timestamp - m_firstTimestamp, price);
        m_lastTick = null; // mark as dirty
    }

    @Override public ITickData getLatestTick() {
        if (m_filled) {
            if (m_lastTick == null) { // dirty ?
                if (m_simpleRegression.getN() >= 2) { // enough ticks
                    double value = getValue();
                    if (m_collectValues) {
                        m_lastTick = new TickData(m_lastTimestamp, (float) value);
                    } else { // use object from pool to minimize allocations
                        m_sharedTick.init(m_lastTimestamp, (float) value);
                        m_lastTick = m_sharedTick;
                    }
                }
            }
            return m_lastTick;
        }
        return null;
    }

    protected double getValue() {
        return m_simpleRegression.predict(m_lastTimestamp - m_firstTimestamp);
    }

    public String toLog() {
        return "SlidingTicksRegressor["
//                + "\nsplitter=" + m_splitter.log()
                + "\n]";
    }

    @Override public void onTimeShift(long shift) {
        m_simpleRegression.clear();

        m_firstTimestamp = 0;
        m_lastTimestamp = 0;

        BarSplitter.BarHolder newestBar = m_splitter.m_newestBar;
        if (newestBar != null) {
            BarSplitter.TickNode node = newestBar.getOldestTick(); // re-add ticks from last known
            while(node != null) {
                ITickData olderTick = node.m_param;
                addTick(olderTick);
                node = node.m_next;
            }
        }
        m_lastTick = null; // mark as dirty

        // todo: replace with super
        notifyOnTimeShift(shift);
//        super.onTimeShift(shift);
    }
}
