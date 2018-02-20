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

    private final long m_barSize;
    private final float m_start;
    private final float m_step;
    private final float m_count;
    private final float m_multiplier;
    private final List<BaseTimesSeriesData> m_emas = new ArrayList<>();
    private final MinMaxSpread m_minMaxSpread;

    public UmmarAlgo(MapConfig config, ITimesSeriesData tsd) {
        super(null);

        boolean collectValues = config.getBoolean(BaseAlgo.COLLECT_VALUES_KEY);
        m_barSize = config.getNumber(Vary.period).longValue();
        m_start = config.getNumber(Vary.start).floatValue();
        m_step = config.getNumber(Vary.step).floatValue();
        m_count = config.getNumber(Vary.count).floatValue();
//        m_drop = config.getNumber(Vary.drop).floatValue();
//        m_smooth = config.getNumber(Vary.smooth).floatValue();
//        m_power = config.getNumber(Vary.power).floatValue();
        m_multiplier = config.getNumber(Vary.multiplier).floatValue();
//        m_threshold = config.getNumber(Vary.threshold).floatValue();

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
        if (m_count != countFloor) {
            float fraction = m_count - countFloor;
            float fractionLength = length - m_step + m_step * fraction;
            BaseTimesSeriesData ema = getOrCreateEma(tsd, m_barSize, fractionLength);
            m_emas.add(ema);
            iEmas.add(ema);
        }

        m_minMaxSpread = new MinMaxSpread(iEmas, tsd, collectValues);
    }

    private BaseTimesSeriesData getOrCreateEma(ITimesSeriesData tsd, long barSize, float length) {
        return new SlidingTicksRegressor(tsd, (long) (length * barSize * m_multiplier));
//        return new BarsEMA(tsd, length, barSize);
//        return new BarsDEMA(tsd, length, barSize);
//        return new BarsTEMA(tsd, length, barSize);
    }


    @Override public String key(boolean detailed) {
        return  ""
                + (detailed ? ",start=" : ",") + m_start
                + (detailed ? ",step=" : ",") + m_step
                + (detailed ? ",count=" : ",") + m_count
//                + (detailed ? ",drop=" : ",") + m_drop
//                + (detailed ? ",smooth=" : ",") + m_smooth
//                + (detailed ? ",power=" : ",") + m_power
                + (detailed ? ",multiplier=" : ",") + m_multiplier
//                + (detailed ? ",threshold=" : ",") + m_threshold
//                /*+ ", " + Utils.millisToYDHMSStr(period)*/;
                ;
    }

    @Override public void setupChart(boolean collectValues, ChartCanvas chartCanvas, TimesSeriesData ticksTs, Watcher firstWatcher) {
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
            addChart(chartData, m_minMaxSpread.getXxx50Ts(), topLayers, "xxx50", Color.GRAY, TickPainter.LINE);

            //            addChart(chartData, m_minMaxSpread.getRibbonSpreadFadingTopTs(), topLayers, "fadeTop", Color.PINK, TickPainter.LINE);
//            addChart(chartData, m_minMaxSpread.getRibbonSpreadFadingBottomTs(), topLayers, "fadeBottom", Color.PINK, TickPainter.LINE);
//            addChart(chartData, m_minMaxSpread.m_ribbonSpreadFadingMidTs.getJoinNonChangedTs(), topLayers, "fadeMid", Color.green, TickPainter.LINE);
//            addChart(chartData, m_minMaxSpread.m_ribbonSpreadFadingMidMidTs.getJoinNonChangedTs(), topLayers, "fadeMidMid", Color.yellow, TickPainter.LINE);
//            addChart(chartData, m_minMaxSpread.m_ribbonSpreadFadingMidMidAdjTs.getJoinNonChangedTs(), topLayers, "fadeMidMidAdj", Color.RED, TickPainter.LINE);

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
//            gain.setHorizontalLineValue(1);
//
//            addChart(chartData, firstWatcher, topLayers, "trades", Color.WHITE, TickPainter.TRADE);
//
//            List<ChartAreaLayerSettings> gainLayers = gain.getLayers();
//            addChart(chartData, firstWatcher.getGainTs(), gainLayers, "gain", Color.blue, TickPainter.LINE);
//        }
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
//        private float m_ribbonSpread;
//        private final BaseTimesSeriesData m_ribbonSpreadFadingMidMidAdjTs;
//        private final BaseTimesSeriesData m_ribbonSpreadFadingMidMidTs;
//        private final BaseTimesSeriesData m_ribbonSpreadFadingMidTs;
//        private BaseTimesSeriesData m_midTs;
//        private float m_mid;
        private float m_ribbonSpreadMax;
        private float m_ribbonSpreadMaxTop;
        private float m_ribbonSpreadMaxBottom;
        private Float m_xxx;
        private Float m_xxx50;
//        private float m_ribbonSpreadFading;
//        private float m_ribbonSpreadFadingTop;
//        private float m_ribbonSpreadFadingBottom;
//        private float m_ribbonSpreadFadingMid;
//        private float m_leadEmaValue;
//        private float m_adj;
//        private float m_powAdj;
//        private float m_ribbonRate;

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

//            m_ribbonSpreadFadingMidMidTs = new BaseTimesSeriesData(this) {
//                @Override public ITickData getLatestTick() {
//                    ITickData latestTick = getParent().getLatestTick();
//                    if (latestTick != null) {
//                        return new TickData(latestTick.getTimestamp(), (m_ribbonSpreadFadingMid + m_mid) / 2);
//                    }
//                    return null;
//                }
//            };

//            m_ribbonSpreadFadingMidMidAdjTs = new BaseTimesSeriesData(this) {
//                @Override public ITickData getLatestTick() {
//                    ITickData latestTick = getParent().getLatestTick();
//                    if (latestTick != null) {
//                        float rate = (m_ribbonSpreadFadingMid > m_mid)
//                                ? m_mid + (m_ribbonSpreadFadingMid - m_mid) * m_ribbonRate
//                                : m_ribbonSpreadFadingMid + (m_mid - m_ribbonSpreadFadingMid) * m_ribbonRate;
//                        return new TickData(latestTick.getTimestamp(), rate);
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

                    m_min = min;
                    m_max = max;
//                    m_mid = (max + min) / 2;

                    float ribbonSpread = max - min;
//                    m_ribbonRate = (m_leadEmaValue - min) / ribbonSpread; // [0...1]
//
                    boolean directionChanged = goUp != m_goUp;
                    m_ribbonSpreadMax = directionChanged // direction changed
                            ? ribbonSpread //reset
                            : Math.max(ribbonSpread, m_ribbonSpreadMax);
                    m_ribbonSpreadMaxTop = goUp ? min + m_ribbonSpreadMax : max;
                    m_ribbonSpreadMaxBottom = goUp ? min : max - m_ribbonSpreadMax;

                    if (directionChanged) {
                        m_xxx = goUp ? max : min;
                    }

                    if (m_xxx != null) {
                        m_xxx50 = goUp ? (m_ribbonSpreadMaxTop + m_xxx) / 2 : (m_ribbonSpreadMaxBottom + m_xxx) / 2;
                    }
//
//                    m_ribbonSpreadFading = (goUp != m_goUp)
//                            ? ribbonSpread
//                            : Math.max(ribbonSpread, ribbonSpread < m_ribbonSpread
//                            ? m_ribbonSpreadFading - (m_ribbonSpread - ribbonSpread) * m_drop
//                            : m_ribbonSpreadFading);
//                    m_ribbonSpreadFadingTop = goUp ? min + m_ribbonSpreadFading : max;
//                    m_ribbonSpreadFadingBottom = goUp ? min : max - m_ribbonSpreadFading;
//                    m_ribbonSpreadFadingMid = (m_ribbonSpreadFadingTop + m_ribbonSpreadFadingBottom) / 2;
//

//                    m_ribbonSpread = ribbonSpread;
                    m_goUp = goUp;

//                    m_leadEmaValue = leadEmaValue;
//
//                    float adjRate = (m_leadEmaValue - m_ribbonSpreadFadingBottom) / m_ribbonSpreadFading; // [0...1]
//                    float powAdjRate = (float) (goUp
//                            ? 1 - Math.pow(1 - adjRate, m_power)
//                            : Math.pow(adjRate, m_power));
//
//                    m_adj = adjRate * 2 - 1;
//                    m_powAdj = powAdjRate * 2 - 1;

                    m_tick = new TickData(getParent().getLatestTick().getTimestamp(), ribbonSpread);
                    m_dirty = false;
                }
            }
            return m_tick;
        }

        TimesSeriesData<TickData> getMinTs() { return new JoinNonChangedInnerTimesSeriesData(this) { @Override protected Float getValue() { return m_min; } }; }
        TimesSeriesData<TickData> getMaxTs() { return new JoinNonChangedInnerTimesSeriesData(this) { @Override protected Float getValue() { return m_max; } }; }

