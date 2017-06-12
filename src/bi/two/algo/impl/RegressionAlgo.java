package bi.two.algo.impl;

import bi.two.algo.BarSplitter;
import bi.two.algo.BaseAlgo;
import bi.two.chart.ITimesSeriesData;
import bi.two.chart.TickData;
import bi.two.ind.RegressionIndicator;
import bi.two.util.MapConfig;

public class RegressionAlgo extends BaseAlgo {
    public final RegressionIndicator m_regressionIndicator;
    private final boolean m_collectValues;

    public RegressionAlgo(MapConfig config, BarSplitter bs) {
        super(null);
        m_collectValues = config.getBoolean("collect.values");

        m_regressionIndicator = new RegressionIndicator(config, bs);
        m_indicators.add(m_regressionIndicator);

        m_regressionIndicator.addListener(this);
    }

    @Override public void onChanged(ITimesSeriesData ts, boolean changed) {
        notifyListeners(changed);
        if (m_collectValues) {
            TickData adjusted = getAdjusted();
            addNewestTick(adjusted);
        }
    }

    @Override public double getDirectionAdjusted() { // [-1 ... 1]
        Double value = m_regressionIndicator.getValue();
        return getDirectionAdjusted(value);
    }

    private double getDirectionAdjusted(Double value) {
        return (value == null)
                ? 0
                : (value > 0)
                    ? -1 :
                    (value < 0) ? +1 : 0;
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
