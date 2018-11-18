package bi.two.algo.impl;

import bi.two.chart.TickData;
import bi.two.exch.Exchange;
import bi.two.ts.ITimesSeriesData;
import bi.two.ts.TicksTimesSeriesData;
import bi.two.util.MapConfig;

abstract class BaseRibbonAlgo2 extends BaseRibbonAlgo {
    private Float m_min;
    private Float m_max;
    private Float m_mid;

    BaseRibbonAlgo2(MapConfig algoConfig, ITimesSeriesData inTsd, Exchange exchange) {
        super(algoConfig, inTsd, exchange);
    }

    @Override public void reset() {
        super.reset();
        m_min = null;
        m_max = null;
        m_mid = null;
    }

    protected abstract void recalc3(float lastPrice, float emasMin, float emasMax, float leadEmaValue, boolean goUp, boolean directionChanged,
                                    float ribbonSpread, float maxRibbonSpread, float ribbonSpreadTop, float ribbonSpreadBottom, float mid);

    protected final void recalc2(float lastPrice, float emasMin, float emasMax, float leadEmaValue, boolean goUp, boolean directionChanged,
                                 float ribbonSpread, float maxRibbonSpread, float ribbonSpreadTop, float ribbonSpreadBottom) {
        m_min = emasMin;
        m_max = emasMax;
        float mid = (emasMin + emasMax) / 2;
        m_mid = mid;

        recalc3(lastPrice, emasMin, emasMax, leadEmaValue, goUp, directionChanged, ribbonSpread, maxRibbonSpread, ribbonSpreadTop, ribbonSpreadBottom, mid);
    }


    TicksTimesSeriesData<TickData> getMinTs() { return new JoinNonChangedInnerTimesSeriesData(this) { @Override protected Float getValue() { return m_min; } }; }
    TicksTimesSeriesData<TickData> getMaxTs() { return new JoinNonChangedInnerTimesSeriesData(this) { @Override protected Float getValue() { return m_max; } }; }
    TicksTimesSeriesData<TickData> getMidTs() { return new JoinNonChangedInnerTimesSeriesData(this) { @Override protected Float getValue() { return m_mid; } }; }
}
