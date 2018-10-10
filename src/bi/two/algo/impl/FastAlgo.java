package bi.two.algo.impl;

import bi.two.ChartCanvas;
import bi.two.Colors;
import bi.two.algo.BaseAlgo;
import bi.two.algo.Watcher;
import bi.two.calc.SlidingTicksRegressor;
import bi.two.chart.*;
import bi.two.exch.Exchange;
import bi.two.opt.Vary;
import bi.two.ts.*;
import bi.two.util.MapConfig;
import bi.two.util.Utils;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class FastAlgo extends BaseAlgo<TickData> {
    public static final boolean ADJUST_TAIL = true;

    private final float m_start;
    private final long m_joinTicks;
    private final float m_step;
    private final float m_count;
    private final long m_barSize;
    private final float m_linRegMultiplier;
    private final double m_commission;
//    private final float m_target;

    private int m_emasNum;
    private BaseTimesSeriesData[] m_emas;
    private boolean m_dirty;
    private TickData m_tickData;

    private boolean m_goUp;
    private Float m_min;
    private Float m_max;
    private Float m_zigZag;
    private Float m_headStart;
    private Float m_tailStart;
    private Float m_midStart;
    private Float m_one;
    private Float m_onePaint;
    private Float m_reverseLevel;
//    private Float m_targetLevel;
    private Float m_power;
    private Float m_value;
    private Float m_mul;
    private Float m_reversePower;
    private Float m_mulAndPrev;
    private Float m_revMulAndPrev;
    private Float m_prevAdj;
    private Float m_adj;
    private float m_maxRibbonSpread;
    private Float m_ribbonSpreadTop;
    private Float m_ribbonSpreadBottom;
    private Float m_spreadClosePower;
    private Float m_midPower;
    private Float m_tailPower;
    private Float m_midTailPower;
    private Float m_direction;
    private Float m_directionIn;
    private Float m_beginRate;

    public FastAlgo(MapConfig algoConfig, ITimesSeriesData tsd, Exchange exchange) {
        super(null);

        reset();

        m_start = algoConfig.getNumber(Vary.start).floatValue();
        m_step = algoConfig.getNumber(Vary.step).floatValue();
        m_count = algoConfig.getNumber(Vary.count).floatValue();
        m_barSize = algoConfig.getNumber(Vary.period).longValue();
        m_linRegMultiplier = algoConfig.getNumber(Vary.multiplier).floatValue();

        m_joinTicks = algoConfig.getNumber(Vary.joinTicks).longValue();
//        m_minOrderMul = algoConfig.getNumber(Vary.minOrderMul).floatValue();
//        m_target = algoConfig.getNumber(Vary.target).floatValue();
//        m_reverse = algoConfig.getNumber(Vary.reverse).floatValue();
//        m_reverseMul = algoConfig.getNumber(Vary.reverseMul).floatValue();

        m_commission = algoConfig.getNumber(Vary.commission).doubleValue();

        boolean collectValues = algoConfig.getBoolean(BaseAlgo.COLLECT_VALUES_KEY);
//        if (collectValues) {
//            m_priceBars = new BarsTimesSeriesData(tsd, m_barSize);
//        }

        ITimesSeriesData ts1 = (m_joinTicks > 0) ? new TickJoinerTimesSeriesData(tsd, m_joinTicks) : tsd;
        boolean hasSchedule = exchange.hasSchedule();
        ITimesSeriesData ts2 = hasSchedule ? new ScheduleTimesSeriesData(ts1, exchange.m_schedule) : ts1;

        createRibbon(ts2, collectValues, hasSchedule);

        setParent(ts2);
    }

    private void createRibbon(ITimesSeriesData tsd, boolean collectValues, boolean hasSchedule) {
        ITimesSeriesListener listener = new RibbonTsListener();

//        long period = (long) (m_start * m_barSize * m_linRegMultiplier);
//        SlidingTicksRegressor sliding0 = new SlidingTicksRegressor(tsd, period, false);
//        m_sliding = new TicksSMA(sliding0, m_barSize);

        List<BaseTimesSeriesData> list = new ArrayList<>();
        float length = m_start;
        int len = (int) m_count;
        for (int i = 0; i < len; i++) {
            BaseTimesSeriesData ema = getOrCreateEma(tsd, m_barSize, length, collectValues, hasSchedule);
            ema.getActive().addListener(listener);
            list.add(ema);
            length += m_step;  // todo: add progressive step
        }
        if (m_count != len) {
            float fraction = m_count - len;
            float fractionLength = length - m_step + (m_step * fraction);
            BaseTimesSeriesData ema = getOrCreateEma(tsd, m_barSize, fractionLength, collectValues, hasSchedule);
            ema.getActive().addListener(listener);
            list.add(ema);
            len++;
        }

        m_emasNum = len;
        m_emas = list.toArray(new BaseTimesSeriesData[len]);
    }

    private BaseTimesSeriesData getOrCreateEma(ITimesSeriesData tsd, long barSize, float length, boolean collectValues, boolean hasSchedule) {
        long period = (long) (length * barSize * m_linRegMultiplier);
        return new SlidingTicksRegressor(tsd, period, false, hasSchedule);

//        return new BarsRegressor(tsd, (int) length, (long) (barSize * m_linRegMultiplier), m_linRegMultiplier*5);

//        BarsRegressor r = new BarsRegressor(tsd, (int) length, (long) (barSize * m_linRegMultiplier), m_linRegMultiplier * 5);
//        return new TicksSMA(r, m_barSize/2);

//        return new BarsEMA(tsd, length, barSize);
//        return new BarsDEMA(tsd, length, barSize);
//        return new BarsTEMA(tsd, length, barSize);
    }

    @Override public String key(boolean detailed) {
        detailed = true;
        return  ""
                + (detailed ? ",start=" : ",") + m_start
                + (detailed ? ",step=" : ",") + m_step
                + (detailed ? ",count=" : ",") + m_count
                + (detailed ? ",linRegMult=" : ",") + m_linRegMultiplier
//                + (detailed ? ",target=" : ",") + m_target
//                + (detailed ? ",reverse=" : ",") + m_reverse
//                + (detailed ? ",revMul=" : ",") + m_reverseMul
//                + (detailed ? "|minOrdMul=" : "|") + m_minOrderMul
                + (detailed ? "|joinTicks=" : "|") + m_joinTicks
                + (detailed ? "|commiss=" : "|") + Utils.format8(m_commission)
                + ", " + m_barSize
//                + ", " + Utils.millisToYDHMSStr(m_barSize)
                ;
    }

    @Override public TickData getLatestTick() {
        return m_tickData;
    }

    @Override public ITickData getAdjusted() {
        if (m_dirty) {
            ITickData parentLatestTick = m_parent.getLatestTick();
            if (parentLatestTick != null) {
                float lastPrice = parentLatestTick.getClosePrice();
                Float adj = recalc(lastPrice);
                if (adj != null) {
                    long timestamp = parentLatestTick.getTimestamp();
                    m_tickData = new TickData(timestamp, adj); // todo: every time here new object, even if value is not chnaged
                    return m_tickData;
                }
                // else - not ready yet
            }
        }
        return m_tickData;
    }

    private Float recalc(float lastPrice) {
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
            if (directionChanged) {
                m_prevAdj = m_adj; // save prev
            }
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

            m_reverseLevel = tail + (head - tail) * 0.5f;
            float mid = (head + tail) / 2;

            if (directionChanged) {
                m_headStart = head; // pink
                m_tailStart = tail;  // dark green
                m_midStart = mid;
                m_one = head + (head - tail) / 2;
                m_onePaint = head;
//                m_targetLevel = tail + m_target * diff;
                m_directionIn = (m_direction == null) ?  0 : m_direction;
                m_beginRate = goUp ? 1 - m_directionIn : 1 + m_directionIn;
            } else {
                if (m_one != null) {
                    if (goUp) {
                        if (tail >= m_one) {
                            m_onePaint = m_one;
                        }
                        if (ADJUST_TAIL) {
                            if (tail < m_tailStart) {
                                m_tailStart = tail;
                                m_midStart = (m_headStart + tail) / 2;
                                m_one = m_headStart + (m_headStart - tail) / 2;
//                                m_targetLevel = tail + m_target * (m_headStart - tail);
                            }
                        }
                    } else {
                        if (tail <= m_one) {
                            m_onePaint = m_one;
                        }
                        if (ADJUST_TAIL) {
                            if (tail > m_tailStart) {
                                m_tailStart = tail;
                                m_midStart = (m_headStart + tail) / 2;
                                m_one = m_headStart + (m_headStart - tail) / 2;
//                                m_targetLevel = tail + m_target * (m_headStart - tail);
                            }
                        }
                    }
                }
            }

            if (m_headStart != null) { // directionChanged once observed
                m_spreadClosePower = (maxRibbonSpread - ribbonSpread) / maxRibbonSpread;
                float midPower = (mid - m_midStart) / (m_headStart - m_midStart);
//                m_midPower = (midPower > 1) ? 1 : midPower;
                float tailPower = (tail - m_tailStart) / (m_midStart - m_tailStart) * 2;
//                m_tailPower = (tailPower > 1) ? 1 : tailPower;
                float midTailPower = (midPower + tailPower) / 2;
                midTailPower = (midTailPower > 1) ? 1 : midTailPower;
                m_midTailPower = midTailPower;

                float direction = m_directionIn + m_beginRate * (goUp ? midTailPower : -midTailPower )  + (goUp ? (- 2 * m_spreadClosePower) : (2 * m_spreadClosePower));
                direction = (direction > 1) ? 1 : direction;
                direction = (direction < -1) ? -1 : direction;
                m_direction = direction;
            }
        }

        return m_direction;
    }

    @Override public void reset() {
        m_min = null;
        m_max = null;
        m_zigZag = null;
        m_headStart = null;
        m_midStart = null;
        m_one = null;
        m_onePaint = null;
        m_tailStart = null;
//        m_targetLevel = null;
        m_power = null;
        m_value = 0F;
        m_mul = 0F;
        m_reverseLevel = null;
        m_reversePower = null;
        m_mulAndPrev = 0F;
        m_revMulAndPrev = 0F;
        m_prevAdj = 0F;
        m_adj = 0F;
        m_maxRibbonSpread = 0f;
        m_ribbonSpreadTop = null;
        m_ribbonSpreadBottom = null;
        m_spreadClosePower = null;
        m_midPower = null;
        m_tailPower = null;
        m_midTailPower = null;
        m_direction = null;
        m_directionIn = null;
        m_beginRate = null;
    }

    TicksTimesSeriesData<TickData> getMinTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_min; } }; }
    TicksTimesSeriesData<TickData> getMaxTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_max; } }; }
    TicksTimesSeriesData<TickData> getZigZagTs() { return new JoinNonChangedInnerTimesSeriesData(getParent(), false) { @Override protected Float getValue() { return m_zigZag; } }; }

    TicksTimesSeriesData<TickData> getOneTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_onePaint; } }; }
    TicksTimesSeriesData<TickData> getHeadStartTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_headStart; } }; }
    TicksTimesSeriesData<TickData> getTailStartTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_tailStart; } }; }
    TicksTimesSeriesData<TickData> getMidTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_midStart; } }; }

    TicksTimesSeriesData<TickData> getRibbonSpreadMaxTopTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_ribbonSpreadTop; } }; }
    TicksTimesSeriesData<TickData> getRibbonSpreadMaxBottomTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_ribbonSpreadBottom; } }; }

    TicksTimesSeriesData<TickData> getReverseLevelTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_reverseLevel; } }; }
    TicksTimesSeriesData<TickData> getSpreadClosePowerTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_spreadClosePower; } }; }
    TicksTimesSeriesData<TickData> getMidPowerTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_midPower; } }; }
    TicksTimesSeriesData<TickData> getTailPowerTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_tailPower; } }; }
    TicksTimesSeriesData<TickData> getMidTailPowerTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_midTailPower; } }; }

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

            Color emaColor = Colors.alpha(Color.BLUE, emaAlpha);
            int size = m_emas.length;
            for (int i = size - 1; i > 0; i--) { // paint without leadEma
                BaseTimesSeriesData ema = m_emas[i];
                addChart(chartData, ema.getJoinNonChangedTs(), topLayers, "ema" + i, emaColor, TickPainter.LINE);
            }