//        ITicksData<TickData> getMidTs() {
//            return m_midTs.getJoinNonChangedTs();
//        }
//
//        TimesSeriesData<TickData> getRibbonSpreadMaxTs() {
//            return new JoinNonChangedInnerTimesSeriesData(this) {
//                @Override protected Float getValue() {
//                    return m_ribbonSpreadMax;
//                }
//            };
//        }
//
        TimesSeriesData<TickData> getRibbonSpreadMaxTopTs() { return new JoinNonChangedInnerTimesSeriesData(this) { @Override protected Float getValue() { return m_ribbonSpreadMaxTop; } }; }
        TimesSeriesData<TickData> getRibbonSpreadMaxBottomTs() { return new JoinNonChangedInnerTimesSeriesData(this) { @Override protected Float getValue() { return m_ribbonSpreadMaxBottom; } }; }
        TimesSeriesData<TickData> getXxxTs() { return new JoinNonChangedInnerTimesSeriesData(this) { @Override protected Float getValue() { return m_xxx; } }; }
        TimesSeriesData<TickData> getXxx50Ts() { return new JoinNonChangedInnerTimesSeriesData(this) { @Override protected Float getValue() { return m_xxx50; } }; }
//
//        TimesSeriesData<TickData> getRibbonSpreadFadingTs() {
//            return new JoinNonChangedInnerTimesSeriesData(this) {
//                @Override protected Float getValue() {
//                    return m_ribbonSpreadFading;
//                }
//            };
//        }
//
//        TimesSeriesData<TickData> getRibbonSpreadFadingTopTs() {
//            return new JoinNonChangedInnerTimesSeriesData(this) {
//                @Override protected Float getValue() {
//                    return m_ribbonSpreadFadingTop;
//                }
//            };
//        }
//
//        TimesSeriesData<TickData> getRibbonSpreadFadingBottomTs() {
//            return new JoinNonChangedInnerTimesSeriesData(this) {
//                @Override protected Float getValue() {
//                    return m_ribbonSpreadFadingBottom;
//                }
//            };
//        }
//
    }
}
