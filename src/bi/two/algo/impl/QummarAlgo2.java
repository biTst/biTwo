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
import bi.two.util.Log;
import bi.two.util.MapConfig;
import bi.two.util.Utils;

import java.awt.*;
import java.util.List;

public class QummarAlgo2 extends BaseRibbonAlgo3 {
    private static final boolean ADJUST_TAIL = true;
    private static final boolean PAINT_RIBBON = false;

    private final float m_enter;
    private final float m_backLevel;
    private Float m_ribbonSpreadMid;
    private Float m_headGainHalf;
    private Float m_enterPower;
private Float m_exit1level;
private Float m_exit2level;
private Float m_exit2power;

    public QummarAlgo2(MapConfig algoConfig, ITimesSeriesData inTsd, Exchange exchange) {
        super(algoConfig, inTsd, exchange, ADJUST_TAIL);
        m_enter = algoConfig.getNumber(Vary.enter).floatValue();
        m_backLevel = algoConfig.getNumber(Vary.backLevel).floatValue();
    }

    @Override protected void recalc4(float lastPrice, float leadEmaValue, boolean goUp, boolean directionChanged,
                                     float ribbonSpread, float maxRibbonSpread, float ribbonSpreadTop, float ribbonSpreadBottom,
                                     float mid, float head, float tail, Float tailStart, float collapseRate) {
        float ribbonSpreadMid = (ribbonSpreadTop + ribbonSpreadBottom) / 2;
        m_ribbonSpreadMid = ribbonSpreadMid;

        float ribbonSpreadHead = goUp ? ribbonSpreadTop : ribbonSpreadBottom;
        Float headStart = m_ribbon.m_headStart;
//        float headGainLevel = (ribbonSpreadHead + headStart) / 2; // todo:   /2 = *0.5; add vary of this level
        float headGainLevel = headStart + (ribbonSpreadHead - headStart) * m_backLevel;
        m_headGainHalf = headGainLevel;

        float enterLevel = m_ribbon.calcEnterLevel(m_enter);
        float tailRun = tail - tailStart;
        float tailRunToEnter = enterLevel - tailStart;
        float enterPower = ((goUp && (tailRunToEnter <= 0)) || (!goUp && (tailRunToEnter >= 0))) ? 0 : tailRun / tailRunToEnter;
        if (enterPower > 1) {
            enterPower = 1;
        }
        m_enterPower = enterPower;

        float exit1level = goUp
                ? Math.max(ribbonSpreadMid, headGainLevel)
                : Math.min(ribbonSpreadMid, headGainLevel);
        m_exit1level = exit1level;

        float exit2level = goUp
                ? Math.min(ribbonSpreadMid, headGainLevel)
                : Math.max(ribbonSpreadMid, headGainLevel);
        m_exit2level = exit2level;

        float exit2power;
        if ((goUp && (exit2level <= tail)) || (!goUp && (tail <= exit2level))) {
            exit2power = 0;
        } else {
            exit2power = (leadEmaValue - exit2level) / (tail - exit2level);
            if (exit2power < 0) {
                exit2power = 0;
            } else if (exit2power > 1) {
                exit2power = 1;
            }
        }
        m_exit2power = exit2power;

        float power = enterPower * (1 - collapseRate) * (1 - exit2power);

if(Float.isInfinite(power)) {
    Log.console("Infinite power: enterPower="+enterPower+"; collapseRate="+collapseRate+"; exit2power="+exit2power);
}
if(Float.isNaN(power)) {
    Log.console("Nan power: enterPower="+enterPower+"; collapseRate="+collapseRate+"; exit2power="+exit2power);
}
if(power > 1) {
    Log.console("power > 1: " + power + ": enterPower="+enterPower+"; collapseRate="+collapseRate+"; exit2power="+exit2power );
}
if(power < -1) {
    Log.console("power < -1: " + power + ": enterPower="+enterPower+"; collapseRate="+collapseRate+"; exit2power="+exit2power );
}
        m_adj = goUp ? power : -power;

        // ...
    }

    @Override public void reset() {
        super.reset();
        m_ribbonSpreadMid = null;
        m_headGainHalf = null;
        m_enterPower = null;

m_exit1level = null;
m_exit2level = null;
m_exit2power = null;
    }

