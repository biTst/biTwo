package bi.two.algo.impl;

import bi.two.ChartCanvas;
import bi.two.Colors;
import bi.two.algo.BaseAlgo;
import bi.two.algo.Watcher;
import bi.two.calc.SlidingTicksRegressor;
import bi.two.chart.*;
import bi.two.opt.Vary;
import bi.two.ts.BaseTicksTimesSeriesData;
import bi.two.ts.BaseTimesSeriesData;
import bi.two.ts.ITimesSeriesData;
import bi.two.ts.TicksTimesSeriesData;
import bi.two.util.MapConfig;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class UmmarAlgo extends BaseBarSizeAlgo {
    private final float m_start;
    private final float m_step;
    private final float m_count;
    private final float m_multiplier;
    private final float m_threshold;
    private final float m_signal;
    private final List<BaseTimesSeriesData> m_emas = new ArrayList<>();
    private final MinMaxSpread m_minMaxSpread;
    private TickData m_tickData;

    public UmmarAlgo(MapConfig algoConfig, ITimesSeriesData tsd) {
        super(null, algoConfig);

        boolean collectValues = algoConfig.getBoolean(BaseAlgo.COLLECT_VALUES_KEY);
        m_start = algoConfig.getNumber(Vary.start).floatValue();
        m_step = algoConfig.getNumber(Vary.step).floatValue();
        m_count = algoConfig.getNumber(Vary.count).floatValue();
        m_multiplier = algoConfig.getNumber(Vary.multiplier).floatValue();
        m_threshold = algoConfig.getNumber(Vary.threshold).floatValue();
        m_signal = algoConfig.getNumber(Vary.signal).floatValue();

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
//                Float adj = m_minMaxSpread.m_adj;
                Float adj = m_minMaxSpread.m_adj2;
                if (adj != null) {
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

    @Override public TickData getLatestTick() {
        return m_tickData;
    }

    @Override public String key(boolean detailed) {
        return  ""
                + (detailed ? ",start=" : ",") + m_start
                + (detailed ? ",step=" : ",") + m_step
                + (detailed ? ",count=" : ",") + m_count
                + (detailed ? ",multiplier=" : ",") + m_multiplier
                + (detailed ? ",threshold=" : ",") + m_threshold
                + (detailed ? ",signal=" : ",") + m_signal
                + (detailed ? ",minOrderMul=" : ",") + m_minOrderMul
//                /*+ ", " + Utils.millisToYDHMSStr(period)*/;
                ;
    }

    @Override public void setupChart(boolean collectValues, ChartCanvas chartCanvas, BaseTicksTimesSeriesData<TickData> ticksTs, Watcher firstWatcher) {
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
            addChart(chartData, m_minMaxSpread.getMirrorTs(), topLayers, "mirror", Colors.DARK_RED, TickPainter.LINE);
            addChart(chartData, m_minMaxSpread.getReverseTs(), topLayers, "reverse", Colors.DARK_GREEN, TickPainter.LINE);
            addChart(chartData, m_minMaxSpread.getMidTs(), topLayers, "mid", Color.PINK, TickPainter.LINE);

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
            addChart(chartData, m_minMaxSpread.getAdj2Ts(), valueLayers, "adj2", Color.MAGENTA, TickPainter.LINE);
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
        private Float m_mirror;
        private Float m_reverse;
        private Float m_mid;
        private DoubleAdjuster m_da;
        private Float m_adj;
        private Float m_adj2;

        MinMaxSpread(List<ITimesSeriesData> emas, ITimesSeriesData baseTsd, boolean collectValues) {
            super(null);
            m_emas = emas;
            m_emasLen = emas.size();

            for (ITimesSeriesData<ITickData> next : emas) {
                next.getActive().addListener(this);
            }

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
                float emasMin = Float.POSITIVE_INFINITY;
                float emasMax = Float.NEGATIVE_INFINITY;
                boolean allDone = true;
                float leadEmaValue = 0;
                for (int i = 0; i < m_emasLen; i++) {
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

                    m_min = emasMin;
                    m_max = emasMax;

                    float ribbonSpread = emasMax - emasMin;

                    m_ribbonSpread = directionChanged // direction changed
                            ? ribbonSpread //reset
                            : Math.max(ribbonSpread, m_ribbonSpread);
                    m_ribbonSpreadTop = goUp ? emasMin + m_ribbonSpread : emasMax;
                    m_ribbonSpreadBottom = goUp ? emasMin : emasMax - m_ribbonSpread;

                    if (directionChanged) {
                        m_xxx = goUp ? emasMax : emasMin;
                        m_da = new DoubleAdjuster(goUp ? 1 : -1);
                    }

                    if (m_xxx != null) {
                        float trend = goUp
                                ? (m_ribbonSpreadTop - m_xxx) * m_threshold
                                : (m_xxx - m_ribbonSpreadBottom) * m_threshold;
                        m_trend = goUp
                                ? m_xxx + trend
                                : m_xxx - trend;
                        m_mirror = goUp
                                ? m_xxx - trend
                                : m_xxx + trend;
                        m_reverse = goUp
                                ? m_ribbonSpreadBottom + trend
                                : m_ribbonSpreadTop - trend;

                        float adj = goUp
                                ? (leadEmaValue - m_ribbonSpreadBottom) / (m_trend - m_ribbonSpreadBottom)
                                : (leadEmaValue - m_trend) / (m_ribbonSpreadTop - m_trend);
                        if (adj > 1) {
                            adj = 1;
                        } else if (adj < 0) {
                            adj = 0;
                        }
                        m_adj = adj * 2 - 1;

                        float mid = m_trend;
                        if (goUp) {
                            if (m_trend < m_ribbonSpreadBottom) {
                                float diff = m_ribbonSpreadBottom - m_trend;
                                mid = m_ribbonSpreadBottom + diff * m_signal;
                            }
                            float bottom = Math.max(m_ribbonSpreadBottom, m_mirror);
                            m_adj2 = m_da.update(m_ribbonSpreadTop, mid, bottom, leadEmaValue);
                        } else {
                            if (m_trend > m_ribbonSpreadTop) {
                                float diff = m_trend - m_ribbonSpreadTop;
                                mid = m_ribbonSpreadTop - diff * m_signal;
                            }
                            float top = Math.min(m_ribbonSpreadTop, m_mirror);
                            m_adj2 = m_da.update(top, mid, m_ribbonSpreadBottom, leadEmaValue);
                        }
                        m_mid = mid;
                    }

                    m_tick = new TickData(getParent().getLatestTick().getTimestamp(), ribbonSpread);
                    m_dirty = false;
                }
            }
            return m_tick;
        }

        TicksTimesSeriesData<TickData> getMinTs() { return new JoinNonChangedInnerTimesSeriesData(this) { @Override protected Float getValue() { return m_min; } }; }
        TicksTimesSeriesData<TickData> getMaxTs() { return new JoinNonChangedInnerTimesSeriesData(this) { @Override protected Float getValue() { return m_max; } }; }

        TicksTimesSeriesData<TickData> getRibbonSpreadMaxTopTs() { return new JoinNonChangedInnerTimesSeriesData(this) { @Override protected Float getValue() { return m_ribbonSpreadTop; } }; }
        TicksTimesSeriesData<TickData> getRibbonSpreadMaxBottomTs() { return new JoinNonChangedInnerTimesSeriesData(this) { @Override protected Float getValue() { return m_ribbonSpreadBottom; } }; }
        TicksTimesSeriesData<TickData> getXxxTs() { return new JoinNonChangedInnerTimesSeriesData(this) { @Override protected Float getValue() { return m_xxx; } }; }
        TicksTimesSeriesData<TickData> getTrendTs() { return new JoinNonChangedInnerTimesSeriesData(this) { @Override protected Float getValue() { return m_trend; } }; }
        TicksTimesSeriesData<TickData> getMirrorTs() { return new JoinNonChangedInnerTimesSeriesData(this) { @Override protected Float getValue() { return m_mirror; } }; }
        TicksTimesSeriesData<TickData> getReverseTs() { return new JoinNonChangedInnerTimesSeriesData(this) { @Override protected Float getValue() { return m_reverse; } }; }
        TicksTimesSeriesData<TickData> getMidTs() { return new JoinNonChangedInnerTimesSeriesData(this) { @Override protected Float getValue() { return m_mid; } }; }
        TicksTimesSeriesData<TickData> getAdj2Ts() { return new JoinNonChangedInnerTimesSeriesData(this) { @Override protected Float getValue() { return m_adj2; } }; }
    }

    //----------------------------------------------------------
    public static class DoubleAdjuster {
        public float m_init;
        public float m_value;
        private boolean m_up;
        private boolean m_justTurned;

        public DoubleAdjuster(float value) {
            m_init = value;
            m_value = value;
            m_up = (value > 0);
        }

        public float update(float top, float mid, float bottom, float lead) {
            if (m_up && (lead < mid)) {
                if (m_justTurned) {
                    float dif = mid - bottom;
                    float adj = (lead - bottom) / dif; // [0...1]
                    if (adj == 0.5) {
                        m_justTurned = false;
                        return m_value; // ignore first broken tick
                    }
                }
                m_up = false;
                m_init = m_value;
            }
            if (!m_up && (lead > mid)) {
                if (m_justTurned) {
                    float dif = top - mid;
                    float adj = (lead - mid) / dif; // [0...1]
                    if (adj == 0.5) {
                        m_justTurned = false;
                        return m_value; // ignore first broken tick
                    }
                }
                m_up = true;
                m_init = m_value;
            }
            m_justTurned = false;

            if (m_up) {
                float dif = top - mid;
                if (dif > 0) {
                    float adj = (lead - mid) / dif; // [0...1]
                    float val = m_init + (1 - m_init) * adj;
                    if (val > m_value) {
                        if (val > 1) {
                            val = 1;
                        }
                        m_value = val;
                    }
                } else if(dif == 0) {
                    if( m_init == m_value) {
                        m_justTurned = true;
                    }
                }
            } else {
                float dif = mid - bottom;
                if (dif > 0) {
                    float adj = (lead - bottom) / dif; // [0...1]
                    float val = (m_init + 1) * adj - 1;
                    if (val < m_value) {
                        if (val < -1) {
                            val = -1;
                        }
                        m_value = val;
                    }
                } else if(dif == 0) {
                    if( m_init == m_value) {
                        m_justTurned = true;
                    }
                }
            }
            return m_value;
        }
    }
}
