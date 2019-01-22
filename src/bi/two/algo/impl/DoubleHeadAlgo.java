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
    private final float m_collapse;
    private final float m_reverseMul;
    private final float m_power;
    private final float m_spread;
    private final float m_spreadPower;
    private final float m_reverse;

    private Float m_headCollapseDouble;
    private Float m_smallerCollapse;
//    private Float m_smallerCollapseDirection;
    private Float m_headCollapseDiff;
    private Float m_headEdgeDiff;
    private Float m_secondHead;
    private Float m_secondHeadDiff;
    private Float m_secondHeadRate;
    private Float m_secondHeadDirection;
    private Float m_enterPower;
    private Float m_simpler;
    private Float m_leadEmaRate;
    private Float m_minLeadEmaRate;
    private Float m_collapseRate;
//    private Float m_adjCollapsed;
    private Float m_enterLevel;
    private Float m_spreadExpand;
    private float m_spreadSum;
    private int m_turnCount;
    private float m_initSpread;
    private Float m_spreadRate;

    @Override public void notifyFinish() {
        float avgSpread = m_spreadSum / m_turnCount;
        console("@#$% avgSpread=" + avgSpread);
    }

    public DoubleHeadAlgo(MapConfig algoConfig, ITimesSeriesData inTsd, Exchange exchange) {
        super(algoConfig, inTsd, exchange, ADJUST_TAIL);
        m_enter = algoConfig.getNumber(Vary.enter).floatValue();
        m_rate = algoConfig.getNumber(Vary.rate).floatValue();
        m_p1 = algoConfig.getNumber(Vary.p1).floatValue();
        m_p2 = algoConfig.getNumber(Vary.p2).floatValue();
        m_collapse = algoConfig.getNumber(Vary.collapse).floatValue();
        m_reverseMul = algoConfig.getNumber(Vary.reverseMul).floatValue();
        m_power = algoConfig.getNumber(Vary.power).floatValue();
        m_spread = algoConfig.getNumber(Vary.spread).floatValue();
        m_spreadPower = algoConfig.getNumber(Vary.spreadPower).floatValue();
        m_reverse = algoConfig.getNumber(Vary.reverse).floatValue();
    }

    @Override protected void recalc4(float lastPrice, float leadEmaValue, float maxRibbonSpread,
                                     float mid, float head, float tail) {
        Boolean goUp = m_goUp;
        float ribbonSpreadTop = m_ribbonSpreadTop;
        float ribbonSpreadBottom = m_ribbonSpreadBottom;
        float ribbonSpreadHead = goUp ? ribbonSpreadTop : ribbonSpreadBottom;
        float ribbonSpreadTail = goUp ? ribbonSpreadBottom : ribbonSpreadTop;

        Float headStart = m_ribbon.m_headStart;
        Float tailStart = m_ribbon.m_tailStart;
//        float spreadExpand = (ribbonSpreadHead - ribbonSpreadTail) / m_initSpread;
//        m_spreadExpand = spreadExpand;

        float headCollapse = head - ribbonSpreadHead;
        float headCollapseDouble = ribbonSpreadHead + headCollapse * m_rate;
        m_headCollapseDouble = headCollapseDouble;

        float smallerCollapse = goUp
                ? (headCollapseDouble > leadEmaValue) ? headCollapseDouble : leadEmaValue
                : (headCollapseDouble < leadEmaValue) ? headCollapseDouble : leadEmaValue;
        m_smallerCollapse = smallerCollapse;

//        float smallerCollapseRate = (smallerCollapse - ribbonSpreadTail) / (ribbonSpreadHead - ribbonSpreadTail);
//        float smallerCollapseDirection = smallerCollapseRate * 2 - 1;
//        if (!goUp) {
//            smallerCollapseDirection = -smallerCollapseDirection;
//        }
//        m_smallerCollapseDirection = smallerCollapseDirection;

        float spread = head - tail;
        float absSpread = (spread < 0) ? -spread : spread;
        float spreadNormalized = absSpread / lastPrice;
        if (m_directionChanged) {
            m_headCollapseDiff = null;
            m_headEdgeDiff = null;
            m_secondHeadDiff = 0f;
            m_minLeadEmaRate = 1f;

            m_initSpread = spread; // can be negative
            m_spreadSum += spreadNormalized;
            m_turnCount++;
        }

        float spreadRate = spreadNormalized / m_spread;
        m_spreadRate = spreadRate;
        float spreadRateLimited = (spreadRate > 1) ? 1 : spreadRate; // [0...1]
        float spreadRatePowered = (float)Math.pow(spreadRateLimited, m_spreadPower); // [0...1]

        float headCollapseDiff = head - smallerCollapse;
        float headCollapseDiffDiff = (m_headCollapseDiff == null) ? 0 : (headCollapseDiff - m_headCollapseDiff);
        m_headCollapseDiff = headCollapseDiff;

        float headEdgeDiff = ribbonSpreadHead - head;
        float headEdgeDiffDiff = (m_headEdgeDiff == null) ? 0 : (headEdgeDiff - m_headEdgeDiff);
        m_headEdgeDiff = headEdgeDiff;

        if (goUp) {
            if (headCollapseDiffDiff > 0) { m_secondHeadDiff += headCollapseDiffDiff * m_p1; }
            if (headEdgeDiffDiff < 0) { m_secondHeadDiff += headEdgeDiffDiff * m_p2; }
            if (m_secondHeadDiff < 0) { m_secondHeadDiff = 0f; }
        } else {
            if (headCollapseDiffDiff < 0) { m_secondHeadDiff += headCollapseDiffDiff * m_p1; }
            if (headEdgeDiffDiff > 0) { m_secondHeadDiff += headEdgeDiffDiff * m_p2; }
            if (m_secondHeadDiff > 0) { m_secondHeadDiff = 0f; }
        }
        float secondHead = ribbonSpreadHead - m_secondHeadDiff;
        m_secondHead = secondHead;

        float secondHeadRate = (secondHead - ribbonSpreadTail) / (ribbonSpreadHead - ribbonSpreadTail); // [1 -> 0]
        secondHeadRate = 1 - (1 - secondHeadRate) * m_reverseMul;
        if (secondHeadRate < 0) { secondHeadRate = 0; } else if (secondHeadRate > 1) { secondHeadRate = 1; } // limit to [0...1]
        secondHeadRate = (float) Math.pow(secondHeadRate, m_power);
        m_secondHeadRate = secondHeadRate;

        float secondHeadDirection = secondHeadRate * 2 - 1;
        if (!goUp) { secondHeadDirection = -secondHeadDirection; }
        m_secondHeadDirection = secondHeadDirection;

        float leadEmaRate = (leadEmaValue - ribbonSpreadTail) / (ribbonSpreadHead - ribbonSpreadTail); // [1 -> 0]
        m_leadEmaRate = leadEmaRate;

        float minLeadEmaRate = m_minLeadEmaRate;
        if (leadEmaRate < minLeadEmaRate) {
            minLeadEmaRate = leadEmaRate;
            m_minLeadEmaRate = minLeadEmaRate;
        }

        float enterLevel = m_ribbon.calcEnterLevel(m_enter / minLeadEmaRate); // todo: calc only if tailStart updated
        if (goUp) {
            if (enterLevel > headStart) { enterLevel = headStart; }
        } else {
            if (enterLevel < headStart) { enterLevel = headStart; }
        }
        m_enterLevel = enterLevel;

        float tailRun = tail - tailStart;
        float tailRunToEnter = enterLevel - tailStart;
        float enterPower = ((goUp && (tailRunToEnter <= 0)) || (!goUp && (tailRunToEnter >= 0))) ? 0 : (tailRun / tailRunToEnter); // [0 -> 1]
        if (enterPower > 1) { enterPower = 1; } else if(enterPower < 0) { enterPower = 0; } // limit to [0...1]
        m_enterPower = enterPower;

        float collapseRate = 1 - leadEmaRate; // [0 -> 1]
        m_collapseRate = collapseRate;

//        float adjToRun = goUp ? (1 - m_prevAdj) : (1 + m_prevAdj);
        float adjToCollapse = goUp ? (-1 - m_prevAdj) : (1 - m_prevAdj);

//        float adjToEdge = ((goUp && (secondHeadDirection > 0)) || (!goUp && (secondHeadDirection < 0))) ? adjToRun : adjToCollapse;
//        float adj = (1 - enterPower) * (m_prevAdj + enterPower * adjToEdge * secondHeadDirection) + enterPower * secondHeadDirection;

//        float adjCollapsed = adj - (adj + (goUp ? 1 : -1)) * m_collapse * collapseRate;
//        if (adjCollapsed > 1) { adjCollapsed = 1; } else if (adjCollapsed < 1) { adjCollapsed = -1; } // limit [-1...1]
//        m_adjCollapsed = adjCollapsed;

        float simpler = (1 - enterPower) * (m_prevAdj + adjToCollapse * m_reverse * collapseRate)
                      + enterPower       * secondHeadDirection * spreadRatePowered;
        m_simpler = simpler;

//        if(adjCollapsed>1 || adjCollapsed<-1) {
//            console("err");
//        }

        m_adj = simpler;
    }

    TicksTimesSeriesData<TickData> getHeadCollapseDoubleTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_headCollapseDouble; } }; }
    TicksTimesSeriesData<TickData> getSmallerCollapseTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_smallerCollapse; } }; }