    @Override public String key(boolean detailed) {
        detailed = true;
        return  ""
                + (detailed ? ",start=" : ",") + m_start
                + (detailed ? ",step=" : ",") + m_step
                + (detailed ? ",count=" : ",") + m_count
                + (detailed ? ",linRegMult=" : ",") + m_linRegMultiplier
                + (detailed ? ",collapse=" : ",") + m_collapse
                + (detailed ? ",enter=" : ",") + m_enter
                + (detailed ? ",backLevel=" : ",") + m_backLevel
                + (detailed ? "|minOrdMul=" : "|") + m_minOrderMul
                + (detailed ? "|joinTicks=" : "|") + m_joinTicks
                + (detailed ? "|turn=" : "|") + m_turnLevel
                + (detailed ? "|commiss=" : "|") + Utils.format8(m_commission)
                + ", " + m_barSize
//                + ", " + Utils.millisToYDHMSStr(m_barSize)
                ;
    }

    TicksTimesSeriesData<TickData> getRibbonSpreadMidTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_ribbonSpreadMid; } }; }
    TicksTimesSeriesData<TickData> getHeadGainHalfTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_headGainHalf; } }; }
    TicksTimesSeriesData<TickData> getEnterPowerTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_enterPower; } }; }
TicksTimesSeriesData<TickData> getExit1levelTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_exit1level; } }; }
TicksTimesSeriesData<TickData> getExit2levelTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_exit2level; } }; }
TicksTimesSeriesData<TickData> getExit2powerTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_exit2power; } }; }

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
            addChart(chartData, getHeadGainHalfTs(), topLayers, "headGainHalf", Colors.SILVVER, TickPainter.LINE_JOIN);

            addChart(chartData, getMinTs(), topLayers, "min", Color.BLUE, TickPainter.LINE_JOIN);
            addChart(chartData, getMaxTs(), topLayers, "max", Color.BLUE, TickPainter.LINE_JOIN);

            addChart(chartData, getZigZagTs(), topLayers, "zigzag", Color.MAGENTA, TickPainter.LINE_JOIN);

            addChart(chartData, getHeadStartTs(), topLayers, "headStart", Color.PINK, TickPainter.LINE_JOIN);
            addChart(chartData, getTailStartTs(), topLayers, "tailStart", Colors.DARK_GREEN, TickPainter.LINE_JOIN);
            addChart(chartData, getMidStartTs(), topLayers, "midStart", Colors.PURPLE, TickPainter.LINE_JOIN);
//            addChart(chartData, getMidTs(), topLayers, "mid", Colors.CHOCOLATE, TickPainter.LINE_JOIN);

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

addChart(chartData, getExit1levelTs(), topLayers, "exit1level", Colors.RED_HOT_RED, TickPainter.LINE_JOIN);
addChart(chartData, getExit2levelTs(), topLayers, "exit2level", Colors.ROSE, TickPainter.LINE_JOIN);
        }

        ChartAreaSettings power = chartSetting.addChartAreaSettings("power", 0, 0.6f, 1, 0.1f, Color.LIGHT_GRAY);
        java.util.List<ChartAreaLayerSettings> powerLayers = power.getLayers();
        {
            addChart(chartData, getEnterPowerTs(), powerLayers, "enterPower", Color.MAGENTA, TickPainter.LINE_JOIN);
            addChart(chartData, getExit2powerTs(), powerLayers, "exit2Power", Colors.TURQUOISE, TickPainter.LINE_JOIN);
            addChart(chartData, getCollapseRateTs(), powerLayers, "collapseRate", Colors.GOLD, TickPainter.LINE_JOIN);
        }

        ChartAreaSettings value = chartSetting.addChartAreaSettings("value", 0, 0.7f, 1, 0.15f, Color.LIGHT_GRAY);
        java.util.List<ChartAreaLayerSettings> valueLayers = value.getLayers();
        {
            addChart(chartData, getDirectionTs(), valueLayers, "direction", Color.RED, TickPainter.LINE_JOIN);
        }

        if (collectValues) {
            addChart(chartData, firstWatcher, topLayers, "trades", Color.WHITE, TickPainter.TRADE);

            ChartAreaSettings gain = chartSetting.addChartAreaSettings("gain", 0, 0.85f, 1, 0.15f, Color.ORANGE);
            gain.setHorizontalLineValue(1);
            {
                List<ChartAreaLayerSettings> gainLayers = gain.getLayers();
                addChart(chartData, firstWatcher.getGainTs(), gainLayers, "gain", Color.blue, TickPainter.LINE_JOIN);
            }
        }
    }
}
