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
import java.util.List;

public class MmarAlgo extends BaseAlgo {
    private static final boolean PINCH = false; // pinch by spread / spreadSmoothed
    private static final boolean FAST_RIBBON = true;

    private final float m_start;
    private final float m_step;
    private final float m_count;
    private final TicksSMA m_spreadSmoothed;
    private final Level m_mainLevel;
//    private final Level m_emaLevel;
//    private final Level m_demaLevel;
//    private final Level m_temaLevel;
    private final Level m_regressorLevel;
    private MinMaxSmoothed m_minMaxSmoothed;
    private TickData m_tickData; // latest tick
    private Float m_ribbonDirection;

    public MmarAlgo(MapConfig config, ITimesSeriesData tsd) {
        super(null);

        long barSize = config.getNumber(Vary.period).longValue();
        m_start = config.getNumber(Vary.start).floatValue();
        m_step = config.getNumber(Vary.step).floatValue();
        m_count = config.getNumber(Vary.count).floatValue();

//        m_emaLevel = new Level(tsd, barSize, m_start, m_step, m_count) {
//            @Override protected BaseTimesSeriesData createEma(ITimesSeriesData tsd, long barSize, float length) {
//                return new BarsEMA(tsd, length, barSize);
//            }
//        };
//        m_demaLevel = new Level(tsd, barSize, m_start, m_step, m_count) {
//            @Override protected BaseTimesSeriesData createEma(ITimesSeriesData tsd, long barSize, float length) {
//                return new BarsDEMA(tsd, length, barSize);
//            }
//        };
        m_regressorLevel = new Level(tsd, barSize, m_start, m_step, m_count) {
            @Override protected BaseTimesSeriesData createEma(ITimesSeriesData tsd, long barSize, float length) {
//                return new BarsRegressor(tsd, (int) length, barSize, 1f);
                return new SlidingTicksRegressor(tsd, (long) (length * barSize));
            }
        };
//        m_temaLevel = new Level(tsd, barSize, m_start, m_step, m_count) {
//            @Override protected BaseTimesSeriesData createEma(ITimesSeriesData tsd, long barSize, float length) {
//                return new BarsTEMA(tsd, length, barSize);
//            }
//        };
        m_mainLevel = m_regressorLevel;

        m_spreadSmoothed = new TicksSMA(m_mainLevel.m_minMaxSpread, barSize * 20);
        setParent(m_mainLevel.m_emas.get(0));
    }

    // ------------------------------
    private static abstract class Level {
        private final List<BaseTimesSeriesData> m_emas = new ArrayList<>();
        private final MinMaxSpread m_minMaxSpread;
        private final TicksRegressor m_midSmoothed;
        private final Velocities m_velocities;
        private final Adjuster m_adjuster;

        protected abstract BaseTimesSeriesData createEma(ITimesSeriesData tsd, long barSize, float length);

        Level(ITimesSeriesData tsd, long barSize, float start, float step, float count) {
            List<ITimesSeriesData> iEmas = new ArrayList<>(); // as list of ITimesSeriesData
            float length = start;
            int countFloor = (int) count;
            for (int i = 0; i < countFloor; i++) {
                BaseTimesSeriesData ema = createEma(tsd, barSize, length);
                m_emas.add(ema);
                iEmas.add(ema);
                length += step;
            }

            float fraction = count - countFloor;
            float fractionLength = length - step + step * fraction;
            BaseTimesSeriesData ema = createEma(tsd, barSize, fractionLength);
            m_emas.add(ema);
            iEmas.add(ema);

            m_minMaxSpread = new MinMaxSpread(iEmas, tsd);

            m_midSmoothed = new TicksRegressor(m_minMaxSpread.getMidTs(), barSize * 3);

//        m_midSmoothed = new TicksFadingAverager(m_minMaxSpread.getMidTs(), barSize * 2);
//        m_midSmoothed_2 = new TicksDFadingAverager(m_minMaxSpread.getMidTs(), barSize * 2 );

//        m_midSmoothed = new TicksDFadingAverager(m_minMaxSpread.getMidTs(), barSize * 2);
//        m_midSmoothed_2 = new TicksTFadingAverager(m_minMaxSpread.getMidTs(), barSize * 2 );

            m_velocities = new Velocities(m_midSmoothed, barSize);

            m_adjuster = new Adjuster(m_velocities.m_midVelocityAvg, 0.0001f, 0.5f);
        }


