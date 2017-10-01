package bi.two.algo.impl;

import bi.two.ChartCanvas;
import bi.two.Colors;
import bi.two.algo.BaseAlgo;
import bi.two.algo.Watcher;
import bi.two.calc.ExponentialMovingBarAverager;
import bi.two.calc.FadingTicksAverager;
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
    private final int m_length;
    private final int m_shortLength = 2;
    private final ExponentialMovingBarAverager m_ema;
    private final FadingTicksAverager m_emaShort;
    private final Adjuster m_adjuster;

    public EmaTrendAlgo(MapConfig config, ITimesSeriesData tsd) {
        super(null);

        m_barSize = config.getNumber(Vary.period).longValue();
        m_length = config.getNumber(Vary.bars).intValue();

        m_ema = new ExponentialMovingBarAverager(tsd, m_length, m_barSize);
        m_emaShort = new FadingTicksAverager(tsd, m_shortLength * m_barSize);

        m_adjuster = new Adjuster(m_ema, m_emaShort);
        m_adjuster.addListener(this);
    }

    @Override public ITickData getAdjusted() {
        ITickData lastTick = m_adjuster.getLatestTick();
        return lastTick;
    }

    @Override public String key(boolean detailed) {
        return (detailed ? "len=" : "") + m_length
                /*+ ", " + Utils.millisToDHMSStr(period)*/;
    }

    @Override public void setupChart(boolean collectValues, ChartCanvas chartCanvas, TimesSeriesData ticksTs, Watcher firstWatcher) {
        ChartData chartData = chartCanvas.getChartData();
        ChartSetting chartSetting = chartCanvas.getChartSetting();

        // layout
        ChartAreaSettings top = chartSetting.addChartAreaSettings("top", 0, 0, 1, 0.4f, Color.RED);
        java.util.List<ChartAreaLayerSettings> topLayers = top.getLayers();
        {
            addChart(chartData, ticksTs, topLayers, "price", Colors.alpha(Color.RED, 70), TickPainter.TICK);
            addChart(chartData, m_ema.getJoinNonChangedTs(), topLayers, "ema", Colors.alpha(Color.BLUE, 100), TickPainter.LINE);
            addChart(chartData, m_emaShort.getJoinNonChangedTs(), topLayers, "short.ema", Color.PINK, TickPainter.LINE);
//            addChart(chartData, m_regressorBars, topLayers, "regressor.bars", Color.ORANGE, TickPainter.BAR);
        }

        ChartAreaSettings bottom = chartSetting.addChartAreaSettings("indicator", 0, 0.4f, 1, 0.2f, Color.GREEN);
        java.util.List<ChartAreaLayerSettings> bottomLayers = bottom.getLayers();
        {
//            addChart(chartData, m_powerer.getJoinNonChangedTs(), bottomLayers, "power", Color.CYAN, TickPainter.LINE);
//            addChart(chartData, m_adjuster.getMinTs(), bottomLayers, "min", Color.MAGENTA, TickPainter.LINE);
//            addChart(chartData, m_smoother.getJoinNonChangedTs(), bottomLayers, "zlema", Color.ORANGE, TickPainter.LINE);
//            addChart(chartData, m_adjuster.getMaxTs(), bottomLayers, "max", Color.MAGENTA, TickPainter.LINE);
//            addChart(chartData, m_adjuster.getZeroTs(), bottomLayers, "zero", Colors.alpha(Color.green, 100), TickPainter.LINE);
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
    public static class Adjuster extends BaseTimesSeriesData<ITickData> {
        private final BaseTimesSeriesData<ITickData> m_ema;
        private final FadingTicksAverager m_emaShort;
        private boolean m_dirty;
        private TickData m_tickData;

        Adjuster(BaseTimesSeriesData<ITickData> ema, FadingTicksAverager emaShort) {
            super(null);
            m_ema = ema;
            m_emaShort = emaShort;

            ema.getActive().addListener(this);
            emaShort.getActive().addListener(this);
        }

        @Override public void onChanged(ITimesSeriesData ts, boolean changed) {
            if (changed) {
                m_dirty = true;
            }
            super.onChanged(this, changed); // notifyListeners
        }


        @Override public ITickData getLatestTick() {
            if (m_dirty) {
                ITickData latestEma = m_ema.getLatestTick();
                if(latestEma != null) {
                    ITickData latestEmaShort = m_emaShort.getLatestTick();
                    if(latestEmaShort != null) {
                        float ema = latestEma.getClosePrice();
                        float emaShort = latestEmaShort.getClosePrice();
                        float diff = emaShort - ema;
                        diff = Math.min(diff, 1f);
                        diff = Math.max(diff, -1f);
                        long timestamp = Math.max(latestEma.getTimestamp(), latestEmaShort.getTimestamp());
                        m_tickData = new TickData(timestamp, diff);
                        m_dirty = false;
                    }
                }
            }
            return m_tickData;
        }
    }
}