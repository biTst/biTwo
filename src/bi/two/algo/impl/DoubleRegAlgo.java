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
import bi.two.util.Utils;
import org.apache.commons.math3.stat.regression.SimpleRegression;

import java.awt.*;
import java.util.List;

import static bi.two.util.Log.console;

public class DoubleRegAlgo extends BaseRibbonAlgo3 {
    private static final boolean ADJUST_TAIL = true;

    private final long m_mature;

    private SimpleRegression m_exitRegression = new SimpleRegression(true);
    private long m_exitStartTime;
    private Float m_exitPeakValue;
    private Float m_exitValue;
    private Float m_exitMature;
    private Float m_exitValueMatured;

    private SimpleRegression m_enterRegression = new SimpleRegression(true);
    private long m_enterStartTime;
    private Float m_enterValue;

    static {
        console("ADJUST_TAIL=" + ADJUST_TAIL); // todo
    }

    public DoubleRegAlgo(MapConfig algoConfig, ITimesSeriesData inTsd, Exchange exchange) {
        super(algoConfig, inTsd, exchange, ADJUST_TAIL);
        m_mature = algoConfig.getNumber(Vary.mature).longValue();  // TimeUnit.MINUTES.toMillis(3);
    }

    @Override public void onTimeShift(long shift) {
        super.onTimeShift(shift);
        if (m_exitStartTime > 0) {
            m_exitStartTime += shift;
        }
        if (m_enterStartTime > 0) {
            m_enterStartTime += shift;
        }
    }

    @Override protected void recalc4(float lastPrice, float leadEmaValue, float ribbonSpread, float maxRibbonSpread,
                                     float ribbonSpreadTop, float ribbonSpreadBottom, float mid, float head, float tail) {
        long timestamp = m_timestamp;
        Boolean goUp = m_goUp;

        if (m_directionChanged) {
            SimpleRegression tmp = m_enterRegression;
            m_enterRegression = m_exitRegression;
            m_enterStartTime = m_exitStartTime;

            m_exitRegression = tmp; // restart exit regression calc
            m_exitStartTime = timestamp;
            tmp.clear();
        }

        if( (m_exitPeakValue == null)
                || ( goUp && (lastPrice > m_exitPeakValue) )
                || ( !goUp && (lastPrice < m_exitPeakValue) )) {
            // restart exit regression
            m_exitStartTime = timestamp;
            m_exitPeakValue = lastPrice;
            m_exitRegression.clear();
        }

        long enterTimeOffset = timestamp - m_enterStartTime;
        m_enterRegression.addData(enterTimeOffset, lastPrice);
        double enterPredict = m_enterRegression.predict(enterTimeOffset);
        Float enterValue = Double.isNaN(enterPredict)
                ? null
                : (float) enterPredict;
        m_enterValue = enterValue;

        long exitTimeOffset = timestamp - m_exitStartTime;
        m_exitRegression.addData(exitTimeOffset, lastPrice);
        float exitPredict = (float) m_exitRegression.predict(exitTimeOffset);
        boolean isNan = Double.isNaN(exitPredict);
        Float exitValue = isNan
                ? null
                : exitPredict;
        m_exitValue = exitValue;

        float exitMature = ((float) exitTimeOffset) / m_mature;
        if (exitMature > 1) {
            exitMature = 1;
        }
        m_exitMature = exitMature;

        Float exitValueMatured = isNan
                ? null
                : (exitMature == 1)
                    ? exitPredict
                    : exitPredict * exitMature + enterValue * (1 - exitMature);
        m_exitValueMatured = exitValueMatured;

    }

    TicksTimesSeriesData<TickData> getExitValueTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_exitValue; } }; }
    TicksTimesSeriesData<TickData> getEnterValueTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_enterValue; } }; }
    TicksTimesSeriesData<TickData> getExitValueMaruredTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_exitValueMatured; } }; }

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
//                + (detailed ? "|enter=" : "|") + m_enter
                + (detailed ? "|mature=" : "|") + m_mature
//                + (detailed ? "|commiss=" : "|") + Utils.format8(m_commission)
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

            addChart(chartData, getMinTs(), topLayers, "min", Colors.alpha(Color.RED, 150), TickPainter.LINE_JOIN);
            addChart(chartData, getMaxTs(), topLayers, "max", Colors.alpha(Color.RED, 150), TickPainter.LINE_JOIN);

