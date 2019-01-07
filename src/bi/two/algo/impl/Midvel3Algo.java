package bi.two.algo.impl;

import bi.two.ChartCanvas;
import bi.two.Colors;
import bi.two.algo.BarSplitter;
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
import java.util.List;

import static bi.two.util.Log.console;

// based on lin reg slope
public class Midvel3Algo extends BaseRibbonAlgo3 {
    private static final boolean ADJUST_TAIL = false;
    private static final boolean DO_NOT_CROSS_ZERO = true;

    static {
        console("ADJUST_TAIL=" + ADJUST_TAIL); // todo
    }

    private final float m_p1;
    private final float m_p2;
    private final float m_p3;
    private final float m_p4;
    private final BaseTimesSeriesData m_midTs;
    private final SlidingTicksRegressorSlope[] m_slopes;
    private final long m_expectedTicksStep;
    private float m_avgVelocity;
    private float m_vMin = 0;
    private float m_vMax = 0;

    private double[] m_slopeConfidenceIntervals;
    private double[] m_meanSquareErrors;

    public Midvel3Algo(MapConfig algoConfig, ITimesSeriesData inTsd, Exchange exchange) {
        super(algoConfig, inTsd, exchange, ADJUST_TAIL);

        long joinTicksInReader = algoConfig.getLong("joinTicksInReader");
        m_expectedTicksStep = Math.max(m_joinTicks, joinTicksInReader); // joinTicksInReader or joinTicks; can be 0

        int steps = 0;
        int arraySize = 1 + steps * 2;
        m_slopes = new SlidingTicksRegressorSlope[arraySize];
        m_slopeConfidenceIntervals = new double[arraySize];
        m_meanSquareErrors = new double[arraySize];

        m_p1 = algoConfig.getNumber(Vary.p1).floatValue();
        m_p2 = algoConfig.getNumber(Vary.p2).floatValue();
        m_p3 = algoConfig.getNumber(Vary.p3).floatValue();
        m_p4 = algoConfig.getNumber(Vary.p4).floatValue();

        m_midTs = new BaseTimesSeriesData(this) {
            @Override public ITickData getLatestTick() {
                ITickData latestTick = getParent().getLatestTick();
                if ((latestTick != null) && (m_mid != null)) {
                    return new TickData(latestTick.getTimestamp(), m_mid);
                }
                return null;
            }

            @Override public void onTimeShift(long shift) { notifyOnTimeShift(shift); }
        };

        float size = m_p1 * m_barSize;
        float step = size * 0.05f;
        for (int i = -steps, k = 0; i <= steps; i++, k++) {
            long slopePeriod = (long) (size + i * step);
            SlidingTicksRegressorSlope slope = new SlidingTicksRegressorSlope(/*inTsd*/ m_midTs, slopePeriod, false,
                    true // todo: recheck
            );
            m_slopes[k] = slope;
        }
    }

    @Override public void notifyFinish() {
//Log.console("###### m_maxDiff="+m_maxDiff);  //m_maxDiff=897000  53652 * 2.0
    }


    @Override protected void recalc4(float lastPrice, float leadEmaValue, float ribbonSpread, float maxRibbonSpread,
                                     float ribbonSpreadTop, float ribbonSpreadBottom, float mid, float head, float tail) {
        int count = 0;
        float sum = 0;
        for (int i = 0; i < m_slopes.length; i++) {
            SlidingTicksRegressorSlope slope = m_slopes[i];
            ITickData latestTick = slope.getLatestTick();
            if (latestTick != null) {
                BarSplitter splitter = slope.m_splitter;
                long period = splitter.m_period;
                long regressionN = slope.getRegressionN();

                long expectedTicksNum;
                if (m_expectedTicksStep == 0) { // if no join
                    throw new RuntimeException("not implemented"); // todo: calc max slope N, reset on timeShift; calc as "slope N"/"max slope N"
                } else {
                    expectedTicksNum = period / m_expectedTicksStep;
                }

                float splitterFillRate = ((float) regressionN) / expectedTicksNum;

                double slopeConfidenceInterval = slope.getSlopeConfidenceInterval();
                if (!Double.isNaN(slopeConfidenceInterval)) {
                    double sci = slopeConfidenceInterval * splitterFillRate * splitterFillRate;
                    m_slopeConfidenceIntervals[i] = sci;
                }

                double meanSquareError = slope.getMeanSquareError();
                if (!Double.isNaN(meanSquareError)) {
                    double mae = meanSquareError * splitterFillRate * splitterFillRate;
                    m_meanSquareErrors[i] = mae;
                }

                float velocity = latestTick.getClosePrice() * 1000;
                float velocityRated = velocity * splitterFillRate;
                sum += velocityRated;
                count++;
                continue;
            }
            count = 0;
            break;
        }
        if (count > 0) {
            float avgVelocity = sum / count;

            float delta = avgVelocity - m_avgVelocity;
            if (avgVelocity > m_vMax) {
                m_vMax = avgVelocity;
            } else {
                if (delta < 0) {
                    m_vMax += m_p2 * delta * (avgVelocity > 0 ? 1 : m_p4);
                    if (DO_NOT_CROSS_ZERO && (m_vMax < 0)) {
                        m_vMax = 0;
                    }
                }
            }
            if (avgVelocity < m_vMin) {
                m_vMin = avgVelocity;
            } else {
                if (delta > 0) {
                    m_vMin += m_p2 * delta * (avgVelocity > 0 ? 1 : m_p4);
                    if (DO_NOT_CROSS_ZERO && (m_vMin > 0)) {
                        m_vMin = 0;
                    }
                }
            }

            m_avgVelocity = avgVelocity;

            float adj = ((avgVelocity - m_vMin) / (m_vMax - m_vMin)) * 2 - 1;
            m_adj = (float)(Math.signum(adj) * Math.pow(Math.abs(adj), m_p3));
        }
    }

