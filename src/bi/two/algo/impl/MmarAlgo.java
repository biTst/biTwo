package bi.two.algo.impl;

import bi.two.ChartCanvas;
import bi.two.Colors;
import bi.two.algo.BaseAlgo;
import bi.two.algo.Watcher;
import bi.two.calc.*;
import bi.two.chart.*;
import bi.two.opt.Vary;
import bi.two.ts.BaseTimesSeriesData;
import bi.two.ts.ITimesSeriesData;
import bi.two.ts.TimesSeriesData;
import bi.two.util.MapConfig;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MmarAlgo extends BaseAlgo {
    private static final boolean PINCH = true; // pinch by spread / spreadSmoothed
    private static final boolean FAST_RIBBON = true;

    private static final Map<String,BaseTimesSeriesData> s_emaCache = new HashMap<>();
    private static int s_emaCacheHit;
    private static int s_emaCacheMiss;

    private final long m_barSize;
    private final float m_start;
    private final float m_step;
    private final float m_count;
    private final float m_drop;
    private final float m_smooth;
    private final float m_power;
    private final TicksSMA m_spreadSmoothed;
    private final Level m_mainLevel;
    private TicksSMA m_barSpreadSmoothed;
    private MinMaxSmoothed m_minMaxSmoothed;
    private TickData m_tickData; // latest tick
    private Float m_ribbonDirection;
    private float m_spreadRate;

    public MmarAlgo(MapConfig config, ITimesSeriesData tsd) {
        super(null);

        boolean collectValues = config.getBoolean(BaseAlgo.COLLECT_VALUES_KEY);
        m_barSize = config.getNumber(Vary.period).longValue();
        m_start = config.getNumber(Vary.start).floatValue();
        m_step = config.getNumber(Vary.step).floatValue();
        m_count = config.getNumber(Vary.count).floatValue();
        m_drop = config.getNumber(Vary.drop).floatValue();
        m_smooth = config.getNumber(Vary.smooth).floatValue();
        m_power = config.getNumber(Vary.power).floatValue();

        m_mainLevel = new Level(tsd, m_barSize, m_start, m_step, m_count, collectValues) {
            @Override protected BaseTimesSeriesData createEma(ITimesSeriesData tsd, long barSize, float length) {
                return new SlidingTicksRegressor(tsd, (long) (length * barSize));
//                return new BarsEMA(tsd, length, barSize);
//                return new BarsDEMA(tsd, length, barSize);
//                return new BarsTEMA(tsd, length, barSize);
            }
        };

        m_spreadSmoothed = new TicksSMA(m_mainLevel.m_minMaxSpread, (long) (m_barSize * m_smooth));

        if(collectValues) {
            BarMinMaxSpread barMinMaxSpread = new BarMinMaxSpread(tsd, m_barSize);
            m_barSpreadSmoothed = new TicksSMA(barMinMaxSpread, (long) (m_barSize * 5.0));
        }

        setParent(m_mainLevel.m_emas.get(0));
    }

    public static void resetIterationCache() {
        System.out.println("resetIterationCache: emaCacheHit=" + s_emaCacheHit + "; emaCacheMiss=" + s_emaCacheMiss);
        s_emaCache.clear();
        s_emaCacheHit = 0;
        s_emaCacheMiss = 0;
    }

    @Override public ITickData getAdjusted() {
        ITickData latestTick = getParent().getLatestTick();
        if (latestTick == null) {
            return null;
        }

        List<BaseTimesSeriesData> emas = m_mainLevel.m_emas;

        Float ribbonDirection = FAST_RIBBON ? calcRibbonDirectionFast(emas) : calcRibbonDirectionLong(emas);
        if ((ribbonDirection == null) || ribbonDirection.isNaN() || ribbonDirection.isInfinite()) {
            return null;
        }

        m_ribbonDirection = ribbonDirection;

        float ribbonAdjusted = ((float) (Math.signum(ribbonDirection) * Math.pow(Math.abs(ribbonDirection), 1 / m_power)));

        if (PINCH) {
            float spread = m_mainLevel.m_minMaxSpread.m_spread;
            ITickData spreadSmoothedLatestTick = m_spreadSmoothed.getLatestTick();
            if (spreadSmoothedLatestTick != null) {
                float spreadSmoothed = spreadSmoothedLatestTick.getClosePrice();
                if (spreadSmoothed != 0) {
                    m_spreadRate = spread / spreadSmoothed;
                    if (m_spreadRate < 1) {
                        ribbonAdjusted = ((float) (ribbonAdjusted * Math.pow(m_spreadRate, m_drop)));
                    }
                    if (ribbonAdjusted > 1) {
                        ribbonAdjusted = 1f;
                    } else if (ribbonAdjusted < -1) {
                        ribbonAdjusted = -1f;
                    }
                }
            }
        }

        long timestamp = latestTick.getTimestamp();
        m_tickData = new TickData(timestamp, ribbonAdjusted);
        return m_tickData;
    }

    private Float calcRibbonDirectionLong(List<BaseTimesSeriesData> emas) {
        int len = emas.size();

        for (BaseTimesSeriesData emaSm1 : emas) {
            ITickData latestTick = emaSm1.getLatestTick();
            if (latestTick == null) {
                return null; // check all for null
            }
        }

        int count = 0;
        float direction = 0;
        for (int i = 0; i < len - 1; i++) {
            BaseTimesSeriesData emaSm1 = emas.get(i);
            ITickData latestTick1 = emaSm1.getLatestTick();
            double value1 = latestTick1.getClosePrice();
            for (int j = i + 1; j < len; j++) {
                BaseTimesSeriesData emaSm2 = emas.get(j);
                ITickData latestTick2 = emaSm2.getLatestTick();
                double value2 = latestTick2.getClosePrice();
                direction += (value1 > value2) ? +1 : -1;
                count++;
            }
        }
        float ribbonDirection = direction / count;
        return ribbonDirection;
    }

    private Float calcRibbonDirectionFast(List<BaseTimesSeriesData> emas) {
        double leadValue = 0;
        Double min = Double.POSITIVE_INFINITY;
        Double max = Double.NEGATIVE_INFINITY;
        for (int i = 0, emasSize = emas.size(); i < emasSize; i++) {
            BaseTimesSeriesData ema = emas.get(i);
            ITickData latestTick = ema.getLatestTick();
            if (latestTick == null) {
                return null; // check all for null
            }
            double value = latestTick.getClosePrice();
            min = Math.min(min, value);
            max = Math.max(max, value);
            if (i == 0) {
                leadValue = value;
            }
        }
        return ((float) ((leadValue - min) / (max - min) * 2 - 1));
    }

    private TimesSeriesData<TickData> getRibbonTs() {
        return new JoinNonChangedInnerTimesSeriesData(this) {
            @Override protected Float getValue() {
                return m_ribbonDirection;
            }
        };
    }

    private TimesSeriesData<TickData> getSpreadRateTs() {
        return new JoinNonChangedInnerTimesSeriesData(this) {
            @Override protected Float getValue() {
                return m_spreadRate;
            }
        };
    }

    @Override public ITickData getLatestTick() {
        return m_tickData;
    }

    @Override public String key(boolean detailed) {
        return  ""
                        + (detailed ? ",start=" : ",") + m_start
                        + (detailed ? ",step=" : ",") + m_step
                        + (detailed ? ",count=" : ",") + m_count
                        + (detailed ? ",drop=" : ",") + m_drop
                        + (detailed ? ",smooth=" : ",") + m_smooth
                        + (detailed ? ",power=" : ",") + m_power
//                /*+ ", " + Utils.millisToYDHMSStr(period)*/;
        ;
    }

    @Override public void setupChart(boolean collectValues, ChartCanvas chartCanvas, TimesSeriesData ticksTs, Watcher firstWatcher) {
        ChartData chartData = chartCanvas.getChartData();
        ChartSetting chartSetting = chartCanvas.getChartSetting();

        // layout
        ChartAreaSettings top = chartSetting.addChartAreaSettings("top", 0, 0, 1, 0.4f, Color.RED);
        List<ChartAreaLayerSettings> topLayers = top.getLayers();
        {
            addChart(chartData, ticksTs, topLayers, "price", Colors.alpha(Color.RED, 70), TickPainter.TICK);
            Color emaColor = Colors.alpha(Color.BLUE, 50);
            List<BaseTimesSeriesData> emas = m_mainLevel.m_emas;
            int size = emas.size();
            for (int i = size - 1; i >= 0; i--) {
                BaseTimesSeriesData ema = emas.get(i);
                Color color = (i == 0) ? Color.BLUE : (i == size - 1) ? Colors.alpha(Color.GRAY, 100) : emaColor;
                addChart(chartData, ema.getJoinNonChangedTs(), topLayers, "ema" + i, color, TickPainter.LINE);
            }

            MinMaxSpread minMaxSpread = m_mainLevel.m_minMaxSpread;
            addChart(chartData, minMaxSpread.getMinTs(), topLayers, "min", Color.MAGENTA, TickPainter.LINE);
            addChart(chartData, minMaxSpread.getMaxTs(), topLayers, "max", Color.MAGENTA, TickPainter.LINE);
            addChart(chartData, minMaxSpread.getMidTs(), topLayers, "mid", Color.MAGENTA, TickPainter.LINE);
            addChart(chartData, minMaxSpread.getMid2Ts(), topLayers, "mid2", Color.WHITE, TickPainter.LINE);

            m_minMaxSmoothed = new MinMaxSmoothed(m_mainLevel, m_spreadSmoothed, m_barSpreadSmoothed);
            addChart(chartData, m_minMaxSmoothed.getMinRibbonSmoothedTs(), topLayers, "minRiSm", Color.ORANGE, TickPainter.LINE);
            addChart(chartData, m_minMaxSmoothed.getMaxRibbonSmoothedTs(), topLayers, "maxRiSm", Color.ORANGE, TickPainter.LINE);
            addChart(chartData, m_minMaxSmoothed.getMinBarSpreadSmoothedTs(), topLayers, "minBsSm", Color.green, TickPainter.LINE);
            addChart(chartData, m_minMaxSmoothed.getMaxBarSpreadSmoothedTs(), topLayers, "maxBsSm", Color.green, TickPainter.LINE);
//            addChart(chartData, m_minMaxSmoothed.getMidSmoothedTs(), topLayers, "midSm", Color.ORANGE, TickPainter.LINE);
        }

        ChartAreaSettings bottom = chartSetting.addChartAreaSettings("indicator", 0, 0.4f, 1, 0.2f, Color.GREEN);
        List<ChartAreaLayerSettings> bottomLayers = bottom.getLayers();
        {
//            addChart(chartData, m_mainLevel.m_velocities.m_midVelocity1.getJoinNonChangedTs(), bottomLayers, "midVel1", Color.PINK, TickPainter.LINE);
//            addChart(chartData, m_mainLevel.m_velocities.m_midVelocity2.getJoinNonChangedTs(), bottomLayers, "midVel2", Color.PINK, TickPainter.LINE);
//            addChart(chartData, m_mainLevel.m_velocities.m_midVelocity3.getJoinNonChangedTs(), bottomLayers, "midVel3", Color.PINK, TickPainter.LINE);
//
//            addChart(chartData, m_mainLevel.m_velocities.m_midVelocity4.getJoinNonChangedTs(), bottomLayers, "midVel4", Color.darkGray, TickPainter.LINE);
//            addChart(chartData, m_mainLevel.m_velocities.m_midVelocity5.getJoinNonChangedTs(), bottomLayers, "midVel5", Color.darkGray, TickPainter.LINE);
//            addChart(chartData, m_mainLevel.m_velocities.m_midVelocity6.getJoinNonChangedTs(), bottomLayers, "midVel6", Color.darkGray, TickPainter.LINE);

            addChart(chartData, m_mainLevel.m_velocities.m_midVelocityAvg.getJoinNonChangedTs(), bottomLayers, "midVelAvg_main", Color.CYAN, TickPainter.LINE);


//            addChart(chartData, m_mainLevel.m_velocities2.m_midVelocity1.getJoinNonChangedTs(), bottomLayers, "midVel_21", Color.green, TickPainter.LINE);
//            addChart(chartData, m_mainLevel.m_velocities2.m_midVelocity2.getJoinNonChangedTs(), bottomLayers, "midVel_22", Color.green, TickPainter.LINE);
//            addChart(chartData, m_mainLevel.m_velocities2.m_midVelocity3.getJoinNonChangedTs(), bottomLayers, "midVel_23", Color.green, TickPainter.LINE);
//
//            addChart(chartData, m_mainLevel.m_velocities2.m_midVelocity4.getJoinNonChangedTs(), bottomLayers, "midVel_24", Colors.DARK_GREEN, TickPainter.LINE);
//            addChart(chartData, m_mainLevel.m_velocities2.m_midVelocity5.getJoinNonChangedTs(), bottomLayers, "midVel_25", Colors.DARK_GREEN, TickPainter.LINE);
//            addChart(chartData, m_mainLevel.m_velocities2.m_midVelocity6.getJoinNonChangedTs(), bottomLayers, "midVel_26", Colors.DARK_GREEN, TickPainter.LINE);

            addChart(chartData, m_mainLevel.m_velocities2.m_midVelocityAvg.getJoinNonChangedTs(), bottomLayers, "midVelAvg_2_main", Colors.LIGHT_BLUE, TickPainter.LINE);

//            addChart(chartData, m_mainLevel.m_adjuster.getMinTs().getJoinNonChangedTs(), bottomLayers, "adj_min", Color.ORANGE, TickPainter.LINE);
//            addChart(chartData, m_mainLevel.m_adjuster.getMaxTs().getJoinNonChangedTs(), bottomLayers, "adj_max", Color.ORANGE, TickPainter.LINE);
//            addChart(chartData, m_mainLevel.m_adjuster.getZeroTs().getJoinNonChangedTs(), bottomLayers, "adj_zero", Color.ORANGE, TickPainter.LINE);
        }

        ChartAreaSettings value = chartSetting.addChartAreaSettings("value", 0, 0.6f, 1, 0.2f, Color.LIGHT_GRAY);
        List<ChartAreaLayerSettings> valueLayers = value.getLayers();
        {
            addChart(chartData, getRibbonTs(), valueLayers, "ribbon", Color.white, TickPainter.LINE);
            addChart(chartData, getSpreadRateTs(), valueLayers, "spread_rate", Colors.DARK_GREEN, TickPainter.LINE);
            addChart(chartData, getJoinNonChangedTs(), valueLayers, "value", Color.blue, TickPainter.LINE);
//            addChart(chartData, m_mainLevel.m_adjuster.getJoinNonChangedTs(), valueLayers, "adjuster", Color.RED, TickPainter.LINE);
        }

        if (collectValues) {
            ChartAreaSettings gain = chartSetting.addChartAreaSettings("gain", 0, 0.8f, 1, 0.2f, Color.ORANGE);
            gain.setHorizontalLineValue(1);

            addChart(chartData, firstWatcher, topLayers, "trades", Color.WHITE, TickPainter.TRADE);

            List<ChartAreaLayerSettings> gainLayers = gain.getLayers();
            addChart(chartData, firstWatcher.getGainTs(), gainLayers, "gain", Color.blue, TickPainter.LINE);
        }
    }


    // ------------------------------
    private static abstract class Level {
        private final List<BaseTimesSeriesData> m_emas = new ArrayList<>();
        private final MinMaxSpread m_minMaxSpread;
        private Velocities m_velocities;
        private Velocities2 m_velocities2;
//        private final Adjuster m_adjuster;

        protected abstract BaseTimesSeriesData createEma(ITimesSeriesData tsd, long barSize, float length);

        Level(ITimesSeriesData tsd, long barSize, float start, float step, float count, boolean collectValues) {
            List<ITimesSeriesData> iEmas = new ArrayList<>(); // as list of ITimesSeriesData
            float length = start;
            int countFloor = (int) count;
            for (int i = 0; i < countFloor; i++) {
                BaseTimesSeriesData ema = getOrCreateEma(tsd, barSize, length);
                m_emas.add(ema);
                iEmas.add(ema);
                length += step;
            }

            float fraction = count - countFloor;
            float fractionLength = length - step + step * fraction;
            BaseTimesSeriesData ema = getOrCreateEma(tsd, barSize, fractionLength);
            m_emas.add(ema);
            iEmas.add(ema);

            m_minMaxSpread = new MinMaxSpread(iEmas, tsd);

            if (collectValues) {
                BaseTimesSeriesData midTs = m_minMaxSpread.m_midTs;
                m_velocities = new Velocities(midTs, barSize);
                m_velocities2 = new Velocities2(midTs, barSize);
            }

//            m_adjuster = new Adjuster(m_velocities.m_midVelocityAvg, 0.0001f, 0.5f);
        }

        private BaseTimesSeriesData getOrCreateEma(ITimesSeriesData tsd, long barSize, float length) {
            String key = tsd.hashCode() + "." + barSize + "." + length;
            BaseTimesSeriesData ret = s_emaCache.get(key);
            if (ret == null) {
                ret = createEma(tsd, barSize, length);
                s_emaCache.put(key, ret);
                s_emaCacheMiss++;
            } else {
                s_emaCacheHit++;
            }
            return ret;
        }

        // ---------------------------------------------------------------------------------------
        private static class Velocities {
            private final TicksSimpleVelocity m_midVelocity1;
            private final TicksSimpleVelocity m_midVelocity2;
            private final TicksSimpleVelocity m_midVelocity3;
            private TicksSimpleVelocity m_midVelocity4;
            private TicksSimpleVelocity m_midVelocity5;
            private TicksSimpleVelocity m_midVelocity6;
            private final Average m_midVelocityAvg;

            Velocities(BaseTimesSeriesData midSmoothed, long barSize) {
                m_midVelocity1 = new TicksSimpleVelocity(midSmoothed, barSize * 1);
                m_midVelocity2 = new TicksSimpleVelocity(midSmoothed, barSize * 2);
                m_midVelocity3 = new TicksSimpleVelocity(midSmoothed, barSize * 3);
                m_midVelocity4 = new TicksSimpleVelocity(midSmoothed, barSize * 5);
                m_midVelocity5 = new TicksSimpleVelocity(midSmoothed, barSize * 8);
                m_midVelocity6 = new TicksSimpleVelocity(midSmoothed, barSize * 12);

                List<ITimesSeriesData> midVelocities = new ArrayList<>();
                midVelocities.add(m_midVelocity1);
                midVelocities.add(m_midVelocity2);
                midVelocities.add(m_midVelocity3);
                m_midVelocityAvg = new Average(midVelocities, midSmoothed);
            }
        }

        // ---------------------------------------------------------------------------------------
        private static class Velocities2 {
            private final TicksVelocity m_midVelocity1;
            private final TicksVelocity m_midVelocity2;
            private final TicksVelocity m_midVelocity3;
            private TicksVelocity m_midVelocity4;
            private TicksVelocity m_midVelocity5;
            private TicksVelocity m_midVelocity6;
            private final Average m_midVelocityAvg;

            Velocities2(BaseTimesSeriesData midSmoothed, long barSize) {
                int multiplier = 100000;
                m_midVelocity1 = new TicksVelocity(midSmoothed, barSize * 1, multiplier);
                m_midVelocity2 = new TicksVelocity(midSmoothed, barSize * 2, multiplier);
                m_midVelocity3 = new TicksVelocity(midSmoothed, barSize * 3, multiplier);
                m_midVelocity4 = new TicksVelocity(midSmoothed, barSize * 5, multiplier);
                m_midVelocity5 = new TicksVelocity(midSmoothed, barSize * 8, multiplier);
                m_midVelocity6 = new TicksVelocity(midSmoothed, barSize * 12, multiplier);

                List<ITimesSeriesData> midVelocities = new ArrayList<>();
                midVelocities.add(m_midVelocity1);
                midVelocities.add(m_midVelocity2);
                midVelocities.add(m_midVelocity3);
                m_midVelocityAvg = new Average(midVelocities, midSmoothed);
            }
        }
    }


    //----------------------------------------------------------
    public static class MinMaxSpread extends BaseTimesSeriesData<ITickData> {
        private final List<ITimesSeriesData> m_tss;
        private final ITimesSeriesData m_first;
        private boolean m_dirty;
        private ITickData m_tick;
        private float m_spread;
        private float m_min;
        private float m_max;
        private float m_mid;
        private BaseTimesSeriesData m_midTs;
        private float m_mid2;
        private BaseTimesSeriesData m_mid2Ts;

        MinMaxSpread(List<ITimesSeriesData> tss, ITimesSeriesData baseTsd) {
            super(null);
            m_tss = tss;

            for (ITimesSeriesData<ITickData> next : tss) {
                next.getActive().addListener(this);
            }
            m_first = tss.get(0);

            m_midTs = new BaseTimesSeriesData(this) {
                @Override public ITickData getLatestTick() {
                    ITickData latestTick = getParent().getLatestTick();
                    if (latestTick != null) {
                        return new TickData(latestTick.getTimestamp(), m_mid);
                    }
                    return null;
                }
            };

            m_mid2Ts = new BaseTimesSeriesData(this) {
                @Override public ITickData getLatestTick() {
                    ITickData latestTick = getParent().getLatestTick();
                    if (latestTick != null) {
                        return new TickData(latestTick.getTimestamp(), m_mid2);
                    }
                    return null;
                }
            };

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
                float factor = 1.0f;
                float sum = 0;
                float weight = 0;
                for (ITimesSeriesData<ITickData> next : m_tss) {
                    ITickData lastTick = next.getLatestTick();
                    if (lastTick != null) {
                        float value = lastTick.getClosePrice();
                        min = Math.min(min, value);
                        max = Math.max(max, value);
                        sum += value * factor;
                        weight += factor;
                        factor *= 0.9f;
                    } else {
                        allDone = false;
                        break; // not ready yet
                    }
                }
                if (allDone) {
                    m_min = min;
                    m_max = max;
                    m_mid = (max + min) / 2;
                    m_mid2 = sum / weight;
                    m_spread = max - min;
                    m_dirty = false;
                    m_tick = new TickData(m_first.getLatestTick().getTimestamp(), m_spread);
                }
            }
            return m_tick;
        }

        TimesSeriesData<TickData> getMinTs() {
            return new JoinNonChangedInnerTimesSeriesData(this) {
                @Override protected Float getValue() {
                    return m_min;
                }
            };
        }

        TimesSeriesData<TickData> getMaxTs() {
            return new JoinNonChangedInnerTimesSeriesData(this) {
                @Override protected Float getValue() {
                    return m_max;
                }
            };
        }

        ITicksData<TickData> getMidTs() {
            return m_midTs.getJoinNonChangedTs();
        }
        ITicksData<TickData> getMid2Ts() {
            return m_mid2Ts.getJoinNonChangedTs();
        }
    }


    //----------------------------------------------------------
    public static class BarMinMaxSpread extends TicksBufferBased<Float> {
        private float m_min;
        private float m_max;

        BarMinMaxSpread(ITimesSeriesData tsd, long period) {
            super(tsd, period);
        }

        @Override public void start() {
            m_min = Float.POSITIVE_INFINITY;
            m_max = Float.NEGATIVE_INFINITY;
        }

        @Override public void processTick(ITickData tick) {
            float value = tick.getClosePrice();
            m_min = Math.min(m_min, value);
            m_max = Math.max(m_max, value);
        }

        @Override public Float done() {
            return m_max - m_min;
        }

        @Override protected float calcTickValue(Float ret) {
            return ret;
        }
    }


    //----------------------------------------------------------
    // used for chart only
    public static class MinMaxSmoothed extends BaseTimesSeriesData<ITickData> {
        private final BaseTimesSeriesData m_ribbonSpreadSmoothed;
        private final MinMaxSpread m_minMaxSpread;
        private final BaseTimesSeriesData m_barSpreadSmoothed;
        private float m_minRibbonSmoothed;
        private float m_maxRibbonSmoothed;
        private float m_minBarSpreadSmoothed;
        private float m_maxBarSpreadSmoothed;
        private ITickData m_parentLatestTick;

        MinMaxSmoothed(Level emaLevel, BaseTimesSeriesData ribbonSpreadSmoothed, BaseTimesSeriesData barSpreadSmoothed) {
            super(ribbonSpreadSmoothed);
            m_minMaxSpread = emaLevel.m_minMaxSpread;
            m_ribbonSpreadSmoothed = ribbonSpreadSmoothed;
            m_barSpreadSmoothed = barSpreadSmoothed;
        }

        @Override public void onChanged(ITimesSeriesData ts, boolean changed) {
            boolean iAmChanged = false;
            if (changed) {
                m_parentLatestTick = m_ribbonSpreadSmoothed.getLatestTick();
                if (m_parentLatestTick != null) {
                    float spreadSmoothed = m_parentLatestTick.getClosePrice();
                    float halfSpreadSmoothed = spreadSmoothed / 2;

                    float mid = m_minMaxSpread.m_mid;
                    m_minRibbonSmoothed = mid - halfSpreadSmoothed;
                    m_maxRibbonSmoothed = mid + halfSpreadSmoothed;

                    ITickData barSpreadLatestTick = m_barSpreadSmoothed.getLatestTick();
                    if (barSpreadLatestTick != null) {
                        float barSpreadSmoothed = barSpreadLatestTick.getClosePrice();
                        float halfBarSpreadSmoothed = barSpreadSmoothed / 2;

                        m_minBarSpreadSmoothed = mid - halfBarSpreadSmoothed;
                        m_maxBarSpreadSmoothed = mid + halfBarSpreadSmoothed;
                    }

                    iAmChanged = true;
                }
            }
            super.onChanged(ts, iAmChanged);
        }

        @Override public ITickData getLatestTick() {
            return m_parentLatestTick;
        }

        TimesSeriesData<TickData> getMinRibbonSmoothedTs() {
            return new JoinNonChangedInnerTimesSeriesData(this) {
                @Override protected Float getValue() {
                    return m_minRibbonSmoothed;
                }
            };
        }

        TimesSeriesData<TickData> getMaxRibbonSmoothedTs() {
            return new JoinNonChangedInnerTimesSeriesData(this) {
                @Override protected Float getValue() {
                    return m_maxRibbonSmoothed;
                }
            };
        }

        TimesSeriesData<TickData> getMinBarSpreadSmoothedTs() {
            return new JoinNonChangedInnerTimesSeriesData(this) {
                @Override protected Float getValue() {
                    return m_minBarSpreadSmoothed;
                }
            };
        }

        TimesSeriesData<TickData> getMaxBarSpreadSmoothedTs() {
            return new JoinNonChangedInnerTimesSeriesData(this) {
                @Override protected Float getValue() {
                    return m_maxBarSpreadSmoothed;
                }
            };
        }
    }
}
