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

public class UmmarAlgo extends BaseAlgo {

    private final double m_minOrderMul;

    private final long m_barSize;
    private final float m_start;
    private final float m_step;
    private final float m_count;
    private final float m_multiplier;
    private final float m_threshold;
    private final List<BaseTimesSeriesData> m_emas = new ArrayList<>();
    private final MinMaxSpread m_minMaxSpread;
    private ITickData m_tickData;

    public UmmarAlgo(MapConfig config, ITimesSeriesData tsd) {
        super(null);

        m_minOrderMul = config.getNumber(Vary.minOrderMul).floatValue();

        boolean collectValues = config.getBoolean(BaseAlgo.COLLECT_VALUES_KEY);
        m_barSize = config.getNumber(Vary.period).longValue();
        m_start = config.getNumber(Vary.start).floatValue();
        m_step = config.getNumber(Vary.step).floatValue();
        m_count = config.getNumber(Vary.count).floatValue();
        m_multiplier = config.getNumber(Vary.multiplier).floatValue();
        m_threshold = config.getNumber(Vary.threshold).floatValue();

        // create ribbon
        List<ITimesSeriesData> iEmas = new ArrayList<>(); // as list of ITimesSeriesData
        float length = m_start;
        int countFloor = (int) m_count;
        for (int i = 0; i < countFloor; i++) {
            BaseTimesSeriesData ema = getOrCreateEma(tsd, m_barSize, length, collectValues);
            m_emas.add(ema);
            iEmas.add(ema);
            length += m_step;
        }
        if (m_count != countFloor) {
            float fraction = m_count - countFloor;
            float fractionLength = length - m_step + m_step * fraction;
            BaseTimesSeriesData ema = getOrCreateEma(tsd, m_barSize, fractionLength, collectValues);
            m_emas.add(ema);
            iEmas.add(ema);
        }

        m_minMaxSpread = new MinMaxSpread(iEmas, tsd, collectValues);

        setParent(m_emas.get(0));
    }

    private BaseTimesSeriesData getOrCreateEma(ITimesSeriesData tsd, long barSize, float length, boolean collectValues) {
        long period = (long) (length * barSize * m_multiplier);
        return new SlidingTicksRegressor(tsd, period, collectValues);
//        return new BarsEMA(tsd, length, barSize);
//        return new BarsDEMA(tsd, length, barSize);
//        return new BarsTEMA(tsd, length, barSize);
    }

    @Override public ITickData getAdjusted() {
        ITickData parentLatestTick = getParent().getLatestTick();
        if (parentLatestTick != null) {
            ITickData latestTick = m_minMaxSpread.getLatestTick();// make sure calculation is up-to-date
            if (latestTick != null) {
                Float adj = m_minMaxSpread.m_adj;
                if(adj != null) {
                    long timestamp = parentLatestTick.getTimestamp();
                    m_tickData = new TickData(timestamp, adj);
                    return m_tickData;
                }
                // else - not ready yet
            }
            // else - not ready yet
        }
        // else - not ready yet
        return null;
    }

    @Override public ITickData getLatestTick() {
        return m_tickData;
    }

