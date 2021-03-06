package bi.two.algo.impl;

import bi.two.ChartCanvas;
import bi.two.Colors;
import bi.two.algo.BaseAlgo;
import bi.two.algo.Watcher;
import bi.two.calc.SlidingTicksRegressor;
import bi.two.chart.*;
import bi.two.exch.Exchange;
import bi.two.ts.BaseTicksTimesSeriesData;
import bi.two.ts.BaseTimesSeriesData;
import bi.two.ts.ITimesSeriesData;
import bi.two.ts.TickJoinerTimesSeriesData;
import bi.two.util.MapConfig;

import java.awt.*;
import java.util.List;

public class TestAlgo extends BaseAlgo<TickData> {
    private final BaseTimesSeriesData<ITickData> m_ema[] = new BaseTimesSeriesData[2];

    public TestAlgo(MapConfig algoConfig, ITimesSeriesData tsd, Exchange exchange) {
        super(tsd, algoConfig);

        long m_joinTicks = 2;
        ITimesSeriesData ts1 = (m_joinTicks > 0) ? new TickJoinerTimesSeriesData(tsd, m_joinTicks) : tsd;

//        m_ema = new BarsEMA(ts2, 5, 60000);
        m_ema[0] = new SlidingTicksRegressor(ts1, 60000 * 5 * 2, false, true);
        m_ema[1] = new SlidingTicksRegressor(ts1, 60000 * 10 * 2, false, true);

        setParent(ts1);
    }

    @Override public String key(boolean detailed) {
        return "TestAlgo";
    }

    @Override public void reset() {
        // todo: implement
    }

    @Override public void setupChart(boolean collectValues, ChartCanvas chartCanvas, BaseTicksTimesSeriesData<TickData> ticksTs, Watcher firstWatcher) {
        ChartData chartData = chartCanvas.getChartData();
        ChartSetting chartSetting = chartCanvas.getChartSetting();

        // layout
        ChartAreaSettings top = chartSetting.addChartAreaSettings("top", 0, 0, 1, 0.6f, Color.RED);
        java.util.List<ChartAreaLayerSettings> topLayers = top.getLayers();
        {
            int priceAlpha = 255; //70;
            addChart(chartData, ticksTs, topLayers, "price", Colors.alpha(Colors.DARK_RED, priceAlpha), TickPainter.TICK_JOIN );

//            addChart(chartData, m_priceBars, topLayers, "priceBars", Colors.alpha(Colors.DARK_RED, 80), TickPainter.BAR);

            int emaAlpha = 255; //20;
            Color emaColor = Colors.alpha(Color.ORANGE, emaAlpha);
            addChart(chartData, m_ema[0].getJoinNonChangedTs(), topLayers, "ema-0", emaColor, TickPainter.LINE);
            addChart(chartData, m_ema[1].getJoinNonChangedTs(), topLayers, "ema-1", emaColor, TickPainter.LINE);
        }

        ChartAreaSettings power = chartSetting.addChartAreaSettings("power", 0, 0.6f, 1, 0.1f, Color.LIGHT_GRAY);
        java.util.List<ChartAreaLayerSettings> powerLayers = power.getLayers();
        {
//            addChart(chartData, getPowerTs(), powerLayers, "power", Color.MAGENTA, TickPainter.LINE_JOIN);
//            addChart(chartData, getReversePowerTs(), powerLayers, "reversePower", Color.LIGHT_GRAY, TickPainter.LINE_JOIN);
        }

        ChartAreaSettings value = chartSetting.addChartAreaSettings("value", 0, 0.7f, 1, 0.15f, Color.LIGHT_GRAY);
        java.util.List<ChartAreaLayerSettings> valueLayers = value.getLayers();
        {
////            addChart(chartData, getTS(true), valueLayers, "value", Color.blue, TickPainter.LINE);
////            addChart(chartData, getJoinNonChangedTs(), valueLayers, "value", Color.blue, TickPainter.LINE);
//            addChart(chartData, getValueTs(), valueLayers, "value", Colors.alpha(Color.MAGENTA, 128), TickPainter.LINE_JOIN);
//            addChart(chartData, getMulTs(), valueLayers, "mul", Color.GRAY, TickPainter.LINE_JOIN);
//            addChart(chartData, getMulAndPrevTs(), valueLayers, "mulAndPrev", Color.RED, TickPainter.LINE_JOIN);
//            addChart(chartData, getRevMulAndPrevTs(), valueLayers, "revMulAndPrev", Colors.GOLD, TickPainter.LINE_JOIN);
////            addChart(chartData, m_velocityAdj.getJoinNonChangedTs(), valueLayers, "velAdj", Color.RED, TickPainter.LINE);
        }

        if (collectValues) {
            addChart(chartData, firstWatcher, topLayers, "trades", Color.WHITE, TickPainter.TRADE);

            ChartAreaSettings gain = chartSetting.addChartAreaSettings("gain", 0, 0.85f, 1, 0.15f, Color.ORANGE);
            gain.addHorizontalLineValue(1);
            {
                List<ChartAreaLayerSettings> gainLayers = gain.getLayers();
                addChart(chartData, firstWatcher.getGainTs(), gainLayers, "gain", Color.blue, TickPainter.LINE_JOIN);
            }
        }
    }

    @Override public void onTimeShift(long shift) {
        // todo: call super
        notifyOnTimeShift(shift);
//        super.onTimeShift(shift);
    }
}