    TicksTimesSeriesData<TickData> getAvgVelocityTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_avgVelocity; } }; }
    TicksTimesSeriesData<TickData> getVMaxTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_vMax; } }; }
    TicksTimesSeriesData<TickData> getVMinTs() { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return m_vMin; } }; }

    TicksTimesSeriesData<TickData> getSlopeConfidenceIntervalTs(final int i) { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return (float)m_slopeConfidenceIntervals[i]; } }; }
    TicksTimesSeriesData<TickData> getMeanSquareErrorTs(final int i) { return new JoinNonChangedInnerTimesSeriesData(getParent()) { @Override protected Float getValue() { return (float)m_meanSquareErrors[i]; } }; }

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
                + (detailed ? ",p3=" : ",") + m_p3
                + (detailed ? ",p4=" : ",") + m_p4
//                + (detailed ? "|joinTicks=" : "|") + m_joinTicks
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
            for (int i = 0; i < m_slopes.length; i++) {
                SlidingTicksRegressorSlope slope = m_slopes[i];
                addChart(chartData, slope.getJoinNonChangedTs(), powerLayers, "slope" + i, velocityColor, TickPainter.LINE_JOIN);
            }
            addChart(chartData, getAvgVelocityTs(), powerLayers, "avgSlope", Colors.YELLOW, TickPainter.LINE_JOIN);
            addChart(chartData, getVMaxTs(), powerLayers, "vmax", Colors.LIGHT_BLUE, TickPainter.LINE_JOIN);
            addChart(chartData, getVMinTs(), powerLayers, "vmin", Colors.LIGHT_BLUE, TickPainter.LINE_JOIN);
        }

        ChartAreaSettings value = chartSetting.addChartAreaSettings("value", 0, 0.7f, 1, 0.15f, Color.LIGHT_GRAY);
        {
            List<ChartAreaLayerSettings> valueLayers = value.getLayers();
//            addChart(chartData, getDirectionTs(), valueLayers, "direction", Color.RED, TickPainter.LINE_JOIN);

            value.addHorizontalLineValue(0.00001);
            for (int i = 0; i < m_slopeConfidenceIntervals.length; i++) {
                addChart(chartData, getSlopeConfidenceIntervalTs(i), valueLayers, "SlopeConfidenceInterval" + i, Color.RED, TickPainter.LINE_JOIN);
            }
        }

        if (collectValues) {
            addChart(chartData, firstWatcher, topLayers, "trades", Color.WHITE, TickPainter.TRADE);

            {
                ChartAreaSettings gain = chartSetting.addChartAreaSettings("gain", 0, 0.85f, 1, 0.15f, Color.ORANGE);
                gain.addHorizontalLineValue(1);

                List<ChartAreaLayerSettings> gainLayers = gain.getLayers();
//                addChart(chartData, firstWatcher.getGainTs(), gainLayers, "gain", Color.blue, TickPainter.LINE_JOIN);

                for (int i = 0; i < m_meanSquareErrors.length; i++) {
                    addChart(chartData, getMeanSquareErrorTs(i), gainLayers, "MeanSquareError" + i, Color.RED, TickPainter.LINE_JOIN);
                }
            }
        }
    }
}
