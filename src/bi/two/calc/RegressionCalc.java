package bi.two.calc;

import bi.two.algo.BarSplitter;
import bi.two.chart.ITickData;
import bi.two.chart.ITicksData;
import bi.two.chart.TickData;
import org.apache.commons.math3.stat.regression.SimpleRegression;

import java.util.List;

public class RegressionCalc {
    private final SimpleRegression m_simpleRegression = new SimpleRegression(true);
    private final int m_barsNum;
    private final ITicksData m_ts;
    private final TickData m_shared = new TickData();

    public RegressionCalc(int barsNum, BarSplitter bs) {
        m_barsNum = barsNum;
        bs.setBarsNum(barsNum);
        m_ts = bs;
    }

    public TickData calcValue() {
        List<? extends ITickData> ticks = m_ts.getTicks();
        int size = ticks.size();
        if (size > 3) {
            m_simpleRegression.clear();
            int validPoints = 0;
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
