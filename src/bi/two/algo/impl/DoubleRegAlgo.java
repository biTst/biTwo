package bi.two.algo.impl;

import bi.two.ChartCanvas;
import bi.two.Colors;
import bi.two.algo.Watcher;
import bi.two.chart.*;
import bi.two.exch.Exchange;
import bi.two.opt.Vary;
import bi.two.ts.BaseTicksTimesSeriesData;
import bi.two.ts.BaseTimesSeriesData;
import bi.two.ts.ITimesSeriesData;
import bi.two.ts.TicksTimesSeriesData;
import bi.two.util.MapConfig;
import bi.two.util.Utils;
import org.apache.commons.math3.stat.regression.SimpleRegression;

import java.awt.*;
import java.util.List;

import static bi.two.util.Log.console;

public class DoubleRegAlgo extends BaseRibbonAlgo3 {
    private static final boolean ADJUST_TAIL = true;

    private final long m_mature;
    private final float m_rate;

    private Regression m_enter = new Regression(null, 0);
    private Regression m_exit = new Regression(null, 0);
    private Float m_exitPeakValue;
    private Float m_adj2;

    static {
        console("ADJUST_TAIL=" + ADJUST_TAIL); // todo
    }

    private Float m_rayStart;
    private long m_rayStartTime;
    private Float m_rayEnd;
    private long m_rayEntTime;
    private Float m_ray;

    private Float m_exitMax;
    private Float m_exitRate;
    private Float m_rayBend;


    public DoubleRegAlgo(MapConfig algoConfig, ITimesSeriesData inTsd, Exchange exchange) {
        super(algoConfig, inTsd, exchange, ADJUST_TAIL);
        m_mature = algoConfig.getNumber(Vary.mature).longValue();  // TimeUnit.MINUTES.toMillis(3);
        m_rate = algoConfig.getNumber(Vary.rate).floatValue();
    }

    @Override public void onTimeShift(long shift) {
        super.onTimeShift(shift);
        m_enter.onTimeShift(shift);
        m_exit.onTimeShift(shift);
    }

    @Override protected void recalc4(float lastPrice, float leadEmaValue, float ribbonSpread, float maxRibbonSpread,
                                     float ribbonSpreadTop, float ribbonSpreadBottom, float mid, float head, float tail) {
        long timestamp = m_timestamp;
        Boolean goUp = m_goUp;

        if (m_directionChanged) {
            m_enter = m_exit;
            IRegressionParent parent = new IRegressionParent() {
                @Override public Float update(long timestamp, float lastPrice) {
                    return m_enter.m_value;
                }
            };
            m_exit = new Regression(parent, timestamp);

            m_rayStart = m_exitPeakValue;
            m_rayStartTime = m_enter.m_startTime;
            m_exitMax = null;

            m_exitPeakValue = null;
        } else {
            if( (m_exitPeakValue == null)
                    || ( goUp && (lastPrice > m_exitPeakValue) )
                    || ( !goUp && (lastPrice < m_exitPeakValue) )) {
                Regression tmp = m_exit;
                m_exit = new Regression(tmp, timestamp);
                m_exitPeakValue = lastPrice;
            }
        }

        m_enter.update(timestamp, lastPrice);
        m_exit.update(timestamp, lastPrice);

        Float enterValue = m_enter.m_value;
        Float exitValue = m_exit.m_value;

        if (m_directionChanged) {
            m_rayEnd = enterValue;
            m_rayEntTime = timestamp;
        }

        if ((m_rayStart != null) && (m_rayEnd != null)) {
            if (goUp) {
                if ((m_exitMax == null) || (exitValue > m_exitMax)) {
                    m_exitMax = exitValue;
                    m_rayBend = 1.0f;
                }
            } else {
                if ((m_exitMax == null) || (exitValue < m_exitMax)) {
                    m_exitMax = exitValue;
                    m_rayBend = 1.0f;
                }
            }
            m_ray = m_rayStart + (m_rayEnd - m_rayStart) / (m_rayEntTime - m_rayStartTime) * (timestamp - m_rayStartTime);

            float exitRate = (exitValue - tail) / (m_exitMax - tail);
            if (m_exitRate != null) {
                float exitRateDiff = exitRate - m_exitRate;
                if (exitRateDiff > 0) {
                    float rayBendMul = (1 - exitRate) / (1 - m_exitRate);
                    if (Float.isNaN(rayBendMul)) {
                        rayBendMul = 0f;
                        m_rayBend = 0f;
                    } else {
                        m_rayBend *= rayBendMul;
                    }
                    m_ray = m_ray - (m_ray - m_exitMax) * (1 - rayBendMul) * m_rate;
                    m_rayEnd = m_ray;
                    m_rayEntTime = timestamp;
                }
            }
            m_exitRate = exitRate;

            if (goUp) {
                if (exitValue > m_ray) {
                    m_rayEnd = exitValue;
                    m_rayEntTime = timestamp;
                }
            } else {
                if (exitValue < m_ray) {
                    m_rayEnd = exitValue;
                    m_rayEntTime = timestamp;
                }
            }
        }

        if ((enterValue != null) && (exitValue != null)) {
            if (m_ray != null) {
                float adj2 = goUp
                        ? (exitValue - ribbonSpreadBottom) / (m_ray - ribbonSpreadBottom)
                        : (exitValue - ribbonSpreadTop) / (m_ray - ribbonSpreadTop);
                if (adj2 > 1) { adj2 = 1; } else if (adj2 < 0) { adj2 = 0; }
                adj2 = adj2 * 2 - 1;
                if (!goUp) { adj2 = -adj2; }
                m_adj2 = adj2;

                m_adj = adj2;
            }

//            float rate = goUp
//                    ? (exitValue - ribbonSpreadBottom) / (ribbonSpreadTop - ribbonSpreadBottom)
//                    : (exitValue - ribbonSpreadTop) / (ribbonSpreadBottom - ribbonSpreadTop);
//            if (rate > 1) { rate = 1; } else if (rate < 0) { rate = 0; }
//            rate = rate * 2 - 1;
//            if (!goUp) { rate = -rate; }
//            m_rate = rate;

//            m_adj = rate;
        }
    }

