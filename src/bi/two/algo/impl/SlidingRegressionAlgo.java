package bi.two.algo.impl;

import bi.two.ChartCanvas;
import bi.two.Colors;
import bi.two.algo.BaseAlgo;
import bi.two.algo.Watcher;
import bi.two.calc.SlidingTicksRegressor;
import bi.two.calc.TicksRegressor;
import bi.two.chart.*;
import bi.two.opt.Vary;
import bi.two.ts.BaseTicksTimesSeriesData;
import bi.two.ts.ITimesSeriesData;
import bi.two.util.MapConfig;

import java.awt.*;
import java.util.List;

public class SlidingRegressionAlgo extends BaseAlgo<TickData> {
    private final float m_barSize;
    private final float m_length;
    private final TicksRegressor m_regressor;
    private final SlidingTicksRegressor m_slidingRegressor;

    public SlidingRegressionAlgo(MapConfig algoConfig, ITimesSeriesData tsd) {
        super(tsd, algoConfig);

        m_barSize = algoConfig.getNumber(Vary.period).longValue();
        m_length = algoConfig.getNumber(Vary.slope).floatValue();

        m_regressor = new TicksRegressor(tsd, (long) (m_length *  m_barSize));
        m_slidingRegressor = new SlidingTicksRegressor(tsd, (long) (m_length *  m_barSize));

    }

    @Override public String key(boolean detailed) {
        return ""
                + (detailed ? "len=" : "") + m_length
                /*+ ", " + Utils.millisToYDHMSStr(period)*/;
    }

    @Override public void setupChart(boolean collectValues, ChartCanvas chartCanvas, BaseTicksTimesSeriesData<TickData> ticksTs, Watcher firstWatcher) {
        ChartData chartData = chartCanvas.getChartData();
        ChartSetting chartSetting = chartCanvas.getChartSetting();

        // layout
        ChartAreaSettings top = chartSetting.addChartAreaSettings("top", 0, 0, 1, 0.9f, Color.RED);
        List<ChartAreaLayerSettings> topLayers = top.getLayers();
        {
            addChart(chartData, ticksTs, topLayers, "price", Colors.alpha(Color.RED, 70), TickPainter.TICK);

            addChart(chartData, m_regressor.getJoinNonChangedTs(), topLayers, "regressor", Color.MAGENTA, TickPainter.LINE);
            addChart(chartData, m_slidingRegressor.getJoinNonChangedTs(), topLayers, "slidingRegressor", Color.ORANGE, TickPainter.LINE);
        }

//        ChartAreaSettings bottom = chartSetting.addChartAreaSettings("indicator", 0, 0.4f, 1, 0.2f, Color.GREEN);
//        List<ChartAreaLayerSettings> bottomLayers = bottom.getLayers();
//        {
//            addChart(chartData, m_mainLevel.m_velocities.m_midVelocity1.getJoinNonChangedTs(), bottomLayers, "midVel1", Color.PINK, TickPainter.LINE);
//        }

//        ChartAreaSettings value = chartSetting.addChartAreaSettings("value", 0, 0.6f, 1, 0.2f, Color.LIGHT_GRAY);
//        List<ChartAreaLayerSettings> valueLayers = value.getLayers();
//        {
//            addChart(chartData, getRibbonTs(), valueLayers, "ribbon", Color.white, TickPainter.LINE);
//        }

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
}
