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
import java.util.concurrent.TimeUnit;

import static bi.two.util.Log.console;

public class QummarAlgo extends BaseRibbonAlgo {
    private static final boolean APPLY_REVERSE = true;
    private static final boolean LIMIT_BY_PRICE = true;
    public static final boolean ADJUST_TAIL = false;

    private final float m_minOrderMul;
    private final float m_target;
    private final float m_reverse;
    private final float m_reverseMul;

//    private BarsTimesSeriesData m_priceBars;
    private boolean m_goUp;
    private Float m_min;
    private Float m_max;
    private Float m_zigZag;
    private Float m_zerro;
    private Float m_turn;
    private Float m_half;
    private Float m_one;
    private Float m_onePaint;
    private Float m_targetLevel;
    private Float m_power;
    private Float m_value;
    private Float m_mul;
    private Float m_reverseLevel;
    private Float m_reversePower;
    private Float m_mulAndPrev;
    private Float m_revMulAndPrev;
    private Float m_prevAdj;
    private Float m_adj;
    private float m_maxRibbonSpread;
    private Float m_ribbonSpreadTop;
    private Float m_ribbonSpreadBottom;

    private BaseTimesSeriesData m_sliding;

    public QummarAlgo(MapConfig algoConfig, ITimesSeriesData tsd, Exchange exchange) {
        super(algoConfig, tsd, exchange);

        reset(); // todo: really need here ?

        m_minOrderMul = algoConfig.getNumber(Vary.minOrderMul).floatValue();
        m_target = algoConfig.getNumber(Vary.target).floatValue();
        m_reverse = algoConfig.getNumber(Vary.reverse).floatValue();
        m_reverseMul = algoConfig.getNumber(Vary.reverseMul).floatValue();

//        if (collectValues) {
//            m_priceBars = new BarsTimesSeriesData(tsd, m_barSize);
//        }
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

            if (directionChanged) {
                m_zerro = head; // pink
                m_turn = tail;  // dark green
                float diff = head - tail;
                m_one = head + diff / 2;
                m_onePaint = head;
                m_half = (head + tail) / 2;
                m_targetLevel = tail + m_target * diff;
            } else {
                if (m_one != null) {
                    if (goUp) {
                        if (tail >= m_one) {
                            m_onePaint = m_one;
                        }
                        if (ADJUST_TAIL) {
                            if (tail < m_turn) {
                                m_turn = tail;
                                m_half = (m_zerro + tail) / 2;
                                m_targetLevel = tail + m_target * (m_zerro - tail);
                            }
                        }
                    } else {
                        if (tail <= m_one) {
                            m_onePaint = m_one;
                        }
                        if (ADJUST_TAIL) {
                            if (tail > m_turn) {
                                m_turn = tail;
                                m_half = (m_zerro + tail) / 2;
                                m_targetLevel = tail + m_target * (m_zerro - tail);
                            }
                        }
                    }
                }
            }

            if (m_zerro != null) { // directionChanged once observed

                float diff = m_targetLevel - m_turn;
                float power = (diff == 0)
                                ? 0 // avoid NaN value
                                : (tail - m_turn) / diff;
                power = (1.0f <= power) ? 1.0f : power;  //  Math.min(1.0f, power);
                power = (0.0f >= power) ? 0.0f : power;  //  Math.max(0.0f, power); // bounds
                m_power = power;

                m_value = (maxRibbonSpread == 0)
                        ? 0
                        : ((head - m_ribbonSpreadBottom) / maxRibbonSpread) * 2 - 1;

                m_mul = power * m_value;
                m_mulAndPrev = m_mul + m_prevAdj * (1 - power);

                Float ribbonSpreadHead = goUp ? m_ribbonSpreadTop : m_ribbonSpreadBottom;
                float reverseLevel = m_zerro + m_reverse * (ribbonSpreadHead - m_zerro);
                m_reverseLevel = reverseLevel;

                float reversePower;
                boolean checkReverse = (goUp && (head < reverseLevel)) || (!goUp && (head > reverseLevel));
                if (checkReverse) {
                    float rp = (head - reverseLevel) / (tail - reverseLevel) * m_reverseMul;
                    reversePower = (1.0f <= rp) ? 1.0f : rp;                      // Math.min(1.0f, rp);
                    reversePower = (0.0f >= reversePower) ? 0.0f : reversePower;  //  Math.max(0.0f, reversePower); // bounds
                } else {
                    reversePower = 0;
                }
                m_reversePower = reversePower;

                m_revMulAndPrev = (goUp ? -reversePower : reversePower) + m_mulAndPrev * (1 - reversePower);

                Float adj = APPLY_REVERSE ? m_revMulAndPrev : m_mulAndPrev;

                if (LIMIT_BY_PRICE) {
                    if (adj > m_adj) {
                        if ((lastPrice > leadEmaValue) && (lastPrice > head)) {
                            m_adj = adj;
                        }
                    } else { // adj < m_adj
                        if ((lastPrice < leadEmaValue) && (lastPrice < head)) {
                            m_adj = adj;
                        }
                    }
                } else {
                    m_adj = adj;
                }
            }
        }
        return m_adj;
    }


    @Override public String key(boolean detailed) {
        detailed = true;
        return  ""
                + (detailed ? ",start=" : ",") + m_start
                + (detailed ? ",step=" : ",") + m_step
                + (detailed ? ",count=" : ",") + m_count
                + (detailed ? ",linRegMult=" : ",") + m_linRegMultiplier
                + (detailed ? ",target=" : ",") + m_target
                + (detailed ? ",reverse=" : ",") + m_reverse
                + (detailed ? ",revMul=" : ",") + m_reverseMul
                + (detailed ? "|minOrdMul=" : "|") + m_minOrderMul
                + (detailed ? "|joinTicks=" : "|") + m_joinTicks
                + (detailed ? "|commiss=" : "|") + Utils.format8(m_commission)
                + ", " + m_barSize
//                + ", " + Utils.millisToYDHMSStr(m_barSize)
                ;
    }

    @Override public long getPreloadPeriod() {
        console("qummar.getPreloadPeriod() start=" + m_start + "; step=" + m_step + "; count=" + m_count);
        return TimeUnit.MINUTES.toMillis(60); // todo: calc from algo params
    }

    @Override public void reset() {
        m_min = null;
        m_max = null;
        m_zigZag = null;
        m_zerro = null;
        m_half = null;
        m_one = null;
        m_onePaint = null;
        m_turn = null;
        m_targetLevel = null;
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
    }

    TicksTimesSeriesData<TickData> getMinTs() { return new JoinNonChangedInnerTimesSeriesData(this) { @Override protected Float getValue() { return m_min; } }; }
    TicksTimesSeriesData<TickData> getMaxTs() { return new JoinNonChangedInnerTimesSeriesData(this) { @Override protected Float getValue() { return m_max; } }; }
    TicksTimesSeriesData<TickData> getRibbonSpreadMaxTopTs() { return new JoinNonChangedInnerTimesSeriesData(this) { @Override protected Float getValue() { return m_ribbonSpreadTop; } }; }
    TicksTimesSeriesData<TickData> getRibbonSpreadMaxBottomTs() { return new JoinNonChangedInnerTimesSeriesData(this) { @Override protected Float getValue() { return m_ribbonSpreadBottom; } }; }
    TicksTimesSeriesData<TickData> getZigZagTs() { return new JoinNonChangedInnerTimesSeriesData(this, false) { @Override protected Float getValue() { return m_zigZag; } }; }
    TicksTimesSeriesData<TickData> getZerroTs() { return new JoinNonChangedInnerTimesSeriesData(this) { @Override protected Float getValue() { return m_zerro; } }; }
    TicksTimesSeriesData<TickData> getTurnTs() { return new JoinNonChangedInnerTimesSeriesData(this) { @Override protected Float getValue() { return m_turn; } }; }
    TicksTimesSeriesData<TickData> getHalfTs() { return new JoinNonChangedInnerTimesSeriesData(this) { @Override protected Float getValue() { return m_half; } }; }
    TicksTimesSeriesData<TickData> getOneTs() { return new JoinNonChangedInnerTimesSeriesData(this) { @Override protected Float getValue() { return m_onePaint; } }; }
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
            addChart(chartData, ticksTs, topLayers, "price", Colors.alpha(Colors.DARK_RED, 100), TickPainter.TICK_JOIN);
