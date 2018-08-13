package bi.two.algo.impl;

import bi.two.ChartCanvas;
import bi.two.Colors;
import bi.two.algo.BaseAlgo;
import bi.two.algo.Watcher;
import bi.two.calc.SlidingTicksRegressor;
import bi.two.chart.*;
import bi.two.ts.BaseTicksTimesSeriesData;
import bi.two.ts.BaseTimesSeriesData;
import bi.two.ts.ITimesSeriesData;
import bi.two.util.MapConfig;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class QummarAlgo extends BaseAlgo<TickData> {
    private final float m_start;
    private final float m_step;
    private final float m_count;
    private final long m_barSize;
    private final float m_linRegMultiplier;

    private final List<BaseTimesSeriesData> m_emas = new ArrayList<>();
    private boolean m_dirty; // when emas are changed

    public QummarAlgo(MapConfig config, ITimesSeriesData tsd) {
        super(null);

        boolean collectValues = false;
        m_start = 5f;
        m_step = 5f;
        m_count = 18f;
        m_barSize = TimeUnit.MINUTES.toMillis(1); // 1min
        m_linRegMultiplier = 2f;

//        ITimesSeriesData priceTsd = TickReader.JOIN_TICKS_IN_READER ? tsd : new TickJoinerTimesSeriesData(tsd, m_joinTicks);
        ITimesSeriesData priceTsd = tsd;
        createRibbon(priceTsd, collectValues);

        setParent(tsd);
    }

    private void createRibbon(ITimesSeriesData tsd, boolean collectValues) {

        ITimesSeriesListener listener = new ITimesSeriesListener() {
            @Override public void onChanged(ITimesSeriesData ts, boolean changed) {
                if (changed) {
                    m_dirty = true;
                }
            }
            @Override public void waitWhenFinished() { }
            @Override public void notifyNoMoreTicks() {}
        };

        List<ITimesSeriesData> iEmas = new ArrayList<>(); // as list of ITimesSeriesData
        float length = m_start;
        int countFloor = (int) m_count;
        for (int i = 0; i < countFloor; i++) {
            BaseTimesSeriesData ema = getOrCreateEma(tsd, m_barSize, length, collectValues);
            ema.getActive().addListener(listener);
            m_emas.add(ema);
            iEmas.add(ema);
            length += m_step;
        }
        if (m_count != countFloor) {
            float fraction = m_count - countFloor;
            float fractionLength = length - m_step + m_step * fraction;
            BaseTimesSeriesData ema = getOrCreateEma(tsd, m_barSize, fractionLength, collectValues);
            ema.getActive().addListener(listener);
            m_emas.add(ema);
            iEmas.add(ema);
        }
    }

    private BaseTimesSeriesData getOrCreateEma(ITimesSeriesData tsd, long barSize, float length, boolean collectValues) {
        long period = (long) (length * barSize * m_linRegMultiplier);
        return new SlidingTicksRegressor(tsd, period, collectValues);
//        return new BarsEMA(tsd, length, barSize);
//        return new BarsDEMA(tsd, length, barSize);
//        return new BarsTEMA(tsd, length, barSize);
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

    @Override public void setupChart(boolean collectValues, ChartCanvas chartCanvas, BaseTicksTimesSeriesData<TickData> ticksTs, Watcher firstWatcher) {
        ChartData chartData = chartCanvas.getChartData();
        ChartSetting chartSetting = chartCanvas.getChartSetting();

        // layout
        ChartAreaSettings top = chartSetting.addChartAreaSettings("top", 0, 0, 1, 0.9f, Color.RED);
        java.util.List<ChartAreaLayerSettings> topLayers = top.getLayers();
        {
            addChart(chartData, ticksTs, topLayers, "price", Colors.alpha(Color.RED, 50), TickPainter.TICK);
//            addChart(chartData, m_priceBars, topLayers, "priceBars", Colors.alpha(Color.RED, 70), TickPainter.BAR);

//            chartData.setTicksData("spline", new NoTicksData());
////            topLayers.add(new ChartAreaLayerSettings("spline", Color.RED, new ChartAreaPainter.SplineChartAreaPainter(ticksTs, 4)));
//            topLayers.add(new ChartAreaLayerSettings("spline", Color.RED, new ChartAreaPainter.PolynomChartAreaPainter(ticksTs)));

            int emaAlpha = 25;
            Color emaColor = Colors.alpha(Color.BLUE, emaAlpha);
            int size = m_emas.size();
            for (int i = size - 1; i > 0; i--) { // paint without leadEma
                BaseTimesSeriesData ema = m_emas.get(i);
                Color color = (i == size - 1)
                        ? Colors.alpha(Color.GRAY, emaAlpha*2) // slowest ema
                        : emaColor;
                addChart(chartData, ema.getJoinNonChangedTs(), topLayers, "ema" + i, color, TickPainter.LINE);
            }

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

            BaseTimesSeriesData leadEma = m_emas.get(0); // fastest ema
            Color color = Colors.alpha(Color.GREEN, emaAlpha * 2);
            addChart(chartData, leadEma.getJoinNonChangedTs(), topLayers, "leadEma", color, TickPainter.LINE);
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
