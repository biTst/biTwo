package bi.two.calc;

import bi.two.algo.BarSplitter;
import bi.two.chart.ITickData;
import bi.two.chart.TickData;
import bi.two.ts.BaseTimesSeriesData;
import bi.two.ts.ITimesSeriesData;
import org.apache.commons.math3.stat.regression.SimpleRegression;

public class SlidingTicksRegressor extends BaseTimesSeriesData<ITickData> {
    private final SimpleRegression m_simpleRegression = new SimpleRegression(true);
    private final BarSplitter m_splitter;
    private long m_firstTimestamp;
    private long m_lastTimestamp;
    private boolean m_initialized;
    private boolean m_filled;
    private TickData m_lastTick;

    public SlidingTicksRegressor(ITimesSeriesData<ITickData> tsd, long period) {
        m_splitter = new BarSplitter(tsd, 1, period);
        setParent(m_splitter);
    }

    @Override public void onChanged(ITimesSeriesData ts, boolean changed) {
        if (!m_initialized && changed) {
            m_initialized = true;

            // onChanged() called after first tick is already processed
            ITickData olderTick = m_splitter.m_newestBar.getLatestTick().m_param;
            addTick(olderTick); //

            m_splitter.m_newestBar.addBarHolderListener(new BarSplitter.BarHolder.IBarHolderListener() {
                @Override public void onTickEnter(ITickData tickData) {
                    addTick(tickData);
                }

                @Override public void onTickExit(ITickData tickData) {
                    m_filled = true;

                    float price = tickData.getClosePrice();
                    long timestamp = tickData.getTimestamp();
                    m_simpleRegression.removeData(timestamp - m_firstTimestamp, price);
                    m_lastTick = null;
                }
            });
        }
        super.onChanged(ts, changed);
    }

    private void addTick(ITickData tickData) {
        float price = tickData.getClosePrice();
        long timestamp = tickData.getTimestamp();
        if (m_firstTimestamp == 0) {
            m_firstTimestamp = timestamp;
        }
        m_lastTimestamp = timestamp;
        m_simpleRegression.addData(timestamp - m_firstTimestamp, price);
        m_lastTick = null;
    }

    @Override public ITickData getLatestTick() {
        if (m_filled) {
            if (m_lastTick == null) {
                double regression = m_simpleRegression.predict(m_lastTimestamp - m_firstTimestamp);
                m_lastTick = new TickData(m_lastTimestamp, (float) regression);
            }
            return m_lastTick;
        }
        return null;
    }

    public String log() {
        return "SlidingTicksRegressor["
//                + "\nsplitter=" + m_splitter.log()
                + "\n]";
    }
}
