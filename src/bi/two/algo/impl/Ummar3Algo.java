package bi.two.algo.impl;

import bi.two.ChartCanvas;
import bi.two.Colors;
import bi.two.algo.BaseAlgo;
import bi.two.algo.Watcher;
import bi.two.calc.SlidingTicksRegressor;
import bi.two.chart.*;
import bi.two.opt.Vary;
import bi.two.ts.BaseTimesSeriesData;
import bi.two.ts.ITimesSeriesData;
import bi.two.ts.TimesSeriesData;
import bi.two.util.MapConfig;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class Ummar3Algo extends BaseAlgo {

    private final double m_minOrderMul;

    private final long m_barSize;
    private final float m_start;
    private final float m_step;
    private final float m_count;
    private final float m_multiplier;

    private final float m_a1;// spread proportional start
    private final float m_a2;// gain proportional start
    private final float m_b1;// spread proportional end
    private final float m_b2;// gain proportional end

    //    private final float m_signal;
    private final List<BaseTimesSeriesData> m_emas = new ArrayList<>();
    //    private final MinMaxSpread m_minMaxSpread;
    private ITickData m_tickData;

    private boolean m_dirty;

    private boolean m_goUp;
    private float m_min;
    private float m_max;
    private float m_ribbonSpread;
    private float m_ribbonSpreadTop;
    private float m_ribbonSpreadBottom;
    private Float m_xxx;
    private float m_height;
    private Float m_trend;
    //    private Float m_mirror;
    //    private Float m_reverse;
    private Float m_top;
    private Float m_bottom;
    private float m_level;
    private Ummar2Algo.DoubleAdjuster m_da;
    private Float m_adj;

    public Ummar3Algo(MapConfig config, ITimesSeriesData tsd) {
        super(null);

        m_minOrderMul = config.getNumber(Vary.minOrderMul).floatValue();

        boolean collectValues = config.getBoolean(BaseAlgo.COLLECT_VALUES_KEY);
        m_barSize = config.getNumber(Vary.period).longValue();
        m_start = config.getNumber(Vary.start).floatValue();
        m_step = config.getNumber(Vary.step).floatValue();
        m_count = config.getNumber(Vary.count).floatValue();
        m_multiplier = config.getNumber(Vary.multiplier).floatValue();

        m_a1 = config.getNumber(Vary.a1).floatValue();
        m_a2 = config.getNumber(Vary.a2).floatValue();
        m_b1 = config.getNumber(Vary.b1).floatValue();
        m_b2 = config.getNumber(Vary.b2).floatValue();

        // create ribbon
        createRibbon(tsd, collectValues);

        setParent(m_emas.get(0));
    }

    private void createRibbon(ITimesSeriesData tsd, boolean collectValues) {

        ITimesSeriesListener listener = new ITimesSeriesListener() {
            @Override public void onChanged(ITimesSeriesData ts, boolean changed) {
                if (changed) {
                    m_dirty = true;
                }
            }

            @Override public void waitWhenFinished() { }
            @Override public void notifyNoMoreTicks() {}
        };

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
            float fractionLength = length - m_step + m_step * fraction;
            BaseTimesSeriesData ema = getOrCreateEma(tsd, m_barSize, fractionLength, collectValues);
            ema.getActive().addListener(listener);
            m_emas.add(ema);
            iEmas.add(ema);
        }
    }

    private BaseTimesSeriesData getOrCreateEma(ITimesSeriesData tsd, long barSize, float length, boolean collectValues) {
        long period = (long) (length * barSize * m_multiplier);
        return new SlidingTicksRegressor(tsd, period, collectValues);
//        return new BarsEMA(tsd, length, barSize);
//        return new BarsDEMA(tsd, length, barSize);
//        return new BarsTEMA(tsd, length, barSize);
    }

    @Override public ITickData getLatestTick() {
        return m_tickData;
    }

    @Override public ITickData getAdjusted() {
        if(m_dirty) {
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
            m_goUp = goUp;

//            m_min = emasMin;
//            m_max = emasMax;

            float ribbonSpread = emasMax - emasMin;

            m_ribbonSpread = directionChanged // direction changed
                    ? ribbonSpread //reset
                    : Math.max(ribbonSpread, m_ribbonSpread);
            m_ribbonSpreadTop = goUp ? emasMin + m_ribbonSpread : emasMax;
            m_ribbonSpreadBottom = goUp ? emasMin : emasMax - m_ribbonSpread;

            if (directionChanged) {
                m_xxx = goUp ? emasMax : emasMin;
                m_height = emasMax - emasMin;
                m_da = new Ummar2Algo.DoubleAdjuster(goUp ? 1 : -1);
            }

            if (m_xxx != null) {
                float height;
                float approachRate;
                float approachLevel;
                float gainLevel;
                float spread = m_ribbonSpreadTop - m_ribbonSpreadBottom;
                if (goUp) {
                    float trend = m_ribbonSpreadTop - m_xxx;
                    gainLevel = m_ribbonSpreadBottom + m_b1 * spread + m_b2 * trend;
                    height = m_xxx - m_ribbonSpreadBottom;
                    approachRate = height / m_height;
                    approachLevel = m_ribbonSpreadBottom + m_a1 * spread + m_a2 * trend;
                } else {
                    float trend = m_xxx - m_ribbonSpreadBottom;
                    gainLevel = m_ribbonSpreadTop - m_b1 * spread + m_b2 * trend;
                    height = m_ribbonSpreadTop - m_xxx;
                    approachRate = height / m_height;
                    approachLevel = m_ribbonSpreadTop - m_a1 * spread + m_a2 * trend;
                }
                if (approachRate > 0) {
                    m_level = approachLevel * approachRate + gainLevel * (1 - approachRate);
                } else {
                    m_level = gainLevel;
                }
                if (m_da != null) {
                    m_adj = m_da.update(m_ribbonSpreadTop, m_level, m_ribbonSpreadBottom, leadEmaValue);
                }

//            m_adj = goUp ? 1f : -1f;

//                m_trend = goUp
//                        ? m_xxx + trend
//                        : m_xxx - trend;
//
//                m_top = m_ribbonSpreadTop - trend;
//                m_bottom = m_ribbonSpreadBottom + trend;
//
//                float height = goUp
//                        ? m_xxx - m_ribbonSpreadBottom
//                        : m_ribbonSpreadTop - m_xxx;


//                m_mirror = goUp
//                        ? m_xxx - trend
//                        : m_xxx + trend;
//                m_reverse = goUp
//                        ? m_ribbonSpreadBottom + trend
//                        : m_ribbonSpreadTop - trend;

//                float adj = goUp
//                        ? (leadEmaValue - m_ribbonSpreadBottom) / (m_trend - m_ribbonSpreadBottom)
//                        : (leadEmaValue - m_trend) / (m_ribbonSpreadTop - m_trend);
//                if (adj > 1) {
//                    adj = 1;
//                } else if (adj < 0) {
//                    adj = 0;
//                }
//                m_adj = adj * 2 - 1;

//                float mid = m_trend;
//                if (goUp) {
//                    if (m_trend < m_ribbonSpreadBottom) {
//                        float diff = m_ribbonSpreadBottom - m_trend;
//                        mid = m_ribbonSpreadBottom + diff * m_signal;
//                    }
//                    float bottom = Math.max(m_ribbonSpreadBottom, m_mirror);
//                    m_adj2 = m_da.update(m_ribbonSpreadTop, mid, bottom, leadEmaValue);
//                } else {
//                    if (m_trend > m_ribbonSpreadTop) {
//                        float diff = m_trend - m_ribbonSpreadTop;
//                        mid = m_ribbonSpreadTop - diff * m_signal;
//                    }
//                    float top = Math.min(m_ribbonSpreadTop, m_mirror);
//                    m_adj2 = m_da.update(top, mid, m_ribbonSpreadBottom, leadEmaValue);
//                }
//                m_mid = mid;
            }

//            m_tick = new TickData(getParent().getLatestTick().getTimestamp(), ribbonSpread);

        }
        m_dirty = false;
        return m_adj;
    }

    TimesSeriesData<TickData> getMinTs() { return new JoinNonChangedInnerTimesSeriesData(this) { @Override protected Float getValue() { return m_min; } }; }
    TimesSeriesData<TickData> getMaxTs() { return new JoinNonChangedInnerTimesSeriesData(this) { @Override protected Float getValue() { return m_max; } }; }

    TimesSeriesData<TickData> getRibbonSpreadMaxTopTs() { return new JoinNonChangedInnerTimesSeriesData(this) { @Override protected Float getValue() { return m_ribbonSpreadTop; } }; }
    TimesSeriesData<TickData> getRibbonSpreadMaxBottomTs() { return new JoinNonChangedInnerTimesSeriesData(this) { @Override protected Float getValue() { return m_ribbonSpreadBottom; } }; }
    TimesSeriesData<TickData> getXxxTs() { return new JoinNonChangedInnerTimesSeriesData(this) { @Override protected Float getValue() { return m_xxx; } }; }
    TimesSeriesData<TickData> getTrendTs() { return new JoinNonChangedInnerTimesSeriesData(this) { @Override protected Float getValue() { return m_trend; } }; }
    //    TimesSeriesData<TickData> getMirrorTs() { return new JoinNonChangedInnerTimesSeriesData(this) { @Override protected Float getValue() { return m_mirror; } }; }
