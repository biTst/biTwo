package bi.two.algo.impl;

import bi.two.ChartCanvas;
import bi.two.Colors;
import bi.two.algo.Watcher;
import bi.two.calc.SlidingTicksRegressorSlope;
import bi.two.chart.*;
import bi.two.exch.Exchange;
import bi.two.opt.Vary;
import bi.two.ts.BaseTicksTimesSeriesData;
import bi.two.ts.BaseTimesSeriesData;
import bi.two.ts.ITimesSeriesData;
import bi.two.ts.TicksTimesSeriesData;
import bi.two.util.MapConfig;
import bi.two.util.Utils;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

// based on lin reg slope
public class Midvel3Algo extends BaseRibbonAlgo3 {
    private static final boolean ADJUST_TAIL = false;

    private final float m_p1;
    private final float m_p2;
    private final BaseTimesSeriesData m_midTs;
    private final List<SlidingTicksRegressorSlope> m_slopes = new ArrayList<>();
    private float m_avgVelocity;
    private float m_vMin = 0;
    private float m_vMax = 0;

    public Midvel3Algo(MapConfig algoConfig, ITimesSeriesData inTsd, Exchange exchange) {
        super(algoConfig, inTsd, exchange, ADJUST_TAIL);

        m_p1 = algoConfig.getNumber(Vary.p1).floatValue();
        m_p2 = algoConfig.getNumber(Vary.p2).floatValue();

        m_midTs = new BaseTimesSeriesData(this) {
            @Override public ITickData getLatestTick() {
                ITickData latestTick = getParent().getLatestTick();
                if ((latestTick != null) && (m_mid != null)) {
                    return new TickData(latestTick.getTimestamp(), m_mid);
                }
                return null;
            }

            @Override public void onTimeShift(long shift) {
                notifyOnTimeShift(shift);
            }
        };

        int steps = 0;
        float size = m_p1 * m_barSize;
        float step = size * 0.05f;
        for (int i = -steps; i <= steps; i++) {
            long slopePeriod = (long) (size + i * step);
            SlidingTicksRegressorSlope slope = new SlidingTicksRegressorSlope(m_midTs, slopePeriod, false, true);
            m_slopes.add(slope);
        }
    }

    @Override protected void recalc4(float lastPrice, float leadEmaValue, boolean goUp, boolean directionChanged,
                                     float ribbonSpread, float maxRibbonSpread, float ribbonSpreadTop, float ribbonSpreadBottom,
                                     float mid, float head, float tail, Float tailStart, float collapseRate) {
        int count = 0;
        float sum = 0;
        for (SlidingTicksRegressorSlope slope : m_slopes) {
            ITickData latestTick = slope.getLatestTick();
            if (latestTick != null) {
                float velocity = latestTick.getClosePrice();
                sum += velocity;
                count++;
            } else {
                count = 0;
                break;
            }
        }
        if (count > 0) {
            float avgVelocity = sum / count;

            float delta = avgVelocity - m_avgVelocity;
            if (avgVelocity > m_vMax) {
                m_vMax = avgVelocity;
            } else {
                if (delta < 0) {
                    m_vMax += m_p2 * delta;
                }
            }
            if (avgVelocity < m_vMin) {
                m_vMin = avgVelocity;
            } else {
                if (delta > 0) {
                    m_vMin += m_p2 * delta;
                }
            }

            m_avgVelocity = avgVelocity;

            m_adj = (avgVelocity - m_vMin) / (m_vMax - m_vMin) * 2 - 1;
        }
    }

