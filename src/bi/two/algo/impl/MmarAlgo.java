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
    private final float m_start;
    private final float m_step;
    private final float m_count;
    private final List<BaseTimesSeriesData> m_emas = new ArrayList<>();
    private final MinMaxSpread m_minMaxSpread;
    private final TicksSMA m_spreadSmoothed;
    private final BaseTimesSeriesData m_midSmoothed;
    private final BaseTimesSeriesData m_midSmoothed_2;
    private final TicksVelocity m_midVelocity1;
    private final TicksVelocity m_midVelocity2;
    private final TicksVelocity m_midVelocity3;
    private final TicksVelocity m_midVelocity1_2;
    private final TicksVelocity m_midVelocity2_2;
    private final TicksVelocity m_midVelocity3_2;
    private final Average m_midVelocityAvg;
    private final Average m_midVelocityAvg_2;
    private MinMaxSmoothed m_minMaxSmoothed;
    private TickData m_tickData; // latest tick
    private Float m_ribbonDirection;

    public MmarAlgo(MapConfig config, ITimesSeriesData tsd) {
        super(null);

        long barSize = config.getNumber(Vary.period).longValue();
        m_start = config.getNumber(Vary.start).floatValue();
        m_step = config.getNumber(Vary.step).floatValue();
        m_count = config.getNumber(Vary.count).floatValue();

        List<ITimesSeriesData> iEmas = new ArrayList<>(); // as list of ITimesSeriesData
        BaseTimesSeriesData first = null;
        float length = m_start;
        int countFloor = (int) m_count;
        for (int i = 0; i < countFloor; i++) {
            BaseTimesSeriesData ema = new BarsEMA(tsd, length, barSize);
            m_emas.add(ema);
            iEmas.add(ema);

            length += m_step;
            if (i == 0) {
                first = ema;
            }
        }

        float fraction = m_count - countFloor;
        BaseTimesSeriesData ema = new BarsEMA(tsd, length - m_step + m_step * fraction, barSize);
        m_emas.add(ema);

        m_minMaxSpread = new MinMaxSpread(iEmas, tsd);

        m_midSmoothed = new TicksRegressor(m_minMaxSpread.getMidTs(), barSize * 8);
        m_midSmoothed_2 = new TicksFadingAverager(m_minMaxSpread.getMidTs(), barSize * 2 );

//        m_midSmoothed = new TicksFadingAverager(m_minMaxSpread.getMidTs(), barSize * 2);
//        m_midSmoothed_2 = new TicksDFadingAverager(m_minMaxSpread.getMidTs(), barSize * 2 );

//        m_midSmoothed = new TicksDFadingAverager(m_minMaxSpread.getMidTs(), barSize * 2);
//        m_midSmoothed_2 = new TicksTFadingAverager(m_minMaxSpread.getMidTs(), barSize * 2 );

        List<ITimesSeriesData> midVelocities = new ArrayList<>();
        m_midVelocity1 = new TicksVelocity(m_midSmoothed, barSize * 1);
        midVelocities.add(m_midVelocity1);
        m_midVelocity2 = new TicksVelocity(m_midSmoothed, barSize * 2);
        midVelocities.add(m_midVelocity2);
        m_midVelocity3 = new TicksVelocity(m_midSmoothed, barSize * 3);
        midVelocities.add(m_midVelocity3);

        List<ITimesSeriesData> midVelocities_2 = new ArrayList<>();
        m_midVelocity1_2 = new TicksVelocity(m_midSmoothed_2, barSize * 1);
        midVelocities_2.add(m_midVelocity1_2);
        m_midVelocity2_2 = new TicksVelocity(m_midSmoothed_2, barSize * 2);
        midVelocities_2.add(m_midVelocity2_2);
        m_midVelocity3_2 = new TicksVelocity(m_midSmoothed_2, barSize * 3);
        midVelocities_2.add(m_midVelocity3_2);

        m_midVelocityAvg = new Average(midVelocities, m_midSmoothed);
        m_midVelocityAvg_2 = new Average(midVelocities_2, m_midSmoothed_2);

        m_spreadSmoothed = new TicksSMA(m_minMaxSpread, barSize * 20);

        setParent(first);
    }

    @Override public ITickData getAdjusted() {
        int len = m_emas.size();

        for (BaseTimesSeriesData emaSm1 : m_emas) {
            ITickData latestTick = emaSm1.getLatestTick();
            if (latestTick == null) {
                return null; // check all for null
            }
        }

        int count = 0;
        float direction = 0;
        for (int i = 0; i < len - 1; i++) {
            BaseTimesSeriesData emaSm1 = m_emas.get(i);
            ITickData latestTick1 = emaSm1.getLatestTick();
            double value1 = latestTick1.getClosePrice();
            for (int j = i + 1; j < len; j++) {
                BaseTimesSeriesData emaSm2 = m_emas.get(j);
                ITickData latestTick2 = emaSm2.getLatestTick();
                double value2 = latestTick2.getClosePrice();
                direction += (value1 > value2) ? +1 : -1;
                count++;
            }
        }
        m_ribbonDirection = direction / count;

        float pinched = m_ribbonDirection;
        float spread = m_minMaxSpread.m_spread;
        ITickData spreadSmoothedLatestTick = m_spreadSmoothed.getLatestTick();
        if (spreadSmoothedLatestTick != null) {
            float spreadSmoothed = spreadSmoothedLatestTick.getClosePrice();
            float rate = spread / spreadSmoothed;
            pinched = (float) (m_ribbonDirection * Math.sqrt(rate));
            if (pinched > 1) {
                pinched = 1;
            } else if (pinched < -1) {
                pinched = -1;
            }
        }

        long timestamp = getParent().getLatestTick().getTimestamp();
        m_tickData = new TickData(timestamp, pinched);
        return m_tickData;
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
            Color emaColor = Colors.alpha(Color.BLUE, 90);
            int size = m_emas.size();
            for (int i = size - 1; i >= 0; i--) {
                BaseTimesSeriesData ema = m_emas.get(i);
                Color color = (i == 0) ? Color.BLUE : (i == size - 1) ? Colors.alpha(Color.GRAY, 100) : emaColor;
                addChart(chartData, ema.getJoinNonChangedTs(), topLayers, "ema" + i, color, TickPainter.LINE);
            }

            addChart(chartData, m_minMaxSpread.getMinTs(), topLayers, "min", Color.MAGENTA, TickPainter.LINE);
            addChart(chartData, m_minMaxSpread.getMaxTs(), topLayers, "max", Color.MAGENTA, TickPainter.LINE);
            addChart(chartData, m_minMaxSpread.getMidTs(), topLayers, "mid", Color.MAGENTA, TickPainter.LINE);
            addChart(chartData, m_midSmoothed.getJoinNonChangedTs(), topLayers, "midSm", Color.RED, TickPainter.LINE);
            addChart(chartData, m_midSmoothed_2.getJoinNonChangedTs(), topLayers, "midSm2", Color.GREEN, TickPainter.LINE);

            m_minMaxSmoothed = new MinMaxSmoothed();
            addChart(chartData, m_minMaxSmoothed.getMinSmoothedTs(), topLayers, "minSm", Color.ORANGE, TickPainter.LINE);
            addChart(chartData, m_minMaxSmoothed.getMaxSmoothedTs(), topLayers, "maxSm", Color.ORANGE, TickPainter.LINE);
//            addChart(chartData, m_minMaxSmoothed.getMidSmoothedTs(), topLayers, "midSm", Color.ORANGE, TickPainter.LINE);
        }

        ChartAreaSettings bottom = chartSetting.addChartAreaSettings("indicator", 0, 0.4f, 1, 0.2f, Color.GREEN);
        List<ChartAreaLayerSettings> bottomLayers = bottom.getLayers();
        {
//            addChart(chartData, m_midVelocity1.getJoinNonChangedTs(), bottomLayers, "midVel1", Color.PINK, TickPainter.LINE);
//            addChart(chartData, m_midVelocity2.getJoinNonChangedTs(), bottomLayers, "midVel2", Color.PINK, TickPainter.LINE);
//            addChart(chartData, m_midVelocity3.getJoinNonChangedTs(), bottomLayers, "midVel3", Color.PINK, TickPainter.LINE);
            addChart(chartData, m_midVelocityAvg.getJoinNonChangedTs(), bottomLayers, "midVelAvg", Color.ORANGE, TickPainter.LINE);

//            addChart(chartData, m_midVelocity1_2.getJoinNonChangedTs(), bottomLayers, "midVel1_2", Colors.LIGHT_GREEN, TickPainter.LINE);
//            addChart(chartData, m_midVelocity2_2.getJoinNonChangedTs(), bottomLayers, "midVel2_2", Colors.LIGHT_GREEN, TickPainter.LINE);
//            addChart(chartData, m_midVelocity3_2.getJoinNonChangedTs(), bottomLayers, "midVel3_2", Colors.LIGHT_GREEN, TickPainter.LINE);
            addChart(chartData, m_midVelocityAvg_2.getJoinNonChangedTs(), bottomLayers, "midVelAvg_2", Color.GREEN, TickPainter.LINE);
        }

        ChartAreaSettings value = chartSetting.addChartAreaSettings("value", 0, 0.6f, 1, 0.2f, Color.LIGHT_GRAY);
        List<ChartAreaLayerSettings> valueLayers = value.getLayers();
        {
            addChart(chartData, getRibbonTs(), valueLayers, "ribbon", Color.white, TickPainter.LINE);
            addChart(chartData, getJoinNonChangedTs(), valueLayers, "value", Color.blue, TickPainter.LINE);
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
        private float m_minSmoothed;
        private float m_maxSmoothed;
        private float m_midSmoothed;
        private ITickData m_parentLatestTick;

        MinMaxSmoothed() {
            super(m_spreadSmoothed);
        }

        @Override public void onChanged(ITimesSeriesData ts, boolean changed) {
            if (changed) {
                m_parentLatestTick = m_spreadSmoothed.getLatestTick();
                float spreadSmoothed = m_parentLatestTick.getClosePrice();
                float halfSpreadSmoothed = spreadSmoothed / 2;

                float min = m_minMaxSpread.m_min;
                float max = m_minMaxSpread.m_max;
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
