package bi.two.algo.impl;

import bi.two.algo.BaseAlgo;
import bi.two.chart.TickData;
import bi.two.opt.Vary;
import bi.two.ts.ITimesSeriesData;
import bi.two.util.MapConfig;

abstract class BaseBarSizeAlgo extends BaseAlgo<TickData> {
    protected final long m_barSize;

    BaseBarSizeAlgo(ITimesSeriesData parent, MapConfig algoConfig) {
        super(parent, algoConfig);
        m_barSize = algoConfig.getNumber(Vary.period).longValue();
    }
}
