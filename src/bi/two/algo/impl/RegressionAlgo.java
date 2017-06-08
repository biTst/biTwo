package bi.two.algo.impl;

import bi.two.algo.BarSplitter;
import bi.two.algo.BaseAlgo;
import bi.two.chart.TickData;
import bi.two.ind.RegressionIndicator;

public class RegressionAlgo extends BaseAlgo {
    public final RegressionIndicator m_regressionIndicator;

    public RegressionAlgo(BarSplitter bs) {
        super(null);

        m_regressionIndicator = new RegressionIndicator(bs);
        m_indicators.add(m_regressionIndicator);
    }

//    @Override public void onChanged(ITimesSeriesData ts, boolean changed) {
//        notifyListeners(iAmChanged);
//    }

    @Override public double getDirectionAdjusted() { // [-1 ... 1]
        Double value = m_regressionIndicator.getValue();
        return getDirectionAdjusted(value);
    }

    private double getDirectionAdjusted(Double value) {
        return (value == null)
                ? 0
                : (value > 0)
                    ? 1 :
                    (value < 0) ? -1 : 0;
    }

    @Override public TickData getAdjusted() {
        long timestamp = m_regressionIndicator.getTimestamp();
        if (timestamp != 0) {
            Double value = m_regressionIndicator.getValue();
            if (value != null) {
                return new TickData(timestamp, (float) getDirectionAdjusted(value));
            }
        }
        return null;
    }
}
