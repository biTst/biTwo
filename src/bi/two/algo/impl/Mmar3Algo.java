package bi.two.algo.impl;

import bi.two.ChartCanvas;
import bi.two.Colors;
import bi.two.algo.BaseAlgo;
import bi.two.algo.Watcher;
import bi.two.calc.Average;
import bi.two.calc.BarsEMA;
import bi.two.calc.SlidingTicksRegressor;
import bi.two.calc.TicksVelocity;
import bi.two.chart.*;
import bi.two.opt.Vary;
import bi.two.ts.BaseTimesSeriesData;
import bi.two.ts.ITimesSeriesData;
import bi.two.ts.TimesSeriesData;
import bi.two.util.MapConfig;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class Mmar3Algo extends BaseAlgo {
    private final long m_barSize;
    private final float m_start;
    private final float m_step;
    private final float m_count;
    private final float m_drop;
    private final float m_smooth;
    private final float m_power;
    private final float m_multiplier;
    private final List<BaseTimesSeriesData> m_emas = new ArrayList<>();
    private final MinMaxSpread m_minMaxSpread;
    private final VelocityAdj m_velocityAdj;
    private BaseTimesSeriesData m_spreadSmoothed;
    private ITickData m_tickData;

    public Mmar3Algo(MapConfig config, ITimesSeriesData tsd) {
        super(null);

        boolean collectValues = config.getBoolean(BaseAlgo.COLLECT_VALUES_KEY);
        m_barSize = config.getNumber(Vary.period).longValue();
        m_start = config.getNumber(Vary.start).floatValue();
        m_step = config.getNumber(Vary.step).floatValue();
        m_count = config.getNumber(Vary.count).floatValue();
        m_drop = config.getNumber(Vary.drop).floatValue();
        m_smooth = config.getNumber(Vary.smooth).floatValue();
        m_power = config.getNumber(Vary.power).floatValue();
        m_multiplier = config.getNumber(Vary.multiplier).floatValue();

        // create ribbon
        List<ITimesSeriesData> iEmas = new ArrayList<>(); // as list of ITimesSeriesData
        float length = m_start;
        int countFloor = (int) m_count;
        for (int i = 0; i < countFloor; i++) {
            BaseTimesSeriesData ema = getOrCreateEma(tsd, m_barSize, length);
            m_emas.add(ema);
            iEmas.add(ema);
            length += m_step;
        }
        float fraction = m_count - countFloor;
        float fractionLength = length - m_step + m_step * fraction;
        BaseTimesSeriesData ema = getOrCreateEma(tsd, m_barSize, fractionLength);
        m_emas.add(ema);
        iEmas.add(ema);

        m_minMaxSpread = new MinMaxSpread(iEmas, tsd, collectValues);
        if (collectValues) {
            m_spreadSmoothed = new BarsEMA(m_minMaxSpread, m_smooth, m_barSize);
        }

        m_velocityAdj = new VelocityAdj(m_minMaxSpread);

        setParent(m_emas.get(0));
    }

    private BaseTimesSeriesData getOrCreateEma(ITimesSeriesData tsd, long barSize, float length) {
        return new SlidingTicksRegressor(tsd, (long) (length * barSize * m_multiplier));
//        return new BarsEMA(tsd, length, barSize);
//        return new BarsDEMA(tsd, length, barSize);
//        return new BarsTEMA(tsd, length, barSize);
    }

    @Override public ITickData getAdjusted() {
        ITickData parentLatestTick = getParent().getLatestTick();
        if (parentLatestTick == null) {
            return null; // not ready yet
        }
        ITickData latestTick = m_minMaxSpread.getLatestTick();// make sure calculation is up-to-date
        if (latestTick == null) {
            return null; // not ready yet
        }

        long timestamp = parentLatestTick.getTimestamp();
        m_tickData = new TickData(timestamp, m_minMaxSpread.m_powAdj);
        return m_tickData;
    }

    @Override public ITickData getLatestTick() {
        return m_tickData;
    }

    private TimesSeriesData<TickData> getPowAdjTs() {
        return new JoinNonChangedInnerTimesSeriesData(this) {
            @Override protected Float getValue() {
                return m_minMaxSpread.m_powAdj;
            }
        };
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
            int size = m_emas.size();
            for (int i = size - 1; i >= 0; i--) {
                BaseTimesSeriesData ema = m_emas.get(i);
                Color color = (i == 0) ? Color.BLUE : (i == size - 1) ? Colors.alpha(Color.GRAY, 100) : emaColor;
                addChart(chartData, ema.getJoinNonChangedTs(), topLayers, "ema" + i, color, TickPainter.LINE);
            }

            addChart(chartData, m_minMaxSpread.getMinTs(), topLayers, "min", Color.MAGENTA, TickPainter.LINE);
            addChart(chartData, m_minMaxSpread.getMaxTs(), topLayers, "max", Color.MAGENTA, TickPainter.LINE);
            addChart(chartData, m_minMaxSpread.getMidTs(), topLayers, "mid", Color.MAGENTA, TickPainter.LINE);

            addChart(chartData, m_minMaxSpread.getRibbonSpreadMaxTopTs(), topLayers, "maxTop", Color.CYAN, TickPainter.LINE);
            addChart(chartData, m_minMaxSpread.getRibbonSpreadMaxBottomTs(), topLayers, "maxBottom", Color.CYAN, TickPainter.LINE);

            addChart(chartData, m_minMaxSpread.getRibbonSpreadFadingTopTs(), topLayers, "fadeTop", Color.PINK, TickPainter.LINE);
            addChart(chartData, m_minMaxSpread.getRibbonSpreadFadingBottomTs(), topLayers, "fadeBottom", Color.PINK, TickPainter.LINE);
            addChart(chartData, m_minMaxSpread.m_ribbonSpreadFadingMidTs.getJoinNonChangedTs(), topLayers, "fadeMid", Color.green, TickPainter.LINE);
            addChart(chartData, m_minMaxSpread.m_ribbonSpreadFadingMidMidTs.getJoinNonChangedTs(), topLayers, "fadeMidMid", Color.yellow, TickPainter.LINE);
            addChart(chartData, m_minMaxSpread.m_ribbonSpreadFadingMidMidAdjTs.getJoinNonChangedTs(), topLayers, "fadeMidMidAdj", Color.RED, TickPainter.LINE);
        }

        ChartAreaSettings bottom = chartSetting.addChartAreaSettings("indicator", 0, 0.4f, 1, 0.2f, Color.GREEN);
        List<ChartAreaLayerSettings> bottomLayers = bottom.getLayers();
        {
//            addChart(chartData, m_minMaxSpread.getJoinNonChangedTs(), bottomLayers, "spread", Color.MAGENTA, TickPainter.LINE);
//            addChart(chartData, m_minMaxSpread.getRibbonSpreadMaxTs(), bottomLayers, "spreadMax", Color.green, TickPainter.LINE);
//            addChart(chartData, m_minMaxSpread.getRibbonSpreadFadingTs(), bottomLayers, "spreadFade", Color.blue, TickPainter.LINE);
//            addChart(chartData, m_spreadSmoothed.getJoinNonChangedTs(), bottomLayers, "spreadSmoothed", Color.yellow, TickPainter.LINE);

            Color velColor = Colors.alpha(Color.yellow, 128);
            addChart(chartData, m_velocityAdj.m_midVelocity1.getJoinNonChangedTs(), bottomLayers, "minVel1", velColor, TickPainter.LINE);
            addChart(chartData, m_velocityAdj.m_midVelocity2.getJoinNonChangedTs(), bottomLayers, "minVel2", velColor, TickPainter.LINE);
            addChart(chartData, m_velocityAdj.m_midVelocity3.getJoinNonChangedTs(), bottomLayers, "minVel3", velColor, TickPainter.LINE);
            addChart(chartData, m_velocityAdj.m_midVelocity4.getJoinNonChangedTs(), bottomLayers, "minVel4", velColor, TickPainter.LINE);
            addChart(chartData, m_velocityAdj.m_midVelocity5.getJoinNonChangedTs(), bottomLayers, "minVel5", velColor, TickPainter.LINE);
            addChart(chartData, m_velocityAdj.m_midVelocityAvg.getJoinNonChangedTs(), bottomLayers, "minVelAvg", Color.MAGENTA, TickPainter.LINE);
        }

        ChartAreaSettings value = chartSetting.addChartAreaSettings("value", 0, 0.6f, 1, 0.2f, Color.LIGHT_GRAY);
        List<ChartAreaLayerSettings> valueLayers = value.getLayers();
        {
            addChart(chartData, getJoinNonChangedTs(), valueLayers, "value", Color.blue, TickPainter.LINE);
            addChart(chartData, getPowAdjTs(), valueLayers, "powValue", Color.MAGENTA, TickPainter.LINE);
        }

        if (collectValues) {
            ChartAreaSettings gain = chartSetting.addChartAreaSettings("gain", 0, 0.8f, 1, 0.2f, Color.ORANGE);
            gain.setHorizontalLineValue(1);

            addChart(chartData, firstWatcher, topLayers, "trades", Color.WHITE, TickPainter.TRADE);

            List<ChartAreaLayerSettings> gainLayers = gain.getLayers();
            addChart(chartData, firstWatcher.getGainTs(), gainLayers, "gain", Color.blue, TickPainter.LINE);
        }
    }


    @Override public String key(boolean detailed) {
        return  ""
                + (detailed ? ",start=" : ",") + m_start
                + (detailed ? ",step=" : ",") + m_step
                + (detailed ? ",count=" : ",") + m_count
                + (detailed ? ",drop=" : ",") + m_drop
                + (detailed ? ",smooth=" : ",") + m_smooth
                + (detailed ? ",power=" : ",") + m_power
                + (detailed ? ",multiplier=" : ",") + m_multiplier
//                /*+ ", " + Utils.millisToYDHMSStr(period)*/;
                ;
    }

    //----------------------------------------------------------
    private class MinMaxSpread extends BaseTimesSeriesData<ITickData> {
        private final List<ITimesSeriesData> m_emas;
        private boolean m_dirty;
        private ITickData m_tick;
        private float m_ribbonSpread;
        private float m_min;
        private float m_max;
        private float m_mid;
        private BaseTimesSeriesData m_midTs;
        private final BaseTimesSeriesData m_ribbonSpreadFadingMidTs;
        private final BaseTimesSeriesData m_ribbonSpreadFadingMidMidTs;
        private final BaseTimesSeriesData m_ribbonSpreadFadingMidMidAdjTs;
        private boolean m_goUp;
        private float m_ribbonSpreadMax;
        private float m_ribbonSpreadMaxTop;
        private float m_ribbonSpreadMaxBottom;
        private float m_ribbonSpreadFading;
        private float m_ribbonSpreadFadingTop;
        private float m_ribbonSpreadFadingBottom;
        private float m_ribbonSpreadFadingMid;
        private float m_leadEmaValue;
        private float m_adj;
        private float m_powAdj;
        private float m_ribbonRate;

        MinMaxSpread(List<ITimesSeriesData> emas, ITimesSeriesData baseTsd, boolean collectValues) {
            super(null);
            m_emas = emas;

            for (ITimesSeriesData<ITickData> next : emas) {
                next.getActive().addListener(this);
            }

            if (collectValues) {
                m_midTs = new BaseTimesSeriesData(this) {
                    @Override public ITickData getLatestTick() {
                        ITickData latestTick = getParent().getLatestTick();
                        if (latestTick != null) {
                            return new TickData(latestTick.getTimestamp(), m_mid);
                        }
                        return null;
                    }
                };
            }

            m_ribbonSpreadFadingMidTs = new BaseTimesSeriesData(this) {
                @Override public ITickData getLatestTick() {
                    ITickData latestTick = getParent().getLatestTick();
                    if (latestTick != null) {
                        return new TickData(latestTick.getTimestamp(), m_ribbonSpreadFadingMid);
                    }
                    return null;
                }
            };

            m_ribbonSpreadFadingMidMidTs = new BaseTimesSeriesData(this) {
                @Override public ITickData getLatestTick() {
                    ITickData latestTick = getParent().getLatestTick();
                    if (latestTick != null) {
                        return new TickData(latestTick.getTimestamp(), (m_ribbonSpreadFadingMid + m_mid) / 2);
                    }
                    return null;
                }
            };

            m_ribbonSpreadFadingMidMidAdjTs = new BaseTimesSeriesData(this) {
                @Override public ITickData getLatestTick() {
                    ITickData latestTick = getParent().getLatestTick();
                    if (latestTick != null) {
                        float rate = (m_ribbonSpreadFadingMid > m_mid)
                                ? m_mid + (m_ribbonSpreadFadingMid - m_mid) * m_ribbonRate
                                : m_ribbonSpreadFadingMid + (m_mid - m_ribbonSpreadFadingMid) * m_ribbonRate;
                        return new TickData(latestTick.getTimestamp(), rate);
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
                float leadEmaValue = 0;
                int size = m_emas.size();
                for (int i = 0; i < size; i++) {
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

                    m_min = min;
                    m_max = max;
                    m_mid = (max + min) / 2;
                    float ribbonSpread = max - min;
                    m_ribbonRate = (m_leadEmaValue - min) / ribbonSpread; // [0...1]

                    m_ribbonSpreadMax = (goUp != m_goUp) // direction changed
                            ? ribbonSpread //reset
                            : ribbonSpread < m_ribbonSpread
                                ? Math.max(ribbonSpread, m_ribbonSpreadMax)
                                : Math.max(ribbonSpread, m_ribbonSpreadMax);
                    m_ribbonSpreadMaxTop = goUp ? min + m_ribbonSpreadMax : max;
                    m_ribbonSpreadMaxBottom = goUp ? min : max - m_ribbonSpreadMax;

                    m_ribbonSpreadFading = (goUp != m_goUp)
                            ? ribbonSpread
                            : Math.max(ribbonSpread, ribbonSpread < m_ribbonSpread
                                ? m_ribbonSpreadFading - (m_ribbonSpread - ribbonSpread) * m_drop
                                : m_ribbonSpreadFading);
                    m_ribbonSpreadFadingTop = goUp ? min + m_ribbonSpreadFading : max;
                    m_ribbonSpreadFadingBottom = goUp ? min : max - m_ribbonSpreadFading;
                    m_ribbonSpreadFadingMid = (m_ribbonSpreadFadingTop + m_ribbonSpreadFadingBottom) / 2;

                    m_ribbonSpread = ribbonSpread;
                    m_goUp = goUp;
                    m_leadEmaValue = leadEmaValue;

                    float adjRate = (m_leadEmaValue - m_ribbonSpreadFadingBottom) / m_ribbonSpreadFading; // [0...1]
                    float powAdjRate = (float) (goUp
                                                ? 1 - Math.pow(1 - adjRate, m_power)
                                                : Math.pow(adjRate, m_power));

                    m_adj = adjRate * 2 - 1;
                    m_powAdj = powAdjRate * 2 - 1;

                    m_tick = new TickData(getParent().getLatestTick().getTimestamp(), ribbonSpread);
                    m_dirty = false;
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

        TimesSeriesData<TickData> getRibbonSpreadMaxTs() {
            return new JoinNonChangedInnerTimesSeriesData(this) {
                @Override protected Float getValue() {
                    return m_ribbonSpreadMax;
                }
            };
        }

        TimesSeriesData<TickData> getRibbonSpreadMaxTopTs() {
            return new JoinNonChangedInnerTimesSeriesData(this) {
                @Override protected Float getValue() {
                    return m_ribbonSpreadMaxTop;
                }
            };
        }

        TimesSeriesData<TickData> getRibbonSpreadMaxBottomTs() {
            return new JoinNonChangedInnerTimesSeriesData(this) {
                @Override protected Float getValue() {
                    return m_ribbonSpreadMaxBottom;
                }
            };
        }

        TimesSeriesData<TickData> getRibbonSpreadFadingTs() {
            return new JoinNonChangedInnerTimesSeriesData(this) {
                @Override protected Float getValue() {
                    return m_ribbonSpreadFading;
                }
            };
        }

        TimesSeriesData<TickData> getRibbonSpreadFadingTopTs() {
            return new JoinNonChangedInnerTimesSeriesData(this) {
                @Override protected Float getValue() {
                    return m_ribbonSpreadFadingTop;
                }
            };
        }

        TimesSeriesData<TickData> getRibbonSpreadFadingBottomTs() {
            return new JoinNonChangedInnerTimesSeriesData(this) {
                @Override protected Float getValue() {
                    return m_ribbonSpreadFadingBottom;
                }
            };
        }

        ITicksData<TickData> getMidTs() {
            return m_midTs.getJoinNonChangedTs();
        }
    }


    //----------------------------------------------------------
    private class VelocityAdj extends BaseTimesSeriesData<ITickData> {
        private final TicksVelocity m_midVelocity1;
        private final TicksVelocity m_midVelocity2;
        private final TicksVelocity m_midVelocity3;
        private final TicksVelocity m_midVelocity4;
        private final TicksVelocity m_midVelocity5;
        private final Average m_midVelocityAvg;

        VelocityAdj(MinMaxSpread parent) {
            super(null);
            BaseTimesSeriesData velocityBaseTsd = parent.m_ribbonSpreadFadingMidMidAdjTs;
            int multiplier = 1000000;
            m_midVelocity1 = new TicksVelocity(velocityBaseTsd, (long) (m_barSize * 1.0), multiplier);
            m_midVelocity2 = new TicksVelocity(velocityBaseTsd, (long) (m_barSize * 1.25), multiplier);
            m_midVelocity3 = new TicksVelocity(velocityBaseTsd, (long) (m_barSize * 1.5), multiplier);
            m_midVelocity4 = new TicksVelocity(velocityBaseTsd, (long) (m_barSize * 1.75), multiplier);
            m_midVelocity5 = new TicksVelocity(velocityBaseTsd, (long) (m_barSize * 2.0), multiplier);

            List<ITimesSeriesData> midVelocities = new ArrayList<>();
            midVelocities.add(m_midVelocity1);
            midVelocities.add(m_midVelocity2);
            midVelocities.add(m_midVelocity3);
            midVelocities.add(m_midVelocity4);
            midVelocities.add(m_midVelocity5);
            m_midVelocityAvg = new Average(midVelocities, velocityBaseTsd);

            setParent(parent);
        }

        @Override public ITickData getLatestTick() {
            return null;
        }
    }
}