        // ---------------------------------------------------------------------------------------
        private static class Velocities {
            private final TicksVelocity m_midVelocity1;
            private final TicksVelocity m_midVelocity2;
            private final TicksVelocity m_midVelocity3;
            private final Average m_midVelocityAvg;

            Velocities(BaseTimesSeriesData midSmoothed, long barSize) {

                m_midVelocity1 = new TicksVelocity(midSmoothed, barSize * 1);
                m_midVelocity2 = new TicksVelocity(midSmoothed, barSize * 2);
                m_midVelocity3 = new TicksVelocity(midSmoothed, barSize * 3);

                List<ITimesSeriesData> midVelocities = new ArrayList<>();
                midVelocities.add(m_midVelocity1);
                midVelocities.add(m_midVelocity2);
                midVelocities.add(m_midVelocity3);
                m_midVelocityAvg = new Average(midVelocities, midSmoothed);
            }
        }
    }


    @Override public ITickData getAdjusted() {
        ITickData latestTick = getParent().getLatestTick();
        if (latestTick == null) {
            return null;
        }

        List<BaseTimesSeriesData> emas = m_mainLevel.m_emas;
        Float ribbonDirection = FAST_RIBBON ? calcRibbonDirectionFast(emas) : calcRibbonDirectionLong(emas);
        if (ribbonDirection == null) {
            return null;
        }

        if (PINCH) {
            float spread = m_mainLevel.m_minMaxSpread.m_spread;
            ITickData spreadSmoothedLatestTick = m_spreadSmoothed.getLatestTick();
            if (spreadSmoothedLatestTick != null) {
                float spreadSmoothed = spreadSmoothedLatestTick.getClosePrice();
                float rate = spread / spreadSmoothed;
                ribbonDirection = (float) (ribbonDirection * Math.sqrt(rate));
                if (ribbonDirection > 1) {
                    ribbonDirection = 1f;
                } else if (ribbonDirection < -1) {
                    ribbonDirection = -1f;
                }
            }
        }

        ribbonDirection = ((float) (Math.signum(ribbonDirection) * Math.pow(Math.abs(ribbonDirection), 0.2)));

        m_ribbonDirection = ribbonDirection;

        long timestamp = latestTick.getTimestamp();
        m_tickData = new TickData(timestamp, ribbonDirection);
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

    @Override public ITickData getLatestTick() {
        return m_tickData;
    }

    @Override public String key(boolean detailed) {
        return  ""
                        + (detailed ? ",start=" : ",") + m_start
                        + (detailed ? ",step=" : ",") + m_step
                        + (detailed ? ",count=" : ",") + m_count
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

            addChart(chartData, m_mainLevel.m_midSmoothed.getJoinNonChangedTs(), topLayers, "midSm", Color.RED, TickPainter.LINE);

            m_minMaxSmoothed = new MinMaxSmoothed(m_mainLevel);
            addChart(chartData, m_minMaxSmoothed.getMinSmoothedTs(), topLayers, "minSm", Color.ORANGE, TickPainter.LINE);
            addChart(chartData, m_minMaxSmoothed.getMaxSmoothedTs(), topLayers, "maxSm", Color.ORANGE, TickPainter.LINE);
//            addChart(chartData, m_minMaxSmoothed.getMidSmoothedTs(), topLayers, "midSm", Color.ORANGE, TickPainter.LINE);
        }

        ChartAreaSettings bottom = chartSetting.addChartAreaSettings("indicator", 0, 0.4f, 1, 0.2f, Color.GREEN);
        List<ChartAreaLayerSettings> bottomLayers = bottom.getLayers();
        {
            addChart(chartData, m_mainLevel.m_velocities.m_midVelocity1.getJoinNonChangedTs(), bottomLayers, "midVel1", Color.PINK, TickPainter.LINE);
            addChart(chartData, m_mainLevel.m_velocities.m_midVelocity2.getJoinNonChangedTs(), bottomLayers, "midVel2", Color.PINK, TickPainter.LINE);
            addChart(chartData, m_mainLevel.m_velocities.m_midVelocity3.getJoinNonChangedTs(), bottomLayers, "midVel3", Color.PINK, TickPainter.LINE);

//            addChart(chartData, m_midVelocityAvg.getJoinNonChangedTs(), bottomLayers, "midVelAvg", Color.ORANGE, TickPainter.LINE);
//
////            addChart(chartData, m_midVelocity1_2.getJoinNonChangedTs(), bottomLayers, "midVel1_2", Colors.LIGHT_GREEN, TickPainter.LINE);
////            addChart(chartData, m_midVelocity2_2.getJoinNonChangedTs(), bottomLayers, "midVel2_2", Colors.LIGHT_GREEN, TickPainter.LINE);
////            addChart(chartData, m_midVelocity3_2.getJoinNonChangedTs(), bottomLayers, "midVel3_2", Colors.LIGHT_GREEN, TickPainter.LINE);
//            addChart(chartData, m_midVelocityAvg_2.getJoinNonChangedTs(), bottomLayers, "midVelAvg_2", Color.GREEN, TickPainter.LINE);
//
//            addChart(chartData, m_midVelocityAvg_d.getJoinNonChangedTs(), bottomLayers, "midVelAvg_d", Color.blue, TickPainter.LINE);
//            addChart(chartData, m_midVelocityAvg_2_d.getJoinNonChangedTs(), bottomLayers, "midVelAvg_2_d", Color.red, TickPainter.LINE);

            addChart(chartData, m_mainLevel.m_velocities.m_midVelocityAvg.getJoinNonChangedTs(), bottomLayers, "midVelAvg_main", Color.CYAN, TickPainter.LINE);
//            addChart(chartData, m_emaLevel.m_midVelocityAvg_Avg.getJoinNonChangedTs(), bottomLayers, "midVelAvg_avg_d", Color.ORANGE, TickPainter.LINE);
//            addChart(chartData, m_demaLevel.m_midVelocityAvg_Avg.getJoinNonChangedTs(), bottomLayers, "midVelAvg_avg_d", Color.ORANGE, TickPainter.LINE);
//            addChart(chartData, m_temaLevel.m_midVelocityAvg_Avg.getJoinNonChangedTs(), bottomLayers, "midVelAvg_avg_t", Color.MAGENTA, TickPainter.LINE);

            addChart(chartData, m_mainLevel.m_adjuster.getMinTs().getJoinNonChangedTs(), bottomLayers, "adj_min", Color.ORANGE, TickPainter.LINE);
            addChart(chartData, m_mainLevel.m_adjuster.getMaxTs().getJoinNonChangedTs(), bottomLayers, "adj_max", Color.ORANGE, TickPainter.LINE);
            addChart(chartData, m_mainLevel.m_adjuster.getZeroTs().getJoinNonChangedTs(), bottomLayers, "adj_zero", Color.ORANGE, TickPainter.LINE);
        }

        ChartAreaSettings value = chartSetting.addChartAreaSettings("value", 0, 0.6f, 1, 0.2f, Color.LIGHT_GRAY);
        List<ChartAreaLayerSettings> valueLayers = value.getLayers();
        {
            addChart(chartData, getRibbonTs(), valueLayers, "ribbon", Color.white, TickPainter.LINE);
            addChart(chartData, getJoinNonChangedTs(), valueLayers, "value", Color.blue, TickPainter.LINE);
            addChart(chartData, m_mainLevel.m_adjuster.getJoinNonChangedTs(), valueLayers, "adjuster", Color.RED, TickPainter.LINE);
        }

        if (collectValues) {
            ChartAreaSettings gain = chartSetting.addChartAreaSettings("gain", 0, 0.8f, 1, 0.2f, Color.ORANGE);
            gain.setHorizontalLineValue(1);

            addChart(chartData, firstWatcher, topLayers, "trades", Color.WHITE, TickPainter.TRADE);

            List<ChartAreaLayerSettings> gainLayers = gain.getLayers();
            addChart(chartData, firstWatcher.getGainTs(), gainLayers, "gain", Color.blue, TickPainter.LINE);
        }
    }


