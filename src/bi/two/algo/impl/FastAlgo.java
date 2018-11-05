package bi.two.algo.impl;

import bi.two.ChartCanvas;
import bi.two.Colors;
import bi.two.algo.Watcher;
import bi.two.calc.Average;
import bi.two.calc.MidPointsVelocity;
import bi.two.calc.PolynomialSplineVelocity;
import bi.two.chart.*;
import bi.two.exch.Exchange;
import bi.two.ts.BaseTicksTimesSeriesData;
import bi.two.ts.BaseTimesSeriesData;
import bi.two.ts.ITimesSeriesData;
import bi.two.ts.TicksTimesSeriesData;
import bi.two.util.MapConfig;
import bi.two.util.Utils;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class FastAlgo extends BaseRibbonAlgo {
    private static final boolean ADJUST_TAIL = true;
    private static final boolean LIMIT_BY_PRICE = true;

    private final VelocityArray m_velocity;
    private final MidPointsVelocity m_leadEmaVelocity;
    private final Collapser m_collapser;

    private boolean m_goUp;
    private Float m_min;
    private Float m_max;
    private Float m_zigZag;
    private Float m_headStart;
    private Float m_tailStart;
    private Float m_midStart;
    private Float m_reverseLevel;
    private float m_maxRibbonSpread;
    private Float m_ribbonSpreadTop;
    private Float m_ribbonSpreadBottom;
    private Float m_spreadClosePower;
    private Float m_exitPower;
    private Float m_noStartCollapseRate;
    private Float m_midPower;
    private Float m_tailPower;
    private Float m_enterPower;
    private Float m_headPower;
    private Float m_direction = 0f;
    private Float m_directionIn;
    private Float m_remainedEnterDistance;
    private Float m_mid;
    private Float m_tailPower2;
    private Float m_spreadClosePowerAdjusted;

    private Float m_1quarter;
    private Float m_1quarterPaint;
    private Float m_3quarter;
    private Float m_3quarterPaint;
    private Float m_5quarter;
    private Float m_5quarterPaint;
    private Float m_6quarter;
    private Float m_6quarterPaint;
    private Float m_7quarter;
    private Float m_7quarterPaint;
    private Float m_8quarter;
    private Float m_8quarterPaint;

    private float m_maxHeadRun;
    private float m_minHeadRun;
    private Float m_revPower;
    private float m_velocityStartHalf;

    public FastAlgo(MapConfig algoConfig, ITimesSeriesData tsd, Exchange exchange) {
        super(algoConfig, tsd, exchange);

//        if (m_collectValues) {
//            m_priceBars = new BarsTimesSeriesData(tsd, m_barSize);
//        }

        BaseTimesSeriesData leadEma = m_emas[0];
        int multiplier = 1000;
        m_velocity = new VelocityArray(leadEma, m_barSize, 1.0f, 0.1f, 2, multiplier);
        m_leadEmaVelocity = new MidPointsVelocity(leadEma, (long) (m_barSize * 1.0), multiplier);

        m_collapser = new Collapser(0.3f);
    }

    @Override public String key(boolean detailed) {
        detailed = true;
        return  ""
                + (detailed ? ",start=" : ",") + m_start
                + (detailed ? ",step=" : ",") + m_step
                + (detailed ? ",count=" : ",") + m_count
                + (detailed ? ",linRegMult=" : ",") + m_linRegMultiplier
//                + (detailed ? "|minOrdMul=" : "|") + m_minOrderMul
                + (detailed ? "|joinTicks=" : "|") + m_joinTicks
                + (detailed ? "|commiss=" : "|") + Utils.format8(m_commission)
                + ", " + m_barSize
//                + ", " + Utils.millisToYDHMSStr(m_barSize)
                ;
    }

    @Override protected Float recalc(float lastPrice) {
        float emasMin = Float.POSITIVE_INFINITY;
        float emasMax = Float.NEGATIVE_INFINITY;
        boolean allDone = true;
        float leadEmaValue = 0;
        for (int i = 0; i < m_emasNum; i++) {
            ITimesSeriesData ema = m_emas[i];
            ITickData lastTick = ema.getLatestTick();
            if (lastTick != null) {
                float value = lastTick.getClosePrice();
                emasMin = (emasMin <= value) ? emasMin : value;  // Math.min(emasMin, value);
                emasMax = (emasMax >= value) ? emasMax : value;  // Math.max(emasMax, value);
                if (i == 0) {
                    leadEmaValue = value;
                }
            } else {
                allDone = false;
                break; // not ready yet
            }
        }

        if (allDone) {
            boolean goUp = (leadEmaValue == emasMax)
                    ? true // go up
                    : ((leadEmaValue == emasMin)
                    ? false // go down
                    : m_goUp); // do not change
            boolean directionChanged = (goUp != m_goUp);
            m_goUp = goUp;

            m_min = emasMin;
            m_max = emasMax;

            // note - ribbonSpread from prev step here
            m_zigZag = directionChanged ? (goUp ? m_ribbonSpreadBottom : m_ribbonSpreadTop) : m_zigZag;

            float ribbonSpread = emasMax - emasMin;
            float maxRibbonSpread = directionChanged
                    ? ribbonSpread //reset
                    : (ribbonSpread >= m_maxRibbonSpread) ? ribbonSpread : m_maxRibbonSpread;  //  Math.max(ribbonSpread, m_maxRibbonSpread);
            m_maxRibbonSpread = maxRibbonSpread;
            m_ribbonSpreadTop = goUp ? emasMin + maxRibbonSpread : emasMax;
            m_ribbonSpreadBottom = goUp ? emasMin : emasMax - maxRibbonSpread;

            float head = goUp ? emasMax : emasMin;
            float tail = goUp ? emasMin : emasMax;

            float mid = (head + tail) / 2;
            m_mid = mid;

            // common ribbon lines
            if (directionChanged) {
                m_headStart = head; // pink
                m_tailStart = tail;  // dark green
                m_midStart = mid;

                float spread = head - tail;
                float half = spread / 2;
                float quarter = spread / 4;
                m_1quarter = tail + quarter;
                m_1quarterPaint = m_1quarter;
                m_3quarter = mid + quarter;
                m_3quarterPaint = m_3quarter;
                m_5quarter = head + quarter;
                m_5quarterPaint = head;
                m_6quarter = head + half;
                m_6quarterPaint = head;
                m_7quarter = head + half + quarter;
                m_7quarterPaint = head;
                m_8quarter = head + spread;
                m_8quarterPaint = head;

                m_velocityStartHalf = getVelocity() / 2;
                m_collapser.init(head, tail);
            } else {
                if (m_6quarter != null) {
                    if (ADJUST_TAIL) {
                        if ((goUp && (tail < m_tailStart)) || (!goUp && (tail > m_tailStart))) {
                            m_tailStart = tail;
                            float spread = m_headStart - tail;
                            float half = spread / 2;
                            float quarter = spread / 4;
                            m_midStart = tail + half;
                            m_1quarter = tail + quarter;
                            m_1quarterPaint = m_1quarter;
                            m_3quarter = m_headStart - quarter;
                            m_3quarterPaint = m_3quarter;
                            m_5quarter = m_headStart + quarter;
                            m_5quarterPaint = m_headStart;
                            m_6quarter = m_headStart + half;
                            m_6quarterPaint = m_headStart;
                            m_7quarter = m_headStart + half + quarter;
                            m_7quarterPaint = m_headStart;
                            m_8quarter = m_headStart + spread;
                            m_8quarterPaint = m_headStart;
                        }
                    }
                    if (m_collectValues) { // this only for painting
                        if (goUp) {
                            if (tail > m_1quarter) {
                                m_1quarterPaint = m_midStart;
                            }
                            if (tail > m_3quarter) {
                                m_3quarterPaint = m_headStart;
                            }
                            if (tail > m_5quarter) {
                                m_5quarterPaint = m_5quarter;
                                m_6quarterPaint = m_5quarter;
                                m_7quarterPaint = m_5quarter;
                                m_8quarterPaint = m_5quarter;
                            }
                            if (tail > m_6quarter) {
                                m_6quarterPaint = m_6quarter;
                                m_7quarterPaint = m_6quarter;
                                m_8quarterPaint = m_6quarter;
                            }
                            if (tail > m_7quarter) {
                                m_7quarterPaint = m_7quarter;
                                m_8quarterPaint = m_8quarter;
                            }
                            if (tail > m_8quarter) {
                                m_8quarterPaint = m_8quarter;
                            }
                        } else {
                            if (tail < m_1quarter) {
                                m_1quarterPaint = m_midStart;
                            }
                            if (tail < m_3quarter) {
                                m_3quarterPaint = m_headStart;
                            }
                            if (tail < m_5quarter) {
                                m_5quarterPaint = m_5quarter;
                                m_6quarterPaint = m_5quarter;
                                m_7quarterPaint = m_5quarter;
                                m_8quarterPaint = m_5quarter;
                            }
                            if (tail < m_6quarter) {
                                m_6quarterPaint = m_6quarter;
                                m_7quarterPaint = m_6quarter;
                                m_8quarterPaint = m_6quarter;
                            }
                            if (tail < m_7quarter) {
                                m_7quarterPaint = m_7quarter;
                                m_8quarterPaint = m_7quarter;
                            }
                            if (tail < m_8quarter) {
                                m_8quarterPaint = m_8quarter;
                            }
                        }
                    }
                }
            }

            if (directionChanged) {
                m_directionIn = (m_direction == null) ?  0 : m_direction;
                m_remainedEnterDistance = goUp ? 1 - m_directionIn : 1 + m_directionIn;
                m_maxHeadRun = 0; // reset
                m_minHeadRun = 0; // reset
                m_revPower = 0f;
            } else {
            }

            if (m_headStart != null) { // directionChanged once observed
                float headRun = leadEmaValue - m_headStart;
                if (!goUp) {
                    headRun = -headRun;
                }
                float maxHeadRun = m_maxHeadRun;
                if (maxHeadRun < headRun) {
                    m_maxHeadRun = headRun;
                    maxHeadRun = headRun;
                }
                float minHeadRun = m_minHeadRun; // negative
                if (headRun < minHeadRun) {
                    m_minHeadRun = headRun;
                    minHeadRun = headRun;
                }
                float sumHeadRun = maxHeadRun - minHeadRun;
                float headPower = (sumHeadRun == 0) ? 1 : (headRun - minHeadRun) / sumHeadRun;
                if (headPower < 0) {
                    headPower = 0;
                }
                m_headPower = headPower;

//                float rev = leadEmaValue - mid;
//                float revPower = rev / (tail - mid);
//                if (revPower < 0) {
//                    revPower = 0;
//                }
////                if (m_revPower < revPower) {
//                    m_revPower = revPower;
////                }

                float enterMidPower = (mid - m_midStart) / (m_headStart - m_midStart);
                float enterTailPower = (tail - m_tailStart) / (m_midStart - m_tailStart) * 2;
                float enterPower = Math.max(enterMidPower, enterTailPower);  //  (enterMidPower + enterTailPower) / 2;
                if (enterPower > 1) {
                    enterPower = 1;
                } else if (enterPower < 0) {
                    enterPower = 0;
                }
                m_enterPower = enterPower;

                float tailPower = (tail - m_tailStart) / (m_headStart - m_tailStart);
                if (tailPower > 1) {
                    tailPower = 1;
                } else if (tailPower < 0) {
                    tailPower = 0;
                }
                m_tailPower = tailPower;
                float reverseLevel = head - (head - mid) * tailPower;
                m_reverseLevel = reverseLevel;
                float revPower = (leadEmaValue - reverseLevel) / (tail - reverseLevel);
                if (revPower < 0) {
                    revPower = 0;
                }
                m_revPower = revPower;

                float exitMidPower = (leadEmaValue - mid) / (tail - mid);
                if (exitMidPower < 0) {
                    exitMidPower = 0;
                }

                float spreadClosePower = (maxRibbonSpread - ribbonSpread) / maxRibbonSpread;
                m_spreadClosePower = spreadClosePower;
//
//                float tailPower2 = (tail - m_tailStart) / (m_headStart - m_tailStart);
//                tailPower2 = (tailPower2 > 1) ? 1 : tailPower2;
//                m_tailPower2 = tailPower2;
//
//                float spreadClosePowerAdjusted = spreadClosePower * tailPower2;
//                m_spreadClosePowerAdjusted = spreadClosePowerAdjusted;
//
//                float direction = m_directionIn
//                        + m_remainedEnterDistance * (goUp ? midTailPower : -midTailPower)
//                        + m_reverseMul * (goUp ? -spreadClosePowerAdjusted : spreadClosePowerAdjusted);
//                direction = (direction > 1) ? 1 : direction;
//                direction = (direction < -1) ? -1 : direction;
//                m_direction = direction;

//                float direction = m_headPower * (goUp ? 1 : -1);
////                float reverseDistance = direction + (goUp ? 1 : -1);
////                float reverseValue = reverseDistance * m_revPower;
////                direction -= reverseValue;

                // exitPower = spreadClosePower "+" revPower
                float exitPower = spreadClosePower;
                float remainedExitPower = 1 - exitPower;
                if (remainedExitPower > 0.5f) {
                    remainedExitPower = 0.5f; // max allow 0.5
                }
                exitPower += remainedExitPower * revPower;
                m_exitPower = exitPower;


                // false-start collapse
                float noStartCollapsePower = (tail - m_tailStart) / (m_midStart - m_tailStart);
                if (noStartCollapsePower > 1) {
                    noStartCollapsePower = 1;
                } else if (noStartCollapsePower < 0) {
                    noStartCollapsePower = 0;
                }
                float velocity = getVelocity();
                float noStartCollapseRate = (velocity - m_velocityStartHalf) / (-2 * m_velocityStartHalf);
                if (noStartCollapseRate < 0) {
                    noStartCollapseRate = 0;
                } else if (noStartCollapseRate > 1) {
                    noStartCollapseRate = 1;
                }
                noStartCollapseRate *= (1 - noStartCollapsePower);
                m_noStartCollapseRate = noStartCollapseRate;

                float direction = m_directionIn + (goUp ? 1 : -1) * m_remainedEnterDistance * enterPower;
                direction *= (1 - noStartCollapseRate);
                float remainedExitDistance = goUp ? 1 + direction : 1 - direction;
//                direction += (goUp ? -1 : 1) * remainedExitDistance * exitPower;
//                direction += (goUp ? -1 : 1) * remainedExitDistance * revPower;
//                direction += (goUp ? -1 : 1) * remainedExitDistance * spreadClosePower;
                direction += (goUp ? -1 : 1) * remainedExitDistance * exitMidPower / 2;

                if (LIMIT_BY_PRICE) {
                    if (direction > m_direction) {
                        if ((lastPrice > leadEmaValue) && (lastPrice > head)) {
                            m_direction = direction;
                        }
                    } else { // adj < m_adj
                        if ((lastPrice < leadEmaValue) && (lastPrice < head)) {
                            m_direction = direction;
                        }
                    }
                } else {
                    m_direction = direction;
                }
            }
        }

        return m_direction;
    }

    private float getVelocity() {
        return m_velocity.m_velocityAvg.getLatestTick().getClosePrice();
    }

    @Override public void reset() {
        m_min = null;
        m_max = null;
        m_zigZag = null;
        m_headStart = null;
        m_midStart = null;
        m_tailStart = null;
        m_reverseLevel = null;
        m_maxRibbonSpread = 0f;
        m_ribbonSpreadTop = null;
        m_ribbonSpreadBottom = null;
        m_spreadClosePower = null;
        m_exitPower = null;
        m_noStartCollapseRate = null;
        m_midPower = null;
        m_tailPower = null;
        m_enterPower = null;
        m_headPower = null;
        m_direction = 0f;
        m_directionIn = null;
        m_remainedEnterDistance = null;
        m_mid = null;
        m_tailPower2 = null;
        m_spreadClosePowerAdjusted = null;
        m_1quarter = null;
        m_3quarter = null;
        m_5quarter = null;
        m_6quarter = null;
        m_7quarter = null;
        m_8quarter = null;
        m_maxHeadRun = 0;
        m_minHeadRun = 0;
        m_revPower = null;
    }

    TicksTimesSeriesData<TickData> getMinTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_min; } }; }
    TicksTimesSeriesData<TickData> getMaxTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_max; } }; }
    TicksTimesSeriesData<TickData> getZigZagTs() { return new JoinNonChangedInnerTimesSeriesData(getParent(), false) { @Override protected Float getValue() { return m_zigZag; } }; }

    TicksTimesSeriesData<TickData> getHeadStartTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_headStart; } }; }
    TicksTimesSeriesData<TickData> getTailStartTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_tailStart; } }; }
    TicksTimesSeriesData<TickData> getMidStartTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_midStart; } }; }
    TicksTimesSeriesData<TickData> getMidTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_mid; } }; }
    TicksTimesSeriesData<TickData> getRibbonSpreadMaxTopTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_ribbonSpreadTop; } }; }

    TicksTimesSeriesData<TickData> getRibbonSpreadMaxBottomTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_ribbonSpreadBottom; } }; }

    TicksTimesSeriesData<TickData> get1quarterTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_1quarterPaint; } }; }
    TicksTimesSeriesData<TickData> get3quarterTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_3quarterPaint; } }; }
    TicksTimesSeriesData<TickData> get5quarterTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_5quarterPaint; } }; }
    TicksTimesSeriesData<TickData> get6quarterTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_6quarterPaint; } }; }
    TicksTimesSeriesData<TickData> get7quarterTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_7quarterPaint; } }; }
    TicksTimesSeriesData<TickData> get8quarterTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_8quarterPaint; } }; }

    TicksTimesSeriesData<TickData> getReverseLevelTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_reverseLevel; } }; }
    TicksTimesSeriesData<TickData> getSpreadClosePowerTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_spreadClosePower; } }; }
    TicksTimesSeriesData<TickData> getSpreadClosePowerAdjustedTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_spreadClosePowerAdjusted; } }; }
    TicksTimesSeriesData<TickData> getMidPowerTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_midPower; } }; }
    TicksTimesSeriesData<TickData> getTailPowerTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_tailPower; } }; }
    TicksTimesSeriesData<TickData> getEnterPowerTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_enterPower; } }; }
    TicksTimesSeriesData<TickData> getTailPower2Ts() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_tailPower2; } }; }
    TicksTimesSeriesData<TickData> getExitPowerTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_exitPower; } }; }
    TicksTimesSeriesData<TickData> getCollapseRateTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_noStartCollapseRate; } }; }

    TicksTimesSeriesData<TickData> getHeadPowerTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_headPower; } }; }
    TicksTimesSeriesData<TickData> getRevPowerTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_revPower; } }; }

    TicksTimesSeriesData<TickData> getDirectionTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_direction; } }; }

    @Override public void setupChart(boolean collectValues, ChartCanvas chartCanvas, BaseTicksTimesSeriesData<TickData> ticksTs, Watcher firstWatcher) {
        ChartData chartData = chartCanvas.getChartData();
        ChartSetting chartSetting = chartCanvas.getChartSetting();

        int priceAlpha = 120;
        int emaAlpha = 50;

        // layout
        ChartAreaSettings top = chartSetting.addChartAreaSettings("top", 0, 0, 1, 0.6f, Color.RED);
        List<ChartAreaLayerSettings> topLayers = top.getLayers();
        {
            addChart(chartData, ticksTs, topLayers, "price", Colors.alpha(Colors.DARK_RED, priceAlpha), TickPainter.TICK_JOIN);
//            addChart(chartData, m_priceBars, topLayers, "priceBars", Colors.alpha(Colors.DARK_RED, 80), TickPainter.BAR);

//            chartData.setTicksData("spline", new NoTicksData());
////            topLayers.add(new ChartAreaLayerSettings("spline", Color.RED, new ChartAreaPainter.SplineChartAreaPainter(ticksTs, 4)));
//            topLayers.add(new ChartAreaLayerSettings("spline", Color.RED, new ChartAreaPainter.PolynomChartAreaPainter(ticksTs)));

//            Color emaColor = Colors.alpha(Color.BLUE, emaAlpha);
//            int size = m_emas.length;
//            for (int i = size - 1; i > 0; i--) { // paint without leadEma
//                BaseTimesSeriesData ema = m_emas[i];
//                addChart(chartData, ema.getJoinNonChangedTs(), topLayers, "ema" + i, emaColor, TickPainter.LINE);
//            }

//            addChart(chartData, m_sliding.getJoinNonChangedTs(), topLayers, "sliding", Colors.BALERINA, TickPainter.LINE);

            addChart(chartData, getMinTs(), topLayers, "min", Color.RED, TickPainter.LINE_JOIN);
            addChart(chartData, getMaxTs(), topLayers, "max", Color.RED, TickPainter.LINE_JOIN);

            addChart(chartData, getZigZagTs(), topLayers, "zigzag", Color.MAGENTA, TickPainter.LINE_JOIN);

            addChart(chartData, getHeadStartTs(), topLayers, "headStart", Color.PINK, TickPainter.LINE_JOIN);
            addChart(chartData, getTailStartTs(), topLayers, "tailStart", Colors.DARK_GREEN, TickPainter.LINE_JOIN);
            addChart(chartData, getMidStartTs(), topLayers, "midStart", Colors.PURPLE, TickPainter.LINE_JOIN);
            addChart(chartData, getMidTs(), topLayers, "mid", Colors.CHOCOLATE, TickPainter.LINE_JOIN);

            Color halfGray = Colors.alpha(Color.GRAY, 128);
            addChart(chartData, get1quarterTs(), topLayers, "1quarter", halfGray, TickPainter.LINE_JOIN);
            addChart(chartData, get3quarterTs(), topLayers, "3quarter", halfGray, TickPainter.LINE_JOIN);
            addChart(chartData, get5quarterTs(), topLayers, "5quarter", halfGray, TickPainter.LINE_JOIN);
            addChart(chartData, get6quarterTs(), topLayers, "6quarter", Colors.LEMONADE, TickPainter.LINE_JOIN);
            addChart(chartData, get7quarterTs(), topLayers, "7quarter", halfGray, TickPainter.LINE_JOIN);
            addChart(chartData, get8quarterTs(), topLayers, "8quarter", Colors.LEMONADE, TickPainter.LINE_JOIN);

//            addChart(chartData, getTargetTs(), topLayers, "target", Colors.HAZELNUT, TickPainter.LINE_JOIN);
//
            addChart(chartData, getReverseLevelTs(), topLayers, "reverseLevel", Colors.GOLD, TickPainter.LINE_JOIN);

            addChart(chartData, getRibbonSpreadMaxTopTs(), topLayers, "maxTop", Colors.SWEET_POTATO, TickPainter.LINE_JOIN);
            addChart(chartData, getRibbonSpreadMaxBottomTs(), topLayers, "maxBottom", Color.CYAN, TickPainter.LINE_JOIN);

//            addChart(chartData, getReverseLevelTs(), topLayers, "reverse", Colors.SPRING_LILAC, TickPainter.LINE_JOIN);

            BaseTimesSeriesData leadEma = m_emas[0]; // fastest ema
            addChart(chartData, leadEma.getJoinNonChangedTs(), topLayers, "leadEma", Colors.GRANNY_SMITH, TickPainter.LINE_JOIN);
        }

        ChartAreaSettings power = chartSetting.addChartAreaSettings("power", 0, 0.6f, 1, 0.1f, Color.LIGHT_GRAY);
        List<ChartAreaLayerSettings> powerLayers = power.getLayers();
        {
            addChart(chartData, getHeadPowerTs(), powerLayers, "headPower", Color.MAGENTA, TickPainter.LINE_JOIN);
            addChart(chartData, getRevPowerTs(), powerLayers, "revPower", Colors.JUST_ORANGE, TickPainter.LINE_JOIN);
            addChart(chartData, getSpreadClosePowerTs(), powerLayers, "spreadClosePower", Colors.SUEDE_BROWN, TickPainter.LINE_JOIN);
//            addChart(chartData, getSpreadClosePowerAdjustedTs(), powerLayers, "spreadClosePowerAdjusted", Colors.CANDY_PINK, TickPainter.LINE_JOIN);
////            addChart(chartData, getMidPowerTs(), powerLayers, "midPower", Color.LIGHT_GRAY, TickPainter.LINE_JOIN);
            addChart(chartData, getTailPowerTs(), powerLayers, "tailPower", Colors.BLUE_PEARL, TickPainter.LINE_JOIN);
            addChart(chartData, getEnterPowerTs(), powerLayers, "enterPower", Colors.CHOCOLATE, TickPainter.LINE_JOIN);
            addChart(chartData, getExitPowerTs(), powerLayers, "exitPower", Colors.EMERALD, TickPainter.LINE_JOIN);
            addChart(chartData, getCollapseRateTs(), powerLayers, "collapseRate", Colors.LIGHT_BLUE_PEARL, TickPainter.LINE_JOIN);
//            addChart(chartData, getTailPower2Ts(), powerLayers, "tailPower2", Colors.TURQUOISE, TickPainter.LINE_JOIN);

//            addChart(chartData, firstWatcher.getFadeOutTs(), powerLayers, "fadeOut", Colors.CANDY_PINK, TickPainter.LINE_JOIN);
//            addChart(chartData, firstWatcher.getFadeInTs(), powerLayers, "fadeIn", Colors.STRING_BEAN, TickPainter.LINE_JOIN);
        }

        ChartAreaSettings value = chartSetting.addChartAreaSettings("value", 0, 0.7f, 1, 0.15f, Color.LIGHT_GRAY);
        List<ChartAreaLayerSettings> valueLayers = value.getLayers();
        {
////            addChart(chartData, getTS(true), valueLayers, "value", Color.blue, TickPainter.LINE);
////            addChart(chartData, getJoinNonChangedTs(), valueLayers, "value", Color.blue, TickPainter.LINE);
//            addChart(chartData, getValueTs(), valueLayers, "value", Colors.alpha(Color.MAGENTA, 128), TickPainter.LINE_JOIN);
//            addChart(chartData, getMulTs(), valueLayers, "mul", Color.GRAY, TickPainter.LINE_JOIN);
            addChart(chartData, getDirectionTs(), valueLayers, "direction", Color.RED, TickPainter.LINE_JOIN);

            Color velocityColor = Colors.alpha(Colors.CLOW_IN_THE_DARK, 60);
            List<BaseTimesSeriesData> m_velocities = m_velocity.m_velocities;
            for (int i = 0; i < m_velocities.size(); i++) {
                BaseTimesSeriesData velocity = m_velocities.get(i);
                addChart(chartData, velocity.getJoinNonChangedTs(), valueLayers, "leadEmaVelocity" + i, velocityColor, TickPainter.LINE_JOIN);
            }
            addChart(chartData, m_velocity.m_velocityAvg.getJoinNonChangedTs(), valueLayers, "leadEmaVelocityAvg", Colors.YELLOW, TickPainter.LINE_JOIN);
            addChart(chartData, m_leadEmaVelocity.getJoinNonChangedTs(), valueLayers, "leadEmaVelocity", Colors.TRANQUILITY, TickPainter.LINE_JOIN);
//            addChart(chartData, getRevMulAndPrevTs(), valueLayers, "revMulAndPrev", Colors.GOLD, TickPainter.LINE_JOIN);
////            addChart(chartData, m_velocityAdj.getJoinNonChangedTs(), valueLayers, "velAdj", Color.RED, TickPainter.LINE);
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

    // --------------------------------------------------------------------------------------
    private static class VelocityArray {
        private final List<BaseTimesSeriesData> m_velocities = new ArrayList<>();
        private final Average m_velocityAvg;

        VelocityArray(BaseTimesSeriesData tsd, long barSize, float velocityStart, float velocityStep, int steps, int multiplier) {
            for (int i = -steps; i <= steps; i++) {
                PolynomialSplineVelocity velocity = new PolynomialSplineVelocity(tsd, (long) (barSize * velocityStart + i * velocityStep), multiplier);
                m_velocities.add(velocity);
            }
            m_velocityAvg = new Average(m_velocities, tsd);
        }
    }

    // --------------------------------------------------------------------------------------
    private static class Collapser {
        private final float m_step;
        private float m_rate;

        Collapser(float step) {
            m_step = step;
            m_rate = 0;
        }

        void init(float head, float tail) {
            m_rate = 0;
        }
    }
}