    TicksTimesSeriesData<TickData> getAvgVelocityTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_avgVelocity; } }; }
    TicksTimesSeriesData<TickData> getVMaxTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_vMax; } }; }
    TicksTimesSeriesData<TickData> getVMinTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_vMin; } }; }

    @Override public void reset() {
        super.reset();
        m_vMin = 0;
        m_vMax = 0;
    }

    @Override public String key(boolean detailed) {
        detailed = true;
        return  ""
                + (detailed ? ",start=" : ",") + m_start
                + (detailed ? ",step=" : ",") + m_step
                + (detailed ? ",count=" : ",") + m_count
                + (detailed ? ",linRegMult=" : ",") + m_linRegMultiplier
//                + (detailed ? ",collapse=" : ",") + Utils.format8((double) m_collapse)
//                + (detailed ? "|minOrdMul=" : "|") + m_minOrderMul
                + (detailed ? ",p1=" : ",") + m_p1
                + (detailed ? ",p2=" : ",") + m_p2
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
        List<ChartAreaLayerSettings> topLayers = top.getLayers();
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

            addChart(chartData, getMinTs(), topLayers, "min", Color.RED, TickPainter.LINE_JOIN);
            addChart(chartData, getMaxTs(), topLayers, "max", Color.RED, TickPainter.LINE_JOIN);

            addChart(chartData, getZigZagTs(), topLayers, "zigzag", Color.MAGENTA, TickPainter.LINE_JOIN);

            addChart(chartData, getHeadStartTs(), topLayers, "headStart", Color.PINK, TickPainter.LINE_JOIN);
            addChart(chartData, getTailStartTs(), topLayers, "tailStart", Colors.DARK_GREEN, TickPainter.LINE_JOIN);
            addChart(chartData, getMidStartTs(), topLayers, "midStart", Colors.PURPLE, TickPainter.LINE_JOIN);
            addChart(chartData, getMidTs(), topLayers, "mid", Colors.CHOCOLATE, TickPainter.LINE_JOIN);

            Color halfGray = Colors.alpha(Color.GRAY, 128);
            addChart(chartData, get1quarterTs(), topLayers, "1quarter", halfGray, TickPainter.LINE_JOIN);
            addChart(chartData, get3quarterTs(), topLayers, "3quarter", halfGray, TickPainter.LINE_JOIN);
            addChart(chartData, get5quarterTs(), topLayers, "5quarter", halfGray, TickPainter.LINE_JOIN);
            addChart(chartData, get6quarterTs(), topLayers, "6quarter", Colors.LEMONADE, TickPainter.LINE_JOIN);
            addChart(chartData, get7quarterTs(), topLayers, "7quarter", halfGray, TickPainter.LINE_JOIN);
            addChart(chartData, get8quarterTs(), topLayers, "8quarter", Colors.LEMONADE, TickPainter.LINE_JOIN);

            addChart(chartData, getRibbonSpreadTopTs(), topLayers, "maxTop", Colors.SWEET_POTATO, TickPainter.LINE_JOIN);
            addChart(chartData, getRibbonSpreadBottomTs(), topLayers, "maxBottom", Color.CYAN, TickPainter.LINE_JOIN);

            BaseTimesSeriesData leadEma = m_emas[0]; // fastest ema
            addChart(chartData, leadEma.getJoinNonChangedTs(), topLayers, "leadEma", Colors.GRANNY_SMITH, TickPainter.LINE_JOIN);
        }

        ChartAreaSettings power = chartSetting.addChartAreaSettings("power", 0, 0.6f, 1, 0.1f, Color.LIGHT_GRAY);
        {
            List<ChartAreaLayerSettings> powerLayers = power.getLayers();
//            addChart(chartData, getCollapseRateTs(), powerLayers, "collapseRate", Colors.YELLOW, TickPainter.LINE_JOIN);

            Color velocityColor = Colors.alpha(Colors.CLOW_IN_THE_DARK, 60);
            for (int i = 0; i < m_slopes.size(); i++) {
                SlidingTicksRegressorSlope slope = m_slopes.get(i);
                addChart(chartData, slope.getJoinNonChangedTs(), powerLayers, "slope"+i, velocityColor, TickPainter.LINE_JOIN);
            }
            addChart(chartData, getAvgVelocityTs(), powerLayers, "avgSlope", Colors.YELLOW, TickPainter.LINE_JOIN);
            addChart(chartData, getVMaxTs(), powerLayers, "vmax", Colors.LIGHT_BLUE, TickPainter.LINE_JOIN);
            addChart(chartData, getVMinTs(), powerLayers, "vmin", Colors.LIGHT_BLUE, TickPainter.LINE_JOIN);
        }

        ChartAreaSettings value = chartSetting.addChartAreaSettings("value", 0, 0.7f, 1, 0.15f, Color.LIGHT_GRAY);
        {
            List<ChartAreaLayerSettings> valueLayers = value.getLayers();
            addChart(chartData, getDirectionTs(), valueLayers, "direction", Color.RED, TickPainter.LINE_JOIN);
        }

        if (collectValues) {
            addChart(chartData, firstWatcher, topLayers, "trades", Color.WHITE, TickPainter.TRADE);

            {
                ChartAreaSettings gain = chartSetting.addChartAreaSettings("gain", 0, 0.85f, 1, 0.15f, Color.ORANGE);
                gain.setHorizontalLineValue(1);

                List<ChartAreaLayerSettings> gainLayers = gain.getLayers();
                addChart(chartData, firstWatcher.getGainTs(), gainLayers, "gain", Color.blue, TickPainter.LINE_JOIN);
            }
        }
    }
}
