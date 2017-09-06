package bi.two.ind;

import bi.two.algo.BarSplitter;
import bi.two.calc.RegressionCalc;
import bi.two.chart.ITickData;
import bi.two.chart.TickData;
import bi.two.util.MapConfig;

public class RegressionIndicator extends BaseIndicator {
    public final RegressionCalc m_calc;
    public final BarSplitter m_bs;
    private TickData m_calcValue;

    public RegressionIndicator(MapConfig config, BarSplitter bs) {
        super(bs);
        m_bs = bs;

        m_calc = new RegressionCalc(config, bs);
    }

    public TickData getTickValue() {
        return m_calcValue;
    }

    @Override public TickData calculateTickValue() {
        m_calcValue = m_calc.calcValue();
        return m_calcValue;
    }

    public Double getValue() {
        if (m_calcValue != null) {
            return (double) m_calcValue.getClosePrice();
        }
        return null;
    }

    public long getTimestamp() {
        if (m_calcValue != null) {
            return m_calcValue.getTimestamp();
        }
        return 0;
    }

    @Override public ITickData getLatestTick() {
        return null;
    }
}