//            addChart(chartData, getZigZagTs(), topLayers, "zigzag", Color.MAGENTA, TickPainter.LINE_JOIN);

            addChart(chartData, getHeadStartTs(), topLayers, "headStart", Colors.HAZELNUT, TickPainter.LINE_JOIN);
            addChart(chartData, getTailStartTs(), topLayers, "tailStart", Colors.HAZELNUT, TickPainter.LINE_JOIN);
            addChart(chartData, getMidStartTs(), topLayers, "midStart", Colors.HAZELNUT, TickPainter.LINE_JOIN);
            addChart(chartData, getMidTs(), topLayers, "mid", Colors.CHOCOLATE, TickPainter.LINE_JOIN);

            Color halfGray = Colors.alpha(Color.GRAY, 128);
            addChart(chartData, get1quarterTs(), topLayers, "1quarter", halfGray, TickPainter.LINE_JOIN);
            addChart(chartData, get3quarterTs(), topLayers, "3quarter", halfGray, TickPainter.LINE_JOIN);
            addChart(chartData, get5quarterTs(), topLayers, "5quarter", halfGray, TickPainter.LINE_JOIN);
            addChart(chartData, get6quarterTs(), topLayers, "6quarter", Colors.LEMONADE, TickPainter.LINE_JOIN);
            addChart(chartData, get7quarterTs(), topLayers, "7quarter", halfGray, TickPainter.LINE_JOIN);
            addChart(chartData, get8quarterTs(), topLayers, "8quarter", Colors.LEMONADE, TickPainter.LINE_JOIN);

            addChart(chartData, getRibbonSpreadTopTs(), topLayers, "RibbonSpreadTop", Colors.alpha(Colors.SWEET_POTATO, 128), TickPainter.LINE_JOIN);
            addChart(chartData, getRibbonSpreadBottomTs(), topLayers, "RibbonSpreadBottom", Colors.alpha(Colors.SWEET_POTATO, 128), TickPainter.LINE_JOIN);

            BaseTimesSeriesData leadEma = m_emas[0]; // fastest ema
            addChart(chartData, leadEma.getJoinNonChangedTs(), topLayers, "leadEma", Colors.alpha(Colors.GRANNY_SMITH, 150), TickPainter.LINE_JOIN);

            addChart(chartData, getExitValueTs(), topLayers, "ExitValue", Colors.TRANQUILITY, TickPainter.LINE_JOIN, false);
            addChart(chartData, getExitValueMaruredTs(), topLayers, "ExitValueMatured", Colors.TURQUOISE, TickPainter.LINE_JOIN);
            addChart(chartData, getEnterValueTs(), topLayers, "EnterValue", Colors.BLUE_PEARL, TickPainter.LINE_JOIN);
        }

        ChartAreaSettings power = chartSetting.addChartAreaSettings("power", 0, 0.6f, 1, 0.1f, Color.LIGHT_GRAY);
        {
            power.addHorizontalLineValue(1);
            power.addHorizontalLineValue(0);
            power.addHorizontalLineValue(-1);
            java.util.List<ChartAreaLayerSettings> powerLayers = power.getLayers();
//            addChart(chartData, getHeadCollapseDiffTs(), powerLayers, "HeadCollapseDiff", Colors.YELLOW, TickPainter.LINE_JOIN);
//            addChart(chartData, getHeadEdgeDiffTs(), powerLayers, "HeadEdgeDiff", Colors.LIGHT_BLUE, TickPainter.LINE_JOIN);
//            addChart(chartData, getSpreadExpandTs(), powerLayers, "SpreadExpand", Colors.LIGHT_BLUE_PEARL, TickPainter.LINE_JOIN);
//            addChart(chartData, getSpreadRateTs(), powerLayers, "SpreadRate", Colors.ROSE, TickPainter.LINE_JOIN);
//            addChart(chartData, getSecondHeadRateTs(), powerLayers, "SecondHeadRate", Colors.RED_HOT_RED, TickPainter.LINE_JOIN);
//            addChart(chartData, getEnterPowerTs(), powerLayers, "EnterPower", Colors.PLUM, TickPainter.LINE_JOIN);
//            addChart(chartData, getLeadEmaRateTs(), powerLayers, "LeadEmaRate", Colors.BURIED_TREASURE, TickPainter.LINE_JOIN);
//            addChart(chartData, getCollapseRateTs(), powerLayers, "CollapseRate", Colors.DARK_GREEN, TickPainter.LINE_JOIN);
        }

        ChartAreaSettings value = chartSetting.addChartAreaSettings("value", 0, 0.7f, 1, 0.15f, Color.LIGHT_GRAY);
        {
            value.addHorizontalLineValue(1);
            value.addHorizontalLineValue(0);
            value.addHorizontalLineValue(-1);
            java.util.List<ChartAreaLayerSettings> valueLayers = value.getLayers();
            addChart(chartData, getDirectionTs(), valueLayers, "direction", Color.RED, TickPainter.LINE_JOIN);
//            addChart(chartData, getAdjCollapsedTs(), valueLayers, "AdjCollapsed", Colors.SWEET_POTATO, TickPainter.LINE_JOIN);
//            addChart(chartData, getSmallerCollapseDirectionTs(), valueLayers, "SmallerCollapseDirection", Colors.SWEET_POTATO, TickPainter.LINE_JOIN);
//            addChart(chartData, getSecondHeadDirectionTs(), valueLayers, "SecondHeadDirection", Colors.DARK_GREEN, TickPainter.LINE_JOIN);
//            addChart(chartData, getSimplerTs(), valueLayers, "Simpler", Colors.CANDY_PINK, TickPainter.LINE_JOIN);
        }

        if (collectValues) {
            addChart(chartData, firstWatcher, topLayers, "trades", Color.WHITE, TickPainter.TRADE);
            {
                ChartAreaSettings gain = chartSetting.addChartAreaSettings("gain", 0, 0.85f, 1, 0.15f, Color.ORANGE);
                gain.addHorizontalLineValue(1);

                List<ChartAreaLayerSettings> gainLayers = gain.getLayers();
                addChart(chartData, firstWatcher.getGainTs(), gainLayers, "gain", Color.blue, TickPainter.LINE_JOIN);
            }
        }
    }
}
