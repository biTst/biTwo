package bi.two.algo.impl;

import bi.two.algo.BarSplitter;
import bi.two.algo.BaseAlgo;
import bi.two.calc.RegressionCalc;
import bi.two.chart.ITimesSeriesData;
import bi.two.chart.TickData;

public class RegressionAlgo extends BaseAlgo {
    private RegressionCalc m_calc;
    private TickData m_value;

    public RegressionAlgo(BarSplitter bs) {
        m_calc = new RegressionCalc(BarSplitter.BARS_NUM, bs);

        bs.addListener(new ITimesSeriesData.ITimesSeriesListener() {
            @Override public void onChanged(ITimesSeriesData ts) {
                m_value = m_calc.calcValue();
                if(m_value != null) {
                    notifyListeners();
                }
            }
        });
    }

    @Override public double getDirectionAdjusted() { // [-1 ... 1]
        if (m_value != null) {
            float value = m_value.getPrice();
            return (value > 0)
                    ? 1 :
                    (value < 0) ? -1 : 0;
        }
        return 0;
    }

    public TickData getValue() { return m_value; }
}
