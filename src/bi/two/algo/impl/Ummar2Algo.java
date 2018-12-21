package bi.two.algo.impl;

import bi.two.ChartCanvas;
import bi.two.Colors;
import bi.two.algo.Watcher;
import bi.two.chart.*;
import bi.two.exch.Exchange;
import bi.two.opt.Vary;
import bi.two.ts.BaseTicksTimesSeriesData;
import bi.two.ts.BaseTimesSeriesData;
import bi.two.ts.ITimesSeriesData;
import bi.two.ts.TicksTimesSeriesData;
import bi.two.util.MapConfig;

import java.awt.*;
import java.util.List;

public class Ummar2Algo extends BaseRibbonAlgo1 {
    private final float m_threshold;
    private final float m_reverse;

    private float m_min;
    private float m_max;
    private Float m_xxx;
    private float m_height;
    private Float m_trend;
    //    private Float m_mirror;
    //    private Float m_reverse;
    private Float m_top;
    private Float m_bottom;
    private float m_level;
    private DoubleAdjuster m_da;

    public Ummar2Algo(MapConfig algoConfig, ITimesSeriesData tsd, Exchange exchange) {
        super(algoConfig, tsd, exchange);

        m_threshold = algoConfig.getNumber(Vary.threshold).floatValue();
        m_reverse = algoConfig.getNumber(Vary.reverse).floatValue();
    }


    @Override protected void recalc2(float lastPrice, float emasMin, float emasMax, float leadEmaValue,
                                     float ribbonSpread, float maxRibbonSpread, float ribbonSpreadTop, float ribbonSpreadBottom) {
//            m_min = emasMin;
//            m_max = emasMax;

        Boolean goUp = m_goUp;
        if (m_directionChanged) {
            m_xxx = goUp ? emasMax : emasMin;
//                m_height = emasMax - emasMin;
            m_da = new DoubleAdjuster(goUp ? 1 : -1);
        }

        if (m_xxx != null) {

            float rate = m_threshold;
            m_level = goUp
                    ? ribbonSpreadBottom < m_xxx
                        ? m_xxx * m_reverse + ribbonSpreadBottom * (1-m_reverse)
                        : ribbonSpreadTop * rate + ribbonSpreadBottom * (1-rate)
                    : ribbonSpreadTop > m_xxx
                        ? m_xxx * m_reverse + ribbonSpreadTop * (1-m_reverse)
                        : ribbonSpreadTop * (1-rate) + ribbonSpreadBottom * rate;

            if (m_da != null) {
                m_adj = m_da.update(ribbonSpreadTop, m_level, ribbonSpreadBottom, leadEmaValue);
            }

//            m_adj = goUp ? 1f : -1f;


//                float trend = goUp
//                        ? (m_ribbonSpreadTop - m_xxx) * m_threshold
//                        : (m_xxx - m_ribbonSpreadBottom) * m_threshold;
//                m_trend = goUp
//                        ? m_xxx + trend
//                        : m_xxx - trend;
//
//                m_top = m_ribbonSpreadTop - trend;
//                m_bottom = m_ribbonSpreadBottom + trend;
//
//                float height = goUp
//                        ? m_xxx - m_ribbonSpreadBottom
//                        : m_ribbonSpreadTop - m_xxx;


//                m_mirror = goUp
//                        ? m_xxx - trend
//                        : m_xxx + trend;
//                m_reverse = goUp
//                        ? m_ribbonSpreadBottom + trend
//                        : m_ribbonSpreadTop - trend;

//                float adj = goUp
//                        ? (leadEmaValue - m_ribbonSpreadBottom) / (m_trend - m_ribbonSpreadBottom)
//                        : (leadEmaValue - m_trend) / (m_ribbonSpreadTop - m_trend);
//                if (adj > 1) {
//                    adj = 1;
//                } else if (adj < 0) {
//                    adj = 0;
//                }
//                m_adj = adj * 2 - 1;

//                float mid = m_trend;
//                if (goUp) {
//                    if (m_trend < m_ribbonSpreadBottom) {
//                        float diff = m_ribbonSpreadBottom - m_trend;
//                        mid = m_ribbonSpreadBottom + diff * m_signal;
//                    }
//                    float bottom = Math.max(m_ribbonSpreadBottom, m_mirror);
//                    m_adj2 = m_da.update(m_ribbonSpreadTop, mid, bottom, leadEmaValue);
//                } else {
//                    if (m_trend > m_ribbonSpreadTop) {
//                        float diff = m_trend - m_ribbonSpreadTop;
//                        mid = m_ribbonSpreadTop - diff * m_signal;
//                    }
//                    float top = Math.min(m_ribbonSpreadTop, m_mirror);
//                    m_adj2 = m_da.update(top, mid, m_ribbonSpreadBottom, leadEmaValue);
//                }
//                m_mid = mid;
        }

//            m_tick = new TickData(getParent().getLatestTick().getTimestamp(), ribbonSpread);
    }

