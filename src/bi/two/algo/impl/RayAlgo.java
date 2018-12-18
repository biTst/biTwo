package bi.two.algo.impl;

import bi.two.ChartCanvas;
import bi.two.Colors;
import bi.two.algo.Watcher;
import bi.two.chart.*;
import bi.two.exch.Exchange;
import bi.two.ts.BaseTicksTimesSeriesData;
import bi.two.ts.BaseTimesSeriesData;
import bi.two.ts.ITimesSeriesData;
import bi.two.ts.TicksTimesSeriesData;
import bi.two.util.MapConfig;
import bi.two.util.Utils;

import java.awt.*;
import java.util.List;

import static bi.two.util.Log.console;

public class RayAlgo extends BaseRibbonAlgo3 {
    private static final boolean ADJUST_TAIL = false;

    static {
        console("ADJUST_TAIL=" + ADJUST_TAIL); // todo
    }

    private long m_lastSplitTime;
    private Float m_lastSplitValue;
    private long m_splitTime;
    private Float m_splitValue;
    private long m_turnTime;
    private float m_turnHead;
    private float m_turnTail;
    private double m_splitToTurnTime;
    private Float m_headRay;
    private Float m_tailRay;
    private Float m_midRay;

    public RayAlgo(MapConfig algoConfig, ITimesSeriesData inTsd, Exchange exchange) {
        super(algoConfig, inTsd, exchange, ADJUST_TAIL);
    }

    @Override public void onTimeShift(long shift) {
        super.onTimeShift(shift);
        if (m_lastSplitTime > 0) {
            m_lastSplitTime += shift;
        }
        if (m_splitTime > 0) {
            m_splitTime += shift;
        }
        if (m_turnTime > 0) {
            m_turnTime += shift;
        }
    }

    @Override protected void recalc4(float lastPrice, float leadEmaValue, boolean goUp, boolean directionChanged, float ribbonSpread,
                                     float maxRibbonSpread, float ribbonSpreadTop, float ribbonSpreadBottom, float mid, float head,
                                     float tail, Float tailStart) {
        if (m_lastSplitValue == null) {
            if (leadEmaValue != head) {
                m_lastSplitValue = (leadEmaValue + head) / 2;
                m_lastSplitTime = m_timestamp;
            }
        } else {
            if (m_directionChanged) {
                m_turnTime = m_timestamp;
                m_splitTime = m_lastSplitTime;
                m_splitValue = m_lastSplitValue;
                m_turnHead = head;
                m_turnTail = tail;
                m_splitToTurnTime = m_turnTime - m_splitTime;
            }

            if (leadEmaValue == head) {
                m_lastSplitValue = null;
            }
        }

        if (m_splitValue != null) {
            if (m_turnTime > 0) {
                double rate = (m_timestamp - m_splitTime) / m_splitToTurnTime;
                m_headRay = (float) (m_splitValue + (m_turnHead - m_splitValue) * rate);
                m_tailRay = (float) (m_splitValue + (m_turnTail - m_splitValue) * rate);
                m_midRay = (m_headRay + m_tailRay) / 2;
            }
        }
    }

