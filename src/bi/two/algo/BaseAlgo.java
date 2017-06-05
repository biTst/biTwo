package bi.two.algo;

import bi.two.chart.BaseTimesSeriesData;
import bi.two.chart.TickData;

public class BaseAlgo extends BaseTimesSeriesData<TickData> {
    public double getDirectionAdjusted() { return 0; } // [-1 ... 1]
}
