package bi.two.algo.impl;

import bi.two.chart.TickData;
import bi.two.exch.Exchange;
import bi.two.ts.ITimesSeriesData;
import bi.two.ts.TicksTimesSeriesData;
import bi.two.util.MapConfig;

abstract class BaseRibbonAlgo3 extends BaseRibbonAlgo2 {
    final String m_joinerName; // not used in computations - but for params logging
    final Ribbon m_ribbon;
    private BaseRibbonAlgo4.RibbonUi m_ribbonUi;

    protected abstract void recalc4(float lastPrice, float leadEmaValue, float ribbonSpread, float maxRibbonSpread,
                                    float ribbonSpreadTop, float ribbonSpreadBottom, float mid, float head, float tail);

    BaseRibbonAlgo3(MapConfig algoConfig, ITimesSeriesData inTsd, Exchange exchange, boolean adjustTail) {
        super(algoConfig, inTsd, exchange, adjustTail);
        m_joinerName = algoConfig.getString("joiner");

        Ribbon.ICollapser nullCollapser = new Ribbon.ICollapser() {
            @Override public void init(boolean goUp, float head, float tail) {}
            @Override public void adjustTail(float tail) {}
        };

        if (m_collectValues) {
            m_ribbonUi = new BaseRibbonAlgo4.RibbonUi(adjustTail, nullCollapser);
            m_ribbon = m_ribbonUi;
        } else {
            m_ribbon = new Ribbon(adjustTail, nullCollapser);
        }
    }

    @Override protected final void recalc3(float lastPrice, float emasMin, float emasMax, float leadEmaValue,
                                           float ribbonSpread, float maxRibbonSpread, float ribbonSpreadTop,
                                           float ribbonSpreadBottom, float mid, float head, float tail) {
        // m_tailStart can be changed inside of m_ribbon.update()
        m_ribbon.update(m_directionChanged, mid, head, tail, m_goUp); // use local var to speedup

        recalc4(lastPrice, leadEmaValue, ribbonSpread, maxRibbonSpread, ribbonSpreadTop, ribbonSpreadBottom, mid, head, tail);
    }

    @Override public void reset() {
        super.reset();
        m_ribbon.reset();
    }

