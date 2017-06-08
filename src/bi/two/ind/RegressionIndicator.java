package bi.two.ind;

import bi.two.algo.BarSplitter;
import bi.two.calc.RegressionCalc;
import bi.two.chart.ITimesSeriesData;
import bi.two.chart.TickData;

public class RegressionIndicator extends BaseIndicator {
    private final RegressionCalc m_calc;
    private TickData m_calcValue;
    private Float m_prevValue;

    public RegressionIndicator(BarSplitter bs) {
        super(bs);

        m_calc = new RegressionCalc(BarSplitter.BARS_NUM, bs);
    }

    @Override public void onChanged(ITimesSeriesData ts, boolean changed) {
        boolean iAmChanged = false;
        if (changed) {
            m_calcValue = m_calc.calcValue();
            if (m_calcValue != null) {
                float value = m_calcValue.getPrice();
                if (m_prevValue != null) {
                    if (value == m_prevValue) {
                        notifyListeners(false);
                        return; // value not changed
                    }
                }
                m_prevValue = value;
                iAmChanged = true;
            }
        }
        notifyListeners(iAmChanged);
    }

    public TickData getTickValue() {
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