//    TicksTimesSeriesData<TickData> getSmallerCollapseDirectionTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_smallerCollapseDirection; } }; }
    TicksTimesSeriesData<TickData> getHeadCollapseDiffTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_headCollapseDiff; } }; }
    TicksTimesSeriesData<TickData> getHeadEdgeDiffTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_headEdgeDiff; } }; }
    TicksTimesSeriesData<TickData> getSecondHeadTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_secondHead; } }; }
    TicksTimesSeriesData<TickData> getSecondHeadRateTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_secondHeadRate; } }; }
    TicksTimesSeriesData<TickData> getSecondHeadDirectionTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_secondHeadDirection; } }; }
    TicksTimesSeriesData<TickData> getEnterPowerTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_enterPower; } }; }
    TicksTimesSeriesData<TickData> getSimplerTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_simpler; } }; }
    TicksTimesSeriesData<TickData> getLeadEmaRateTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_leadEmaRate; } }; }
    TicksTimesSeriesData<TickData> getCollapseRateTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_collapseRate; } }; }
//    TicksTimesSeriesData<TickData> getAdjCollapsedTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_adjCollapsed; } }; }
    TicksTimesSeriesData<TickData> getEnterLevelTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_enterLevel; } }; }
    TicksTimesSeriesData<TickData> getSpreadExpandTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_spreadExpand; } }; }
    TicksTimesSeriesData<TickData> getSpreadRateTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_spreadRate; } }; }

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
                + (detailed ? "|enter=" : "|") + m_enter
                + (detailed ? "|rate=" : "|") + m_rate
                + (detailed ? "|p1=" : "|") + m_p1
                + (detailed ? "|p2=" : "|") + m_p2
