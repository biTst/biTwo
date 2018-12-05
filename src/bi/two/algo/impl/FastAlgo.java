package bi.two.algo.impl;

import bi.two.ChartCanvas;
import bi.two.Colors;
import bi.two.algo.Watcher;
import bi.two.calc.Average;
import bi.two.calc.MidPointsVelocity;
import bi.two.calc.PolynomialSplineVelocity;
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
import java.util.ArrayList;
import java.util.List;

public class FastAlgo extends BaseRibbonAlgo3 {
    private static final boolean ADJUST_TAIL = true;
    private static final boolean LIMIT_BY_PRICE = false;

    private final float m_p1;
    private final float m_p2;
    private final float m_p3;
    private final VelocityArray m_velocity;
    private final MidPointsVelocity m_leadEmaVelocity;

//    private Float m_reverseLevel;
//    private Float m_spreadClosePower;
//    private Float m_exitPower;
    private Float m_noStartCollapseRate;
    private Float m_midPower;
//    private Float m_tailPower;
//    private Float m_enterPower;
//    private Float m_headPower;
//    private float m_maxHeadRun;
//    private float m_minHeadRun;
    private Float m_directionNoLimit = 0f;
    private Float m_directionIn;
    private Float m_tailPower2;
    private Float m_spreadClosePowerAdjusted;

    //    private Float m_revPower;
    private float m_velocityStartHalf;

    public FastAlgo(MapConfig algoConfig, ITimesSeriesData tsd, Exchange exchange) {
        super(algoConfig, tsd, exchange, ADJUST_TAIL);

//        if (m_collectValues) {
//            m_priceBars = new BarsTimesSeriesData(tsd, m_barSize);
//        }

        m_p1 = algoConfig.getNumber(Vary.p1).floatValue();
        m_p2 = algoConfig.getNumber(Vary.p2).floatValue();
        m_p3 = algoConfig.getNumber(Vary.p3).floatValue();

        BaseTimesSeriesData leadEma = m_emas[0];
        int multiplier = 4000;
        m_velocity = new VelocityArray(leadEma, m_barSize, 1.0f, 0.1f, 2, multiplier);
        m_leadEmaVelocity = new MidPointsVelocity(leadEma, (long) (m_barSize * 1.0), multiplier);
    }