    //----------------------------------------------------------
    public static class Average extends BaseTimesSeriesData<ITickData> {
        private final List<ITimesSeriesData> m_tss;
        private boolean m_dirty;
        private float m_average;
        private TickData m_tick;

        Average(List<ITimesSeriesData> tss, ITimesSeriesData baseDs) {
            super(null);
            m_tss = tss;
            for (ITimesSeriesData<ITickData> next : tss) {
                next.getActive().addListener(this);
            }
            setParent(baseDs); // subscribe to list first - will be called onChanged() and set as dirty ONLY
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
                boolean allDone = true;
                float sum = 0;
                for (ITimesSeriesData<ITickData> next : m_tss) {
                    ITickData lastTick = next.getLatestTick();
                    if (lastTick != null) {
                        float value = lastTick.getClosePrice();
                        sum += value;
                    } else {
                        allDone = false;
                        break; // not ready yet
                    }
                }
                if (allDone) {
                    m_average = sum / m_tss.size();
                    m_dirty = false;
                    m_tick = new TickData(getParent().getLatestTick().getTimestamp(), m_average);
                }
            }
            return m_tick;
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
        private JoinNonChangedInnerTimesSeriesData m_midTs;

        MinMaxSpread(List<ITimesSeriesData> tss, ITimesSeriesData baseDs) {
            super(null);
            m_tss = tss;
            for (ITimesSeriesData<ITickData> next : tss) {
                next.getActive().addListener(this);
            }
            m_first = tss.get(0);
            setParent(baseDs); // subscribe to list first - will be called onChanged() and set as dirty ONLY
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
                for (ITimesSeriesData<ITickData> next : m_tss) {
                    ITickData lastTick = next.getLatestTick();
                    if (lastTick != null) {
                        float value = lastTick.getClosePrice();
                        min = Math.min(min, value);
                        max = Math.max(max, value);
                    } else {
                        allDone = false;
                        break; // not ready yet
                    }
                }
                if (allDone) {
                    m_min = min;
                    m_max = max;
                    m_mid = (max + min) / 2;
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

        TimesSeriesData<TickData> getMidTs() {
            if (m_midTs == null) {
                m_midTs = new JoinNonChangedInnerTimesSeriesData(this) {
                    @Override protected Float getValue() {
                        return m_mid;
                    }
                };
            }
            return m_midTs;
        }
    }

    //----------------------------------------------------------
    public class MinMaxSmoothed extends BaseTimesSeriesData<ITickData> {
        private final Level m_emaLevel;
        private float m_minSmoothed;
        private float m_maxSmoothed;
        private float m_midSmoothed;
        private ITickData m_parentLatestTick;

        MinMaxSmoothed(Level emaLevel) {
            super(m_spreadSmoothed);
            m_emaLevel = emaLevel;
        }

        @Override public void onChanged(ITimesSeriesData ts, boolean changed) {
            if (changed) {
                m_parentLatestTick = m_spreadSmoothed.getLatestTick();
                float spreadSmoothed = m_parentLatestTick.getClosePrice();
                float halfSpreadSmoothed = spreadSmoothed / 2;

                float min = m_emaLevel.m_minMaxSpread.m_min;
                float max = m_emaLevel.m_minMaxSpread.m_max;
                m_midSmoothed = (min + max) / 2;
                m_minSmoothed = m_midSmoothed - halfSpreadSmoothed;
                m_maxSmoothed = m_midSmoothed + halfSpreadSmoothed;
            }
            super.onChanged(ts, changed);
        }

        @Override public ITickData getLatestTick() {
            return m_parentLatestTick;
        }

        TimesSeriesData<TickData> getMinSmoothedTs() {
            return new JoinNonChangedInnerTimesSeriesData(this) {
                @Override protected Float getValue() {
                    return m_minSmoothed;
                }
            };
        }

        TimesSeriesData<TickData> getMaxSmoothedTs() {
            return new JoinNonChangedInnerTimesSeriesData(this) {
                @Override protected Float getValue() {
                    return m_maxSmoothed;
                }
            };
        }

        TimesSeriesData<TickData> getMidSmoothedTs() {
            return new JoinNonChangedInnerTimesSeriesData(this) {
                @Override protected Float getValue() {
                    return m_midSmoothed;
                }
            };
        }
    }

}
