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
    private static final boolean ADJUST_TAIL = true;
    private static final boolean LIMIT_BY_PRICE = false;

    static {
        console("ADJUST_TAIL=" + ADJUST_TAIL); // todo
    }

    private final float m_enter;
    private final long m_mature;

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
    private Float m_smallerCollapse;
    private Float m_leadEmaRate;
    private Float m_collapseBest;
    private Float m_collapseBestRate;
    private Float m_collapseBestDirection;
    private Float m_sumDirection;

    private SimpleRegression m_enterRegression = new SimpleRegression(true);
    private long m_enterStartTime;
    private Float m_enterValue;
    private SimpleRegression m_exitRegression = new SimpleRegression(true);
    private long m_exitStartTime;
    private Float m_exitValue;
    private Float m_exitValueMatured;
    private Float m_exitMature;

    private Float m_absFalseStartRate;
    private Float m_tailToHeadStartPower;
    private boolean m_exit;
    private Float m_exitMed;
    private Float m_exitMedLead;
    private Float m_exitMedLeadRate;
    private Float m_adjNoLimit;
    private Float m_enterConfidence;
    private Float m_exitConfidence;

    public RayAlgo(MapConfig algoConfig, ITimesSeriesData inTsd, Exchange exchange) {
        super(algoConfig, inTsd, exchange, ADJUST_TAIL);

        m_enter = algoConfig.getNumber(Vary.enter).floatValue();
        m_mature = algoConfig.getNumber(Vary.mature).longValue();  // TimeUnit.MINUTES.toMillis(3);
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

    @Override protected void recalc4(float lastPrice, float leadEmaValue,
                                     float mid, float head, float tail) {
        long timestamp = m_timestamp;

        if (m_directionChanged) {
            SimpleRegression tmp = m_enterRegression;
            m_enterRegression = m_exitRegression;
            m_enterStartTime = m_exitStartTime;

            m_exitRegression = tmp; // restart exit regression calc
            m_exitStartTime = timestamp;
            tmp.clear();
        }

        Boolean goUp = m_goUp;
        float ribbonSpreadTop = m_ribbonSpreadTop;
        float ribbonSpreadBottom = m_ribbonSpreadBottom;
        float ribbonSpreadHead = goUp ? ribbonSpreadTop : ribbonSpreadBottom;
        float ribbonSpreadTail = goUp ? ribbonSpreadBottom : ribbonSpreadTop;

        boolean exit = goUp
                ? (lastPrice < ribbonSpreadTop) // up
                : (lastPrice > ribbonSpreadBottom); // down
        if (exit) { // exit started/continue
            if (!m_exit) { // exit just started
                m_exitStartTime = timestamp;
                m_exitRegression.clear();
            }
        }
        m_exit = exit;

        long exitTimeOffset = timestamp - m_exitStartTime;
        float exitMature = ((float) exitTimeOffset) / m_mature;
        if (exitMature > 1) {
            exitMature = 1;
        }
        m_exitMature = exitMature;
        m_exitRegression.addData(exitTimeOffset, lastPrice);
        float exitPredict = (float) m_exitRegression.predict(exitTimeOffset);
        boolean isNan = Double.isNaN(exitPredict);
        Float exitValue = isNan
                ? null
                : exitPredict;
        m_exitValue = exitValue;
        Float exitValueMatured = isNan
                ? null
                : (exitMature == 1)
                    ? exitPredict
                    : exitPredict * exitMature + ribbonSpreadHead * (1 - exitMature);
        m_exitValueMatured = exitValueMatured;

        long enterTimeOffset = timestamp - m_enterStartTime;
        m_enterRegression.addData(enterTimeOffset, lastPrice);
        double enterPredict = m_enterRegression.predict(enterTimeOffset);
        Float enterValue = Double.isNaN(enterPredict)
                ? null
                : (float) enterPredict;
        m_enterValue = enterValue;

        if (m_collectValues) { // for now used in UI only
//        m_enterConfidence = (float)m_enterRegression.getSlopeConfidenceInterval();
            double exitConfidence = m_exitRegression.getSlopeConfidenceInterval();
            m_exitConfidence = Double.isNaN(exitConfidence) ? null : (float) exitConfidence;
        }

        Float headStart = m_ribbon.m_headStart;
        Float tailStart = m_ribbon.m_tailStart;
        float ribbonSpreadHeadRun = ribbonSpreadHead - headStart;
        float absFalseStartRate = (ribbonSpreadHeadRun == 0) ? 1 : (leadEmaValue - headStart) / ribbonSpreadHeadRun;
        if (absFalseStartRate > 1) {
            absFalseStartRate = 1;
        } else if (absFalseStartRate < 0) {
            absFalseStartRate = 0;
        }

        float tailToHeadStartPower = (tail - headStart) / (tailStart - headStart);
        if (tailToHeadStartPower < 0) {
            tailToHeadStartPower = 0;
        }
        m_tailToHeadStartPower = tailToHeadStartPower;

        m_absFalseStartRate = absFalseStartRate * tailToHeadStartPower;

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

        float smallerCollapse = goUp
                ? m_headCollapseDouble > leadEmaValue ? m_headCollapseDouble : leadEmaValue
                : m_headCollapseDouble < leadEmaValue ? m_headCollapseDouble : leadEmaValue;
        m_smallerCollapse = smallerCollapse;

        float exitMed = (exitValueMatured == null)
                ? smallerCollapse
                : (smallerCollapse + exitValueMatured) / 2;
        m_exitMed = exitMed;

        float leadEmaRate = (leadEmaValue - ribbonSpreadTail) / (head - ribbonSpreadTail);
        m_leadEmaRate = leadEmaRate;

        float exitMedLead = exitMed * leadEmaRate + leadEmaValue * (1 - leadEmaRate); // ledEma adjusted
        m_exitMedLead = exitMedLead;

        float exitMedLeadRate = (enterValue == null)
                ? 0
                : (goUp && (exitMedLead > enterValue)) || (!goUp && (exitMedLead < enterValue))
                    ? 1 // irregular
                    : (exitMedLead - tail) / (enterValue - tail);
        if (exitMedLeadRate > 1) {
            exitMedLeadRate = 1;
        } else if (exitMedLeadRate < 0) {
            exitMedLeadRate = 0;
        }
        exitMedLeadRate = exitMedLeadRate * 2 - 1;
        if (!goUp) {
            exitMedLeadRate = -exitMedLeadRate;
        }
        m_exitMedLeadRate = exitMedLeadRate;

        Float collapseBest;
        Float collapseBestRate;
        Float collapseBestDirection;
        Float sumDirection;
        if (exitValue != null) {
            float collapseMax = goUp
                ? (exitValue < smallerCollapse) ? exitValue : smallerCollapse
                : (exitValue > smallerCollapse) ? exitValue : smallerCollapse;
            float collapseMin = goUp
                ? (exitValue > smallerCollapse) ? exitValue : smallerCollapse
                : (exitValue < smallerCollapse) ? exitValue : smallerCollapse;

            collapseBest = ((1 - leadEmaRate) * collapseMax) + (leadEmaRate * collapseMin);

            collapseBestRate = (collapseBest - tail) / (ribbonSpreadHead - tail);
            if (collapseBestRate > 1) {
                collapseBestRate = 1f;
            } else if (collapseBestRate < 0) {
                collapseBestRate = 0f;
            }
            collapseBestDirection = collapseBestRate * 2 - 1;
            if (!goUp) {
                collapseBestDirection = -collapseBestDirection;
            }

            sumDirection = -1 + collapseBestRate * (1 + absFalseStartRate);
            if (!goUp) {
                sumDirection = -sumDirection;
            }
        } else {
            collapseBest = null;
            collapseBestRate = null;
            collapseBestDirection = null;
            sumDirection = null;
        }
        m_collapseBest = collapseBest;
        m_collapseBestRate = collapseBestRate;
        m_collapseBestDirection = collapseBestDirection;
        m_sumDirection = sumDirection;

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

            m_adjNoLimit = exitMedLeadRate;

            if (LIMIT_BY_PRICE) {

// limit only in one direction
//                if (goUp) {
//                    if (exitMedLeadRate > m_adj) {
//                        if (lastPrice > exitMedLead) {
//                            m_adj = exitMedLeadRate;
//                        }
//                    } else {
//                        m_adj = exitMedLeadRate;
//                    }
//                } else {
//                    if (exitMedLeadRate < m_adj) {
//                        if (lastPrice < exitMedLead) {
//                            m_adj = exitMedLeadRate;
//                        }
//                    } else {
//                        m_adj = exitMedLeadRate;
//                    }
//                }

                if (exitMedLeadRate > m_adj) {
                    if (lastPrice > exitMedLead) {
                        m_adj = exitMedLeadRate;
                    }
                } else { // adj < m_adj
                    if (lastPrice < exitMedLead) {
                        m_adj = exitMedLeadRate;
                    }
                }
            } else {
                m_adj = exitMedLeadRate;
            }
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
    TicksTimesSeriesData<TickData> getExitValueMaruredTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_exitValueMatured; } }; }
    TicksTimesSeriesData<TickData> getEnterValueTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_enterValue; } }; }
    TicksTimesSeriesData<TickData> getAbsFalseStartRateTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_absFalseStartRate; } }; }
    TicksTimesSeriesData<TickData> getSmallerCollapseTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_smallerCollapse; } }; }
    TicksTimesSeriesData<TickData> getLeadEmaRateTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_leadEmaRate; } }; }
    TicksTimesSeriesData<TickData> getCollapseBestTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_collapseBest; } }; }
    TicksTimesSeriesData<TickData> getCollapseBestRateTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_collapseBestRate; } }; }
    TicksTimesSeriesData<TickData> getCollapseBestDirectionTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_collapseBestDirection; } }; }
    TicksTimesSeriesData<TickData> getSumDirectionTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_sumDirection; } }; }
    TicksTimesSeriesData<TickData> getTailToHeadStartPowerTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_tailToHeadStartPower; } }; }
    TicksTimesSeriesData<TickData> getExitMatureTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_exitMature; } }; }
    TicksTimesSeriesData<TickData> getExitMedTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_exitMed; } }; }
    TicksTimesSeriesData<TickData> getExitMedLeadTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_exitMedLead; } }; }
    TicksTimesSeriesData<TickData> getExitMedLeadRateTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_exitMedLeadRate; } }; }
    TicksTimesSeriesData<TickData> getEnterConfidenceTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_enterConfidence; } }; }
    TicksTimesSeriesData<TickData> getExitConfidenceTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_exitConfidence; } }; }


    @Override public String key(boolean detailed) {
        detailed = true;
        return  ""
                + (detailed ? ",start=" : ",") + m_start
                + (detailed ? ",step=" : ",") + m_step
                + (detailed ? ",count=" : ",") + m_count
                + (detailed ? ",linRegMult=" : ",") + m_linRegMultiplier
                + (detailed ? "|minOrdMul=" : "|") + m_minOrderMul
                + (detailed ? "|mature=" : "|") + m_mature
                + (detailed ? "|joinTicks=" : "|") + m_joinTicks
                + (detailed ? "|joiner=" : "|") + m_joinerName
                + (detailed ? "|turn=" : "|") + Utils.format8(m_turnLevel)
                + (detailed ? "|enter=" : "|") + m_enter
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
//            addChart(chartData, getMidTs(), topLayers, "mid", Colors.CHOCOLATE, TickPainter.LINE_JOIN);

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

//            addChart(chartData, getSplitTs(), topLayers, "split", Colors.GENTLE_PLUM, TickPainter.LINE_JOIN);
            addChart(chartData, getHeadRayTs(), topLayers, "headRay", Colors.alpha(Colors.ROSE, 128), TickPainter.LINE_JOIN, false);
            addChart(chartData, getTailRayTs(), topLayers, "tailRay", Colors.alpha(Colors.ROSE, 128), TickPainter.LINE_JOIN, false);
            addChart(chartData, getMidRayTs(), topLayers, "midRay", Colors.alpha(Colors.ROSE, 90), TickPainter.LINE_JOIN, false);
//            addChart(chartData, getHeadPlusRayTs(), topLayers, "headPlusRay", Colors.CHOCOLATE, TickPainter.LINE_JOIN, false);
//            addChart(chartData, getHeadPlusRay2Ts(), topLayers, "headPlusRay2", Colors.HAZELNUT, TickPainter.LINE_JOIN, false);
//            addChart(chartData, getHeadPlusRay3Ts(), topLayers, "headPlusRay3", Colors.TAN, TickPainter.LINE_JOIN, false);
//            addChart(chartData, getHeadCollapseDoubleTs(), topLayers, "HeadCollapseDouble", Colors.alpha(Colors.YELLOW, 128), TickPainter.LINE_JOIN);
//            addChart(chartData, getExitValueTs(), topLayers, "ExitValue", Colors.BLUE_PEARL, TickPainter.LINE_JOIN);
            addChart(chartData, getExitValueMaruredTs(), topLayers, "ExitValueMatured", Colors.TURQUOISE, TickPainter.LINE_JOIN);
            addChart(chartData, getEnterValueTs(), topLayers, "EnterValue", Colors.LIGHT_BLUE_PEARL, TickPainter.LINE_JOIN);
            addChart(chartData, getSmallerCollapseTs(), topLayers, "SmallerCollapse", Colors.YELLOW, TickPainter.LINE_JOIN);
//            addChart(chartData, getCollapseBestTs(), topLayers, "CollapseBest", Color.BLUE, TickPainter.LINE_JOIN);
            addChart(chartData, getExitMedTs(), topLayers, "ExitMed", Colors.CAMOUFLAGE, TickPainter.LINE_JOIN);
            addChart(chartData, getExitMedLeadTs(), topLayers, "ExitMedLead", Colors.HAZELNUT, TickPainter.LINE_JOIN);
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
//            addChart(chartData, getAbsFalseStartRateTs(), powerLayers, "AbsFalseStartRate", Colors.LEMONADE, TickPainter.LINE_JOIN);
//            addChart(chartData, getLeadEmaRateTs(), powerLayers, "LeadEmaRate", Colors.ROSE, TickPainter.LINE_JOIN);
//            addChart(chartData, getCollapseBestRateTs(), powerLayers, "CollapseBestRate", Colors.DARK_GREEN, TickPainter.LINE_JOIN);
//            addChart(chartData, getTailToHeadStartPowerTs(), powerLayers, "TailToHeadStartPower", Colors.TURQUOISE, TickPainter.LINE_JOIN);
//            addChart(chartData, getExitMatureTs(), powerLayers, "ExitMature", Colors.LIGHT_GREEN, TickPainter.LINE_JOIN);
//            addChart(chartData, getEnterConfidenceTs(), powerLayers, "EnterConfidence", Colors.LIGHT_GREEN, TickPainter.LINE_JOIN);
            addChart(chartData, getExitConfidenceTs(), powerLayers, "ExitConfidence", Colors.ROSE, TickPainter.LINE_JOIN);
        }

        ChartAreaSettings value = chartSetting.addChartAreaSettings("value", 0, 0.7f, 1, 0.15f, Color.LIGHT_GRAY);
        {
            value.addHorizontalLineValue(1);
            value.addHorizontalLineValue(0);
            value.addHorizontalLineValue(-1);
            java.util.List<ChartAreaLayerSettings> valueLayers = value.getLayers();
//            addChart(chartData, getDirectionTs(), valueLayers, "direction", Color.RED, TickPainter.LINE_JOIN);
//            addChart(chartData, getLvlTs(), valueLayers, "lvl", Colors.BLUE_PEARL, TickPainter.LINE_JOIN);
            addChart(chartData, getCollapseBestDirectionTs(), valueLayers, "CollapseBestDirection", Colors.DARK_GREEN, TickPainter.LINE_JOIN);
            addChart(chartData, getSumDirectionTs(), valueLayers, "sumDirection", Colors.GOLD, TickPainter.LINE_JOIN);
            addChart(chartData, getExitMedLeadRateTs(), valueLayers, "ExitMedLeadRate", Colors.RED_HOT_RED, TickPainter.LINE_JOIN);
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
