package bi.two.algo.impl;

import bi.two.chart.TickData;
import bi.two.exch.Exchange;
import bi.two.ts.ITimesSeriesData;
import bi.two.ts.TicksTimesSeriesData;
import bi.two.util.MapConfig;

abstract class BaseRibbonAlgo2 extends BaseRibbonAlgo1 {
    protected final boolean m_adjustTail;
    protected Float m_mid;
    private Float m_zigZag;
    Float m_prevAdj = 0F;

    BaseRibbonAlgo2(MapConfig algoConfig, ITimesSeriesData inTsd, Exchange exchange, boolean adjustTail) {
        super(algoConfig, inTsd, exchange);
        m_adjustTail = adjustTail;
    }

    @Override public void reset() {
        super.reset();
        m_mid = null;
        m_zigZag = null;
        m_prevAdj = 0F;
    }

    protected abstract void recalc3(float mid, float head, float tail);

    protected final void recalc2() {
        Float emasMin = m_emasMin;
        Float emasMax = m_emasMax;
        float mid = (emasMin + emasMax) / 2;
        m_mid = mid;

        Boolean goUp = m_goUp;
        if (m_directionChanged) {
            // note - ribbonSpread from prev step here
            m_zigZag = goUp ? m_ribbonSpreadBottom : m_ribbonSpreadTop;
            m_prevAdj = m_adj; // save prev
        }

        float head = goUp ? emasMax : emasMin;
        float tail = goUp ? emasMin : emasMax;

        recalc3(
                mid, head, tail);
    }


    TicksTimesSeriesData<TickData> getMinTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_emasMin; } }; }
    TicksTimesSeriesData<TickData> getMaxTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_emasMax; } }; }
    TicksTimesSeriesData<TickData> getMidTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_mid; } }; }
    TicksTimesSeriesData<TickData> getZigZagTs() { return new JoinNonChangedInnerTimesSeriesData(getParent(), false) { @Override protected Float getValue() { return m_zigZag; } }; }
}