//            addChart(chartData, m_sliding.getJoinNonChangedTs(), topLayers, "sliding", Colors.BALERINA, TickPainter.LINE);

            addChart(chartData, getMinTs(), topLayers, "min", Color.RED, TickPainter.LINE_JOIN);
            addChart(chartData, getMaxTs(), topLayers, "max", Color.RED, TickPainter.LINE_JOIN);

            addChart(chartData, getZigZagTs(), topLayers, "zigzag", Color.MAGENTA, TickPainter.LINE_JOIN);

            addChart(chartData, getOneTs(), topLayers, "one", Colors.LEMONADE, TickPainter.LINE_JOIN);
            addChart(chartData, getHeadStartTs(), topLayers, "headStart", Color.PINK, TickPainter.LINE_JOIN);
            addChart(chartData, getTailStartTs(), topLayers, "tailStart", Colors.DARK_GREEN, TickPainter.LINE_JOIN);
            addChart(chartData, getMidTs(), topLayers, "midStart", Colors.PURPLE, TickPainter.LINE_JOIN);

//            addChart(chartData, getTargetTs(), topLayers, "target", Colors.HAZELNUT, TickPainter.LINE_JOIN);
//
//            addChart(chartData, getReverseLevelTs(), topLayers, "reverseLevel", Color.LIGHT_GRAY, TickPainter.LINE_JOIN);

            addChart(chartData, getRibbonSpreadMaxTopTs(), topLayers, "maxTop", Colors.SWEET_POTATO, TickPainter.LINE_JOIN);
            addChart(chartData, getRibbonSpreadMaxBottomTs(), topLayers, "maxBottom", Color.CYAN, TickPainter.LINE_JOIN);

            addChart(chartData, getReverseLevelTs(), topLayers, "reverse", Colors.SPRING_LILAC, TickPainter.LINE_JOIN);

            BaseTimesSeriesData leadEma = m_emas[0]; // fastest ema
            addChart(chartData, leadEma.getJoinNonChangedTs(), topLayers, "leadEma", Colors.GRANNY_SMITH, TickPainter.LINE_JOIN);
        }

        ChartAreaSettings power = chartSetting.addChartAreaSettings("power", 0, 0.6f, 1, 0.1f, Color.LIGHT_GRAY);
        List<ChartAreaLayerSettings> powerLayers = power.getLayers();
        {
            addChart(chartData, getSpreadClosePowerTs(), powerLayers, "spreadClosePower", Color.MAGENTA, TickPainter.LINE_JOIN);
//            addChart(chartData, getMidPowerTs(), powerLayers, "midPower", Color.LIGHT_GRAY, TickPainter.LINE_JOIN);
//            addChart(chartData, getTailPowerTs(), powerLayers, "tailPower", Colors.BLUE_PEARL, TickPainter.LINE_JOIN);
            addChart(chartData, getMidTailPowerTs(), powerLayers, "midTailPower", Colors.SUEDE_BROWN, TickPainter.LINE_JOIN);

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

    @Override public void onTimeShift(long shift) {
        // todo: call super
        notifyOnTimeShift(shift);
//        super.onTimeShift(shift);
    }



    //-------------------------------------------------------------------------------------------
    private class RibbonTsListener implements ITimesSeriesListener {
        @Override public void onChanged(ITimesSeriesData ts, boolean changed) {
            if (changed) {
                m_dirty = true;
            }
        }

        @Override public void waitWhenAllFinish() { }
        @Override public void notifyNoMoreTicks() {}
        @Override public void onTimeShift(long shift) { /*noop*/ }
    }
}