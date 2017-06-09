package bi.two.calc;

import bi.two.algo.BarSplitter;
import bi.two.chart.ITickData;
import bi.two.chart.ITicksData;
import bi.two.chart.TickData;
import bi.two.util.MapConfig;
import org.apache.commons.math3.stat.regression.SimpleRegression;

import java.util.List;

public class RegressionCalc {
    public static final String REGRESSION_BARS_NUM = "regression.barsNum";

    private final SimpleRegression m_simpleRegression = new SimpleRegression(true);
    public final int m_barsNum;
    private final ITicksData m_ts;
    private final TickData m_shared = new TickData();

    public RegressionCalc(MapConfig config, BarSplitter bs) {
        m_barsNum = config.getInt(REGRESSION_BARS_NUM);
        bs.setBarsNum(m_barsNum);
        m_ts = bs;
    }

    public TickData calcValue() {
        List<? extends ITickData> ticks = m_ts.getTicks();
        int size = ticks.size();
        if (size > 3) {
            m_simpleRegression.clear();
            int validPoints = 0;
            size = Math.min(size, m_barsNum);
            for (int i = 0; i < size; i++) {
                ITickData tick = ticks.get(i);
                ITickData nextTick = getValidTick(tick);
                if (nextTick != null) {
                    float price = nextTick.getMaxPrice();
                    if (nextTick.isValid()) {
                        m_simpleRegression.addData(m_barsNum - i, price);
                        validPoints++;
                    }
                }
            }

            if (validPoints > 3) {
                ITickData latestTick = ticks.get(0);
                long latestTickTime = latestTick.getTimestamp();
                double slope = m_simpleRegression.getSlope();
                m_shared.init(latestTickTime, (float) slope);
                return m_shared;
            }
        }
        return null;
    }

    private ITickData getValidTick(ITickData tick) {
        if (tick.isValid()) {
            return tick;
        }
        ITickData olderTick = tick.getOlderTick();
        return (olderTick == null) ? null : getValidTick(olderTick);
    }
}