//    TimesSeriesData<TickData> getReverseTs() { return new JoinNonChangedInnerTimesSeriesData(this) { @Override protected Float getValue() { return m_reverse; } }; }
    TimesSeriesData<TickData> getTopTs() { return new JoinNonChangedInnerTimesSeriesData(this) { @Override protected Float getValue() { return m_top; } }; }
    TimesSeriesData<TickData> getBottomTs() { return new JoinNonChangedInnerTimesSeriesData(this) { @Override protected Float getValue() { return m_bottom; } }; }
    TimesSeriesData<TickData> getLevelTs() { return new JoinNonChangedInnerTimesSeriesData(this) { @Override protected Float getValue() { return m_level; } }; }


    @Override public String key(boolean detailed) {
        return  ""
                + (detailed ? ",start=" : ",") + m_start
                + (detailed ? ",step=" : ",") + m_step
                + (detailed ? ",count=" : ",") + m_count
                + (detailed ? ",multiplier=" : ",") + m_multiplier
//                + (detailed ? ",threshold=" : ",") + m_threshold
//                + (detailed ? ",reverse=" : ",") + m_reverse
//                + (detailed ? ",signal=" : ",") + m_signal
                + (detailed ? ",minOrderMul=" : ",") + m_minOrderMul
//                /*+ ", " + Utils.millisToYDHMSStr(period)*/;
                ;
    }


    @Override public void setupChart(boolean collectValues, ChartCanvas chartCanvas, TimesSeriesData ticksTs, Watcher firstWatcher) {
        ChartData chartData = chartCanvas.getChartData();
        ChartSetting chartSetting = chartCanvas.getChartSetting();

        // layout
        ChartAreaSettings top = chartSetting.addChartAreaSettings("top", 0, 0, 1, 0.6f, Color.RED);
        List<ChartAreaLayerSettings> topLayers = top.getLayers();
        {
            addChart(chartData, ticksTs, topLayers, "price", Colors.alpha(Color.RED, 70), TickPainter.TICK);

//            chartData.setTicksData("spline", new NoTicksData());
////            topLayers.add(new ChartAreaLayerSettings("spline", Color.RED, new ChartAreaPainter.SplineChartAreaPainter(ticksTs, 4)));
//            topLayers.add(new ChartAreaLayerSettings("spline", Color.RED, new ChartAreaPainter.PolynomChartAreaPainter(ticksTs)));

//            Color emaColor = Colors.alpha(Color.BLUE, 25);
//            int size = m_emas.size();
//            for (int i = size - 1; i > 0; i--) {
//                BaseTimesSeriesData ema = m_emas.get(i);
//                Color color = (i == size - 1) ? Colors.alpha(Color.GRAY, 50) : emaColor;
//                addChart(chartData, ema.getJoinNonChangedTs(), topLayers, "ema" + i, color, TickPainter.LINE);
//            }

//            addChart(chartData, getMinTs(), topLayers, "min", Color.MAGENTA, TickPainter.LINE);
//            addChart(chartData, getMaxTs(), topLayers, "max", Color.MAGENTA, TickPainter.LINE);
////
            addChart(chartData, getRibbonSpreadMaxTopTs(), topLayers, "maxTop", Color.CYAN, TickPainter.LINE);
            addChart(chartData, getRibbonSpreadMaxBottomTs(), topLayers, "maxBottom", Color.CYAN, TickPainter.LINE);
            addChart(chartData, getXxxTs(), topLayers, "xxx", Color.GRAY, TickPainter.LINE);
////            addChart(chartData, getTrendTs(), topLayers, "trend", Color.GRAY, TickPainter.LINE);
////            addChart(chartData, getMirrorTs(), topLayers, "mirror", Colors.DARK_RED, TickPainter.LINE);
////            addChart(chartData, getReverseTs(), topLayers, "reverse", Colors.DARK_GREEN, TickPainter.LINE);
//            addChart(chartData, getTopTs(), topLayers, "top", Colors.DARK_RED, TickPainter.LINE);
//            addChart(chartData, getBottomTs(), topLayers, "bottom", Colors.DARK_GREEN, TickPainter.LINE);
            addChart(chartData, getLevelTs(), topLayers, "level", Color.PINK, TickPainter.LINE);
////            addChart(chartData, m_minMaxSpread.getMidTs(), topLayers, "mid", Color.PINK, TickPainter.LINE);

            BaseTimesSeriesData leadEma = m_emas.get(0);
            Color color = Color.GREEN;
            addChart(chartData, leadEma.getJoinNonChangedTs(), topLayers, "leadEma" , color, TickPainter.LINE);
        }

        ChartAreaSettings value = chartSetting.addChartAreaSettings("value", 0, 0.6f, 1, 0.2f, Color.LIGHT_GRAY);
        List<ChartAreaLayerSettings> valueLayers = value.getLayers();
        {
// ???
//            addChart(chartData, getTS(true), valueLayers, "value", Color.blue, TickPainter.LINE);
            addChart(chartData, getJoinNonChangedTs(), valueLayers, "value", Color.blue, TickPainter.LINE);
//            addChart(chartData, m_minMaxSpread.getAdj2Ts(), valueLayers, "adj2", Color.MAGENTA, TickPainter.LINE);
//            addChart(chartData, getPowAdjTs(), valueLayers, "powValue", Color.MAGENTA, TickPainter.LINE);
//            addChart(chartData, m_velocityAdj.getJoinNonChangedTs(), valueLayers, "velAdj", Color.RED, TickPainter.LINE);
        }

        if (collectValues) {
            ChartAreaSettings gain = chartSetting.addChartAreaSettings("gain", 0, 0.8f, 1, 0.2f, Color.ORANGE);
            gain.setHorizontalLineValue(1);

            addChart(chartData, firstWatcher, topLayers, "trades", Color.WHITE, TickPainter.TRADE);

            List<ChartAreaLayerSettings> gainLayers = gain.getLayers();
            addChart(chartData, firstWatcher.getGainTs(), gainLayers, "gain", Color.blue, TickPainter.LINE);
        }
    }

}
