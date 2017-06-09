package bi.two.ind;

import bi.two.algo.BarSplitter;
import bi.two.calc.RegressionCalc;
import bi.two.chart.TickData;

public class RegressionIndicator extends BaseIndicator {
    private final RegressionCalc m_calc;
    private TickData m_calcValue;

    public RegressionIndicator(BarSplitter bs) {
        super(bs);

        m_calc = new RegressionCalc(BarSplitter.BARS_NUM, bs);
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
            double value = m_calcValue.getPrice();
            return value;
        }
        return null;
    }

    public long getTimestamp() {
        if (m_calcValue != null) {
            return m_calcValue.getTimestamp();
        }
        return 0;
    }
}
