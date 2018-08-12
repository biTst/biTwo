package bi.two.algo.impl;

import bi.two.ChartCanvas;
import bi.two.Colors;
import bi.two.algo.BaseAlgo;
import bi.two.algo.Watcher;
import bi.two.chart.*;
import bi.two.ts.ITimesSeriesData;
import bi.two.ts.TimesSeriesData;
import bi.two.util.MapConfig;

import java.awt.*;

public class QummarAlgo extends BaseAlgo {
    public QummarAlgo(MapConfig config, ITimesSeriesData tsd) {
        super(null);

        setParent(tsd);
    }

    @Override public String key(boolean detailed) {
        return  "q"
//                + (detailed ? ",start=" : ",") + m_start
//                + (detailed ? ",step=" : ",") + m_step
//                + (detailed ? ",count=" : ",") + m_count
//                + (detailed ? ",multiplier=" : ",") + m_multiplier
//                + (detailed ? "|s1=" : "|") + m_s1
//                + (detailed ? ",s2=" : ",") + m_s2
//                + (detailed ? ",e1=" : ",") + m_e1
//                + (detailed ? ",e2=" : ",") + m_e2
//                + (detailed ? "|minOrderMul=" : "|") + m_minOrderMul
//                + (detailed ? "|joinTicks=" : "|") + m_joinTicks
//                /*+ ", " + Utils.millisToYDHMSStr(period)*/;
                ;
    }

    @Override public void setupChart(boolean collectValues, ChartCanvas chartCanvas, TimesSeriesData ticksTs, Watcher firstWatcher) {
        ChartData chartData = chartCanvas.getChartData();
        ChartSetting chartSetting = chartCanvas.getChartSetting();

        // layout
        ChartAreaSettings top = chartSetting.addChartAreaSettings("top", 0, 0, 1, 0.6f, Color.RED);
        java.util.List<ChartAreaLayerSettings> topLayers = top.getLayers();
        {
            addChart(chartData, ticksTs, topLayers, "price", Colors.alpha(Color.RED, 70), TickPainter.TICK);
//            addChart(chartData, m_priceBars, topLayers, "priceBars", Colors.alpha(Color.RED, 70), TickPainter.BAR);

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

//            addChart(chartData, getMinTs(), topLayers, "min", Color.MAGENTA, TickPainter.LINE);
//            addChart(chartData, getMaxTs(), topLayers, "max", Color.MAGENTA, TickPainter.LINE);
////
//            addChart(chartData, getRibbonSpreadMaxTopTs(), topLayers, "maxTop", Color.CYAN, TickPainter.LINE);
//            addChart(chartData, getRibbonSpreadMaxBottomTs(), topLayers, "maxBottom", Color.CYAN, TickPainter.LINE);
//            addChart(chartData, getXxxTs(), topLayers, "xxx", Color.GRAY, TickPainter.LINE);
////            addChart(chartData, getTrendTs(), topLayers, "trend", Color.GRAY, TickPainter.LINE);
////            addChart(chartData, getMirrorTs(), topLayers, "mirror", Colors.DARK_RED, TickPainter.LINE);
////            addChart(chartData, getReverseTs(), topLayers, "reverse", Colors.DARK_GREEN, TickPainter.LINE);
//            addChart(chartData, getTopTs(), topLayers, "top", Colors.DARK_RED, TickPainter.LINE);
//            addChart(chartData, getBottomTs(), topLayers, "bottom", Colors.DARK_GREEN, TickPainter.LINE);
//            addChart(chartData, getLevelTs(), topLayers, "level", Color.PINK, TickPainter.LINE);
////            addChart(chartData, m_minMaxSpread.getMidTs(), topLayers, "mid", Color.PINK, TickPainter.LINE);

//            BaseTimesSeriesData leadEma = m_emas.get(0);
//            Color color = Color.GREEN;
//            addChart(chartData, leadEma.getJoinNonChangedTs(), topLayers, "leadEma" , color, TickPainter.LINE);
        }

//        ChartAreaSettings value = chartSetting.addChartAreaSettings("value", 0, 0.6f, 1, 0.2f, Color.LIGHT_GRAY);
//        java.util.List<ChartAreaLayerSettings> valueLayers = value.getLayers();
//        {
//// ???
////            addChart(chartData, getTS(true), valueLayers, "value", Color.blue, TickPainter.LINE);
//            addChart(chartData, getJoinNonChangedTs(), valueLayers, "value", Color.blue, TickPainter.LINE);
////            addChart(chartData, m_minMaxSpread.getAdj2Ts(), valueLayers, "adj2", Color.MAGENTA, TickPainter.LINE);
////            addChart(chartData, getPowAdjTs(), valueLayers, "powValue", Color.MAGENTA, TickPainter.LINE);
////            addChart(chartData, m_velocityAdj.getJoinNonChangedTs(), valueLayers, "velAdj", Color.RED, TickPainter.LINE);
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
