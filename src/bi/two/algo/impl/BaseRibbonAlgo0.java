package bi.two.algo.impl;

import bi.two.chart.TickData;
import bi.two.opt.Vary;
import bi.two.ts.ITimesSeriesData;
import bi.two.util.MapConfig;

abstract class BaseRibbonAlgo0 extends BaseBarSizeAlgo {
    protected final float m_start;
    protected final float m_step;
    protected final float m_count;
    protected final float m_linRegMultiplier;

    protected TickData m_tickData; // latest tick

    BaseRibbonAlgo0(ITimesSeriesData parent, MapConfig algoConfig) {
        super(parent, algoConfig);

        m_start = algoConfig.getNumber(Vary.start).floatValue();
        m_step = algoConfig.getNumber(Vary.step).floatValue();
        m_count = algoConfig.getNumber(Vary.count).floatValue();
        m_linRegMultiplier = algoConfig.getNumber(Vary.multiplier).floatValue();
    }

    @Override public TickData getLatestTick() {
        return m_tickData;
    }
}
