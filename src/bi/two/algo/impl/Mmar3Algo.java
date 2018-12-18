package bi.two.algo.impl;

import bi.two.ChartCanvas;
import bi.two.Colors;
import bi.two.algo.Watcher;
import bi.two.calc.Average;
import bi.two.calc.BarsEMA;
import bi.two.calc.PolynomialSplineVelocity;
import bi.two.calc.SlidingTicksRegressor;
import bi.two.chart.*;
import bi.two.opt.Vary;
import bi.two.ts.*;
import bi.two.util.MapConfig;
import bi.two.util.ReverseListIterator;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

public class Mmar3Algo extends BaseRibbonAlgo0 {
    private final float m_drop;
    private final float m_smooth;
    private final float m_power;
    private final float m_threshold;
    private final List<BaseTimesSeriesData> m_emas = new ArrayList<>();
    private final MinMaxSpread m_minMaxSpread;
    private final Average m_velocityAvg;
    private final SlidingTicksRegressor m_velocityAdjRegr;
    private final VelocityAdj m_velocityAdj;
    private BaseTimesSeriesData m_spreadSmoothed;

    public Mmar3Algo(MapConfig algoConfig, ITimesSeriesData tsd) {
        super(null, algoConfig);

        m_drop = algoConfig.getNumber(Vary.drop).floatValue();
        m_smooth = algoConfig.getNumber(Vary.smooth).floatValue();
        m_power = algoConfig.getNumber(Vary.power).floatValue();
        m_threshold = algoConfig.getNumber(Vary.threshold).floatValue();

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

        m_minMaxSpread = new MinMaxSpread(iEmas, tsd, m_collectValues);
        if (m_collectValues) {
            m_spreadSmoothed = new BarsEMA(m_minMaxSpread, m_smooth, m_barSize);
        }

        float multiplier = 10000000f;
        float start = 1.2f;
        float step = 0.06f;
        int count = 1;
        BaseTimesSeriesData velocityBaseTsd = m_minMaxSpread.m_ribbonSpreadFadingMidMidAdjTs;
        m_velocityAvg = new Average(buildVelocities(velocityBaseTsd, m_barSize, start, step, count, multiplier), velocityBaseTsd);

        m_velocityAdjRegr = new SlidingTicksRegressor(m_velocityAvg, (long) (m_barSize * 1.0f));

        m_velocityAdj = new VelocityAdj(m_velocityAdjRegr, m_threshold);

        setParent(m_emas.get(0));
    }

    private static List<BaseTimesSeriesData> buildVelocities(BaseTimesSeriesData velocityBaseTsd, long barSize, float start, float step, int count, float multiplier) {
        List<BaseTimesSeriesData> velocities = new ArrayList<>(count);
        double len = start;
        for (int i = 0; i < count; i++) {
            PolynomialSplineVelocity midVelocity = new PolynomialSplineVelocity(velocityBaseTsd, (long) (barSize * len), multiplier);
            velocities.add(midVelocity);
            len += step;
        }
        return velocities;
    }


    private BaseTimesSeriesData getOrCreateEma(ITimesSeriesData tsd, long barSize, float length) {
        return new SlidingTicksRegressor(tsd, (long) (length * barSize * m_linRegMultiplier));
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

        ITickData latestTick2 = m_velocityAdj.getLatestTick();
        if (latestTick2 == null) {
            return null; // not ready yet
        }

        long timestamp = parentLatestTick.getTimestamp();
        m_tickData = new TickData(timestamp, (m_minMaxSpread.m_powAdj + m_velocityAdj.m_adj) / 2);
        return m_tickData;
    }

    private TicksTimesSeriesData<TickData> getPowAdjTs() {
        return new JoinNonChangedInnerTimesSeriesData(this) {
            @Override protected Float getValue() {
                return m_minMaxSpread.m_powAdj;
            }
        };
    }

