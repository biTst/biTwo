package bi.two.algo.impl;

import bi.two.chart.TickData;
import bi.two.exch.Exchange;
import bi.two.ts.ITimesSeriesData;
import bi.two.ts.TicksTimesSeriesData;
import bi.two.util.MapConfig;

abstract class BaseRibbonAlgo2 extends BaseRibbonAlgo1 {
    protected final boolean m_adjustTail;
    private Float m_min;
    private Float m_max;
    private Float m_mid;
    private Float m_zigZag;

    BaseRibbonAlgo2(MapConfig algoConfig, ITimesSeriesData inTsd, Exchange exchange, boolean adjustTail) {
        super(algoConfig, inTsd, exchange);
        m_adjustTail = adjustTail;
    }

    @Override public void reset() {
        super.reset();
        m_min = null;
        m_max = null;
        m_mid = null;
        m_zigZag = null;
    }

    protected abstract void recalc3(float lastPrice, float emasMin, float emasMax, float leadEmaValue, boolean goUp, boolean directionChanged,
                                    float ribbonSpread, float maxRibbonSpread, float ribbonSpreadTop, float ribbonSpreadBottom, float mid, float head, float tail);

    protected final void recalc2(float lastPrice, float emasMin, float emasMax, float leadEmaValue, boolean goUp, boolean directionChanged,
                                 float ribbonSpread, float maxRibbonSpread, float ribbonSpreadTop, float ribbonSpreadBottom) {
        m_min = emasMin;
        m_max = emasMax;
        float mid = (emasMin + emasMax) / 2;
        m_mid = mid;

        if (directionChanged) {
            // note - ribbonSpread from prev step here
            m_zigZag = goUp ? ribbonSpreadBottom : ribbonSpreadTop;
        }

        float head = goUp ? emasMax : emasMin;
        float tail = goUp ? emasMin : emasMax;

        recalc3(lastPrice, emasMin, emasMax, leadEmaValue, goUp, directionChanged, ribbonSpread, maxRibbonSpread, ribbonSpreadTop,
                ribbonSpreadBottom, mid, head, tail);
    }


    TicksTimesSeriesData<TickData> getMinTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_min; } }; }
    TicksTimesSeriesData<TickData> getMaxTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_max; } }; }
    TicksTimesSeriesData<TickData> getMidTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_mid; } }; }
    TicksTimesSeriesData<TickData> getZigZagTs() { return new JoinNonChangedInnerTimesSeriesData(getParent(), false) { @Override protected Float getValue() { return m_zigZag; } }; }
}
