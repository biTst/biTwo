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

public class QummarAlgo2 extends BaseRibbonAlgo4 {
    private static final boolean ADJUST_TAIL = false;
    private static final boolean PAINT_RIBBON = false;
    private static final boolean LIMIT_BY_PRICE = true;

    private final float m_enter;
    private final float m_backLevel;
    private Float m_ribbonSpreadMid;
    private Float m_headGainLevel;
    private Float m_enterPower;
//private Float m_exit1level;
//private Float m_exit2level;
    private Float m_exitMidLevel;
    private Float m_exit2power;
//    private Float m_penaltyLevel;
//    private Float m_enterPenalty;
//    private Float m_enterAdjusted;
    private Float m_adjNoLimit;

    public QummarAlgo2(MapConfig algoConfig, ITimesSeriesData inTsd, Exchange exchange) {
        super(algoConfig, inTsd, exchange, ADJUST_TAIL);
        m_enter = algoConfig.getNumber(Vary.enter).floatValue();
        m_backLevel = algoConfig.getNumber(Vary.backLevel).floatValue();
    }

    @Override protected void recalc5(float lastPrice, float leadEmaValue,
                                     float ribbonSpread, float maxRibbonSpread, float ribbonSpreadTop, float ribbonSpreadBottom,
                                     float mid, float head, float tail, Float tailStart, float collapseRate) {
        float ribbonSpreadMid = (ribbonSpreadTop + ribbonSpreadBottom) / 2;
        m_ribbonSpreadMid = ribbonSpreadMid;

        Boolean goUp = m_goUp;
        float ribbonSpreadHead = goUp ? ribbonSpreadTop : ribbonSpreadBottom;
        Float headStart = m_ribbon.m_headStart;
        float headGainLevel = headStart + (ribbonSpreadHead - headStart) * m_backLevel;
        m_headGainLevel = headGainLevel;

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

//        float penalty = 0.6f;
//        float penaltyLevel = tail + (head - tail) * penalty;
//        m_penaltyLevel = penaltyLevel;

//        float headMidDiff = head - penaltyLevel;
//        float enterPenalty = (headMidDiff == 0) ? 0 : (leadEmaValue - penaltyLevel) / headMidDiff;
//        if (enterPenalty < 0) {
//            enterPenalty = 0;
//        }
//        m_enterPenalty = enterPenalty;

//        float enterAdjusted = enterPower * (1 - collapseRate) * enterPenalty;
//        m_enterAdjusted = enterAdjusted;

        float exit1level = goUp
                ? Math.max(ribbonSpreadMid, headGainLevel)
                : Math.min(ribbonSpreadMid, headGainLevel);
//        m_exit1level = exit1level;

        float exit2level = goUp
                ? Math.min(ribbonSpreadMid, headGainLevel)
                : Math.max(ribbonSpreadMid, headGainLevel);
//        m_exit2level = exit2level;

        float exitMidLevel = (exit1level + exit2level) / 2;
        m_exitMidLevel = exitMidLevel;

        float exitLevel = exitMidLevel; //exit2level;
        float exit2power;
        if ((goUp && (exitLevel <= tail)) || (!goUp && (tail <= exitLevel))) {
            exit2power = 0;
        } else {
            exit2power = (leadEmaValue - exitLevel) / (tail - exitLevel);
            if (exit2power < 0) {
                exit2power = 0;
            } else if (exit2power > 1) {
                exit2power = 1;
            }
        }
        m_exit2power = exit2power;

////        int sign = goUp ? 1 : -1;
////        float adj = m_prevAdj + sign * m_remainedEnterDistance * enterPower;
////        adj = adj * (1 - collapseRate);
//
        float power = enterPower * (1 - collapseRate) * (1 - exit2power);

//if(Float.isInfinite(power)) {
//    Log.console("Infinite power: enterPower="+enterPower+"; collapseRate="+collapseRate+"; exit2power="+exit2power);
//}
//if(Float.isNaN(power)) {
//    Log.console("Nan power: enterPower="+enterPower+"; collapseRate="+collapseRate+"; exit2power="+exit2power);
//}
//if(power > 1) {
//    Log.console("power > 1: " + power + ": enterPower="+enterPower+"; collapseRate="+collapseRate+"; exit2power="+exit2power );
//}
//if(power < -1) {
//    Log.console("power < -1: " + power + ": enterPower="+enterPower+"; collapseRate="+collapseRate+"; exit2power="+exit2power );
//}

        float adj = goUp ? power : -power;
        m_adjNoLimit = adj;

        if (LIMIT_BY_PRICE) {
            float limitLevel = (leadEmaValue + head) / 2;
            if (goUp) {
                if (adj > m_adj) {
                    if (lastPrice > limitLevel) {
                        m_adj = adj;
                    }
                } else {
                    m_adj = adj;
                }
            } else {
                if (adj < m_adj) {
                    if (lastPrice < limitLevel) {
                        m_adj = adj;
                    }
                } else {
                    m_adj = adj;
                }
            }
        } else {
            m_adj = adj;
        }
    }

