package bi.two.algo.impl;

import bi.two.ChartCanvas;
import bi.two.Colors;
import bi.two.algo.BaseAlgo;
import bi.two.algo.Watcher;
import bi.two.calc.BarsEMA;
import bi.two.calc.SlidingTicksRegressor;
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
    private final List<BaseTimesSeriesData> m_emas = new ArrayList<>();
    private final MinMaxSpread m_minMaxSpread;
    private final BaseTimesSeriesData m_spreadSmoothed;

    public Mmar3Algo(MapConfig config, ITimesSeriesData tsd) {
        super(null);

        boolean collectValues = config.getBoolean(BaseAlgo.COLLECT_VALUES_KEY);
        m_barSize = config.getNumber(Vary.period).longValue();
        m_start = config.getNumber(Vary.start).floatValue();
        m_step = config.getNumber(Vary.step).floatValue();
        m_count = config.getNumber(Vary.count).floatValue();
        m_drop = config.getNumber(Vary.drop).floatValue();
        m_smooth = config.getNumber(Vary.smooth).floatValue();

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

        m_minMaxSpread = new MinMaxSpread(iEmas, tsd);

        m_spreadSmoothed = new BarsEMA(m_minMaxSpread, m_smooth, m_barSize);


    }

    private BaseTimesSeriesData getOrCreateEma(ITimesSeriesData tsd, long barSize, float length) {
        return new SlidingTicksRegressor(tsd, (long) (length * barSize));
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
        }

        ChartAreaSettings bottom = chartSetting.addChartAreaSettings("indicator", 0, 0.4f, 1, 0.2f, Color.GREEN);
        List<ChartAreaLayerSettings> bottomLayers = bottom.getLayers();
        {
        }

        ChartAreaSettings value = chartSetting.addChartAreaSettings("value", 0, 0.6f, 1, 0.2f, Color.LIGHT_GRAY);
        List<ChartAreaLayerSettings> valueLayers = value.getLayers();
        {
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


    @Override public String key(boolean detailed) {
        return  ""
//                + (detailed ? ",start=" : ",") + m_start
//                + (detailed ? ",step=" : ",") + m_step
//                + (detailed ? ",count=" : ",") + m_count
//                + (detailed ? ",drop=" : ",") + m_drop
//                + (detailed ? ",smooth=" : ",") + m_smooth
//                + (detailed ? ",power=" : ",") + m_power
//                /*+ ", " + Utils.millisToYDHMSStr(period)*/;
                ;
    }

    //----------------------------------------------------------
    public static class MinMaxSpread extends BaseTimesSeriesData<ITickData> {
        private final List<ITimesSeriesData> m_tsd;
        private final ITimesSeriesData m_first;
        private boolean m_dirty;
        private ITickData m_tick;
        private float m_spread;
        private float m_min;
        private float m_max;
        private float m_mid;
        private BaseTimesSeriesData m_midTs;

        MinMaxSpread(List<ITimesSeriesData> tsd, ITimesSeriesData baseTsd) {
            super(null);
            m_tsd = tsd;

            for (ITimesSeriesData<ITickData> next : tsd) {
                next.getActive().addListener(this);
            }
            m_first = tsd.get(0);

            m_midTs = new BaseTimesSeriesData(this) {
                @Override public ITickData getLatestTick() {
                    ITickData latestTick = getParent().getLatestTick();
                    if (latestTick != null) {
                        return new TickData(latestTick.getTimestamp(), m_mid);
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
                for (ITimesSeriesData<ITickData> next : m_tsd) {
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

        ITicksData<TickData> getMidTs() {
            return m_midTs.getJoinNonChangedTs();
        }
    }
}