    TicksTimesSeriesData<TickData> getSplitTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_lastSplitValue; } }; }
    TicksTimesSeriesData<TickData> getHeadRayTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_headRay; } }; }
    TicksTimesSeriesData<TickData> getTailRayTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_tailRay; } }; }
    TicksTimesSeriesData<TickData> getMidRayTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_midRay; } }; }

    @Override public String key(boolean detailed) {
        detailed = true;
        return  ""
                + (detailed ? ",start=" : ",") + m_start
                + (detailed ? ",step=" : ",") + m_step
                + (detailed ? ",count=" : ",") + m_count
                + (detailed ? ",linRegMult=" : ",") + m_linRegMultiplier
                + (detailed ? "|minOrdMul=" : "|") + m_minOrderMul
                + (detailed ? "|joinTicks=" : "|") + m_joinTicks
                + (detailed ? "|joiner=" : "|") + m_joinerName
                + (detailed ? "|turn=" : "|") + Utils.format8(m_turnLevel)
                + (detailed ? "|commiss=" : "|") + Utils.format8(m_commission)
                + ", " + m_barSize
//                + ", " + Utils.millisToYDHMSStr(m_barSize)
                ;
    }


    @Override public void setupChart(boolean collectValues, ChartCanvas chartCanvas, BaseTicksTimesSeriesData<TickData> ticksTs, Watcher firstWatcher) {
        ChartData chartData = chartCanvas.getChartData();
        ChartSetting chartSetting = chartCanvas.getChartSetting();

        int priceAlpha = 130;
        int emaAlpha = 50;

        // layout
        ChartAreaSettings top = chartSetting.addChartAreaSettings("top", 0, 0, 1, 0.6f, Color.RED);
        java.util.List<ChartAreaLayerSettings> topLayers = top.getLayers();
        {
            addChart(chartData, ticksTs, topLayers, "price", Colors.alpha(Colors.DARK_RED, priceAlpha), TickPainter.TICK_JOIN);
//            addChart(chartData, m_priceBars, topLayers, "priceBars", Colors.alpha(Colors.DARK_RED, 80), TickPainter.BAR);

//            Color emaColor = Colors.alpha(Color.BLUE, emaAlpha);
//            int size = m_emas.length;
//            for (int i = size - 1; i > 0; i--) { // paint without leadEma
//                BaseTimesSeriesData ema = m_emas[i];
//                addChart(chartData, ema.getJoinNonChangedTs(), topLayers, "ema" + i, emaColor, TickPainter.LINE);
//            }

//            addChart(chartData, m_sliding.getJoinNonChangedTs(), topLayers, "sliding", Colors.BALERINA, TickPainter.LINE);

            addChart(chartData, getMinTs(), topLayers, "min", Color.RED, TickPainter.LINE_JOIN);
            addChart(chartData, getMaxTs(), topLayers, "max", Color.RED, TickPainter.LINE_JOIN);

//            addChart(chartData, getZigZagTs(), topLayers, "zigzag", Color.MAGENTA, TickPainter.LINE_JOIN);

//            addChart(chartData, getHeadStartTs(), topLayers, "headStart", Color.PINK, TickPainter.LINE_JOIN);
//            addChart(chartData, getTailStartTs(), topLayers, "tailStart", Colors.DARK_GREEN, TickPainter.LINE_JOIN);
//            addChart(chartData, getMidStartTs(), topLayers, "midStart", Colors.PURPLE, TickPainter.LINE_JOIN);
//            addChart(chartData, getMidTs(), topLayers, "mid", Colors.CHOCOLATE, TickPainter.LINE_JOIN);

//            Color halfGray = Colors.alpha(Color.GRAY, 128);
//            addChart(chartData, get1quarterTs(), topLayers, "1quarter", halfGray, TickPainter.LINE_JOIN);
//            addChart(chartData, get3quarterTs(), topLayers, "3quarter", halfGray, TickPainter.LINE_JOIN);
//            addChart(chartData, get5quarterTs(), topLayers, "5quarter", halfGray, TickPainter.LINE_JOIN);
//            addChart(chartData, get6quarterTs(), topLayers, "6quarter", Colors.LEMONADE, TickPainter.LINE_JOIN);
//            addChart(chartData, get7quarterTs(), topLayers, "7quarter", halfGray, TickPainter.LINE_JOIN);
//            addChart(chartData, get8quarterTs(), topLayers, "8quarter", Colors.LEMONADE, TickPainter.LINE_JOIN);

            addChart(chartData, getRibbonSpreadTopTs(), topLayers, "maxTop", Colors.SWEET_POTATO, TickPainter.LINE_JOIN);
            addChart(chartData, getRibbonSpreadBottomTs(), topLayers, "maxBottom", Color.CYAN, TickPainter.LINE_JOIN);

            BaseTimesSeriesData leadEma = m_emas[0]; // fastest ema
            addChart(chartData, leadEma.getJoinNonChangedTs(), topLayers, "leadEma", Colors.GRANNY_SMITH, TickPainter.LINE_JOIN);

            addChart(chartData, getSplitTs(), topLayers, "split", Colors.GENTLE_PLUM, TickPainter.LINE_JOIN);
            addChart(chartData, getHeadRayTs(), topLayers, "headRay", Colors.ROSE, TickPainter.LINE_JOIN, false);
            addChart(chartData, getTailRayTs(), topLayers, "tailRay", Colors.ROSE, TickPainter.LINE_JOIN, false);
            addChart(chartData, getMidRayTs(), topLayers, "midRay", Colors.alpha(Colors.ROSE, 128), TickPainter.LINE_JOIN, false);
        }

        ChartAreaSettings power = chartSetting.addChartAreaSettings("power", 0, 0.6f, 1, 0.1f, Color.LIGHT_GRAY);
        {
            java.util.List<ChartAreaLayerSettings> powerLayers = power.getLayers();
//            addChart(chartData, getCollapseRateTs(), powerLayers, "collapseRate", Colors.YELLOW, TickPainter.LINE_JOIN);
        }

        ChartAreaSettings value = chartSetting.addChartAreaSettings("value", 0, 0.7f, 1, 0.15f, Color.LIGHT_GRAY);
        {
            java.util.List<ChartAreaLayerSettings> valueLayers = value.getLayers();
//            addChart(chartData, getDirectionTs(), valueLayers, "direction", Color.RED, TickPainter.LINE_JOIN);

        }

        if (collectValues) {
            addChart(chartData, firstWatcher, topLayers, "trades", Color.WHITE, TickPainter.TRADE);
            {
                ChartAreaSettings gain = chartSetting.addChartAreaSettings("gain", 0, 0.85f, 1, 0.15f, Color.ORANGE);
                gain.setHorizontalLineValue(1);

                List<ChartAreaLayerSettings> gainLayers = gain.getLayers();
                addChart(chartData, firstWatcher.getGainTs(), gainLayers, "gain", Color.blue, TickPainter.LINE_JOIN);
            }
        }
    }
}