    @Override public void setupChart(boolean collectValues, ChartCanvas chartCanvas, BaseTicksTimesSeriesData<TickData> ticksTs, Watcher firstWatcher) {
        ChartData chartData = chartCanvas.getChartData();
        ChartSetting chartSetting = chartCanvas.getChartSetting();

        // layout
        ChartAreaSettings top = chartSetting.addChartAreaSettings("top", 0, 0, 1, 0.99f, Color.RED);
        List<ChartAreaLayerSettings> topLayers = top.getLayers();
        {
            addChart(chartData, ticksTs, topLayers, "price", Colors.alpha(Color.RED, 70), TickPainter.TICK);

//            chartData.setTicksData("spline", new NoTicksData());
////            topLayers.add(new ChartAreaLayerSettings("spline", Color.RED, new ChartAreaPainter.SplineChartAreaPainter(ticksTs, 4)));
//            topLayers.add(new ChartAreaLayerSettings("spline", Color.RED, new ChartAreaPainter.PolynomChartAreaPainter(ticksTs)));

            Color emaColor = Colors.alpha(Color.BLUE, 25);
            int size = m_emas.size();
            for (int i = size - 1; i > 0; i--) {
                BaseTimesSeriesData ema = m_emas.get(i);
                Color color = (i == size - 1) ? Colors.alpha(Color.GRAY, 50) : emaColor;
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
//        ChartAreaSettings value = chartSetting.addChartAreaSettings("value", 0, 0.6f, 1, 0.2f, Color.LIGHT_GRAY);
//        List<ChartAreaLayerSettings> valueLayers = value.getLayers();
//        {
//            addChart(chartData, getJoinNonChangedTs(), valueLayers, "value", Color.blue, TickPainter.LINE);
//            addChart(chartData, getPowAdjTs(), valueLayers, "powValue", Color.MAGENTA, TickPainter.LINE);
//            addChart(chartData, m_velocityAdj.getJoinNonChangedTs(), valueLayers, "velAdj", Color.RED, TickPainter.LINE);
//        }
//
//        if (collectValues) {
//            ChartAreaSettings gain = chartSetting.addChartAreaSettings("gain", 0, 0.8f, 1, 0.2f, Color.ORANGE);
//            gain.addHorizontalLineValue(1);
//
//            addChart(chartData, firstWatcher, topLayers, "trades", Color.WHITE, TickPainter.TRADE);
//
//            List<ChartAreaLayerSettings> gainLayers = gain.getLayers();
//            addChart(chartData, firstWatcher.getGainTs(), gainLayers, "gain", Color.blue, TickPainter.LINE);
//        }
    }


    @Override public String key(boolean detailed) {
        return  ""
                + (detailed ? ",start=" : ",") + m_start
                + (detailed ? ",step=" : ",") + m_step
                + (detailed ? ",count=" : ",") + m_count
                + (detailed ? ",drop=" : ",") + m_drop
                + (detailed ? ",smooth=" : ",") + m_smooth
                + (detailed ? ",power=" : ",") + m_power
                + (detailed ? ",multiplier=" : ",") + m_linRegMultiplier
                + (detailed ? ",threshold=" : ",") + m_threshold
//                /*+ ", " + Utils.millisToYDHMSStr(period)*/;
                ;
    }

    private static class NoTicksData implements ITicksData {
        @Override public ITickData getTick(int index) {
            throw new RuntimeException("should not be called"); // or return 0 ?
        }

        public int getTicksNum() {
            throw new RuntimeException("should not be called"); // or return 0 ?
        }

        @Override public Object syncObject() {
            throw new RuntimeException("should not be called"); // or return 0 ?
        }

        @Override public Iterator getTicksIterator() {
            throw new RuntimeException("should not be called"); // or return 0 ?
        }

        @Override public Iterable getTicksIterable() {
            throw new RuntimeException("should not be called"); // or return 0 ?
        }

        @Override public ReverseListIterator getReverseTicksIterator() {
            throw new RuntimeException("should not be called"); // or return 0 ?
        }

        @Override public Iterable getReverseTicksIterable() {
            throw new RuntimeException("should not be called"); // or return 0 ?
        }

        @Override public int binarySearch(ITickData o, Comparator comparator) {
            throw new RuntimeException("should not be called"); // or return 0 ?
        }

        @Override public ITimesSeriesData getParent() {
            return null;
        }

        @Override public ITickData getLatestTick() {
            return null;
        }

        @Override public void addListener(ITimesSeriesListener listener) {

        }

        @Override public void removeListener(ITimesSeriesListener listener) {

        }

        @Override public ITimesSeriesData getActive() {
            return null;
        }
    }

    //----------------------------------------------------------
    private class VelocityAdj extends BaseTimesSeriesData<ITickData> {
        private final float m_threshold;
        private boolean m_dirty;
        private ITickData m_tick;
        private boolean m_goUp;
        private float m_min;
        private float m_max;
        private float m_adj;

        VelocityAdj(ITimesSeriesData parent, float threshold) {
            super(parent);
            m_threshold = threshold;
            m_min = -m_threshold;
            m_max = m_threshold;
        }

        @Override public void onChanged(ITimesSeriesData ts, boolean changed) {
            if (changed) {
                m_dirty = true;
            }
            super.onChanged(ts, changed);
        }

        @Override public ITickData getLatestTick() {
            if (m_dirty) {
                ITickData lastTick = getParent().getLatestTick();
                if (lastTick != null) {
                    float velocity = lastTick.getClosePrice();

                    boolean goUp = (velocity >= m_threshold)
                            ? true // go up
                            : ((velocity <= -m_threshold)
                                ? false // go down
                                : m_goUp); // do not change

                    float min = Math.min(m_min, velocity);
                    float max = Math.max(m_max, velocity);
                    if (goUp != m_goUp) { // direction changed
                        if (goUp) {
                            min = -m_threshold;
                        } else {
                            max = m_threshold;
                        }
                    }

                    float rate = (velocity - min) / (max - min); // [0...1]
                    m_adj = rate * 2 - 1;

                    m_min = min;
                    m_max = max;

                    m_goUp = goUp;

                    m_tick = new TickData(getParent().getLatestTick().getTimestamp(), m_adj);
                    m_dirty = false;
                }
            }
            return m_tick;
        }

        TicksTimesSeriesData<TickData> getMinTs() {
            return new JoinNonChangedInnerTimesSeriesData(this) {
                @Override protected Float getValue() {
                    return m_min;
                }
            };
        }

        TicksTimesSeriesData<TickData> getMaxTs() {
            return new JoinNonChangedInnerTimesSeriesData(this) {
                @Override protected Float getValue() {
                    return m_max;
                }
            };
        }
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

        TicksTimesSeriesData<TickData> getMinTs() {
            return new JoinNonChangedInnerTimesSeriesData(this) {
                @Override protected Float getValue() {
                    return m_min;
                }
            };
        }

        TicksTimesSeriesData<TickData> getMaxTs() {
            return new JoinNonChangedInnerTimesSeriesData(this) {
                @Override protected Float getValue() {
                    return m_max;
                }
            };
        }

        TicksTimesSeriesData<TickData> getRibbonSpreadMaxTs() {
            return new JoinNonChangedInnerTimesSeriesData(this) {
                @Override protected Float getValue() {
                    return m_ribbonSpreadMax;
                }
            };
        }

        TicksTimesSeriesData<TickData> getRibbonSpreadMaxTopTs() {
            return new JoinNonChangedInnerTimesSeriesData(this) {
                @Override protected Float getValue() {
                    return m_ribbonSpreadMaxTop;
                }
            };
        }

        TicksTimesSeriesData<TickData> getRibbonSpreadMaxBottomTs() {
            return new JoinNonChangedInnerTimesSeriesData(this) {
                @Override protected Float getValue() {
                    return m_ribbonSpreadMaxBottom;
                }
            };
        }

        TicksTimesSeriesData<TickData> getRibbonSpreadFadingTs() {
            return new JoinNonChangedInnerTimesSeriesData(this) {
                @Override protected Float getValue() {
                    return m_ribbonSpreadFading;
                }
            };
        }

        TicksTimesSeriesData<TickData> getRibbonSpreadFadingTopTs() {
            return new JoinNonChangedInnerTimesSeriesData(this) {
                @Override protected Float getValue() {
                    return m_ribbonSpreadFadingTop;
                }
            };
        }

        TicksTimesSeriesData<TickData> getRibbonSpreadFadingBottomTs() {
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
}