//            addChart(chartData, m_priceBars, topLayers, "priceBars", Colors.alpha(Colors.DARK_RED, 80), TickPainter.BAR);

//            chartData.setTicksData("spline", new NoTicksData());
////            topLayers.add(new ChartAreaLayerSettings("spline", Color.RED, new ChartAreaPainter.SplineChartAreaPainter(ticksTs, 4)));
//            topLayers.add(new ChartAreaLayerSettings("spline", Color.RED, new ChartAreaPainter.PolynomChartAreaPainter(ticksTs)));

//            int emaAlpha = 20;
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

            addChart(chartData, getOneTs(), topLayers, "one", Colors.LEMONADE, TickPainter.LINE_JOIN);
            addChart(chartData, getZerroTs(), topLayers, "zerro", Color.PINK, TickPainter.LINE_JOIN);
            addChart(chartData, getTurnTs(), topLayers, "turn", Colors.DARK_GREEN, TickPainter.LINE_JOIN);
            addChart(chartData, getHalfTs(), topLayers, "half", Colors.PURPLE, TickPainter.LINE_JOIN);

            addChart(chartData, getTargetTs(), topLayers, "target", Colors.HAZELNUT, TickPainter.LINE_JOIN);

            addChart(chartData, getReverseLevelTs(), topLayers, "reverseLevel", Color.LIGHT_GRAY, TickPainter.LINE_JOIN);

            addChart(chartData, getRibbonSpreadMaxTopTs(), topLayers, "maxTop", Colors.SWEET_POTATO, TickPainter.LINE_JOIN);
            addChart(chartData, getRibbonSpreadMaxBottomTs(), topLayers, "maxBottom", Color.CYAN, TickPainter.LINE_JOIN);

            BaseTimesSeriesData leadEma = m_emas[0]; // fastest ema
            addChart(chartData, leadEma.getJoinNonChangedTs(), topLayers, "leadEma", Colors.GRANNY_SMITH, TickPainter.LINE_JOIN);
        }

        ChartAreaSettings power = chartSetting.addChartAreaSettings("power", 0, 0.6f, 1, 0.1f, Color.LIGHT_GRAY);
        List<ChartAreaLayerSettings> powerLayers = power.getLayers();
        {
//            addChart(chartData, getPowerTs(), powerLayers, "power", Color.MAGENTA, TickPainter.LINE_JOIN);
//            addChart(chartData, getReversePowerTs(), powerLayers, "reversePower", Color.LIGHT_GRAY, TickPainter.LINE_JOIN);

            addChart(chartData, firstWatcher.getFadeOutTs(), powerLayers, "fadeOut", Colors.CANDY_PINK, TickPainter.LINE_JOIN);
            addChart(chartData, firstWatcher.getFadeInTs(), powerLayers, "fadeIn", Colors.STRING_BEAN, TickPainter.LINE_JOIN);
        }

        ChartAreaSettings value = chartSetting.addChartAreaSettings("value", 0, 0.7f, 1, 0.15f, Color.LIGHT_GRAY);
        List<ChartAreaLayerSettings> valueLayers = value.getLayers();
        {
//            addChart(chartData, getTS(true), valueLayers, "value", Color.blue, TickPainter.LINE);
//            addChart(chartData, getJoinNonChangedTs(), valueLayers, "value", Color.blue, TickPainter.LINE);
            addChart(chartData, getValueTs(), valueLayers, "value", Colors.alpha(Color.MAGENTA, 128), TickPainter.LINE_JOIN);
            addChart(chartData, getMulTs(), valueLayers, "mul", Color.GRAY, TickPainter.LINE_JOIN);
            addChart(chartData, getMulAndPrevTs(), valueLayers, "mulAndPrev", Color.RED, TickPainter.LINE_JOIN);
            addChart(chartData, getRevMulAndPrevTs(), valueLayers, "revMulAndPrev", Colors.GOLD, TickPainter.LINE_JOIN);
//            addChart(chartData, m_velocityAdj.getJoinNonChangedTs(), valueLayers, "velAdj", Color.RED, TickPainter.LINE);
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
