package bi.two.algo.impl;

import bi.two.algo.BarSplitter;
import bi.two.algo.BaseAlgo;
import bi.two.calc.RegressionCalc;
import bi.two.chart.ITimesSeriesData;
import bi.two.chart.TickData;

public class RegressionAlgo extends BaseAlgo {
    private final RegressionCalc m_calc;
    private TickData m_calcValue;
    private Float m_prevValue;

    public RegressionAlgo(BarSplitter bs) {
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

    @Override public double getDirectionAdjusted() { // [-1 ... 1]
        if (m_calcValue != null) {
            float value = m_calcValue.getPrice();
            return (value > 0)
                    ? 1 :
                    (value < 0) ? -1 : 0;
        }
        return 0;
    }

    @Override public TickData getAdjusted() {
        if (m_calcValue != null) {
            return new TickData(m_calcValue.getTimestamp(), (float) getDirectionAdjusted());
        }
        return null;
    }
}
