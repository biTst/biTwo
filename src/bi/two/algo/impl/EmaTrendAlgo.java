package bi.two.algo.impl;

import bi.two.ChartCanvas;
import bi.two.Colors;
import bi.two.algo.BaseAlgo;
import bi.two.algo.Watcher;
import bi.two.calc.Differ;
import bi.two.calc.TicksRegressor;
import bi.two.calc.TicksSimpleAverager;
import bi.two.chart.*;
import bi.two.opt.Vary;
import bi.two.ts.BaseTimesSeriesData;
import bi.two.ts.ITimesSeriesData;
import bi.two.ts.TimesSeriesData;
import bi.two.util.MapConfig;

import java.awt.*;
import java.util.List;

public class EmaTrendAlgo extends BaseAlgo {
    private final long m_barSize;
//    private final float m_length;
    private final float m_longLength;
    private final float m_shortLength;
    private final float m_signalLength; // macD signal len
    private final float m_threshold;
//    private final ExponentialMovingBarAverager m_ema;
//    private final FadingTicksAverager m_emaShort;
    private final Differ m_macD;
    private final Adjuster m_adjuster;
//    private final RegressionAlgo.Regressor2 m_regressor;
    private final TicksRegressor m_regressorSlow;
    private final TicksRegressor m_regressorFast;
    private final TicksSimpleAverager m_signal;
    private final Differ m_hist;

    public EmaTrendAlgo(MapConfig config, ITimesSeriesData tsd) {
        super(null);

        m_barSize = config.getNumber(Vary.period).longValue();
//        m_length = config.getNumber(Vary.emaLen).floatValue();
        m_longLength = config.getNumber(Vary.longEmaLen).floatValue();
        m_shortLength = config.getNumber(Vary.shortEmaLen).floatValue();
        m_signalLength = config.getNumber(Vary.signal).floatValue();
        m_threshold = config.getNumber(Vary.threshold).floatValue();

//        m_ema = new ExponentialMovingBarAverager(tsd, m_length, m_barSize);
//        m_emaShort = new FadingTicksAverager(tsd, (long) (m_shortLength * m_barSize));
//m_regressor = new RegressionAlgo.Regressor2(tsd, m_length, m_barSize, 1);
        m_regressorSlow = new TicksRegressor(tsd, (long) (m_longLength *  m_barSize));
        m_regressorFast = new TicksRegressor(tsd, (long) (m_shortLength *  m_barSize));

//        m_differ = new Differ(m_ema, m_emaShort);
//        m_differ = new Differ(m_ema, m_regressor);
        m_macD = new Differ(m_regressorFast, m_regressorSlow);
        m_signal = new TicksSimpleAverager(m_macD, (long) (m_signalLength *  m_barSize));
        m_hist = new Differ(m_macD, m_signal);

        m_adjuster = new Adjuster(m_hist, tsd, m_threshold);
        m_adjuster.addListener(this);
    }

    @Override public ITickData getAdjusted() {
        ITickData lastTick = m_adjuster.getLatestTick();
        return lastTick;
    }

    @Override public String key(boolean detailed) {
        return
//                (detailed ? "len=" : "") + m_length
                (detailed ? "lLen=" : "") + m_longLength
                + (detailed ? ",sLen=" : ",") + m_shortLength
                + (detailed ? ",sig=" : ",") + m_signalLength
                + (detailed ? ",thr=" : ",") + m_threshold
                /*+ ", " + Utils.millisToYDHMSStr(period)*/;
    }

    @Override public void setupChart(boolean collectValues, ChartCanvas chartCanvas, TimesSeriesData ticksTs, Watcher firstWatcher) {
        ChartData chartData = chartCanvas.getChartData();
        ChartSetting chartSetting = chartCanvas.getChartSetting();

        // layout
        ChartAreaSettings top = chartSetting.addChartAreaSettings("top", 0, 0, 1, 0.4f, Color.RED);
        java.util.List<ChartAreaLayerSettings> topLayers = top.getLayers();
        {
            addChart(chartData, ticksTs, topLayers, "price", Colors.alpha(Color.RED, 70), TickPainter.TICK);
            addChart(chartData, m_regressorSlow.getJoinNonChangedTs(), topLayers, "slow", Colors.alpha(Color.BLUE, 100), TickPainter.LINE);
            addChart(chartData, m_regressorFast.getJoinNonChangedTs(), topLayers, "fast", Colors.alpha(Color.CYAN, 100), TickPainter.LINE);
        }

        ChartAreaSettings bottom = chartSetting.addChartAreaSettings("indicator", 0, 0.4f, 1, 0.2f, Color.GREEN);
        java.util.List<ChartAreaLayerSettings> bottomLayers = bottom.getLayers();
        {
            // Color.CYAN
            addChart(chartData, m_macD.getJoinNonChangedTs(), bottomLayers, "macD", Color.PINK, TickPainter.LINE);
            addChart(chartData, m_signal.getJoinNonChangedTs(), bottomLayers, "signal", Color.MAGENTA, TickPainter.LINE);
            addChart(chartData, m_hist.getJoinNonChangedTs(), bottomLayers, "hist", Color.green, TickPainter.LINE);
            // Color.ORANGE
        }

        ChartAreaSettings value = chartSetting.addChartAreaSettings("value", 0, 0.6f, 1, 0.2f, Color.LIGHT_GRAY);
        java.util.List<ChartAreaLayerSettings> valueLayers = value.getLayers();
        {
            addChart(chartData, m_adjuster.getJoinNonChangedTs(), valueLayers, "value", Color.blue, TickPainter.LINE);
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
    // threshold relative to price
    private static class Adjuster extends BaseTimesSeriesData<ITickData> {
        private final float m_thresholdRate;
        private final ITimesSeriesData m_priceTsd;
        private boolean m_dirty;
        private TickData m_tickData;

        Adjuster(Differ differ, ITimesSeriesData tsd, float threshold) {
            super(differ);
            m_priceTsd = tsd;
            m_thresholdRate = threshold;
        }

        @Override public void onChanged(ITimesSeriesData ts, boolean changed) {
            if (changed) {
                m_dirty = true;
            }
            super.onChanged(this, changed); // notifyListeners
        }

        @Override public ITickData getLatestTick() {
            if (m_dirty) {
                ITimesSeriesData parent = getParent();
                ITickData latestDiff = parent.getLatestTick();
                if (latestDiff != null) {
                    float diff = latestDiff.getClosePrice();

                    float lastPrice = m_priceTsd.getLatestTick().getClosePrice();
                    float threshold = m_thresholdRate * lastPrice / 100;
                    diff = Math.min(diff, threshold);
                    diff = Math.max(diff, -threshold); // [-threshold; threshold]
                    float scaled = diff / threshold; // [-1; 1]

                    long timestamp = latestDiff.getTimestamp();
                    m_tickData = new TickData(timestamp, scaled);
                    m_dirty = false;
                }
            }
            return m_tickData;
        }
    }
}