package bi.two.algo.impl;

import bi.two.ChartCanvas;
import bi.two.Colors;
import bi.two.algo.Watcher;
import bi.two.chart.*;
import bi.two.exch.Exchange;
import bi.two.ts.BaseTicksTimesSeriesData;
import bi.two.ts.BaseTimesSeriesData;
import bi.two.ts.ITimesSeriesData;
import bi.two.ts.TicksTimesSeriesData;
import bi.two.util.MapConfig;
import bi.two.util.Utils;

import java.awt.*;
import java.util.List;

import static bi.two.util.Log.console;

public class DoubleHeadAlgo extends BaseRibbonAlgo3 {
    private static final boolean ADJUST_TAIL = true;
    public static final int RATE = 2;

    static {
        console("ADJUST_TAIL=" + ADJUST_TAIL); // todo
    }

    private Float m_headCollapseDouble;
    private Float m_smallerCollapse;
    private Float m_smallerCollapseDirection;
    private Float m_headDiff;

    public DoubleHeadAlgo(MapConfig algoConfig, ITimesSeriesData inTsd, Exchange exchange) {
        super(algoConfig, inTsd, exchange, ADJUST_TAIL);
    }

    @Override protected void recalc4(float lastPrice, float leadEmaValue, float ribbonSpread, float maxRibbonSpread,
                                     float ribbonSpreadTop, float ribbonSpreadBottom, float mid, float head, float tail, Float tailStart) {

        Boolean goUp = m_goUp;
        float ribbonSpreadHead = goUp ? ribbonSpreadTop : ribbonSpreadBottom;
        float ribbonSpreadTail = goUp ? ribbonSpreadBottom : ribbonSpreadTop;

        float headCollapse = head - ribbonSpreadHead;
        m_headCollapseDouble = ribbonSpreadHead + headCollapse * RATE;

        float smallerCollapse = goUp
                ? (m_headCollapseDouble > leadEmaValue) ? m_headCollapseDouble : leadEmaValue
                : (m_headCollapseDouble < leadEmaValue) ? m_headCollapseDouble : leadEmaValue;
        m_smallerCollapse = smallerCollapse;

        float smallerCollapseRate = (smallerCollapse - ribbonSpreadTail) / (ribbonSpreadHead - ribbonSpreadTail);
//        if (smallerCollapseRate > 1) {
//            smallerCollapseRate = 1f;
//        } else if (smallerCollapseRate < 0) {
//            smallerCollapseRate = 0f;
//        }
        float smallerCollapseDirection = smallerCollapseRate * 2 - 1;
        if (!goUp) {
            smallerCollapseDirection = -smallerCollapseDirection;
        }
        m_smallerCollapseDirection = smallerCollapseDirection;

        float headDiff = head - smallerCollapse;
        m_headDiff = headDiff;

    }

