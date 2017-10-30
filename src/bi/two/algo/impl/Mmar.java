package bi.two.algo.impl;

import bi.two.ChartCanvas;
import bi.two.Colors;
import bi.two.algo.BaseAlgo;
import bi.two.algo.Watcher;
import bi.two.calc.BarsTEMA;
import bi.two.calc.TicksRegressor;
import bi.two.chart.*;
import bi.two.opt.Vary;
import bi.two.ts.ITimesSeriesData;
import bi.two.ts.TimesSeriesData;
import bi.two.util.MapConfig;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class Mmar extends BaseAlgo {
    private final long m_barSize;
    private final float m_start;
    private final float m_step;
    private final float m_count;
    private final float m_smooth;
    private final List<BarsTEMA> m_emas = new ArrayList<>();
    private final List<TicksRegressor> m_emasSm = new ArrayList<>(); //smooched
    private final TicksRegressor m_firstEmaSm;
    private TickData m_tickData; // latest tick
//    private final TicksRegressor m_first;

    public Mmar(MapConfig config, ITimesSeriesData tsd) {
        super(null);

        m_barSize = config.getNumber(Vary.period).longValue();
        m_start = config.getNumber(Vary.start).floatValue();
        m_step = config.getNumber(Vary.step).floatValue();
        m_count = config.getNumber(Vary.count).floatValue();
        m_smooth = config.getNumber(Vary.smooth).floatValue();

        TicksRegressor first = null;
        float length = m_start;
        for (int i = 0; i < m_count; i++) {
            BarsTEMA ema = new BarsTEMA(tsd, length, m_barSize);
            m_emas.add(ema);
            TicksRegressor emaSm = new TicksRegressor(ema, (long) (m_smooth *  m_barSize));
            m_emasSm.add(emaSm);

            length += m_step;
            if (i == 0) {
                first = emaSm;
            }
        }
        m_firstEmaSm = first;

//        m_first = new TicksRegressor(firstEma, 5 *  m_barSize);

        setParent(m_firstEmaSm);
    }

    @Override public ITickData getAdjusted() {
        ITickData latestTick = m_firstEmaSm.getLatestTick();
        if (latestTick == null) {
            return null; // not yet defined/filled
        }
        double firstValue = latestTick.getClosePrice();
        double minValue = Double.POSITIVE_INFINITY;
        double maxValue = Double.NEGATIVE_INFINITY;
        for (TicksRegressor emaSm : m_emasSm) {
            latestTick = emaSm.getLatestTick();
            if (latestTick == null) {
                return null; // not yet defined/filled
            }
            double value = latestTick.getClosePrice();
            minValue = Math.min(minValue, value);
            maxValue = Math.max(maxValue, value);
        }

        double spread = maxValue - minValue;
        double adj = (firstValue - minValue) / spread * 2 - 1; // [-1;1]
        long timestamp = getParent().getLatestTick().getTimestamp();

        float adjFloat = (float) adj;
        m_tickData = new TickData(timestamp, adjFloat);
        return m_tickData;
    }

    @Override public ITickData getLatestTick() {
        return m_tickData;
    }

    @Override public String key(boolean detailed) {
        return  ""
                        + (detailed ? ",start=" : ",") + m_start
                        + (detailed ? ",step=" : ",") + m_step
                        + (detailed ? ",count=" : ",") + m_count
                        + (detailed ? ",smooth=" : ",") + m_smooth
//                /*+ ", " + Utils.millisToYDHMSStr(period)*/;
        ;
    }

    @Override public void setupChart(boolean collectValues, ChartCanvas chartCanvas, TimesSeriesData ticksTs, Watcher firstWatcher) {
        ChartData chartData = chartCanvas.getChartData();
        ChartSetting chartSetting = chartCanvas.getChartSetting();

        // layout
        ChartAreaSettings top = chartSetting.addChartAreaSettings("top", 0, 0, 1, 0.6f, Color.RED);
        List<ChartAreaLayerSettings> topLayers = top.getLayers();
        {
            addChart(chartData, ticksTs, topLayers, "price", Colors.alpha(Color.RED, 70), TickPainter.TICK);
            Color emaColor = Colors.alpha(Color.BLUE, 30);
            Color emaSmColor = Colors.alpha(Color.PINK, 50);
            int size = m_emas.size();
            for (int i = 0; i < size; i++) {
                BarsTEMA ema = m_emas.get(i);
                Color color = (i == 0) ? Color.BLUE : (i == size - 1) ? Colors.alpha(Color.GRAY, 100) : emaColor;
                addChart(chartData, ema.getJoinNonChangedTs(), topLayers, "ema" + i, color, TickPainter.LINE);

                TicksRegressor emaSm = m_emasSm.get(i);
                Color colorSm = (i == 0) ? Color.PINK : (i == size - 1) ? Colors.alpha(Color.GRAY, 100) : emaSmColor;
                addChart(chartData, emaSm.getJoinNonChangedTs(), topLayers, "emaSm" + i, colorSm, TickPainter.LINE);
            }

//            addChart(chartData, m_first.getJoinNonChangedTs(), topLayers, "first", Color.PINK, TickPainter.LINE);
        }

//        ChartAreaSettings bottom = chartSetting.addChartAreaSettings("indicator", 0, 0.4f, 1, 0.2f, Color.GREEN);
//        List<ChartAreaLayerSettings> bottomLayers = bottom.getLayers();
//        {
//            // Color.CYAN
//            addChart(chartData, m_macD.getJoinNonChangedTs(), bottomLayers, "macD", Color.PINK, TickPainter.LINE);
//            addChart(chartData, m_signal.getJoinNonChangedTs(), bottomLayers, "signal", Color.MAGENTA, TickPainter.LINE);
//            addChart(chartData, m_hist.getJoinNonChangedTs(), bottomLayers, "hist", Color.green, TickPainter.LINE);
//            // Color.ORANGE
//        }
//
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
}