    @Override public String key(boolean detailed) {
        return  ""
                + (detailed ? ",start=" : ",") + m_start
                + (detailed ? ",step=" : ",") + m_step
                + (detailed ? ",count=" : ",") + m_count
                + (detailed ? ",multiplier=" : ",") + m_multiplier
                + (detailed ? ",threshold=" : ",") + m_threshold
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

            addChart(chartData, m_minMaxSpread.getMinTs(), topLayers, "min", Color.MAGENTA, TickPainter.LINE);
            addChart(chartData, m_minMaxSpread.getMaxTs(), topLayers, "max", Color.MAGENTA, TickPainter.LINE);
//            addChart(chartData, m_minMaxSpread.getMidTs(), topLayers, "mid", Color.MAGENTA, TickPainter.LINE);

            addChart(chartData, m_minMaxSpread.getRibbonSpreadMaxTopTs(), topLayers, "maxTop", Color.CYAN, TickPainter.LINE);
            addChart(chartData, m_minMaxSpread.getRibbonSpreadMaxBottomTs(), topLayers, "maxBottom", Color.CYAN, TickPainter.LINE);
            addChart(chartData, m_minMaxSpread.getXxxTs(), topLayers, "xxx", Color.GRAY, TickPainter.LINE);
            addChart(chartData, m_minMaxSpread.getTrendTs(), topLayers, "trend", Color.GRAY, TickPainter.LINE);
            addChart(chartData, m_minMaxSpread.getReverseTs(), topLayers, "reverse", Colors.DARK_RED, TickPainter.LINE);
            addChart(chartData, m_minMaxSpread.getMirrorTs(), topLayers, "mirror", Colors.DARK_GREEN, TickPainter.LINE);

            BaseTimesSeriesData leadEma = m_emas.get(0);
            Color color = Color.GREEN;
            addChart(chartData, leadEma.getJoinNonChangedTs(), topLayers, "leadEma" , color, TickPainter.LINE);
        }

//        ChartAreaSettings bottom = chartSetting.addChartAreaSettings("velocity", 0, 0.4f, 1, 0.2f, Color.GREEN);
//        List<ChartAreaLayerSettings> bottomLayers = bottom.getLayers();
//        {
////            addChart(chartData, m_minMaxSpread.getJoinNonChangedTs(), bottomLayers, "spread", Color.MAGENTA, TickPainter.LINE);
////            addChart(chartData, m_minMaxSpread.getRibbonSpreadMaxTs(), bottomLayers, "spreadMax", Color.green, TickPainter.LINE);
////            addChart(chartData, m_minMaxSpread.getRibbonSpreadFadingTs(), bottomLayers, "spreadFade", Color.blue, TickPainter.LINE);
////            addChart(chartData, m_spreadSmoothed.getJoinNonChangedTs(), bottomLayers, "spreadSmoothed", Color.yellow, TickPainter.LINE);
//
////            Color velColor = Colors.alpha(Color.yellow, 10);
////            List<BaseTimesSeriesData> m_tss = m_velocityAvg.m_tss;
////            for (int i = 0; i < m_tss.size(); i++) {
////                BaseTimesSeriesData tss = m_tss.get(i);
////                addChart(chartData, tss.getJoinNonChangedTs(), bottomLayers, "minVel_" + i, velColor, TickPainter.LINE);
////            }
//            addChart(chartData, m_velocityAvg.getJoinNonChangedTs(), bottomLayers, "minVelAvg", Color.MAGENTA, TickPainter.LINE);
//            addChart(chartData, m_velocityAdjRegr.getJoinNonChangedTs(), bottomLayers, "minVelAvgRegr", Color.orange, TickPainter.LINE);
//
//            addChart(chartData, m_velocityAdj.getMinTs(), bottomLayers, "vel_min", Color.PINK, TickPainter.LINE);
//            addChart(chartData, m_velocityAdj.getMaxTs(), bottomLayers, "vel_max", Color.PINK, TickPainter.LINE);
//        }
//
        ChartAreaSettings value = chartSetting.addChartAreaSettings("value", 0, 0.6f, 1, 0.2f, Color.LIGHT_GRAY);
        List<ChartAreaLayerSettings> valueLayers = value.getLayers();
        {
// ???
//            addChart(chartData, getTS(true), valueLayers, "value", Color.blue, TickPainter.LINE);
            addChart(chartData, getJoinNonChangedTs(), valueLayers, "value", Color.blue, TickPainter.LINE);
//            addChart(chartData, getPowAdjTs(), valueLayers, "powValue", Color.MAGENTA, TickPainter.LINE);
//            addChart(chartData, m_velocityAdj.getJoinNonChangedTs(), valueLayers, "velAdj", Color.RED, TickPainter.LINE);
        }
//
        if (collectValues) {
            ChartAreaSettings gain = chartSetting.addChartAreaSettings("gain", 0, 0.8f, 1, 0.2f, Color.ORANGE);
            gain.setHorizontalLineValue(1);

            addChart(chartData, firstWatcher, topLayers, "trades", Color.WHITE, TickPainter.TRADE);

            List<ChartAreaLayerSettings> gainLayers = gain.getLayers();
            addChart(chartData, firstWatcher.getGainTs(), gainLayers, "gain", Color.blue, TickPainter.LINE);
        }
    }


    //----------------------------------------------------------
    private class MinMaxSpread extends BaseTimesSeriesData<ITickData> {
        private final List<ITimesSeriesData> m_emas;
        private final int m_emasLen;
        private boolean m_dirty;
        private ITickData m_tick;
        private boolean m_goUp;
        private float m_min;
        private float m_max;
        private float m_ribbonSpread;
        private float m_ribbonSpreadTop;
        private float m_ribbonSpreadBottom;
        private Float m_xxx;
        private Float m_trend;
        private Float m_reverse;
        private Float m_mirror;
        private Float m_adj;

        MinMaxSpread(List<ITimesSeriesData> emas, ITimesSeriesData baseTsd, boolean collectValues) {
            super(null);
            m_emas = emas;
            m_emasLen = emas.size();

            for (ITimesSeriesData<ITickData> next : emas) {
                next.getActive().addListener(this);
            }

//            if (collectValues) {
//                m_midTs = new BaseTimesSeriesData(this) {
//                    @Override public ITickData getLatestTick() {
//                        ITickData latestTick = getParent().getLatestTick();
//                        if (latestTick != null) {
//                            return new TickData(latestTick.getTimestamp(), m_mid);
//                        }
//                        return null;
//                    }
//                };
//            }

//            m_ribbonSpreadFadingMidTs = new BaseTimesSeriesData(this) {
//                @Override public ITickData getLatestTick() {
//                    ITickData latestTick = getParent().getLatestTick();
//                    if (latestTick != null) {
//                        return new TickData(latestTick.getTimestamp(), m_ribbonSpreadFadingMid);
//                    }
//                    return null;
//                }
//            };

            setParent(baseTsd); // subscribe to list first - will be called onChanged() and set as dirty ONLY
        }

        @Override public void onChanged(ITimesSeriesData ts, boolean changed) {
            if (ts == getParent()) {
                super.onChanged(ts, changed); // forward onChanged() only for parent ds. other should only reset changed state
            } else {
                if (changed) {
                    m_dirty = true;
                }
            }
        }

        @Override public ITickData getLatestTick() {
            if (m_dirty) {
                float min = Float.POSITIVE_INFINITY;
                float max = Float.NEGATIVE_INFINITY;
                boolean allDone = true;
                float leadEmaValue = 0;
                for (int i = 0; i < m_emasLen; i++) {
                    ITimesSeriesData ema = m_emas.get(i);
                    ITickData lastTick = ema.getLatestTick();
                    if (lastTick != null) {
                        float value = lastTick.getClosePrice();
                        min = Math.min(min, value);
                        max = Math.max(max, value);
                        if (i == 0) {
                            leadEmaValue = value;
                        }
                    } else {
                        allDone = false;
                        break; // not ready yet
                    }
                }
                if (allDone) {
                    boolean goUp = (leadEmaValue == max)
                                    ? true // go up
                                    : ((leadEmaValue == min)
                                        ? false // go down
                                        : m_goUp); // do not change
                    boolean directionChanged = (goUp != m_goUp);
                    m_goUp = goUp;

                    m_min = min;
                    m_max = max;

                    float ribbonSpread = max - min;

                    m_ribbonSpread = directionChanged // direction changed
                            ? ribbonSpread //reset
                            : Math.max(ribbonSpread, m_ribbonSpread);
                    m_ribbonSpreadTop = goUp ? min + m_ribbonSpread : max;
                    m_ribbonSpreadBottom = goUp ? min : max - m_ribbonSpread;

                    if (directionChanged) {
                        m_xxx = goUp ? max : min;
                    }

                    if (m_xxx != null) {
                        float trend = goUp
                                ? (m_ribbonSpreadTop - m_xxx) * m_threshold
                                : (m_xxx - m_ribbonSpreadBottom) * m_threshold;
//                        m_trend = goUp
//                                ? m_ribbonSpreadTop - trend
//                                : m_ribbonSpreadBottom + trend;
                        m_trend = goUp
                                ? m_xxx + trend
                                : m_xxx - trend;

                        if (goUp) {
                            if (m_trend < m_ribbonSpreadBottom) {
                                float diff = m_ribbonSpreadBottom - m_trend;
                                m_trend = m_ribbonSpreadBottom + diff;
                            }
                        } else {
                            if (m_trend > m_ribbonSpreadTop) {
                                float diff = m_trend - m_ribbonSpreadTop;
                                m_trend = m_ribbonSpreadTop - diff;
                            }
                        }

                        m_mirror = goUp
                                    ? m_ribbonSpreadBottom + trend
                                    : m_ribbonSpreadTop - trend;

//                        m_mirror = goUp
//                                ? (m_ribbonSpreadBottom < m_xxx)
//                                    ? m_ribbonSpreadBottom + trend
//                                    : m_xxx
//                                : (m_ribbonSpreadTop > m_xxx)
//                                    ? m_ribbonSpreadTop - trend
//                                    : m_xxx;

                        m_reverse = goUp
                                ? m_xxx - trend
                                : m_xxx + trend;

                        float adj = goUp
                                ? (leadEmaValue - m_ribbonSpreadBottom) / (m_trend - m_ribbonSpreadBottom)
                                : (leadEmaValue - m_trend) / (m_ribbonSpreadTop - m_trend);
                        if (adj > 1) {
                            adj = 1;
                        } else if (adj < 0) {
                            adj = 0;
                        }
                        m_adj = adj * 2 - 1;
                    }

                    m_tick = new TickData(getParent().getLatestTick().getTimestamp(), ribbonSpread);
                    m_dirty = false;
                }
            }
            return m_tick;
        }

        TimesSeriesData<TickData> getMinTs() { return new JoinNonChangedInnerTimesSeriesData(this) { @Override protected Float getValue() { return m_min; } }; }
        TimesSeriesData<TickData> getMaxTs() { return new JoinNonChangedInnerTimesSeriesData(this) { @Override protected Float getValue() { return m_max; } }; }

        TimesSeriesData<TickData> getRibbonSpreadMaxTopTs() { return new JoinNonChangedInnerTimesSeriesData(this) { @Override protected Float getValue() { return m_ribbonSpreadTop; } }; }
        TimesSeriesData<TickData> getRibbonSpreadMaxBottomTs() { return new JoinNonChangedInnerTimesSeriesData(this) { @Override protected Float getValue() { return m_ribbonSpreadBottom; } }; }
        TimesSeriesData<TickData> getXxxTs() { return new JoinNonChangedInnerTimesSeriesData(this) { @Override protected Float getValue() { return m_xxx; } }; }
        TimesSeriesData<TickData> getTrendTs() { return new JoinNonChangedInnerTimesSeriesData(this) { @Override protected Float getValue() { return m_trend; } }; }
        TimesSeriesData<TickData> getReverseTs() { return new JoinNonChangedInnerTimesSeriesData(this) { @Override protected Float getValue() { return m_reverse; } }; }
        TimesSeriesData<TickData> getMirrorTs() { return new JoinNonChangedInnerTimesSeriesData(this) { @Override protected Float getValue() { return m_mirror; } }; }
    }
}