//                + (detailed ? "|collapse=" : "|") + m_collapse
                + (detailed ? "|reverseMul=" : "|") + m_reverseMul
                + (detailed ? "|power=" : "|") + m_power
                + (detailed ? "|spread=" : "|") + Utils.format6((double) m_spread)
                + (detailed ? "|spreadPower=" : "|") + m_spreadPower
                + (detailed ? "|reverse=" : "|") + Utils.format6((double) m_reverse)
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
//            addChart(chartData, get3quarterTs(), topLayers, "3quarter", halfGray, TickPainter.LINE_JOIN);
//            addChart(chartData, get5quarterTs(), topLayers, "5quarter", halfGray, TickPainter.LINE_JOIN);
//            addChart(chartData, get6quarterTs(), topLayers, "6quarter", Colors.LEMONADE, TickPainter.LINE_JOIN);
//            addChart(chartData, get7quarterTs(), topLayers, "7quarter", halfGray, TickPainter.LINE_JOIN);
//            addChart(chartData, get8quarterTs(), topLayers, "8quarter", Colors.LEMONADE, TickPainter.LINE_JOIN);

            addChart(chartData, getRibbonSpreadTopTs(), topLayers, "RibbonSpreadTop", Colors.alpha(Colors.SWEET_POTATO, 128), TickPainter.LINE_JOIN);
            addChart(chartData, getRibbonSpreadBottomTs(), topLayers, "RibbonSpreadBottom", Colors.alpha(Colors.SWEET_POTATO, 128), TickPainter.LINE_JOIN);

            BaseTimesSeriesData leadEma = m_emas[0]; // fastest ema
            addChart(chartData, leadEma.getJoinNonChangedTs(), topLayers, "leadEma", Colors.alpha(Colors.GRANNY_SMITH, 150), TickPainter.LINE_JOIN);

            addChart(chartData, getEnterLevelTs(), topLayers, "EnterLevel", Colors.alpha(Colors.LIGHT_GREEN, 128), TickPainter.LINE_JOIN, false);
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
//            addChart(chartData, getHeadCollapseDiffTs(), powerLayers, "HeadCollapseDiff", Colors.YELLOW, TickPainter.LINE_JOIN);
//            addChart(chartData, getHeadEdgeDiffTs(), powerLayers, "HeadEdgeDiff", Colors.LIGHT_BLUE, TickPainter.LINE_JOIN);
//            addChart(chartData, getSpreadExpandTs(), powerLayers, "SpreadExpand", Colors.LIGHT_BLUE_PEARL, TickPainter.LINE_JOIN);
//            addChart(chartData, getSpreadRateTs(), powerLayers, "SpreadRate", Colors.ROSE, TickPainter.LINE_JOIN);
            addChart(chartData, getSecondHeadRateTs(), powerLayers, "SecondHeadRate", Colors.RED_HOT_RED, TickPainter.LINE_JOIN);
            addChart(chartData, getEnterPowerTs(), powerLayers, "EnterPower", Colors.PLUM, TickPainter.LINE_JOIN);
            addChart(chartData, getLeadEmaRateTs(), powerLayers, "LeadEmaRate", Colors.BURIED_TREASURE, TickPainter.LINE_JOIN);
            addChart(chartData, getCollapseRateTs(), powerLayers, "CollapseRate", Colors.DARK_GREEN, TickPainter.LINE_JOIN);
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
            addChart(chartData, getSecondHeadDirectionTs(), valueLayers, "SecondHeadDirection", Colors.DARK_GREEN, TickPainter.LINE_JOIN);
            addChart(chartData, getSimplerTs(), valueLayers, "Simpler", Colors.CANDY_PINK, TickPainter.LINE_JOIN);
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