    TicksTimesSeriesData<TickData> getEnterValue2Ts() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_enter.m_value; } }; }
    TicksTimesSeriesData<TickData> getExitValue2Ts() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_exit.m_value; } }; }
    TicksTimesSeriesData<TickData> getRateTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_rate; } }; }
    TicksTimesSeriesData<TickData> getRayTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_ray; } }; }
    TicksTimesSeriesData<TickData> getExitRateTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_exitRate; } }; }
    TicksTimesSeriesData<TickData> getRayBendTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_rayBend; } }; }
    TicksTimesSeriesData<TickData> getRate2Ts() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_adj2; } }; }

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
//                + (detailed ? "|enter=" : "|") + m_enter
                + (detailed ? "|mature=" : "|") + m_mature
                + (detailed ? "|rate=" : "|") + m_rate
//                + (detailed ? "|commiss=" : "|") + Utils.format8(m_commission)
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

            addChart(chartData, getRibbonSpreadTopTs(), topLayers, "RibbonSpreadTop", Colors.alpha(Colors.SWEET_POTATO, 128), TickPainter.LINE_JOIN);
            addChart(chartData, getRibbonSpreadBottomTs(), topLayers, "RibbonSpreadBottom", Colors.alpha(Colors.SWEET_POTATO, 128), TickPainter.LINE_JOIN);

            BaseTimesSeriesData leadEma = m_emas[0]; // fastest ema
            addChart(chartData, leadEma.getJoinNonChangedTs(), topLayers, "leadEma", Colors.alpha(Colors.GRANNY_SMITH, 150), TickPainter.LINE_JOIN);

            addChart(chartData, getEnterValue2Ts(), topLayers, "EnterValue2", Colors.BLUE_PEARL, TickPainter.LINE_JOIN);
            addChart(chartData, getExitValue2Ts(), topLayers, "ExitValue2", Colors.YELLOW, TickPainter.LINE_JOIN);
            addChart(chartData, getRayTs(), topLayers, "ray", Colors.PLUM, TickPainter.LINE_JOIN, false);
        }

        ChartAreaSettings power = chartSetting.addChartAreaSettings("power", 0, 0.6f, 1, 0.1f, Color.LIGHT_GRAY);
        {
            power.addHorizontalLineValue(1);
            power.addHorizontalLineValue(0);
            power.addHorizontalLineValue(-1);
            java.util.List<ChartAreaLayerSettings> powerLayers = power.getLayers();
            addChart(chartData, getRateTs(), powerLayers, "rate", Colors.YELLOW, TickPainter.LINE_JOIN);
            addChart(chartData, getExitRateTs(), powerLayers, "ExitRate", Colors.DARK_GREEN, TickPainter.LINE_JOIN);
            addChart(chartData, getRayBendTs(), powerLayers, "RayBend", Colors.BURIED_TREASURE, TickPainter.LINE_JOIN);
            addChart(chartData, getRate2Ts(), powerLayers, "rate2", Colors.ROSE, TickPainter.LINE_JOIN);
//            addChart(chartData, getHeadEdgeDiffTs(), powerLayers, "HeadEdgeDiff", Colors.LIGHT_BLUE, TickPainter.LINE_JOIN);
//            addChart(chartData, getSpreadExpandTs(), powerLayers, "SpreadExpand", Colors.LIGHT_BLUE_PEARL, TickPainter.LINE_JOIN);
//            addChart(chartData, getSecondHeadRateTs(), powerLayers, "SecondHeadRate", Colors.RED_HOT_RED, TickPainter.LINE_JOIN);
        }

        ChartAreaSettings value = chartSetting.addChartAreaSettings("value", 0, 0.7f, 1, 0.15f, Color.LIGHT_GRAY);
        {
            value.addHorizontalLineValue(1);
            value.addHorizontalLineValue(0);
            value.addHorizontalLineValue(-1);
            java.util.List<ChartAreaLayerSettings> valueLayers = value.getLayers();
            addChart(chartData, getDirectionTs(), valueLayers, "direction", Color.RED, TickPainter.LINE_JOIN);
//            addChart(chartData, getAdjCollapsedTs(), valueLayers, "AdjCollapsed", Colors.SWEET_POTATO, TickPainter.LINE_JOIN);
//            addChart(chartData, getSmallerCollapseDirectionTs(), valueLayers, "SmallerCollapseDirection", Colors.SWEET_POTATO, TickPainter.LINE_JOIN);
//            addChart(chartData, getSecondHeadDirectionTs(), valueLayers, "SecondHeadDirection", Colors.DARK_GREEN, TickPainter.LINE_JOIN);
//            addChart(chartData, getSimplerTs(), valueLayers, "Simpler", Colors.CANDY_PINK, TickPainter.LINE_JOIN);
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



    // --------------------------------------------------------
    private interface IRegressionParent {
        Float update(long timestamp, float lastPrice);
    }

    // --------------------------------------------------------
    private class Regression implements IRegressionParent {
        private IRegressionParent m_parent;
        private long m_startTime;
        private SimpleRegression m_regression = new SimpleRegression(true);
        private Float m_value;

        private Regression(IRegressionParent parent, long startTime) {
            m_parent = parent;
            m_startTime = startTime;
        }

        public Float update(long timestamp, float lastPrice) {
            long timeOffset = timestamp - m_startTime;
            m_regression.addData(timeOffset, lastPrice);
            double predict = m_regression.predict(timeOffset);
            boolean isNan = Double.isNaN(predict);
            if ((timeOffset < m_mature) || isNan) {
                Float parentValue = (m_parent != null)
                        ? m_parent.update(timestamp, lastPrice)
                        : null;
                if (isNan) {
                    m_value = parentValue;
                    return parentValue;
                } else {
                    float value = (float) predict;
                    if (parentValue == null) {
                        m_value = value;
                        return value;
                    } else {
                        float mature = ((float) timeOffset) / m_mature;
                        float parentMature = 1 - mature;
                        float ret = (value * mature) + (parentValue * parentMature);
                        m_value = ret;
                        return ret;
                    }
                }
            } else {
                float ret = (float) predict;
                m_value = ret;
                return ret;
            }
        }

        public void onTimeShift(long shift) {
            m_startTime += shift;
        }
    }
}
