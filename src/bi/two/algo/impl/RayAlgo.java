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

public class RayAlgo extends BaseRibbonAlgo3 {
    private static final boolean ADJUST_TAIL = false;

    static {
        console("ADJUST_TAIL=" + ADJUST_TAIL); // todo
    }

    private final float m_enter;

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
    private Float m_headPlusRay;
    private Float m_headPlusRay2;
    private Float m_headPlusRay3;
    private Float m_lvl;
    private float m_headPlus;
    private Float m_enterPower;
    private Float m_headCollapseDouble;

    private SimpleRegression m_enterRegression = new SimpleRegression(true);
    private SimpleRegression m_exitRegression = new SimpleRegression(true);
    private long m_exitStartTime;
    private Float m_exitValue;

    public RayAlgo(MapConfig algoConfig, ITimesSeriesData inTsd, Exchange exchange) {
        super(algoConfig, inTsd, exchange, ADJUST_TAIL);

        m_enter = algoConfig.getNumber(Vary.enter).floatValue();
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

    @Override protected void recalc4(float lastPrice, float leadEmaValue, float ribbonSpread,
                                     float maxRibbonSpread, float ribbonSpreadTop, float ribbonSpreadBottom, float mid, float head,
                                     float tail, Float tailStart) {
        if (m_directionChanged) {
            SimpleRegression tmp = m_enterRegression;
            m_enterRegression = m_exitRegression;
            m_exitRegression = tmp;
            tmp.clear();
        }

        long timestamp = m_timestamp;
        Boolean goUp = m_goUp;
        long exitStartTime = m_exitStartTime;
        boolean exit = goUp
                ? (lastPrice < ribbonSpreadTop) // up
                : (lastPrice > ribbonSpreadBottom); // down
        if (exit) { // exit started/continue
            if (exitStartTime == 0) { // exit started
                m_exitStartTime = timestamp;
            }
            long exitTimeOffset = m_exitStartTime - timestamp;
            m_exitRegression.addData(exitTimeOffset, lastPrice);
            double predict = m_exitRegression.predict(exitTimeOffset);
            if (Double.isNaN(predict)) {
                m_exitValue = null;
            } else {
                m_exitValue = (float)predict;
            }
        } else { // exit not confirmed
            if (exitStartTime != 0) { // reset exit calculations
                m_exitStartTime = 0;
                m_exitRegression.clear();
                m_exitValue = null;
            }
        }

        float ribbonSpreadHead = goUp ? ribbonSpreadTop : ribbonSpreadBottom;
        if (m_lastSplitValue == null) {
            if (head != ribbonSpreadHead) {
                m_lastSplitValue = (head + ribbonSpreadHead) / 2;
                m_lastSplitTime = timestamp;
            }
//            if (leadEmaValue != head) {
//                m_lastSplitValue = (leadEmaValue + head) / 2;
//                m_lastSplitTime = m_timestamp;
//            }
        } else {
            if (m_directionChanged) {
                m_turnTime = timestamp;
                m_splitTime = m_lastSplitTime;
                m_splitValue = m_lastSplitValue;
                m_turnHead = head;
                m_turnTail = tail;
                m_splitToTurnTime = m_turnTime - m_splitTime;

                float headPlus = (m_lvl == null)
                        ? 0
                        : goUp
                            ? (1 - m_lvl) / (m_lvl + 1)
                            : (m_lvl + 1) / (1 - m_lvl);
                if (headPlus < 0) {
                    headPlus = 0;
                }
                m_headPlus = headPlus;
            }

            if (head == ribbonSpreadHead) {
//            if (leadEmaValue == head) {
                m_lastSplitValue = null;
            }
        }

        float headCollapse = head - ribbonSpreadHead;
        m_headCollapseDouble = ribbonSpreadHead + headCollapse * 2;

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

        if ((m_splitValue != null) && (m_turnTime > 0)) {
            double rate = (timestamp - m_splitTime) / m_splitToTurnTime;
            m_headRay = (float) (m_splitValue + (m_turnHead - m_splitValue) * rate);
            m_tailRay = (float) (m_splitValue + (m_turnTail - m_splitValue) * rate);
            m_midRay = (m_headRay + m_tailRay) / 2;
            m_headPlusRay = m_headRay + (m_headRay - m_tailRay) * m_headPlus;
            m_headPlusRay2 = m_headRay + (m_headRay - m_tailRay) * m_headPlus * (1 - enterPower);
            m_headPlusRay3 = goUp
                    ? (m_ribbonSpreadTop > m_headPlusRay2) ? m_ribbonSpreadTop : m_headPlusRay2
                    : (m_ribbonSpreadBottom < m_headPlusRay2) ? m_ribbonSpreadBottom : m_headPlusRay2;

            float lvl = (head - m_tailRay) / (m_headPlusRay3 - m_tailRay) * 2 - 1;
            if (!goUp) {
                lvl = -lvl;
            }
            if (lvl > 1) {
                lvl = 1.0f;
            }
            if (lvl < -1) {
                lvl = -1;
            }
            m_lvl = lvl;

            m_adj = lvl;
        }
    }

    TicksTimesSeriesData<TickData> getSplitTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_lastSplitValue; } }; }
    TicksTimesSeriesData<TickData> getHeadRayTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_headRay; } }; }
    TicksTimesSeriesData<TickData> getTailRayTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_tailRay; } }; }
    TicksTimesSeriesData<TickData> getMidRayTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_midRay; } }; }
    TicksTimesSeriesData<TickData> getLvlTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_lvl; } }; }
    TicksTimesSeriesData<TickData> getHeadPlusTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_headPlus; } }; }
    TicksTimesSeriesData<TickData> getHeadPlusRayTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_headPlusRay; } }; }
    TicksTimesSeriesData<TickData> getHeadPlusRay2Ts() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_headPlusRay2; } }; }
    TicksTimesSeriesData<TickData> getHeadPlusRay3Ts() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_headPlusRay3; } }; }
    TicksTimesSeriesData<TickData> getEnterPowerTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_enterPower; } }; }
    TicksTimesSeriesData<TickData> getHeadCollapseDoubleTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_headCollapseDouble; } }; }
    TicksTimesSeriesData<TickData> getExitValueTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_exitValue; } }; }

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
            addChart(chartData, getMidStartTs(), topLayers, "midStart", Colors.PURPLE, TickPainter.LINE_JOIN);
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
//            addChart(chartData, getHeadPlusRayTs(), topLayers, "headPlusRay", Colors.CHOCOLATE, TickPainter.LINE_JOIN, false);
            addChart(chartData, getHeadPlusRay2Ts(), topLayers, "headPlusRay2", Colors.HAZELNUT, TickPainter.LINE_JOIN, false);
            addChart(chartData, getHeadPlusRay3Ts(), topLayers, "headPlusRay3", Colors.TAN, TickPainter.LINE_JOIN, false);
            addChart(chartData, getHeadCollapseDoubleTs(), topLayers, "HeadCollapseDouble", Colors.YELLOW, TickPainter.LINE_JOIN);
            addChart(chartData, getExitValueTs(), topLayers, "ExitValue", Colors.TAN, TickPainter.LINE_JOIN);
        }

        ChartAreaSettings power = chartSetting.addChartAreaSettings("power", 0, 0.6f, 1, 0.1f, Color.LIGHT_GRAY);
        {
            power.addHorizontalLineValue(1);
            power.addHorizontalLineValue(0);
            power.addHorizontalLineValue(-1);
            java.util.List<ChartAreaLayerSettings> powerLayers = power.getLayers();
//            addChart(chartData, getCollapseRateTs(), powerLayers, "collapseRate", Colors.YELLOW, TickPainter.LINE_JOIN);
//            addChart(chartData, getHeadPlusTs(), powerLayers, "headPlus", Colors.ROSE, TickPainter.LINE_JOIN);
//            addChart(chartData, getEnterPowerTs(), powerLayers, "EnterPower", Colors.LEMONADE, TickPainter.LINE_JOIN);
        }

        ChartAreaSettings value = chartSetting.addChartAreaSettings("value", 0, 0.7f, 1, 0.15f, Color.LIGHT_GRAY);
        {
            value.addHorizontalLineValue(1);
            value.addHorizontalLineValue(0);
            value.addHorizontalLineValue(-1);
            java.util.List<ChartAreaLayerSettings> valueLayers = value.getLayers();
//            addChart(chartData, getDirectionTs(), valueLayers, "direction", Color.RED, TickPainter.LINE_JOIN);
            addChart(chartData, getLvlTs(), valueLayers, "lvl", Colors.BLUE_PEARL, TickPainter.LINE_JOIN);
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
