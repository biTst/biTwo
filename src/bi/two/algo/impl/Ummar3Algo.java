package bi.two.algo.impl;

import bi.two.ChartCanvas;
import bi.two.Colors;
import bi.two.algo.Watcher;
import bi.two.chart.*;
import bi.two.exch.Exchange;
import bi.two.opt.Vary;
import bi.two.ts.*;
import bi.two.util.MapConfig;

import java.awt.*;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static bi.two.util.Log.console;

public class Ummar3Algo extends BaseRibbonAlgo1 {

    private final float m_s1;// spread proportional start
    private final float m_s2;// gain proportional start
    private final float m_e1;// spread proportional end
    private final float m_e2;// gain proportional end

    private BarsTimesSeriesData m_priceBars;

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
    private Ummar2Algo.DoubleAdjuster m_da;

    public Ummar3Algo(MapConfig algoConfig, ITimesSeriesData tsd, Exchange exchange) {
        super(algoConfig, tsd, exchange);

        m_s1 = algoConfig.getNumber(Vary.s1).floatValue();
        m_s2 = algoConfig.getNumber(Vary.s2).floatValue();
        m_e1 = algoConfig.getNumber(Vary.e1).floatValue();
        m_e2 = algoConfig.getNumber(Vary.e2).floatValue();

        if (m_collectValues) {
            m_priceBars = new BarsTimesSeriesData(tsd, m_barSize);
        }
    }

    @Override protected void recalc2(float lastPrice, float emasMin, float emasMax, float leadEmaValue, boolean goUp,
                                     boolean directionChanged, float ribbonSpread, float maxRibbonSpread, float ribbonSpreadTop, float ribbonSpreadBottom) {

//            m_min = emasMin;
//            m_max = emasMax;

        if (directionChanged) {
            m_xxx = goUp ? emasMax : emasMin;
            m_height = emasMax - emasMin;
            m_da = new Ummar2Algo.DoubleAdjuster(goUp ? 1 : -1);
        }

        if (m_xxx != null) {
            float height;
            float approachRate;
            float approachLevel;
            float gainLevel;
            float spread = ribbonSpreadTop - ribbonSpreadBottom;
            if (goUp) {
                float trend = ribbonSpreadTop - m_xxx;
                gainLevel = ribbonSpreadBottom + m_e1 * spread + m_e2 * trend;
                height = m_xxx - ribbonSpreadBottom;
                approachLevel = ribbonSpreadBottom + m_s1 * spread + m_s2 * trend;
            } else {
                float trend = m_xxx - ribbonSpreadBottom;
                gainLevel = ribbonSpreadTop - m_e1 * spread + m_e2 * trend;
                height = ribbonSpreadTop - m_xxx;
                approachLevel = ribbonSpreadTop - m_s1 * spread + m_s2 * trend;
            }
            approachRate = height / m_height;
            if (approachRate > 0) {
                m_level = approachLevel * approachRate + gainLevel * (1 - approachRate);
            } else {
                m_level = gainLevel;
            }
            if (m_da != null) {
                m_adj = m_da.update(ribbonSpreadTop, m_level, ribbonSpreadBottom, leadEmaValue);
            }

//            m_adj = goUp ? 1f : -1f;

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
                + (detailed ? "|s1=" : "|") + m_s1
                + (detailed ? ",s2=" : ",") + m_s2
                + (detailed ? ",e1=" : ",") + m_e1
                + (detailed ? ",e2=" : ",") + m_e2
                + (detailed ? "|minOrdMul=" : "|") + m_minOrderMul
                + (detailed ? "|joinTicks=" : "|") + m_joinTicks
//                /*+ ", " + Utils.millisToYDHMSStr(period)*/;
                ;
    }

    @Override public long getPreloadPeriod() {
        console("ummar3.getPreloadPeriod() m_start=" + m_start + "; m_step=" + m_step + "; m_count=" + m_count);
        return TimeUnit.MINUTES.toMillis(25); // todo: calc from algo params
    }

    @Override public void setupChart(boolean collectValues, ChartCanvas chartCanvas, BaseTicksTimesSeriesData<TickData> ticksTs, Watcher firstWatcher) {
        ChartData chartData = chartCanvas.getChartData();
        ChartSetting chartSetting = chartCanvas.getChartSetting();

        // layout
        ChartAreaSettings top = chartSetting.addChartAreaSettings("top", 0, 0, 1, 0.6f, Color.RED);
        List<ChartAreaLayerSettings> topLayers = top.getLayers();
        {
//            addChart(chartData, ticksTs, topLayers, "price", Colors.alpha(Color.RED, 70), TickPainter.TICK);
            addChart(chartData, m_priceBars, topLayers, "priceBars", Colors.alpha(Color.RED, 70), TickPainter.BAR);

//            chartData.setTicksData("spline", new NoTicksData());
////            topLayers.add(new ChartAreaLayerSettings("spline", Color.RED, new ChartAreaPainter.SplineChartAreaPainter(ticksTs, 4)));
//            topLayers.add(new ChartAreaLayerSettings("spline", Color.RED, new ChartAreaPainter.PolynomChartAreaPainter(ticksTs)));

//            Color emaColor = Colors.alpha(Color.BLUE, 25);
//            int size = m_emas.size();
//            for (int i = size - 1; i > 0; i--) {
//                BaseTimesSeriesData ema = m_emas[i];
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
            gain.setHorizontalLineValue(1);

            addChart(chartData, firstWatcher, topLayers, "trades", Color.WHITE, TickPainter.TRADE);

            List<ChartAreaLayerSettings> gainLayers = gain.getLayers();
            addChart(chartData, firstWatcher.getGainTs(), gainLayers, "gain", Color.blue, TickPainter.LINE);
        }
    }
}