    @Override public void reset() {
        super.reset();
        m_ribbonSpreadMid = null;
        m_headGainLevel = null;
        m_enterPower = null;

//m_exit1level = null;
//m_exit2level = null;
m_exit2power = null;
        m_exitMidLevel = null;
//        m_enterPenalty = null;
//        m_penaltyLevel = null;
//        m_enterAdjusted = null;
    }

    @Override public String key(boolean detailed) {
        detailed = true;
        return  ""
                + (detailed ? ",start=" : ",") + m_start
                + (detailed ? ",step=" : ",") + m_step
                + (detailed ? ",count=" : ",") + m_count
                + (detailed ? ",linRegMult=" : ",") + m_linRegMultiplier
                + (detailed ? ",collapse=" : ",") + Utils.format8((double) m_collapse)
                + (detailed ? ",enter=" : ",") + m_enter
                + (detailed ? ",backLevel=" : ",") + m_backLevel
                + (detailed ? "|minOrdMul=" : "|") + m_minOrderMul
                + (detailed ? "|joinTicks=" : "|") + m_joinTicks
                + (detailed ? "|joiner=" : "|") + m_joinerName
                + (detailed ? "|turn=" : "|") + Utils.format8(m_turnLevel)
                + (detailed ? "|commiss=" : "|") + Utils.format8(m_commission)
                + ", " + m_barSize
//                + ", " + Utils.millisToYDHMSStr(m_barSize)
                ;
    }

    TicksTimesSeriesData<TickData> getRibbonSpreadMidTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_ribbonSpreadMid; } }; }
    TicksTimesSeriesData<TickData> getHeadGainLevel() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_headGainLevel; } }; }
    TicksTimesSeriesData<TickData> getEnterPowerTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_enterPower; } }; }
