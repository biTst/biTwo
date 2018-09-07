package bi.two.algo.impl;

import bi.two.ChartCanvas;
import bi.two.Colors;
import bi.two.algo.BaseAlgo;
import bi.two.algo.Watcher;
import bi.two.calc.SlidingTicksRegressor;
import bi.two.chart.*;
import bi.two.opt.Vary;
import bi.two.ts.*;
import bi.two.util.MapConfig;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class QummarAlgo extends BaseAlgo<TickData> {
    private static final float REVERSE_LEVEL = 0.5f;
    private static final float REVERSE_MUL = 2.0f; // 2
    private static final boolean APPLY_REVERSE = true;

    private final float m_start;
    private final float m_step;
    private final float m_count;
    private final long m_barSize;
    private final float m_linRegMultiplier;
    private final long m_joinTicks;
    private final float m_minOrderMul;
    private final float m_target;

    private BarsTimesSeriesData m_priceBars;
    private final List<BaseTimesSeriesData> m_emas = new ArrayList<>();
    private boolean m_dirty; // when emas are changed
    private boolean m_goUp;
    private Float m_min;
    private Float m_max;
    private Float m_zigZag;
    private Float m_zerro;
    private Float m_turn;
    private Float m_targetLevel;
    private Float m_power;
    private Float m_value = 0F;
    private Float m_mul = 0F;
    private Float m_reverseLevel;
    private Float m_reversePower;
    private Float m_mulAndPrev = 0F;
    private Float m_revMulAndPrev = 0F;
    private Float m_prevAdj = 0F;
    private Float m_adj = 0F;

    private TickData m_tickData;
    private float m_maxRibbonSpread;
    private Float m_ribbonSpreadTop;
    private Float m_ribbonSpreadBottom;

    private BaseTimesSeriesData m_sliding;

    public QummarAlgo(MapConfig algoConfig, ITimesSeriesData tsd) {
        super(null);

        m_start = algoConfig.getNumber(Vary.start).floatValue();
        m_step = algoConfig.getNumber(Vary.step).floatValue();
        m_count = algoConfig.getNumber(Vary.count).floatValue();
        m_barSize = algoConfig.getNumber(Vary.period).longValue();
        m_linRegMultiplier = algoConfig.getNumber(Vary.multiplier).floatValue();

        m_joinTicks = algoConfig.getNumber(Vary.joinTicks).longValue();
        m_minOrderMul = algoConfig.getNumber(Vary.minOrderMul).floatValue();
        m_target = algoConfig.getNumber(Vary.target).floatValue();

        boolean collectValues = algoConfig.getBoolean(BaseAlgo.COLLECT_VALUES_KEY);
        if (collectValues) {
            m_priceBars = new BarsTimesSeriesData(tsd, m_barSize);
        }

        boolean joinTicksInReader = algoConfig.getBoolean(BaseAlgo.JOIN_TICKS_IN_READER_KEY);
        ITimesSeriesData priceTsd = joinTicksInReader ? tsd : new TickJoinerTimesSeriesData(tsd, m_joinTicks);
        createRibbon(priceTsd, collectValues);

        setParent(tsd);
    }

    private void createRibbon(ITimesSeriesData tsd, boolean collectValues) {
        ITimesSeriesListener listener = new RibbonTsListener();

//        long period = (long) (m_start * m_barSize * m_linRegMultiplier);
//        SlidingTicksRegressor sliding0 = new SlidingTicksRegressor(tsd, period, false);
//        m_sliding = new TicksSMA(sliding0, m_barSize);

        List<ITimesSeriesData> iEmas = new ArrayList<>(); // as list of ITimesSeriesData
        float length = m_start;
        int countFloor = (int) m_count;
        for (int i = 0; i < countFloor; i++) {
            BaseTimesSeriesData ema = getOrCreateEma(tsd, m_barSize, length, collectValues);
            ema.getActive().addListener(listener);
            m_emas.add(ema);
            iEmas.add(ema);
            length += m_step;
        }
        if (m_count != countFloor) {
            float fraction = m_count - countFloor;
            float fractionLength = length - m_step + (m_step * fraction);
            BaseTimesSeriesData ema = getOrCreateEma(tsd, m_barSize, fractionLength, collectValues);
            ema.getActive().addListener(listener);
            m_emas.add(ema);
            iEmas.add(ema);
        }
    }

    private BaseTimesSeriesData getOrCreateEma(ITimesSeriesData tsd, long barSize, float length, boolean collectValues) {
        long period = (long) (length * barSize * m_linRegMultiplier);
        return new SlidingTicksRegressor(tsd, period, false);

//        return new BarsRegressor(tsd, (int) length, (long) (barSize * m_linRegMultiplier), m_linRegMultiplier*5);

//        BarsRegressor r = new BarsRegressor(tsd, (int) length, (long) (barSize * m_linRegMultiplier), m_linRegMultiplier * 5);
//        return new TicksSMA(r, m_barSize/2);

//        return new BarsEMA(tsd, length, barSize);
//        return new BarsDEMA(tsd, length, barSize);
//        return new BarsTEMA(tsd, length, barSize);
    }

    @Override public TickData getLatestTick() {
        return m_tickData;
    }

    @Override public ITickData getAdjusted() {
        if (m_dirty) {
            ITickData parentLatestTick = getParent().getLatestTick();
            if (parentLatestTick != null) {
                Float adj = recalc();
                if (adj != null) {
                    long timestamp = parentLatestTick.getTimestamp();
                    m_tickData = new TickData(timestamp, adj);
                    return m_tickData;
                }
                // else - not ready yet
            }
        }
        return m_tickData;
    }

    private Float recalc() {
        float emasMin = Float.POSITIVE_INFINITY;
        float emasMax = Float.NEGATIVE_INFINITY;
        boolean allDone = true;
        float leadEmaValue = 0;
        int emasLen = m_emas.size();
        for (int i = 0; i < emasLen; i++) {
            ITimesSeriesData ema = m_emas.get(i);
            ITickData lastTick = ema.getLatestTick();
            if (lastTick != null) {
                float value = lastTick.getClosePrice();
                emasMin = Math.min(emasMin, value);
                emasMax = Math.max(emasMax, value);
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
            m_maxRibbonSpread = directionChanged
                    ? ribbonSpread //reset
                    : Math.max(ribbonSpread, m_maxRibbonSpread);
            m_ribbonSpreadTop = goUp ? emasMin + m_maxRibbonSpread : emasMax;
            m_ribbonSpreadBottom = goUp ? emasMin : emasMax - m_maxRibbonSpread;

            if (directionChanged) {
                m_zerro = Float.valueOf(goUp ? emasMax : emasMin);
                m_turn = Float.valueOf(goUp ? emasMin : emasMax);
                m_targetLevel = m_turn + m_target * (m_zerro - m_turn);
            }

            if (m_zerro != null) { // directionChanged once observed
                float head = goUp ? emasMax : emasMin;
                float tail = goUp ? emasMin : emasMax;

                float power = (tail - m_turn) / (m_targetLevel - m_turn);
                m_power = Math.max(0.0f, Math.min(1.0f, power)); // bounds

                m_value = ((head - m_ribbonSpreadBottom) / m_maxRibbonSpread) * 2 - 1;

                m_mul = m_power * m_value;
                m_mulAndPrev = m_mul + m_prevAdj * (1 - m_power);

                Float ribbonSpreadHead = goUp ? m_ribbonSpreadTop : m_ribbonSpreadBottom;
                m_reverseLevel = m_zerro + REVERSE_LEVEL * (ribbonSpreadHead - m_zerro);

                boolean checkReverse = (goUp && (head < m_reverseLevel)) || (!goUp && (head > m_reverseLevel));
                float reversePower = 0;
                if (checkReverse) {
                    reversePower = (head - m_reverseLevel) / (tail - m_reverseLevel) * REVERSE_MUL;
                    reversePower = Math.max(0.0f, Math.min(1.0f, reversePower)); // bounds
                }

                m_reversePower = reversePower;

                m_revMulAndPrev = (goUp ? -reversePower : reversePower) + m_mulAndPrev * (1 - reversePower);

                m_adj = APPLY_REVERSE ? m_revMulAndPrev : m_mulAndPrev;
            }
        }
        m_dirty = false;
        return m_adj;
    }


    @Override public String key(boolean detailed) {
        return  ""
                + (detailed ? ",start=" : ",") + m_start
                + (detailed ? ",step=" : ",") + m_step
                + (detailed ? ",count=" : ",") + m_count
                + (detailed ? ",linRegMultiplier=" : ",") + m_linRegMultiplier
//                + (detailed ? "|s1=" : "|") + m_s1
//                + (detailed ? ",s2=" : ",") + m_s2
//                + (detailed ? ",e1=" : ",") + m_e1
//                + (detailed ? ",e2=" : ",") + m_e2
                + (detailed ? "|minOrderMul=" : "|") + m_minOrderMul
                + (detailed ? "|joinTicks=" : "|") + m_joinTicks
                + ", " + m_barSize
//                + ", " + Utils.millisToYDHMSStr(m_barSize)
                ;
    }

    @Override public long getPreloadPeriod() {
        console("qummar.getPreloadPeriod() start=" + m_start + "; step=" + m_step + "; count=" + m_count);
        return TimeUnit.MINUTES.toMillis(60); // todo: calc from algo params
    }

    TicksTimesSeriesData<TickData> getMinTs() { return new JoinNonChangedInnerTimesSeriesData(this) { @Override protected Float getValue() { return m_min; } }; }
    TicksTimesSeriesData<TickData> getMaxTs() { return new JoinNonChangedInnerTimesSeriesData(this) { @Override protected Float getValue() { return m_max; } }; }
    TicksTimesSeriesData<TickData> getRibbonSpreadMaxTopTs() { return new JoinNonChangedInnerTimesSeriesData(this) { @Override protected Float getValue() { return m_ribbonSpreadTop; } }; }
    TicksTimesSeriesData<TickData> getRibbonSpreadMaxBottomTs() { return new JoinNonChangedInnerTimesSeriesData(this) { @Override protected Float getValue() { return m_ribbonSpreadBottom; } }; }
    TicksTimesSeriesData<TickData> getZigZagTs() { return new JoinNonChangedInnerTimesSeriesData(this, false) { @Override protected Float getValue() { return m_zigZag; } }; }
    TicksTimesSeriesData<TickData> getZerroTs() { return new JoinNonChangedInnerTimesSeriesData(this) { @Override protected Float getValue() { return m_zerro; } }; }
    TicksTimesSeriesData<TickData> getTurnTs() { return new JoinNonChangedInnerTimesSeriesData(this) { @Override protected Float getValue() { return m_turn; } }; }
    TicksTimesSeriesData<TickData> getTargetTs() { return new JoinNonChangedInnerTimesSeriesData(this) { @Override protected Float getValue() { return m_targetLevel; } }; }
    TicksTimesSeriesData<TickData> getPowerTs() { return new JoinNonChangedInnerTimesSeriesData(this) { @Override protected Float getValue() { return m_power; } }; }
    TicksTimesSeriesData<TickData> getValueTs() { return new JoinNonChangedInnerTimesSeriesData(this) { @Override protected Float getValue() { return m_value; } }; }
    TicksTimesSeriesData<TickData> getMulTs() { return new JoinNonChangedInnerTimesSeriesData(this) { @Override protected Float getValue() { return m_mul; } }; }
    TicksTimesSeriesData<TickData> getMulAndPrevTs() { return new JoinNonChangedInnerTimesSeriesData(this) { @Override protected Float getValue() { return m_mulAndPrev; } }; }
    TicksTimesSeriesData<TickData> getReverseLevelTs() { return new JoinNonChangedInnerTimesSeriesData(this) { @Override protected Float getValue() { return m_reverseLevel; } }; }
    TicksTimesSeriesData<TickData> getReversePowerTs() { return new JoinNonChangedInnerTimesSeriesData(this) { @Override protected Float getValue() { return m_reversePower; } }; }
    TicksTimesSeriesData<TickData> getRevMulAndPrevTs() { return new JoinNonChangedInnerTimesSeriesData(this) { @Override protected Float getValue() { return m_revMulAndPrev; } }; }

    @Override public void setupChart(boolean collectValues, ChartCanvas chartCanvas, BaseTicksTimesSeriesData<TickData> ticksTs, Watcher firstWatcher) {
        ChartData chartData = chartCanvas.getChartData();
        ChartSetting chartSetting = chartCanvas.getChartSetting();

        // layout
        ChartAreaSettings top = chartSetting.addChartAreaSettings("top", 0, 0, 1, 0.6f, Color.RED);
        List<ChartAreaLayerSettings> topLayers = top.getLayers();
        {
            addChart(chartData, ticksTs, topLayers, "price", Colors.alpha(Colors.DARK_RED, 70), TickPainter.TICK);
            addChart(chartData, m_priceBars, topLayers, "priceBars", Colors.alpha(Colors.DARK_RED, 80), TickPainter.BAR);

//            chartData.setTicksData("spline", new NoTicksData());
////            topLayers.add(new ChartAreaLayerSettings("spline", Color.RED, new ChartAreaPainter.SplineChartAreaPainter(ticksTs, 4)));
//            topLayers.add(new ChartAreaLayerSettings("spline", Color.RED, new ChartAreaPainter.PolynomChartAreaPainter(ticksTs)));

            int emaAlpha = 20;
            Color emaColor = Colors.alpha(Color.BLUE, emaAlpha);
            int size = m_emas.size();
            for (int i = size - 1; i > 0; i--) { // paint without leadEma
                BaseTimesSeriesData ema = m_emas.get(i);
                addChart(chartData, ema.getJoinNonChangedTs(), topLayers, "ema" + i, emaColor, TickPainter.LINE);
            }

//            addChart(chartData, m_sliding.getJoinNonChangedTs(), topLayers, "sliding", Colors.BALERINA, TickPainter.LINE);


            addChart(chartData, getMinTs(), topLayers, "min", Color.RED, TickPainter.LINE);
            addChart(chartData, getMaxTs(), topLayers, "max", Color.RED, TickPainter.LINE);

            addChart(chartData, getZigZagTs(), topLayers, "zigzag", Color.MAGENTA, TickPainter.LINE);

            addChart(chartData, getZerroTs(), topLayers, "zerro", Color.PINK, TickPainter.LINE);
            addChart(chartData, getTurnTs(), topLayers, "turn", Colors.DARK_GREEN, TickPainter.LINE);
            addChart(chartData, getTargetTs(), topLayers, "target", Colors.HAZELNUT, TickPainter.LINE);

            addChart(chartData, getReverseLevelTs(), topLayers, "reverseLevel", Color.LIGHT_GRAY, TickPainter.LINE);

            addChart(chartData, getRibbonSpreadMaxTopTs(), topLayers, "maxTop", Colors.SWEET_POTATO, TickPainter.LINE);
            addChart(chartData, getRibbonSpreadMaxBottomTs(), topLayers, "maxBottom", Color.CYAN, TickPainter.LINE);

            BaseTimesSeriesData leadEma = m_emas.get(0); // fastest ema
            addChart(chartData, leadEma.getJoinNonChangedTs(), topLayers, "leadEma", Colors.GRANNY_SMITH, TickPainter.LINE);
        }

        ChartAreaSettings power = chartSetting.addChartAreaSettings("power", 0, 0.6f, 1, 0.1f, Color.LIGHT_GRAY);
        List<ChartAreaLayerSettings> powerLayers = power.getLayers();
        {
            addChart(chartData, getPowerTs(), powerLayers, "power", Color.MAGENTA, TickPainter.LINE);
            addChart(chartData, getReversePowerTs(), powerLayers, "reversePower", Color.LIGHT_GRAY, TickPainter.LINE);
        }

        ChartAreaSettings value = chartSetting.addChartAreaSettings("value", 0, 0.7f, 1, 0.15f, Color.LIGHT_GRAY);
        List<ChartAreaLayerSettings> valueLayers = value.getLayers();
        {
//            addChart(chartData, getTS(true), valueLayers, "value", Color.blue, TickPainter.LINE);
//            addChart(chartData, getJoinNonChangedTs(), valueLayers, "value", Color.blue, TickPainter.LINE);
            addChart(chartData, getValueTs(), valueLayers, "value", Colors.alpha(Color.MAGENTA, 128), TickPainter.LINE);
            addChart(chartData, getMulTs(), valueLayers, "mul", Color.GRAY, TickPainter.LINE);
            addChart(chartData, getMulAndPrevTs(), valueLayers, "mulAndPrev", Color.RED, TickPainter.LINE);
            addChart(chartData, getRevMulAndPrevTs(), valueLayers, "revMulAndPrev", Colors.GOLD, TickPainter.LINE);
//            addChart(chartData, getPowAdjTs(), valueLayers, "powValue", Color.MAGENTA, TickPainter.LINE);
//            addChart(chartData, m_velocityAdj.getJoinNonChangedTs(), valueLayers, "velAdj", Color.RED, TickPainter.LINE);
        }

        if (collectValues) {
            addChart(chartData, firstWatcher, topLayers, "trades", Color.WHITE, TickPainter.TRADE);

            ChartAreaSettings gain = chartSetting.addChartAreaSettings("gain", 0, 0.85f, 1, 0.15f, Color.ORANGE);
            gain.setHorizontalLineValue(1);

            List<ChartAreaLayerSettings> gainLayers = gain.getLayers();
            addChart(chartData, firstWatcher.getGainTs(), gainLayers, "gain", Color.blue, TickPainter.LINE);
        }
    }

    private class RibbonTsListener implements ITimesSeriesListener {
        @Override public void onChanged(ITimesSeriesData ts, boolean changed) {
            if (changed) {
                m_dirty = true;
            }
        }

        @Override public void waitWhenFinished() { }
        @Override public void notifyNoMoreTicks() {}
    }
}
