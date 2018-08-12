package bi.two.calc;

import bi.two.algo.BarSplitter;
import bi.two.chart.ITickData;
import bi.two.chart.TickData;
import bi.two.ts.BaseTimesSeriesData;
import bi.two.ts.ITimesSeriesData;
import bi.two.ts.TicksTimesSeriesData;
import org.apache.commons.math3.stat.regression.SimpleRegression;

// -----------------------------------------------------------------------------
// todo - switch to 'extends BarsBasedCalculator'
public class BarsRegressor extends BaseTimesSeriesData<ITickData>
        implements TicksTimesSeriesData.ITicksProcessor<BarSplitter.BarHolder, Float> {
    public final BarSplitter m_splitter;
    private final SimpleRegression m_simpleRegression = new SimpleRegression(true);
    private long m_lastBarTickTime;
    private boolean m_initialized;
    private boolean m_filled;
    private boolean m_dirty;
    private TickData m_tickData;

    public BarsRegressor(ITimesSeriesData<ITickData> tsd, int barsNum, long barSize, float divider) {
        m_splitter = new BarSplitter(tsd, (int) (barsNum * divider), (long) (barSize/divider));
        setParent(m_splitter);
    }

    @Override public void init() {
        m_simpleRegression.clear();
        m_lastBarTickTime = 0;// reset
    }

    @Override public void processTick(BarSplitter.BarHolder barHolder) {
        BarSplitter.TickNode latestTickNode = barHolder.getLatestTick();
        if (latestTickNode != null) { // have ticks in bar ?
            ITickData latestTick = latestTickNode.m_param;

            long timestamp = latestTick.getTimestamp();
            if (m_lastBarTickTime == 0) {
                m_lastBarTickTime = timestamp;
            }

            float price = latestTick.getClosePrice();
            m_simpleRegression.addData(m_lastBarTickTime - timestamp, price);
        }
    }

    @Override public Float done() {
        double value = m_simpleRegression.getIntercept();
        return (float) value;
    }

    @Override public ITickData getLatestTick() {
        if (m_filled) {
            if (m_dirty) {
                calculateLatestTick();
            }
            return m_tickData;
        }
        return null;
    }

    private void calculateLatestTick() {
        Float regression = m_splitter.iterateTicks( this);

        long timestamp = m_parent.getLatestTick().getTimestamp();
        m_tickData = new TickData(timestamp, regression);
        m_dirty = false;
    }

    @Override public void onChanged(ITimesSeriesData ts, boolean changed) {
        boolean iAmChanged = false;
        if (changed) {
            if (!m_initialized) {
                m_initialized = true;
                final BarSplitter.BarHolder oldestTick = m_splitter.getOldestTick();
                oldestTick.addBarHolderListener(new BarSplitter.BarHolder.IBarHolderListener() {
                    @Override public void onTickEnter(ITickData tickData) {
                        m_filled = true;
                        oldestTick.removeBarHolderListener(this);
                    }
                    @Override public void onTickExit(ITickData tickData) {}
                });
            }
            m_dirty = true;
            iAmChanged = m_filled;
        }
        super.onChanged(this, iAmChanged); // notifyListeners
    }

    public String log() {
        return "BarsRegressor["
                + "\nsplitter=" + m_splitter.log()
                + "\n]";
    }
}