    TicksTimesSeriesData<TickData> getMinTs() { return new JoinNonChangedInnerTimesSeriesData(this) { @Override protected Float getValue() { return m_min; } }; }
    TicksTimesSeriesData<TickData> getMaxTs() { return new JoinNonChangedInnerTimesSeriesData(this) { @Override protected Float getValue() { return m_max; } }; }

    TicksTimesSeriesData<TickData> getXxxTs() { return new JoinNonChangedInnerTimesSeriesData(this) { @Override protected Float getValue() { return m_xxx; } }; }
    TicksTimesSeriesData<TickData> getTrendTs() { return new JoinNonChangedInnerTimesSeriesData(this) { @Override protected Float getValue() { return m_trend; } }; }
//    TicksTimesSeriesData<TickData> getMirrorTs() { return new JoinNonChangedInnerTimesSeriesData(this) { @Override protected Float getValue() { return m_mirror; } }; }
//    TicksTimesSeriesData<TickData> getReverseTs() { return new JoinNonChangedInnerTimesSeriesData(this) { @Override protected Float getValue() { return m_reverse; } }; }
    TicksTimesSeriesData<TickData> getTopTs() { return new JoinNonChangedInnerTimesSeriesData(this) { @Override protected Float getValue() { return m_top; } }; }
    TicksTimesSeriesData<TickData> getBottomTs() { return new JoinNonChangedInnerTimesSeriesData(this) { @Override protected Float getValue() { return m_bottom; } }; }
    TicksTimesSeriesData<TickData> getLevelTs() { return new JoinNonChangedInnerTimesSeriesData(this) { @Override protected Float getValue() { return m_level; } }; }


    @Override public String key(boolean detailed) {
        return  ""
                + (detailed ? ",start=" : ",") + m_start
                + (detailed ? ",step=" : ",") + m_step
                + (detailed ? ",count=" : ",") + m_count
                + (detailed ? ",multiplier=" : ",") + m_linRegMultiplier
                + (detailed ? ",threshold=" : ",") + m_threshold
                + (detailed ? ",reverse=" : ",") + m_reverse
//                + (detailed ? ",signal=" : ",") + m_signal
                + (detailed ? "|minOrdMul=" : "|") + m_minOrderMul
                + (detailed ? ",joinTicks=" : ",") + m_joinTicks
//                /*+ ", " + Utils.millisToYDHMSStr(period)*/;
                ;
    }


