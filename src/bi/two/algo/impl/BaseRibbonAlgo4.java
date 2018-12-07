package bi.two.algo.impl;

import bi.two.chart.TickData;
import bi.two.exch.Exchange;
import bi.two.opt.Vary;
import bi.two.ts.ITimesSeriesData;
import bi.two.ts.TicksTimesSeriesData;
import bi.two.util.MapConfig;

abstract class BaseRibbonAlgo4 extends BaseRibbonAlgo3 {
    final float m_collapse;
    private final Collapser m_collapser;
    private Float m_collapseRate;
    Float m_remainedEnterDistance; // positive

    BaseRibbonAlgo4(MapConfig algoConfig, ITimesSeriesData inTsd, Exchange exchange, boolean adjustTail) {
        super(algoConfig, inTsd, exchange, adjustTail);

        m_collapse = algoConfig.getNumber(Vary.collapse).floatValue();
        m_collapser = new Collapser(m_collapse);
        m_ribbon.setCollapser(m_collapser);
    }

    protected abstract void recalc5(float lastPrice, float leadEmaValue, boolean goUp, boolean directionChanged,
                                    float ribbonSpread, float maxRibbonSpread, float ribbonSpreadTop, float ribbonSpreadBottom,
                                    float mid, float head, float tail, Float tailStart, float collapseRate);

    @Override protected void recalc4(float lastPrice, float leadEmaValue, boolean goUp, boolean directionChanged, float ribbonSpread, float maxRibbonSpread,
                           float ribbonSpreadTop, float ribbonSpreadBottom, float mid, float head, float tail, Float tailStart) {
        float collapseRate = m_collapser.update(tail);
        m_collapseRate = collapseRate;
        if (directionChanged) {
            m_remainedEnterDistance = goUp ? 1 - m_prevAdj : 1 + m_prevAdj;
        }
        recalc5( lastPrice, leadEmaValue, goUp, directionChanged, ribbonSpread, maxRibbonSpread, ribbonSpreadTop,
                ribbonSpreadBottom, mid, head, tail, tailStart, collapseRate);
    }

    @Override public void reset() {
        super.reset();
        m_collapseRate = null;
        m_remainedEnterDistance = null;
    }

    TicksTimesSeriesData<TickData> getCollapseRateTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_collapseRate; } }; }


    // --------------------------------------------------------------------------------------
    protected static class Collapser implements Ribbon.ICollapser {
        private final float m_step;
        private boolean m_goUp;
        private float m_head;
        private float m_tail;
        private float m_quarter;
        private float m_waitQuarterNum;
        private float m_waitLevel;
        private float m_rate;

        Collapser(float step) {
            m_step = step;
            m_rate = 0;
        }

        @Override public void init(boolean goUp, float head, float tail) {
            m_rate = 0;
            m_goUp = goUp;
            m_head = head;
            m_waitQuarterNum = 1;
            adjustTail(tail);
            m_tail = tail;
        }

        @Override public void adjustTail(float tail) {
            m_tail = tail;
            m_quarter = (m_head - m_tail) / 4;
            m_waitLevel = m_tail + m_quarter * m_waitQuarterNum;
        }

        public float update(float tail) {
            boolean reached = (m_goUp && (m_waitLevel <= tail)) || (!m_goUp && (m_waitLevel >= tail));
            if (reached) {
                m_waitQuarterNum++;
                m_waitLevel = m_tail + m_quarter * m_waitQuarterNum;
                m_rate = m_rate + (1 - m_rate) * m_step;
            }
            return m_rate;
        }
    }
}
