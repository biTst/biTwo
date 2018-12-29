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

import java.awt.*;
import java.util.List;

import static bi.two.util.Log.console;

public class DoubleHeadAlgo extends BaseRibbonAlgo3 {
    private static final boolean ADJUST_TAIL = true;

    static {
        console("ADJUST_TAIL=" + ADJUST_TAIL); // todo
    }

    private final float m_enter;
    private final float m_rate;
    private final float m_p1;
    private final float m_p2;

    private Float m_headCollapseDouble;
    private Float m_smallerCollapse;
    private Float m_smallerCollapseDirection;
    private Float m_headCollapseDiff;
    private Float m_headEdgeDiff;
    private Float m_secondHead;
    private Float m_secondHeadDiff;
    private Float m_secondHeadRate;
    private Float m_secondHeadDirection;
    private Float m_enterPower;
    private Float m_prevAdjPlus;

    public DoubleHeadAlgo(MapConfig algoConfig, ITimesSeriesData inTsd, Exchange exchange) {
        super(algoConfig, inTsd, exchange, ADJUST_TAIL);
        m_enter = algoConfig.getNumber(Vary.enter).floatValue();
        m_rate = algoConfig.getNumber(Vary.rate).floatValue();
        m_p1 = algoConfig.getNumber(Vary.p1).floatValue();
        m_p2 = algoConfig.getNumber(Vary.p2).floatValue();
    }

    @Override protected void recalc4(float lastPrice, float leadEmaValue, float ribbonSpread, float maxRibbonSpread,
                                     float ribbonSpreadTop, float ribbonSpreadBottom, float mid, float head, float tail, Float tailStart) {
        Boolean goUp = m_goUp;
        float ribbonSpreadHead = goUp ? ribbonSpreadTop : ribbonSpreadBottom;
        float ribbonSpreadTail = goUp ? ribbonSpreadBottom : ribbonSpreadTop;

        float headCollapse = head - ribbonSpreadHead;
        m_headCollapseDouble = ribbonSpreadHead + headCollapse * m_rate;

        float smallerCollapse = goUp
                ? (m_headCollapseDouble > leadEmaValue) ? m_headCollapseDouble : leadEmaValue
                : (m_headCollapseDouble < leadEmaValue) ? m_headCollapseDouble : leadEmaValue;
        m_smallerCollapse = smallerCollapse;

        float smallerCollapseRate = (smallerCollapse - ribbonSpreadTail) / (ribbonSpreadHead - ribbonSpreadTail);
//        if (smallerCollapseRate > 1) {
//            smallerCollapseRate = 1f;
//        } else if (smallerCollapseRate < 0) {
//            smallerCollapseRate = 0f;
//        }
        float smallerCollapseDirection = smallerCollapseRate * 2 - 1;
        if (!goUp) {
            smallerCollapseDirection = -smallerCollapseDirection;
        }
        m_smallerCollapseDirection = smallerCollapseDirection;

        if (m_directionChanged) {
            m_headCollapseDiff = null;
            m_headEdgeDiff = null;
            m_secondHeadDiff = 0f;
        }
        float headCollapseDiff = head - smallerCollapse;
        float headCollapseDiffDiff = (m_headCollapseDiff == null) ? 0 : (headCollapseDiff - m_headCollapseDiff);
        m_headCollapseDiff = headCollapseDiff;

        float headEdgeDiff = ribbonSpreadHead - head;
        float headEdgeDiffDiff = (m_headEdgeDiff == null) ? 0 : (headEdgeDiff - m_headEdgeDiff);
        m_headEdgeDiff = headEdgeDiff;

        if (goUp) {
            if (headCollapseDiffDiff > 0) {
                m_secondHeadDiff += headCollapseDiffDiff * m_p1;
            }
            if (headEdgeDiffDiff < 0) {
                m_secondHeadDiff += headEdgeDiffDiff * m_p2;
            }
            if (m_secondHeadDiff < 0) {
                m_secondHeadDiff = 0f;
            }
        } else {
            if (headCollapseDiffDiff < 0) {
                m_secondHeadDiff += headCollapseDiffDiff * m_p1;
            }
            if (headEdgeDiffDiff > 0) {
                m_secondHeadDiff += headEdgeDiffDiff * m_p2;
            }
            if (m_secondHeadDiff > 0) {
                m_secondHeadDiff = 0f;
            }
        }
        float secondHead = ribbonSpreadHead - m_secondHeadDiff;
        m_secondHead = secondHead;

        float secondHeadRate = (secondHead - ribbonSpreadTail) / (ribbonSpreadHead - ribbonSpreadTail);
        if (secondHeadRate < 0) {
            secondHeadRate = 0;
        } else if (secondHeadRate > 1) {
            secondHeadRate = 1;
        }
        m_secondHeadRate = secondHeadRate;

        float secondHeadDirection = secondHeadRate * 2 - 1;
        if (!goUp) {
            secondHeadDirection = -secondHeadDirection;
        }
        m_secondHeadDirection = secondHeadDirection;

        float enterLevel = m_ribbon.calcEnterLevel(m_enter); // todo: calc only if tailStart updated
        float tailRun = tail - tailStart;
        float tailRunToEnter = enterLevel - tailStart;
        float enterPower = ((goUp && (tailRunToEnter <= 0)) || (!goUp && (tailRunToEnter >= 0))) ? 0 : tailRun / tailRunToEnter;
        if (enterPower > 1) {
            enterPower = 1;
        } else if(enterPower < 0) {
            enterPower = 0;
        }
        m_enterPower = enterPower;

        float adjToRun = goUp ? (1 - m_prevAdj) : (1 + m_prevAdj);
        float adjToCollapse = goUp ? (1 + m_prevAdj) : (1 - m_prevAdj);

        float adjToEdge = ((goUp && (secondHeadDirection > 0)) || (!goUp && (secondHeadDirection < 0))) ? adjToRun : adjToCollapse;
        float prevAdjPlus = (1 - enterPower) * (m_prevAdj + enterPower * adjToEdge * secondHeadDirection) + enterPower * secondHeadDirection;
        m_prevAdjPlus = prevAdjPlus;

        m_adj = prevAdjPlus;

//        m_adj = secondHeadDirection;
    }