    @Override protected void recalc4(float lastPrice, float leadEmaValue, boolean goUp, boolean directionChanged,
                                     float ribbonSpread, float maxRibbonSpread, float ribbonSpreadTop, float ribbonSpreadBottom,
                                     float mid, float head, float tail, Float tailStart, float collapseRate) {
        if (directionChanged) {
            m_velocityStartHalf = getVelocity() / 2;
            m_directionIn = (m_adj == null) ? 0 : m_adj;
//                m_maxHeadRun = 0; // reset
//                m_minHeadRun = 0; // reset
//                m_revPower = 0f;
        }

        Float headStart = m_ribbon.m_headStart; // use local var to speedup
        if (headStart != null) { // directionChanged once observed
//            float collapseRate = m_ribbon.m_collapser.update(tail);

//                {
//                    float headRun = leadEmaValue - m_headStart;
//                    if (!goUp) {
//                        headRun = -headRun;
//                    }
//                    float maxHeadRun = m_maxHeadRun;
//                    if (maxHeadRun < headRun) {
//                        m_maxHeadRun = headRun;
//                        maxHeadRun = headRun;
//                    }
//                    float minHeadRun = m_minHeadRun; // negative
//                    if (headRun < minHeadRun) {
//                        m_minHeadRun = headRun;
//                        minHeadRun = headRun;
//                    }
//                    float sumHeadRun = maxHeadRun - minHeadRun;
//                    float headPower = (sumHeadRun == 0) ? 1 : (headRun - minHeadRun) / sumHeadRun;
//                    if (headPower < 0) {
//                        headPower = 0;
//                    }
//                    m_headPower = headPower;
//                }

//                float rev = leadEmaValue - mid;
//                float revPower = rev / (tail - mid);
//                if (revPower < 0) {
//                    revPower = 0;
//                }
////                if (m_revPower < revPower) {
//                    m_revPower = revPower;
////                }

            Float midStart = m_ribbon.m_midStart; // use local var to speedup
            float tailRun = tail - tailStart;
            float tailToMidPower = tailRun / (midStart - tailStart);
            if (tailToMidPower > 1) {
                tailToMidPower = 1;
            } else if (tailToMidPower < 0) {
                tailToMidPower = 0;
            }

            float enterMidPower = (mid - midStart) / (headStart - midStart);
            float enterPower = Math.max(enterMidPower, tailToMidPower * 2);  //  (enterMidPower + tailToMidPower) / 2;
            if (enterPower > 1) {
                enterPower = 1;
            } else if (enterPower < 0) {
                enterPower = 0;
            }
//                m_enterPower = enterPower;

            // revPower
            float tailPower = tailRun / (headStart - tailStart);
            if (tailPower > 1) {
                tailPower = 1;
            } else if (tailPower < 0) {
                tailPower = 0;
            }
//                m_tailPower = tailPower;
            float reverseLevel = head - (head - mid) * tailPower;
//                m_reverseLevel = reverseLevel;
            float revPower = (leadEmaValue - reverseLevel) / (tail - reverseLevel);
            if (revPower < 0) {
                revPower = 0;
            }
//                m_revPower = revPower;

            float tailMidRun = tail - mid;
            float exitMidPower = (tailMidRun != 0) ? (leadEmaValue - mid) / tailMidRun : 0;
            if (exitMidPower < 0) {
                exitMidPower = 0;
            }

            // spreadClosePower
            float spreadClosePower = (maxRibbonSpread - ribbonSpread) / maxRibbonSpread;
//                m_spreadClosePower = spreadClosePower;

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
//                m_exitPower = exitPower;

            // false-start collapse
            float velocity = getVelocity();
            float noStartCollapseRate = (m_velocityStartHalf > 0) ? (velocity - m_velocityStartHalf) / (-2 * m_velocityStartHalf) : 0;
            if (noStartCollapseRate < 0) {
                noStartCollapseRate = 0;
            } else if (noStartCollapseRate > 1) {
                noStartCollapseRate = 1;
            }
            noStartCollapseRate *= (1 - tailToMidPower);

            int sign = goUp ? 1 : -1;
            float direction = m_directionIn + sign * m_remainedEnterDistance * enterPower;
            direction *= (1 - noStartCollapseRate * m_p3);
            direction *= (1 - collapseRate);

            // pre-start next turn
            float remainedExitDistance = goUp ? 1 + direction : 1 - direction;
            direction -= sign * remainedExitDistance * exitMidPower * m_p1;
            direction -= sign * remainedExitDistance * exitPower * m_p2;
//                direction -= sign * remainedExitDistance * revPower;
//                direction -= sign * remainedExitDistance * spreadClosePower;
            if (direction < -1) {
                direction = -1;
            } else if (direction > 1) {
                direction = 1;
            }

            if (m_collectValues) { // this only for painting
                m_noStartCollapseRate = noStartCollapseRate;
                m_directionNoLimit = direction;
            }

            if (LIMIT_BY_PRICE) {
                float adj = (m_adj == null) ? 0 : m_adj;
                if (direction > adj) {
                    if ((lastPrice > leadEmaValue) && (lastPrice > head)) {
                        m_adj = direction;
                    }
                } else { // adj < m_adj
                    if ((lastPrice < leadEmaValue) && (lastPrice < head)) {
                        m_adj = direction;
                    }
                }
            } else {
                m_adj = direction;
            }
        }
    }

    @Override public String key(boolean detailed) {
        detailed = true;
        return  ""
                + (detailed ? ",start=" : ",") + m_start
                + (detailed ? ",step=" : ",") + m_step
                + (detailed ? ",count=" : ",") + m_count
                + (detailed ? ",linRegMult=" : ",") + m_linRegMultiplier
                + (detailed ? ",collapse=" : ",") + m_collapse
                + (detailed ? ",p1=" : ",") + m_p1
                + (detailed ? ",p2=" : ",") + m_p2
                + (detailed ? ",p3=" : ",") + m_p3
//                + (detailed ? "|minOrdMul=" : "|") + m_minOrderMul
                + (detailed ? "|joinTicks=" : "|") + m_joinTicks
                + (detailed ? "|turn=" : "|") + Utils.format8(m_turnLevel)
                + (detailed ? "|commiss=" : "|") + Utils.format8(m_commission)
                + ", " + m_barSize
//                + ", " + Utils.millisToYDHMSStr(m_barSize)
                ;
    }

    private float getVelocity() {
        Average velocityAvg = m_velocity.m_velocityAvg;
        ITickData latestTick = velocityAvg.getLatestTick();
        return (latestTick == null) ? 0f : latestTick.getClosePrice();
    }

    @Override public void reset() {
        super.reset();
//        m_reverseLevel = null;
//        m_spreadClosePower = null;
//        m_exitPower = null;
        m_noStartCollapseRate = null;
        m_midPower = null;
//        m_tailPower = null;
//        m_enterPower = null;
//        m_headPower = null;
//        m_maxHeadRun = 0;
//        m_minHeadRun = 0;
        m_directionIn = null;
        m_tailPower2 = null;
        m_spreadClosePowerAdjusted = null;
        m_directionNoLimit = 0f;
//        m_revPower = null;
    }