//TicksTimesSeriesData<TickData> getExit1levelTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_exit1level; } }; }
//TicksTimesSeriesData<TickData> getExit2levelTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_exit2level; } }; }
TicksTimesSeriesData<TickData> getExit2powerTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_exit2power; } }; }
TicksTimesSeriesData<TickData> getExitMidLevelTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_exitMidLevel; } }; }
//TicksTimesSeriesData<TickData> getPenaltyLevelTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_penaltyLevel; } }; }
//TicksTimesSeriesData<TickData> getEnterPenaltyTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_enterPenalty; } }; }
//TicksTimesSeriesData<TickData> getEnterAdjustedTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_enterAdjusted; } }; }
    TicksTimesSeriesData<TickData> getAdjNoLimitTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_adjNoLimit; } }; }

    @Override public void setupChart(boolean collectValues, ChartCanvas chartCanvas, BaseTicksTimesSeriesData<TickData> ticksTs, Watcher firstWatcher) {
        ChartData chartData = chartCanvas.getChartData();
        ChartSetting chartSetting = chartCanvas.getChartSetting();

        int priceAlpha = 120;

        // layout
        ChartAreaSettings top = chartSetting.addChartAreaSettings("top", 0, 0, 1, 0.6f, Color.RED);
        java.util.List<ChartAreaLayerSettings> topLayers = top.getLayers();
        {
            addChart(chartData, ticksTs, topLayers, "price", Colors.alpha(Colors.DARK_RED, priceAlpha), TickPainter.TICK_JOIN);
//            addChart(chartData, m_priceBars, topLayers, "priceBars", Colors.alpha(Colors.DARK_RED, 80), TickPainter.BAR);

            if (PAINT_RIBBON) {
                int emaAlpha = 50;
                Color emaColor = Colors.alpha(Color.BLUE, emaAlpha);
                int size = m_emas.length;
                for (int i = size - 1; i > 0; i--) { // paint without leadEma
                    BaseTimesSeriesData ema = m_emas[i];
                    addChart(chartData, ema.getJoinNonChangedTs(), topLayers, "ema" + i, emaColor, TickPainter.LINE);
                }
            }

            addChart(chartData, getRibbonSpreadMidTs(), topLayers, "ribbonMid", Colors.CAMOUFLAGE, TickPainter.LINE_JOIN);
            addChart(chartData, getHeadGainLevel(), topLayers, "headGainLevel", Colors.alpha(Colors.SILVVER, 200), TickPainter.LINE_JOIN);

            addChart(chartData, getMinTs(), topLayers, "min", Color.BLUE, TickPainter.LINE_JOIN);
            addChart(chartData, getMaxTs(), topLayers, "max", Color.BLUE, TickPainter.LINE_JOIN);
            addChart(chartData, getMidTs(), topLayers, "mid", Colors.alpha(Colors.CHOCOLATE, 130), TickPainter.LINE_JOIN);

            addChart(chartData, getZigZagTs(), topLayers, "zigzag", Color.MAGENTA, TickPainter.LINE_JOIN);

            addChart(chartData, getHeadStartTs(), topLayers, "headStart", Color.PINK, TickPainter.LINE_JOIN);
            addChart(chartData, getTailStartTs(), topLayers, "tailStart", Colors.DARK_GREEN, TickPainter.LINE_JOIN);
            addChart(chartData, getMidStartTs(), topLayers, "midStart", Colors.PURPLE, TickPainter.LINE_JOIN);

            Color halfGray = Colors.alpha(Color.GRAY, 128);
            addChart(chartData, get1quarterTs(), topLayers, "1quarter", halfGray, TickPainter.LINE_JOIN);
            addChart(chartData, get3quarterTs(), topLayers, "3quarter", halfGray, TickPainter.LINE_JOIN);
            addChart(chartData, get5quarterTs(), topLayers, "5quarter", halfGray, TickPainter.LINE_JOIN);
            addChart(chartData, get6quarterTs(), topLayers, "6quarter", Colors.TAN, TickPainter.LINE_JOIN);
            addChart(chartData, get7quarterTs(), topLayers, "7quarter", halfGray, TickPainter.LINE_JOIN);
            addChart(chartData, get8quarterTs(), topLayers, "8quarter", Colors.TAN, TickPainter.LINE_JOIN);

            addChart(chartData, getRibbonSpreadTopTs(), topLayers, "ribbonTop", Colors.SWEET_POTATO, TickPainter.LINE_JOIN);
            addChart(chartData, getRibbonSpreadBottomTs(), topLayers, "ribbonBottom", Color.CYAN, TickPainter.LINE_JOIN);

            BaseTimesSeriesData leadEma = m_emas[0]; // fastest ema
            addChart(chartData, leadEma.getJoinNonChangedTs(), topLayers, "leadEma", Colors.GRANNY_SMITH, TickPainter.LINE_JOIN);

//addChart(chartData, getExit1levelTs(), topLayers, "exit1level", Colors.RED_HOT_RED, TickPainter.LINE_JOIN);
//addChart(chartData, getExit2levelTs(), topLayers, "exit2level", Colors.ROSE, TickPainter.LINE_JOIN);
addChart(chartData, getExitMidLevelTs(), topLayers, "ExitMidLevel", Colors.ROSE, TickPainter.LINE_JOIN);
//addChart(chartData, getPenaltyLevelTs(), topLayers, "PenaltyLevel", Colors.GENTLE_PLUM, TickPainter.LINE_JOIN);
        }

        ChartAreaSettings power = chartSetting.addChartAreaSettings("power", 0, 0.6f, 1, 0.1f, Color.LIGHT_GRAY);
        java.util.List<ChartAreaLayerSettings> powerLayers = power.getLayers();
        {
            addChart(chartData, getEnterPowerTs(),   powerLayers, "enterPower",   Colors.alpha(Color.MAGENTA,    200), TickPainter.LINE_JOIN);
            addChart(chartData, getExit2powerTs(),   powerLayers, "exit2Power",   Colors.alpha(Colors.TURQUOISE, 200), TickPainter.LINE_JOIN);
            addChart(chartData, getCollapseRateTs(), powerLayers, "collapseRate", Colors.alpha(Colors.GOLD,      200), TickPainter.LINE_JOIN);
//            addChart(chartData, getEnterPenaltyTs(), powerLayers, "enterPenalty", Colors.CHOCOLATE, TickPainter.LINE_JOIN);
//            addChart(chartData, getEnterAdjustedTs(), powerLayers, "EnterAdjusted", Colors.DARK_GREEN, TickPainter.LINE_JOIN);
        }

        ChartAreaSettings value = chartSetting.addChartAreaSettings("value", 0, 0.7f, 1, 0.15f, Color.LIGHT_GRAY);
        java.util.List<ChartAreaLayerSettings> valueLayers = value.getLayers();
        {
            addChart(chartData, getDirectionTs(), valueLayers, "direction", Color.RED, TickPainter.LINE_JOIN);
            addChart(chartData, getAdjNoLimitTs(), valueLayers, "AdjNoLimit", Colors.alpha(Colors.FUSCHIA_PEARL, 200), TickPainter.LINE_JOIN);
        }

        if (collectValues) {
            addChart(chartData, firstWatcher, topLayers, "trades", Color.WHITE, TickPainter.TRADE);

            ChartAreaSettings gain = chartSetting.addChartAreaSettings("gain", 0, 0.85f, 1, 0.15f, Color.ORANGE);
            gain.addHorizontalLineValue(1);
            {
                List<ChartAreaLayerSettings> gainLayers = gain.getLayers();
                addChart(chartData, firstWatcher.getGainTs(), gainLayers, "gain", Color.blue, TickPainter.LINE_JOIN);
            }
        }
    }
}