    TicksTimesSeriesData<TickData> getHeadCollapseDoubleTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_headCollapseDouble; } }; }
    TicksTimesSeriesData<TickData> getSmallerCollapseTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_smallerCollapse; } }; }
    TicksTimesSeriesData<TickData> getSmallerCollapseDirectionTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_smallerCollapseDirection; } }; }
    TicksTimesSeriesData<TickData> getHeadCollapseDiffTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_headCollapseDiff; } }; }
    TicksTimesSeriesData<TickData> getHeadEdgeDiffTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_headEdgeDiff; } }; }
    TicksTimesSeriesData<TickData> getSecondHeadTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_secondHead; } }; }
    TicksTimesSeriesData<TickData> getSecondHeadRateTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_secondHeadRate; } }; }
    TicksTimesSeriesData<TickData> getSecondHeadDirectionTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_secondHeadDirection; } }; }
    TicksTimesSeriesData<TickData> getEnterPowerTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_enterPower; } }; }
    TicksTimesSeriesData<TickData> getPrevAdjPlusTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_prevAdjPlus; } }; }

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

            addChart(chartData, getRibbonSpreadTopTs(), topLayers, "maxTop", Colors.alpha(Colors.SWEET_POTATO, 128), TickPainter.LINE_JOIN);
            addChart(chartData, getRibbonSpreadBottomTs(), topLayers, "maxBottom", Colors.alpha(Colors.SWEET_POTATO, 128), TickPainter.LINE_JOIN);

            BaseTimesSeriesData leadEma = m_emas[0]; // fastest ema
            addChart(chartData, leadEma.getJoinNonChangedTs(), topLayers, "leadEma", Colors.alpha(Colors.GRANNY_SMITH, 150), TickPainter.LINE_JOIN);

            addChart(chartData, getHeadCollapseDoubleTs(), topLayers, "HeadCollapseDouble", Colors.alpha(Colors.YELLOW, 128), TickPainter.LINE_JOIN, false);
            addChart(chartData, getSmallerCollapseTs(), topLayers, "SmallerCollapse", Colors.BLUE_PEARL, TickPainter.LINE_JOIN);
            addChart(chartData, getSecondHeadTs(), topLayers, "SecondHead", Colors.CANDY_PINK, TickPainter.LINE_JOIN, false);
        }

        ChartAreaSettings power = chartSetting.addChartAreaSettings("power", 0, 0.6f, 1, 0.1f, Color.LIGHT_GRAY);
        {
            power.addHorizontalLineValue(1);
            power.addHorizontalLineValue(0);
            power.addHorizontalLineValue(-1);
            java.util.List<ChartAreaLayerSettings> powerLayers = power.getLayers();
            addChart(chartData, getHeadCollapseDiffTs(), powerLayers, "HeadCollapseDiff", Colors.YELLOW, TickPainter.LINE_JOIN);
            addChart(chartData, getHeadEdgeDiffTs(), powerLayers, "HeadEdgeDiff", Colors.LIGHT_BLUE, TickPainter.LINE_JOIN);
//            addChart(chartData, getSecondHeadRateTs(), powerLayers, "SecondHeadRate", Colors.RED_HOT_RED, TickPainter.LINE_JOIN);
//            addChart(chartData, getEnterPowerTs(), powerLayers, "EnterPower", Colors.PLUM, TickPainter.LINE_JOIN);
        }

        ChartAreaSettings value = chartSetting.addChartAreaSettings("value", 0, 0.7f, 1, 0.15f, Color.LIGHT_GRAY);
        {
            value.addHorizontalLineValue(1);
            value.addHorizontalLineValue(0);
            value.addHorizontalLineValue(-1);
            java.util.List<ChartAreaLayerSettings> valueLayers = value.getLayers();
            addChart(chartData, getDirectionTs(), valueLayers, "direction", Color.RED, TickPainter.LINE_JOIN);
            addChart(chartData, getSmallerCollapseDirectionTs(), valueLayers, "SmallerCollapseDirection", Colors.SWEET_POTATO, TickPainter.LINE_JOIN);
            addChart(chartData, getSecondHeadDirectionTs(), valueLayers, "SecondHeadDirection", Colors.DARK_GREEN, TickPainter.LINE_JOIN);
            addChart(chartData, getPrevAdjPlusTs(), valueLayers, "PrevAdjPlus", Colors.CANDY_PINK, TickPainter.LINE_JOIN);
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