    //    TicksTimesSeriesData<TickData> getReverseLevelTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_reverseLevel; } }; }
//    TicksTimesSeriesData<TickData> getSpreadClosePowerTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_spreadClosePower; } }; }
    TicksTimesSeriesData<TickData> getSpreadClosePowerAdjustedTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_spreadClosePowerAdjusted; } }; }
    TicksTimesSeriesData<TickData> getMidPowerTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_midPower; } }; }
//    TicksTimesSeriesData<TickData> getTailPowerTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_tailPower; } }; }
//    TicksTimesSeriesData<TickData> getEnterPowerTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_enterPower; } }; }
    TicksTimesSeriesData<TickData> getTailPower2Ts() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_tailPower2; } }; }
//    TicksTimesSeriesData<TickData> getExitPowerTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_exitPower; } }; }
    TicksTimesSeriesData<TickData> getNoStartCollapseRateTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_noStartCollapseRate; } }; }

//    TicksTimesSeriesData<TickData> getHeadPowerTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_headPower; } }; }
//    TicksTimesSeriesData<TickData> getRevPowerTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_revPower; } }; }

    TicksTimesSeriesData<TickData> getDirectionNoLimitTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_directionNoLimit; } }; }

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
//            addChart(chartData, getReverseLevelTs(), topLayers, "reverseLevel", Colors.GOLD, TickPainter.LINE_JOIN);

            addChart(chartData, getRibbonSpreadTopTs(), topLayers, "maxTop", Colors.SWEET_POTATO, TickPainter.LINE_JOIN);
            addChart(chartData, getRibbonSpreadBottomTs(), topLayers, "maxBottom", Color.CYAN, TickPainter.LINE_JOIN);

//            addChart(chartData, getReverseLevelTs(), topLayers, "reverse", Colors.SPRING_LILAC, TickPainter.LINE_JOIN);

            BaseTimesSeriesData leadEma = m_emas[0]; // fastest ema
            addChart(chartData, leadEma.getJoinNonChangedTs(), topLayers, "leadEma", Colors.GRANNY_SMITH, TickPainter.LINE_JOIN);
        }

        ChartAreaSettings power = chartSetting.addChartAreaSettings("power", 0, 0.6f, 1, 0.1f, Color.LIGHT_GRAY);
        List<ChartAreaLayerSettings> powerLayers = power.getLayers();
        {
//            addChart(chartData, getHeadPowerTs(), powerLayers, "headPower", Color.MAGENTA, TickPainter.LINE_JOIN);
//            addChart(chartData, getRevPowerTs(), powerLayers, "revPower", Colors.JUST_ORANGE, TickPainter.LINE_JOIN);
//            addChart(chartData, getSpreadClosePowerTs(), powerLayers, "spreadClosePower", Colors.SUEDE_BROWN, TickPainter.LINE_JOIN);
//            addChart(chartData, getSpreadClosePowerAdjustedTs(), powerLayers, "spreadClosePowerAdjusted", Colors.CANDY_PINK, TickPainter.LINE_JOIN);
////            addChart(chartData, getMidPowerTs(), powerLayers, "midPower", Color.LIGHT_GRAY, TickPainter.LINE_JOIN);
//            addChart(chartData, getTailPowerTs(), powerLayers, "tailPower", Colors.BLUE_PEARL, TickPainter.LINE_JOIN);
//            addChart(chartData, getEnterPowerTs(), powerLayers, "enterPower", Colors.CHOCOLATE, TickPainter.LINE_JOIN);
//            addChart(chartData, getExitPowerTs(), powerLayers, "exitPower", Colors.EMERALD, TickPainter.LINE_JOIN);
            addChart(chartData, getNoStartCollapseRateTs(), powerLayers, "noStartCollapseRate", Colors.LIGHT_BLUE_PEARL, TickPainter.LINE_JOIN);
            addChart(chartData, getCollapseRateTs(), powerLayers, "collapseRate", Colors.YELLOW, TickPainter.LINE_JOIN);
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
            addChart(chartData, getDirectionNoLimitTs(), valueLayers, "directionNoLimit", Colors.alpha(Color.RED, 45), TickPainter.LINE_JOIN);
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
                PolynomialSplineVelocity velocity = new PolynomialSplineVelocity(tsd, (long) (barSize * (velocityStart + i * velocityStep)), multiplier);
                m_velocities.add(velocity);
            }
            m_velocityAvg = new Average(m_velocities, tsd);
        }
    }
}