    TicksTimesSeriesData<TickData> getHeadCollapseDoubleTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_headCollapseDouble; } }; }
    TicksTimesSeriesData<TickData> getSmallerCollapseTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_smallerCollapse; } }; }
    TicksTimesSeriesData<TickData> getSmallerCollapseDirectionTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_smallerCollapseDirection; } }; }
    TicksTimesSeriesData<TickData> getHeadDiffTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_headDiff; } }; }

    @Override public String key(boolean detailed) {
        detailed = true;
        return  ""
                + (detailed ? ",start=" : ",") + m_start
                + (detailed ? ",step=" : ",") + m_step
                + (detailed ? ",count=" : ",") + m_count
                + (detailed ? ",linRegMult=" : ",") + m_linRegMultiplier
                + (detailed ? "|minOrdMul=" : "|") + m_minOrderMul
                + (detailed ? "|joinTicks=" : "|") + m_joinTicks
                + (detailed ? "|joiner=" : "|") + m_joinerName
                + (detailed ? "|turn=" : "|") + Utils.format8(m_turnLevel)
                + (detailed ? "|commiss=" : "|") + Utils.format8(m_commission)
                + ", " + m_barSize
//                + ", " + Utils.millisToYDHMSStr(m_barSize)
                ;
    }

    @Override public void setupChart(boolean collectValues, ChartCanvas chartCanvas, BaseTicksTimesSeriesData<TickData> ticksTs, Watcher firstWatcher) {
        ChartData chartData = chartCanvas.getChartData();
        ChartSetting chartSetting = chartCanvas.getChartSetting();

        int priceAlpha = 130;
        int emaAlpha = 50;

        // layout
        ChartAreaSettings top = chartSetting.addChartAreaSettings("top", 0, 0, 1, 0.6f, Color.RED);
        java.util.List<ChartAreaLayerSettings> topLayers = top.getLayers();
        {
            addChart(chartData, ticksTs, topLayers, "price", Colors.alpha(Colors.DARK_RED, priceAlpha), TickPainter.TICK_JOIN);
//            addChart(chartData, m_priceBars, topLayers, "priceBars", Colors.alpha(Colors.DARK_RED, 80), TickPainter.BAR);

//            Color emaColor = Colors.alpha(Color.BLUE, emaAlpha);
//            int size = m_emas.length;
//            for (int i = size - 1; i > 0; i--) { // paint without leadEma
//                BaseTimesSeriesData ema = m_emas[i];
//                addChart(chartData, ema.getJoinNonChangedTs(), topLayers, "ema" + i, emaColor, TickPainter.LINE);
//            }

//            addChart(chartData, m_sliding.getJoinNonChangedTs(), topLayers, "sliding", Colors.BALERINA, TickPainter.LINE);

            addChart(chartData, getMinTs(), topLayers, "min", Colors.alpha(Color.RED, 150), TickPainter.LINE_JOIN);
            addChart(chartData, getMaxTs(), topLayers, "max", Colors.alpha(Color.RED, 150), TickPainter.LINE_JOIN);

//            addChart(chartData, getZigZagTs(), topLayers, "zigzag", Color.MAGENTA, TickPainter.LINE_JOIN);

            addChart(chartData, getHeadStartTs(), topLayers, "headStart", Colors.HAZELNUT, TickPainter.LINE_JOIN);
            addChart(chartData, getTailStartTs(), topLayers, "tailStart", Colors.HAZELNUT, TickPainter.LINE_JOIN);
            addChart(chartData, getMidStartTs(), topLayers, "midStart", Colors.HAZELNUT, TickPainter.LINE_JOIN);
            addChart(chartData, getMidTs(), topLayers, "mid", Colors.CHOCOLATE, TickPainter.LINE_JOIN);

            Color halfGray = Colors.alpha(Color.GRAY, 128);
            addChart(chartData, get1quarterTs(), topLayers, "1quarter", halfGray, TickPainter.LINE_JOIN);
            addChart(chartData, get3quarterTs(), topLayers, "3quarter", halfGray, TickPainter.LINE_JOIN);
            addChart(chartData, get5quarterTs(), topLayers, "5quarter", halfGray, TickPainter.LINE_JOIN);
            addChart(chartData, get6quarterTs(), topLayers, "6quarter", Colors.LEMONADE, TickPainter.LINE_JOIN);
            addChart(chartData, get7quarterTs(), topLayers, "7quarter", halfGray, TickPainter.LINE_JOIN);
            addChart(chartData, get8quarterTs(), topLayers, "8quarter", Colors.LEMONADE, TickPainter.LINE_JOIN);

            addChart(chartData, getRibbonSpreadTopTs(), topLayers, "maxTop", Colors.alpha(Colors.SWEET_POTATO, 128), TickPainter.LINE_JOIN);
            addChart(chartData, getRibbonSpreadBottomTs(), topLayers, "maxBottom", Colors.alpha(Colors.SWEET_POTATO, 128), TickPainter.LINE_JOIN);

            BaseTimesSeriesData leadEma = m_emas[0]; // fastest ema
            addChart(chartData, leadEma.getJoinNonChangedTs(), topLayers, "leadEma", Colors.alpha(Colors.GRANNY_SMITH, 150), TickPainter.LINE_JOIN);

            addChart(chartData, getHeadCollapseDoubleTs(), topLayers, "HeadCollapseDouble", Colors.alpha(Colors.YELLOW, 128), TickPainter.LINE_JOIN);
            addChart(chartData, getSmallerCollapseTs(), topLayers, "SmallerCollapse", Colors.BLUE_PEARL, TickPainter.LINE_JOIN);
        }

        ChartAreaSettings power = chartSetting.addChartAreaSettings("power", 0, 0.6f, 1, 0.1f, Color.LIGHT_GRAY);
        {
            power.addHorizontalLineValue(1);
            power.addHorizontalLineValue(0);
            power.addHorizontalLineValue(-1);
            java.util.List<ChartAreaLayerSettings> powerLayers = power.getLayers();
            addChart(chartData, getHeadDiffTs(), powerLayers, "HeadDiff", Colors.YELLOW, TickPainter.LINE_JOIN);
        }

        ChartAreaSettings value = chartSetting.addChartAreaSettings("value", 0, 0.7f, 1, 0.15f, Color.LIGHT_GRAY);
        {
            value.addHorizontalLineValue(1);
            value.addHorizontalLineValue(0);
            value.addHorizontalLineValue(-1);
            java.util.List<ChartAreaLayerSettings> valueLayers = value.getLayers();
            addChart(chartData, getDirectionTs(), valueLayers, "direction", Color.RED, TickPainter.LINE_JOIN);
            addChart(chartData, getSmallerCollapseDirectionTs(), valueLayers, "SmallerCollapseDirection", Colors.SWEET_POTATO, TickPainter.LINE_JOIN);
        }

        if (collectValues) {
            addChart(chartData, firstWatcher, topLayers, "trades", Color.WHITE, TickPainter.TRADE);
            {
                ChartAreaSettings gain = chartSetting.addChartAreaSettings("gain", 0, 0.85f, 1, 0.15f, Color.ORANGE);
                gain.addHorizontalLineValue(1);

                List<ChartAreaLayerSettings> gainLayers = gain.getLayers();
                addChart(chartData, firstWatcher.getGainTs(), gainLayers, "gain", Color.blue, TickPainter.LINE_JOIN);
            }
        }
    }

}