    @Override public void setupChart(boolean collectValues, ChartCanvas chartCanvas, BaseTicksTimesSeriesData<TickData> ticksTs, Watcher firstWatcher) {
        ChartData chartData = chartCanvas.getChartData();
        ChartSetting chartSetting = chartCanvas.getChartSetting();

        // layout
        ChartAreaSettings top = chartSetting.addChartAreaSettings("top", 0, 0, 1, 0.6f, Color.RED);
        List<ChartAreaLayerSettings> topLayers = top.getLayers();
        {
            addChart(chartData, ticksTs, topLayers, "price", Colors.alpha(Color.RED, 70), TickPainter.TICK);

//            chartData.setTicksData("spline", new NoTicksData());
////            topLayers.add(new ChartAreaLayerSettings("spline", Color.RED, new ChartAreaPainter.SplineChartAreaPainter(ticksTs, 4)));
//            topLayers.add(new ChartAreaLayerSettings("spline", Color.RED, new ChartAreaPainter.PolynomChartAreaPainter(ticksTs)));

//            Color emaColor = Colors.alpha(Color.BLUE, 25);
//            int size = m_emas.size();
//            for (int i = size - 1; i > 0; i--) {
//                BaseTimesSeriesData ema = m_emas.get(i);
//                Color color = (i == size - 1) ? Colors.alpha(Color.GRAY, 50) : emaColor;
//                addChart(chartData, ema.getJoinNonChangedTs(), topLayers, "ema" + i, color, TickPainter.LINE);
//            }

//            addChart(chartData, getMinTs(), topLayers, "min", Color.MAGENTA, TickPainter.LINE);
//            addChart(chartData, getMaxTs(), topLayers, "max", Color.MAGENTA, TickPainter.LINE);
////
            addChart(chartData, getRibbonSpreadTopTs(), topLayers, "maxTop", Color.CYAN, TickPainter.LINE);
            addChart(chartData, getRibbonSpreadBottomTs(), topLayers, "maxBottom", Color.CYAN, TickPainter.LINE);
            addChart(chartData, getXxxTs(), topLayers, "xxx", Color.GRAY, TickPainter.LINE);
////            addChart(chartData, getTrendTs(), topLayers, "trend", Color.GRAY, TickPainter.LINE);
////            addChart(chartData, getMirrorTs(), topLayers, "mirror", Colors.DARK_RED, TickPainter.LINE);
////            addChart(chartData, getReverseTs(), topLayers, "reverse", Colors.DARK_GREEN, TickPainter.LINE);
//            addChart(chartData, getTopTs(), topLayers, "top", Colors.DARK_RED, TickPainter.LINE);
//            addChart(chartData, getBottomTs(), topLayers, "bottom", Colors.DARK_GREEN, TickPainter.LINE);
            addChart(chartData, getLevelTs(), topLayers, "level", Color.PINK, TickPainter.LINE);
////            addChart(chartData, m_minMaxSpread.getMidTs(), topLayers, "mid", Color.PINK, TickPainter.LINE);

            BaseTimesSeriesData leadEma = m_emas[0];
            Color color = Color.GREEN;
            addChart(chartData, leadEma.getJoinNonChangedTs(), topLayers, "leadEma" , color, TickPainter.LINE);
        }

        ChartAreaSettings value = chartSetting.addChartAreaSettings("value", 0, 0.6f, 1, 0.2f, Color.LIGHT_GRAY);
        List<ChartAreaLayerSettings> valueLayers = value.getLayers();
        {
// ???
//            addChart(chartData, getTS(true), valueLayers, "value", Color.blue, TickPainter.LINE);
            addChart(chartData, getJoinNonChangedTs(), valueLayers, "value", Color.blue, TickPainter.LINE);
//            addChart(chartData, m_minMaxSpread.getAdj2Ts(), valueLayers, "adj2", Color.MAGENTA, TickPainter.LINE);
//            addChart(chartData, getPowAdjTs(), valueLayers, "powValue", Color.MAGENTA, TickPainter.LINE);
//            addChart(chartData, m_velocityAdj.getJoinNonChangedTs(), valueLayers, "velAdj", Color.RED, TickPainter.LINE);
        }

        if (collectValues) {
            ChartAreaSettings gain = chartSetting.addChartAreaSettings("gain", 0, 0.8f, 1, 0.2f, Color.ORANGE);
            gain.addHorizontalLineValue(1);

            addChart(chartData, firstWatcher, topLayers, "trades", Color.WHITE, TickPainter.TRADE);

            List<ChartAreaLayerSettings> gainLayers = gain.getLayers();
            addChart(chartData, firstWatcher.getGainTs(), gainLayers, "gain", Color.blue, TickPainter.LINE);
        }
    }



    //----------------------------------------------------------
    public static class DoubleAdjuster {
        public float m_init;
        public float m_value;
        private boolean m_up;

        public DoubleAdjuster(float value) {
            m_init = value;
            m_value = value;
            m_up = (value > 0);
        }

        public float update(float top, float mid, float bottom, float lead) {
            if (m_up && (lead < mid)) {
                m_up = false;
                m_init = m_value;
            }
            if (!m_up && (lead > mid)) {
                m_up = true;
                m_init = m_value;
            }

            if (m_up) {
                float dif = top - mid;
                if (dif > 0) {
                    float adj = (lead - mid) / dif; // [0...1]
                    float val = m_init + (1 - m_init) * adj;
                    if (val > m_value) {
                        if (val > 1) {
                            val = 1;
                        }
                        m_value = val;
                    }
                }
            } else {
                float dif = mid - bottom;
                if (dif > 0) {
                    float adj = (lead - bottom) / dif; // [0...1]
                    float val = (m_init + 1) * adj - 1;
                    if (val < m_value) {
                        if (val < -1) {
                            val = -1;
                        }
                        m_value = val;
                    }
                }
            }
            return m_value;
        }
    }
}
