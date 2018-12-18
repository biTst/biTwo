package bi.two.calc;

import bi.two.chart.ITickData;
import bi.two.ts.ITimesSeriesData;

public class SlidingTicksRegressorSlope extends SlidingTicksRegressor {

    public SlidingTicksRegressorSlope(ITimesSeriesData<ITickData> tsd, long period) {
        super(tsd, period);
    }

    public SlidingTicksRegressorSlope(ITimesSeriesData tsd, long period, boolean collectValues) {
        super(tsd, period, collectValues);
    }

    public SlidingTicksRegressorSlope(ITimesSeriesData tsd, long period, boolean collectValues, boolean cloneTicks) {
        super(tsd, period, collectValues, cloneTicks);
    }

    protected double getValue() {
        return m_simpleRegression.getSlope();
    }

    public double getSlopeConfidenceInterval() {
        return m_simpleRegression.getSlopeConfidenceInterval();
    }
}