    TicksTimesSeriesData<TickData> getHeadStartTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_ribbon.m_headStart; } }; }
    TicksTimesSeriesData<TickData> getTailStartTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_ribbon.m_tailStart; } }; }
    TicksTimesSeriesData<TickData> getMidStartTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_ribbon.m_midStart; } }; }

    TicksTimesSeriesData<TickData> get1quarterTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_ribbonUi.m_1quarterPaint; } }; }
    TicksTimesSeriesData<TickData> get3quarterTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_ribbonUi.m_3quarterPaint; } }; }
    TicksTimesSeriesData<TickData> get5quarterTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_ribbonUi.m_5quarterPaint; } }; }
    TicksTimesSeriesData<TickData> get6quarterTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_ribbonUi.m_6quarterPaint; } }; }
    TicksTimesSeriesData<TickData> get7quarterTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_ribbonUi.m_7quarterPaint; } }; }
    TicksTimesSeriesData<TickData> get8quarterTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_ribbonUi.m_8quarterPaint; } }; }


    // --------------------------------------------------------------------------------------
    protected static class Ribbon {
        protected final boolean m_adjustTail;
        protected ICollapser m_collapser;
        protected Float m_headStart;
        protected Float m_tailStart;
        protected Float m_midStart;

        public void setCollapser(BaseRibbonAlgo4.Collapser collapser) { m_collapser = collapser; }

        Ribbon(boolean adjustTail, ICollapser collapser) {
            m_collapser = collapser;
            m_adjustTail = adjustTail;
        }

        protected Float update(boolean directionChanged, float mid, float head, float tail, boolean goUp) {
            Float tailStart = m_tailStart; // use local var to speedup
            // common ribbon lines
            if (directionChanged) {
                m_headStart = head; // pink
                tailStart = tail;
                m_tailStart = tail;  // dark green
                m_midStart = mid;

                m_collapser.init(goUp, head, tail);
            } else {
                if (m_adjustTail) {
                    if ((tailStart != null) && ((goUp && (tail < tailStart)) || (!goUp && (tail > tailStart)))) {
                        tailStart = tail;
                        float spread = m_headStart - tail;
                        onTailAdjusted(tail, spread); // m_tailStart updated inside

                        m_collapser.adjustTail(tail);
                    }
                }
            }
            return tailStart;
        }

        protected void onTailAdjusted(float tail, float spread) {
            m_tailStart = tail;
            float half = spread / 2;
            m_midStart = tail + half;
        }

        protected void reset() {
            m_headStart = null;
            m_midStart = null;
            m_tailStart = null;
        }

        public float calcEnterLevel(float enter) {
            return m_tailStart + (m_headStart - m_tailStart) * enter;
        }


        // --------------------------------------------------------------------------------------
        public interface ICollapser {
            void init(boolean goUp, float head, float tail);
            void adjustTail(float tail);
        }
    }


    // --------------------------------------------------------------------------------------
    protected static class RibbonUi extends Ribbon {
        private Float m_1quarter;
        private Float m_1quarterPaint;
        private Float m_3quarter;
        private Float m_3quarterPaint;
        private Float m_5quarter;
        private Float m_5quarterPaint;
        private Float m_6quarter;
        private Float m_6quarterPaint;
        private Float m_7quarter;
        private Float m_7quarterPaint;
        private Float m_8quarter;
        private Float m_8quarterPaint;

        RibbonUi(boolean adjustTail, ICollapser collapser) {
            super(adjustTail, collapser);
        }

        @Override protected Float update(boolean directionChanged, float mid, float head, float tail, boolean goUp) {
            Float ret = super.update(directionChanged, mid, head, tail, goUp);
            if (directionChanged) {
                // this only for painting
                float spread = head - tail;
                float half = spread / 2;
                float quarter = spread / 4;
                m_1quarter = tail + quarter;
                m_1quarterPaint = m_1quarter;
                m_3quarter = mid + quarter;
                m_3quarterPaint = m_3quarter;
                m_5quarter = head + quarter;
                m_5quarterPaint = head;
                m_6quarter = head + half;
                m_6quarterPaint = head;
                m_7quarter = head + half + quarter;
                m_7quarterPaint = head;
                m_8quarter = head + spread;
                m_8quarterPaint = head;
            } else {
                if (m_6quarter != null) {
                    if (goUp) {
                        if (tail > m_1quarter) {
                            m_1quarterPaint = m_midStart;
                        }
                        if (tail > m_3quarter) {
                            m_3quarterPaint = m_headStart;
                        }
                        if (tail > m_5quarter) {
                            m_5quarterPaint = m_5quarter;
                            m_6quarterPaint = m_5quarter;
                            m_7quarterPaint = m_5quarter;
                            m_8quarterPaint = m_5quarter;
                        }
                        if (tail > m_6quarter) {
                            m_6quarterPaint = m_6quarter;
                            m_7quarterPaint = m_6quarter;
                            m_8quarterPaint = m_6quarter;
                        }
                        if (tail > m_7quarter) {
                            m_7quarterPaint = m_7quarter;
                            m_8quarterPaint = m_7quarter;
                        }
                        if (tail > m_8quarter) {
                            m_8quarterPaint = m_8quarter;
                        }
                    } else {
                        if (tail < m_1quarter) {
                            m_1quarterPaint = m_midStart;
                        }
                        if (tail < m_3quarter) {
                            m_3quarterPaint = m_headStart;
                        }
                        if (tail < m_5quarter) {
                            m_5quarterPaint = m_5quarter;
                            m_6quarterPaint = m_5quarter;
                            m_7quarterPaint = m_5quarter;
                            m_8quarterPaint = m_5quarter;
                        }
                        if (tail < m_6quarter) {
                            m_6quarterPaint = m_6quarter;
                            m_7quarterPaint = m_6quarter;
                            m_8quarterPaint = m_6quarter;
                        }
                        if (tail < m_7quarter) {
                            m_7quarterPaint = m_7quarter;
                            m_8quarterPaint = m_7quarter;
                        }
                        if (tail < m_8quarter) {
                            m_8quarterPaint = m_8quarter;
                        }
                    }
                }
            }
            return ret;
        }

        @Override protected void onTailAdjusted(float tail, float spread) {
            super.onTailAdjusted(tail, spread);
            float half = spread / 2;
            float quarter = spread / 4;
            m_1quarter = tail + quarter;
            m_1quarterPaint = m_1quarter;
            m_3quarter = m_headStart - quarter;
            m_3quarterPaint = m_3quarter;
            m_5quarter = m_headStart + quarter;
            m_5quarterPaint = m_headStart;
            m_6quarter = m_headStart + half;
            m_6quarterPaint = m_headStart;
            m_7quarter = m_headStart + half + quarter;
            m_7quarterPaint = m_headStart;
            m_8quarter = m_headStart + spread;
            m_8quarterPaint = m_headStart;
        }

        @Override protected void reset() {
            super.reset();
            m_1quarter = null;
            m_3quarter = null;
            m_5quarter = null;
            m_6quarter = null;
            m_7quarter = null;
            m_8quarter = null;
        }
    }
}
