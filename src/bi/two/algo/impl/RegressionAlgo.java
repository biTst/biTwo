
package bi.two.algo.impl;

import bi.two.ChartCanvas;
import bi.two.Colors;
import bi.two.Main;
import bi.two.algo.BarSplitter;
import bi.two.algo.BaseAlgo;
import bi.two.algo.Watcher;
import bi.two.calc.BarsEMA;
import bi.two.calc.BarsRegressor;
import bi.two.calc.TicksRegressor;
import bi.two.chart.*;
import bi.two.opt.Vary;
import bi.two.ts.BaseTicksTimesSeriesData;
import bi.two.ts.BaseTimesSeriesData;
import bi.two.ts.ITimesSeriesData;
import bi.two.ts.TicksTimesSeriesData;
import bi.two.util.MapConfig;
import bi.two.util.Utils;

import java.awt.*;
import java.util.HashMap;
import java.util.List;

public class RegressionAlgo extends BaseAlgo<TickData> {
    public static final float DEF_THRESHOLD = 0.1f;

    public final HashMap<String,BarsRegressor> s_regressorsCache = new HashMap<>();
    public final HashMap<String,BarSplitter> s_regressorBarsCache = new HashMap<>();
    public final HashMap<String,Differ> s_differCache = new HashMap<>();
    public final HashMap<String,Scaler> s_scalerCache = new HashMap<>();
    public final HashMap<String,BarsEMA> s_averagerCache = new HashMap<>();
    public final HashMap<String,SimpleMovingBarAverager> s_signalerCache = new HashMap<>();
    public final HashMap<String,Powerer> s_powererCache = new HashMap<>();
    public final HashMap<String,TicksRegressor> s_smootherCache = new HashMap<>();

    private final boolean m_collectValues;
    private final long m_barSize;
    public final int m_curveLength;
    public final float m_divider;
    public final float m_slopeLength;
    public final float m_signalLength;
    public final float m_powerLevel;
    public final float m_smootherLevel;
    public final float m_threshold;
    public final float m_dropLevel;
    public final float m_directionThreshold;

    public final BarsRegressor m_regressor;
    public final BarSplitter m_regressorBars; // buffer to calc diff
    public final Differ m_differ; // Linear Regression Slope
    public final Scaler m_scaler; // diff scaled by price; lrs = (lrc-lrc[1])/close*1000
    public final BarsEMA m_averager;
    public final SimpleMovingBarAverager m_signaler;
    public final Powerer m_powerer;
    public final BaseTimesSeriesData m_smoother;
    public final Adjuster m_adjuster;

    public String log() {
        int size = getTicksNum();
        return "RegressionAlgo[ticksNum=" + size +
                "\n regressor=" + m_regressor.log() +
                "\n regressorBars=" + m_regressorBars.log() +
                "\n averager=" + m_averager.log() +
                "\n signaler=" + m_signaler.log() +
                "\n]";
    }


    public RegressionAlgo(MapConfig config, ITimesSeriesData tsd) {
        super(null);

        m_barSize = config.getNumber(Vary.period).longValue();
        m_curveLength = config.getNumber(Vary.bars).intValue();
        m_divider = config.getNumber(Vary.divider).floatValue();
        m_slopeLength = config.getNumber(Vary.slope).floatValue();
        m_signalLength = config.getNumber(Vary.signal).floatValue();
        m_powerLevel = config.getNumber(Vary.power).floatValue();
        m_smootherLevel = config.getNumber(Vary.smooth).floatValue();
        m_threshold = config.getNumber(Vary.threshold).floatValue();
        m_dropLevel = config.getNumber(Vary.drop).floatValue();
        m_directionThreshold = config.getNumber(Vary.reverse).floatValue();

        m_collectValues = config.getBoolean(COLLECT_VALUES_KEY);

//        long regressorPeriod = m_curveLength * barSize;
//        String key = tsd.hashCode() + "." + regressorPeriod;
//        Regressor regressor = s_regressorsCache.get(key);
//        if (regressor == null) {
//            regressor = new Regressor(tsd, regressorPeriod);
//            s_regressorsCache.put(key, regressor);
//        }
//        m_regressor = regressor;

        String key = tsd.hashCode() + "." + m_curveLength + "." + m_barSize + "." + m_divider;
        BarsRegressor regressor = s_regressorsCache.get(key);
        if (regressor == null) {
            regressor = new BarsRegressor(tsd, m_curveLength, m_barSize, m_divider);
            s_regressorsCache.put(key, regressor);
//            regressor.addListener(new RegressorVerifier(regressor));
        }
        m_regressor = regressor;

        // TODO - move into Differ
        BarSplitter regressorBars = s_regressorBarsCache.get(key);
        if (regressorBars == null) {
            regressorBars = new BarSplitter(m_regressor, m_collectValues ? 100 : 2, m_barSize);
            s_regressorBarsCache.put(key, regressorBars);
        }
        m_regressorBars = regressorBars;

//        if (m_collectValues) {
//            m_regressorBarsAvg = new BarsSimpleAverager(m_regressorBars);
//        }

        Differ differ = s_differCache.get(key);
        if (differ == null) {
            differ = new Differ(m_regressorBars);
            s_differCache.put(key, differ);
        }
        m_differ = differ;
//        m_differ.addListener(new DifferVerifier(m_differ));

        Scaler scaler = s_scalerCache.get(key);
        if (scaler == null) {
            scaler = new Scaler(m_differ, tsd, 1000);
            s_scalerCache.put(key, scaler);
        }
        m_scaler = scaler;
//        m_scaler.addListener(new ScalerVerifier(m_scaler));

        key = key + "." + m_slopeLength;
        BarsEMA averager = s_averagerCache.get(key);
        if (averager == null) {
            averager = new BarsEMA(m_scaler, m_slopeLength, m_barSize);
            s_averagerCache.put(key, averager);
        }
        m_averager = averager;
//        m_averager.addListener(new AveragerVerifier(m_averager));

        key = key + "." + m_signalLength;
        SimpleMovingBarAverager signaler = s_signalerCache.get(key);
        if (signaler == null) {
            signaler = new SimpleMovingBarAverager(m_averager, m_signalLength, m_barSize);
            s_signalerCache.put(key, signaler);
        }
        m_signaler = signaler;
//        m_averager.addListener(new SignalerVerifier(m_signaler));

        key = key + "." + m_powerLevel;
        Powerer powerer = s_powererCache.get(key);
        if (powerer == null) {
            powerer = new Powerer(m_averager, m_signaler, m_powerLevel);
            s_powererCache.put(key, powerer);
        }
        m_powerer = powerer;
//        m_powerer.addListener(new PowererVerifier(m_powerer));

//        m_smoother = new ZeroLagExpotentialMovingBarAverager(m_powerer, 100, barSize/10);
//        m_smoother = new Regressor2(m_powerer, 70, barSize/10);
        key = key + "." + m_smootherLevel;
        TicksRegressor smoother = s_smootherCache.get(key);
        if (smoother == null) {
            smoother = new TicksRegressor(m_powerer, (long) (m_smootherLevel * m_barSize));
            s_smootherCache.put(key, smoother);
        }
        m_smoother = smoother;

        m_adjuster = new Adjuster(m_smoother, m_threshold, m_dropLevel, m_directionThreshold);

//        m_regressionIndicator = new RegressionIndicator(config, bs);
//        m_indicators.add(m_regressionIndicator);
//
//        m_regressionIndicator.addListener(this);
        m_adjuster.addListener(this);
    }

    @Override public double getDirectionAdjusted() { // [-1 ... 1]
        throw new RuntimeException("getDirectionAdjusted");
    }

    @Override public ITickData getAdjusted() {
        ITickData lastTick = m_adjuster.getLatestTick();
        return lastTick;
    }

    @Override public void setupChart(boolean collectValues, ChartCanvas chartCanvas, BaseTicksTimesSeriesData<TickData> ticksTs, Watcher firstWatcher) {
        ChartData chartData = chartCanvas.getChartData();
        ChartSetting chartSetting = chartCanvas.getChartSetting();

        // layout
        ChartAreaSettings top = chartSetting.addChartAreaSettings("top", 0, 0, 1, 0.4f, Color.RED);
        List<ChartAreaLayerSettings> topLayers = top.getLayers();
        {
            addChart(chartData, ticksTs, topLayers, "price", Colors.alpha(Color.RED, 70), TickPainter.TICK);
            addChart(chartData, m_regressor.m_splitter, topLayers, "price.buff", Colors.alpha(Color.BLUE, 100), TickPainter.BAR); // regressor price buffer
            addChart(chartData, m_regressor.getJoinNonChangedTs(), topLayers, "regressor", Color.PINK, TickPainter.LINE); // Linear Regression Curve
            addChart(chartData, m_regressorBars, topLayers, "regressor.bars", Color.ORANGE, TickPainter.BAR);
        }

        ChartAreaSettings bottom = chartSetting.addChartAreaSettings("indicator", 0, 0.4f, 1, 0.2f, Color.GREEN);
        List<ChartAreaLayerSettings> bottomLayers = bottom.getLayers();
        {
            ////addChart(chartData, algo.m_differ.getJoinNonChangedTs(), bottomLayers, "diff", Colors.alpha(Color.GREEN, 100), TickPainter.LINE); // diff = Linear Regression Slope
            //addChart(chartData, algo.m_scaler.getJoinNonChangedTs(), bottomLayers, "slope", Colors.alpha(Colors.LIME, 60), TickPainter.LINE /*RIGHT_CIRCLE*/); // diff (Linear Regression Slope) scaled by price
            ////addChart(chartData, algo.m_averager.m_splitter, bottomLayers, "slope.buf", Colors.alpha(Color.YELLOW, 100), TickPainter.BAR));
            //addChart(chartData, algo.m_averager.getJoinNonChangedTs(), bottomLayers, "slope.avg", Colors.alpha(Color.RED, 60), TickPainter.LINE);
            ////addChart(chartData, algo.m_averager.m_splitteralgo.m_signaler.m_splitter, bottomLayers, "sig.buf", Colors.alpha(Color.DARK_GRAY, 100), TickPainter.BAR));
            //addChart(chartData, algo.m_signaler.getJoinNonChangedTs(), bottomLayers, "signal.avg", Colors.alpha(Color.GRAY,100), TickPainter.LINE);
            addChart(chartData, m_powerer.getJoinNonChangedTs(), bottomLayers, "power", Color.CYAN, TickPainter.LINE);
            addChart(chartData, m_adjuster.getMinTs(), bottomLayers, "min", Color.MAGENTA, TickPainter.LINE);
            addChart(chartData, m_smoother.getJoinNonChangedTs(), bottomLayers, "zlema", Color.ORANGE, TickPainter.LINE);
            addChart(chartData, m_adjuster.getMaxTs(), bottomLayers, "max", Color.MAGENTA, TickPainter.LINE);
            addChart(chartData, m_adjuster.getZeroTs(), bottomLayers, "zero", Colors.alpha(Color.green, 100), TickPainter.LINE);
        }

        ChartAreaSettings value = chartSetting.addChartAreaSettings("value", 0, 0.6f, 1, 0.2f, Color.LIGHT_GRAY);
        List<ChartAreaLayerSettings> valueLayers = value.getLayers();
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

    @Override public String key(boolean detailed) {
        return (detailed ? "curve=" : "") + m_curveLength
                + (detailed ? ",slope=" : ",") + m_slopeLength
                + (detailed ? ",divider=" : ",") + m_divider
                + (detailed ? ",signal=" : ",") + m_signalLength
                + (detailed ? ",power=" : ",") + m_powerLevel
                + (detailed ? ",smoother=" : ",") + m_smootherLevel
                + (detailed ? ",threshold=" : ",") + m_threshold
                + (detailed ? ",drop=" : ",") + m_dropLevel
                + (detailed ? ",reverse=" : ",") + m_directionThreshold
                /*+ ", " + Utils.millisToYDHMSStr(period)*/;
    }


    //----------------------------------------------------------
    public static class Differ extends BaseTimesSeriesData<ITickData> {
        private final BarSplitter m_barSplitter;
        public boolean m_initialized;
        public boolean m_filled;
        public boolean m_dirty;
        public BarSplitter.BarHolder m_newestBar;
        public BarSplitter.BarHolder m_secondBar;
        public TickData m_tickData;

        public Differ(BarSplitter barSplitter) {
            super(barSplitter);
            m_barSplitter = barSplitter;
        }

        @Override public ITickData getLatestTick() {
            if (m_dirty) {
                BarSplitter.TickNode newestBarTickNode = m_newestBar.getLatestTick();
                BarSplitter.TickNode secondBarTickNode = m_secondBar.getLatestTick();
                if ((newestBarTickNode != null) && (secondBarTickNode != null)) {
                    ITickData newestBarTick = newestBarTickNode.m_param;
                    ITickData secondBarTick = secondBarTickNode.m_param;

                    float newestPrice = newestBarTick.getMaxPrice();
                    float secondPrice = secondBarTick.getMaxPrice();
                    float diff = newestPrice - secondPrice;

                    long timestamp = newestBarTick.getTimestamp();
                    m_tickData = new TickData(timestamp, diff);
                    m_dirty = false;
                }
            }
            return m_tickData;
        }

        @Override public void onChanged(ITimesSeriesData ts, boolean changed) {
            boolean iAmChanged = false;
            if (changed) {
                if (!m_initialized) {
                    m_initialized = true;
                    m_newestBar = m_barSplitter.m_newestBar;
                    m_secondBar = m_newestBar.getOlderBar();
                    m_secondBar.addBarHolderListener(new BarSplitter.BarHolder.IBarHolderListener() {
                        @Override public void onTickEnter(ITickData tickData) {
                            m_filled = true;
                            m_secondBar.removeBarHolderListener(this);
                        }
                        @Override public void onTickExit(ITickData tickData) {}
                    });
                }
                m_dirty = true;
                iAmChanged = m_filled;
            }
            super.onChanged(this, iAmChanged); // notifyListeners
        }
    }


    //----------------------------------------------------------
    public static class SimpleMovingBarAverager extends BaseTimesSeriesData<ITickData> {
        private final BarSplitter m_barSplitter;
        private final float m_signalLength;
        private final int m_fullBarsNum;
        private boolean m_initialized;
        private boolean m_dirty;
        private boolean m_filled;
        private final BarsProcessor m_barsProcessor = new BarsProcessor();
        private TickData m_tickData;

        public SimpleMovingBarAverager(ITimesSeriesData<ITickData> tsd, float signalLength, long barSize) {
            super();
            m_signalLength = signalLength;
            m_fullBarsNum = (int) Math.floor(signalLength);
            int barsNum = (int) Math.ceil(signalLength);
            m_barSplitter = new BarSplitter(tsd, barsNum, barSize);
            setParent(m_barSplitter);
        }

        @Override public void onChanged(ITimesSeriesData ts, boolean changed) {
            boolean iAmChanged = false;
            if (changed) {
                if (!m_initialized) {
                    m_initialized = true;
                    m_barSplitter.m_newestBar.addBarHolderListener(new BarSplitter.BarHolder.IBarHolderListener() {
                        @Override public void onTickEnter(ITickData tickData) {
                            m_dirty = true;
                        }
                        @Override public void onTickExit(ITickData tickData) {}
                    });

                    final BarSplitter.BarHolder oldestTick = m_barSplitter.getOldestTick();
                    oldestTick.addBarHolderListener(new BarSplitter.BarHolder.IBarHolderListener() {
                        @Override public void onTickEnter(ITickData tickData) {
                            m_filled = true;
                            oldestTick.removeBarHolderListener(this);
                        }
                        @Override public void onTickExit(ITickData tickData){}
                    });
                }
                iAmChanged = m_filled && m_dirty;
            }
            super.onChanged(this, iAmChanged); // notifyListeners
        }

        @Override public ITickData getLatestTick() {
            if (m_filled) {
                if (m_dirty) {
                    Float ret = m_barSplitter.iterateTicks(m_barsProcessor);
                    long timestamp = m_parent.getLatestTick().getTimestamp();
                    m_tickData = new TickData(timestamp, ret);
                    m_dirty = false;
                }
                return m_tickData;
            }
            return null;
        }

        public String log() {
            return "SimpleAverager["
                    + "\n splitter=" + m_barSplitter.log()
                    + "\n]";
        }


        //-------------------------------------------------------------------------------------
        private class BarsProcessor implements ITicksProcessor<BarSplitter.BarHolder, Float> {
            private int count = 0;
            private float sum = 0;

            @Override public void init() {
                count = 0;
                sum = 0;
            }

            @Override public void processTick(BarSplitter.BarHolder barHolder) {
                BarSplitter.TickNode latestNode = barHolder.getLatestTick();
                if (latestNode != null) { // sometimes bars may have no ticks inside
                    ITickData latestTick = latestNode.m_param;
                    float closePrice = latestTick.getClosePrice();
                    if(count == m_fullBarsNum) {
                        float rate = m_signalLength - m_fullBarsNum;
                        sum += closePrice * rate;
                    } else {
                        sum += closePrice;
                    }
                    count++;
                }
            }

            @Override public Float done() {
                return sum/m_signalLength;
            }
        }
    }


    //----------------------------------------------------------
    public static class Powerer extends BaseTimesSeriesData<ITickData> {
        private final BarsEMA m_averager;
        private final SimpleMovingBarAverager m_signaler;
        private final float m_rate;
        private boolean m_dirty;
        private ITickData m_tick;
        private float m_xxx;

        public Powerer(BarsEMA averager, SimpleMovingBarAverager signaler, float rate) {
            super(signaler);
            m_averager = averager;
            m_signaler = signaler;
            m_rate = rate;
        }

        @Override public ITickData getLatestTick() {
            if (m_dirty) {
                m_dirty = false;
                m_tick = new TickData(m_averager.getLatestTick().getTimestamp(), m_xxx);
            }
            return m_tick;
        }

        @Override public void onChanged(ITimesSeriesData ts, boolean changed) {
            boolean iAmChanged = false;
            if (changed) {
                ITickData slrsTick = m_averager.getLatestTick();
                if (slrsTick != null) {
                    ITickData alrsTick = m_signaler.getLatestTick();
                    if (alrsTick != null) {
                        float slrs = slrsTick.getClosePrice();
                        float alrs = alrsTick.getClosePrice();

                        float diff = slrs - alrs;
                        m_xxx = slrs + diff * m_rate;
                        iAmChanged = true;
                        m_dirty = true;
                    }
                }
            }
            super.onChanged(ts, iAmChanged);
        }
    }


    //----------------------------------------------------------
    private static class Adjuster extends BaseTimesSeriesData<ITickData> {
        private final DirectionTracker m_directionTracker;
        private final float m_dropLevel;
        private boolean m_dirty;
        private ITickData m_tick;
        private float m_strongThreshold;
        private float m_xxx;
        private float m_min;
        private float m_max;
        private float m_zero;

        Adjuster(BaseTimesSeriesData<ITickData> parent, float strongThreshold, float dropLevel, float directionThreshold) {
            super(parent);
            m_strongThreshold = strongThreshold;
            m_max = m_strongThreshold;
            m_min = -m_strongThreshold;
            m_dropLevel = dropLevel;
            m_directionTracker = new DirectionTracker(directionThreshold);
        }

        @Override public ITickData getLatestTick() {
            if (m_dirty) {
                m_dirty = false;
                m_tick = new TickData(m_parent.getLatestTick().getTimestamp(), m_xxx);
            }
            return m_tick;
        }

        @Override public void onChanged(ITimesSeriesData ts, boolean changed) {
            boolean iAmChanged = false;
            if (changed) {
                ITickData tick = m_parent.getLatestTick();
                if (tick != null) {
                    float value = tick.getClosePrice();
                    float delta = m_directionTracker.update(value);

                    if (delta != 0) { // value changed
//System.out.println("=== value=" + value + "; delta=" + delta + "; m_min=" + m_min + "; zero=" + m_zero + "; m_max=" + m_max);

                        if ((m_max > value) && (value > m_min)) { // between
                            if (delta > 0) { // going up
                                m_max = Math.max(m_max - delta, m_strongThreshold); // lower ceil, not less than threshold
                                if (value > 0) {
                                    if(m_min < -m_strongThreshold) {
                                        if(m_zero < 0) {
                                            m_zero += delta * m_zero / m_min;
                                        }
                                    }
                                    m_min = Math.min(m_min + delta, -m_strongThreshold); // not more than -threshold
                                }
                            } else if (delta < 0) { // going down
                                m_min = Math.min(m_min - delta, -m_strongThreshold); // raise floor, not more than -threshold
                                if (value < 0) {
                                    if(m_max > m_strongThreshold) {
                                        if(m_zero > 0) {
                                            m_zero += delta * m_zero / m_max;
                                        }
                                    }
                                    m_max = Math.max(m_max + delta, m_strongThreshold); // not less than threshold
                                }
                            }
                        }

                        if (value > m_max) { // strong UP
                            m_max = value; // ceil
                            m_zero = value * m_dropLevel;
                            m_min = -m_strongThreshold;
                        } else if (value < m_min) { // strong DOWN
                            m_min = value; // floor
                            m_zero = value * m_dropLevel;
                            m_max = m_strongThreshold;
                        }

                        if (value > m_zero) {
                            m_xxx = (value - m_zero) / (m_max - m_zero); // [0 .. 1]
                        } else {
                            m_xxx = (value - m_min) / (m_zero - m_min) - 1; // [-1 .. 0]
                        }
//System.out.println("===     value=" + value + "; m_min=" + m_min + "; zero=" + m_zero + "; m_max=" + m_max + "  ==>>  xxx=" + m_xxx);


                        if (Math.abs(m_xxx) > 1) {
                            System.out.println("ERROR: m_xxx=" + m_xxx + "; value=" + value + "; m_min=" + m_min + "; zero=" + m_zero + "; m_max=" + m_max);
                        }

                        iAmChanged = true;
                        m_dirty = true;
                    }
                }
            }
            super.onChanged(ts, iAmChanged);
        }

        public TicksTimesSeriesData<TickData> getMinTs() {
            return new JoinNonChangedInnerTimesSeriesData(this) {
                @Override protected Float getValue() {
                    return m_min;
                }
            };
        }

        public TicksTimesSeriesData<TickData> getMaxTs() {
            return new JoinNonChangedInnerTimesSeriesData(this) {
                @Override protected Float getValue() {
                    return m_max;
                }
            };
        }

        public TicksTimesSeriesData<TickData> getZeroTs() {
            return new JoinNonChangedInnerTimesSeriesData(this) {
                @Override protected Float getValue() {
                    return m_zero;
                }
            };
        }


        //----------------------------------------------------------
        private static class DirectionTracker {
            private final float m_threshold;
            private float m_prevValue = Float.NaN;
            private float m_lastValue = Float.NaN;

            public DirectionTracker(float threshold) {
                m_threshold = threshold;
            }

            public float update(float value) {
                if (Float.isNaN(m_prevValue)) {
                    m_prevValue = value;
                    return 0;
                }

                if (Float.isNaN(m_lastValue)) {
                    float diffAbs = Math.abs(m_prevValue - value);
                    if (diffAbs > m_threshold) {
                        m_lastValue = value;
                        return m_lastValue - m_prevValue;
                    }
                    return 0;
                }

                if (m_lastValue > m_prevValue) { // up
                    if (value > m_lastValue) { // more up
                        float diff = value - m_lastValue;
                        m_lastValue = value;
                        return diff;
                    }
                    // possible down ?
                    float diff = m_lastValue - value;
                    if (diff > m_threshold) {
                        m_prevValue = m_lastValue;
                        m_lastValue = value;
                        return -diff;
                    }
                    return 0;
                }

                if (m_lastValue < m_prevValue) { // down
                    if (value < m_lastValue) { // more down
                        float diff = value - m_lastValue;
                        m_lastValue = value;
                        return diff;
                    }
                    // possible up ?
                    float diff = value - m_lastValue;
                    if (diff > m_threshold) {
                        m_prevValue = m_lastValue;
                        m_lastValue = value;
                        return diff;
                    }
                    return 0;
                }

                return 0;
            }
        }
    }


    //----------------------------------------------------------
    public static class Scaler extends BaseTimesSeriesData<ITickData> {
        private final ITimesSeriesData<ITickData> m_price;
        private final float m_multiplier;
        private boolean m_dirty;
        private ITickData m_tick;
        private float m_scaled;

        Scaler(ITimesSeriesData<ITickData> differ, ITimesSeriesData<ITickData> price, float multiplier) {
            super(differ);
            m_price = price;
            m_multiplier = multiplier;
        }

        @Override public ITickData getLatestTick() {
            if (m_dirty) {
                m_dirty = false;
                m_tick = new TickData(m_parent.getLatestTick().getTimestamp(), m_scaled);
            }
            return m_tick;
        }

        @Override public void onChanged(ITimesSeriesData ts, boolean changed) {
            boolean iAmChanged = false;
            if (changed) {
                ITickData differTick = m_parent.getLatestTick();
                if (differTick != null) {
                    ITickData priceTick = m_price.getLatestTick();
                    if (priceTick != null) {
                        float diff = differTick.getClosePrice();
                        float price = priceTick.getClosePrice();
                        m_scaled = diff / price * m_multiplier;
//                        System.out.println("scaler: diff=" + Utils.format8((double) diff)
//                                        + "; price=" + Utils.format8((double) price)
//                                        + "; scaled=" + Utils.format8((double) m_scaled)
//                        );

                        iAmChanged = true;
                        m_dirty = true;
                    }
                }
            }
            super.onChanged(ts, iAmChanged);
        }
    }


    //=============================================================================================
    private static abstract class BaseVerifier implements ITimesSeriesListener {
        @Override public void waitWhenFinished() { /* noop */ }
        @Override public void notifyNoMoreTicks() { /* noop */ }
    }

    
    //=============================================================================================
    private static class RegressorVerifier extends BaseVerifier {
        private final BarsRegressor m_regressor;
        private boolean m_checkTickExtraData;

        RegressorVerifier(BarsRegressor regressor) {
            m_regressor = regressor;
            m_checkTickExtraData = true;
        }

        @Override public void onChanged(ITimesSeriesData ts, boolean changed) {
            // verifier
            ITimesSeriesData parent = m_regressor.m_parent;
            BarSplitter.BarHolder splitterLatestBar = (BarSplitter.BarHolder) parent.getLatestTick();
            ITickData splitterLatestTick = splitterLatestBar.getLatestTick().m_param;
            if (m_checkTickExtraData) {
                m_checkTickExtraData = false;
                if (!(splitterLatestTick instanceof Main.TickExtraData)) {
                    m_regressor.removeListener(this);
                    return;
                }
            }

            ITickData latestTick = m_regressor.getLatestTick();
            if (latestTick != null) {
                float regressVal = latestTick.getClosePrice();
                Main.TickExtraData splitterLatestData = (Main.TickExtraData) splitterLatestTick;
                float closePrice = splitterLatestBar.getClosePrice();
                String expectStr = splitterLatestData.m_extra[1];
                float expectVal = Float.parseFloat(expectStr);
                float err = (regressVal - expectVal) / expectVal;
                System.out.println("regressVal=" + regressVal
                        + "; closePrice=" + closePrice
                        + "; expectVal=" + expectVal
                        + "; err=" + Utils.format8((double) err)
                );
            }
        }
    }


    //=============================================================================================
    private static class DifferVerifier extends BaseVerifier {
        private final Differ m_differ;
        private boolean m_checkTickExtraData = true;

        DifferVerifier(Differ differ) {
            m_differ = differ;
        }

        @Override public void onChanged(ITimesSeriesData ts, boolean changed) {
            // verifier
            ITimesSeriesData parentSplitter = m_differ.getParent();
            ITimesSeriesData parentRegressor = parentSplitter.getParent();
            ITimesSeriesData parent = parentRegressor.getParent();
            BarSplitter.BarHolder splitterLatestBar = (BarSplitter.BarHolder) parent.getLatestTick();
            ITickData splitterLatestTick = splitterLatestBar.getLatestTick().m_param;
            if (m_checkTickExtraData) {
                m_checkTickExtraData = false;
                if (!(splitterLatestTick instanceof Main.TickExtraData)) {
                    m_differ.removeListener(this);
                    return;
                }
            }

            ITickData latestTick = m_differ.getLatestTick();
            if (latestTick != null) {
                float differVal = latestTick.getClosePrice();
                Main.TickExtraData splitterLatestData = (Main.TickExtraData) splitterLatestTick;
                String expectStr = splitterLatestData.m_extra[2];
                float expectVal = Float.parseFloat(expectStr);
                float err = differVal - expectVal;
                System.out.println("differVal=" + Utils.format8((double) differVal)
                        + "; expectVal=" + Utils.format8((double) expectVal)
                        + "; err=" + Utils.format8((double) err)
                );
            }
        }
    }

    
    //=============================================================================================
    private static class ScalerVerifier extends BaseVerifier {
        private final Scaler m_scaler;
        private boolean m_checkTickExtraData = true;

        ScalerVerifier(Scaler scaler) {
            m_scaler = scaler;
            m_checkTickExtraData = true;
        }

        @Override public void onChanged(ITimesSeriesData ts, boolean changed) {
            // verifier
            ITimesSeriesData parentDiffer = m_scaler.getParent();
            ITimesSeriesData parentSplitter = parentDiffer.getParent();
            ITimesSeriesData parentRegressor = parentSplitter.getParent();
            ITimesSeriesData parent = parentRegressor.getParent();
            BarSplitter.BarHolder splitterLatestBar = (BarSplitter.BarHolder) parent.getLatestTick();
            ITickData splitterLatestTick = splitterLatestBar.getLatestTick().m_param;
            if (m_checkTickExtraData) {
                m_checkTickExtraData = false;
                if (!(splitterLatestTick instanceof Main.TickExtraData)) {
                    m_scaler.removeListener(this);
                    return;
                }
            }

            ITickData latestTick = m_scaler.getLatestTick();
            if (latestTick != null) {
                float scalerVal = latestTick.getClosePrice();
                Main.TickExtraData splitterLatestData = (Main.TickExtraData) splitterLatestTick;
                String expectStr = splitterLatestData.m_extra[3];
                float expectVal = Float.parseFloat(expectStr);
                float err = scalerVal - expectVal;

                System.out.println("scalerVal=" + Utils.format8((double) scalerVal)
                        + "; expectVal=" + Utils.format8((double) expectVal)
                        + "; err=" + Utils.format8((double) err)
                );
            }
        }
    }


    //=============================================================================================
    private static class AveragerVerifier extends BaseVerifier {
        private final BarsEMA m_averager;
        private boolean m_checkTickExtraData = true;

        AveragerVerifier(BarsEMA averager) {
            m_averager = averager;
        }

        @Override public void onChanged(ITimesSeriesData ts, boolean changed) {
            // verifier
            ITimesSeriesData averagerSpitter = m_averager.getParent();
            ITimesSeriesData parenScaler = averagerSpitter.getParent();
            ITimesSeriesData parentDiffer = parenScaler.getParent();
            ITimesSeriesData parentSplitter = parentDiffer.getParent();
            ITimesSeriesData parentRegressor = parentSplitter.getParent();
            ITimesSeriesData parent = parentRegressor.getParent();
            BarSplitter.BarHolder splitterLatestBar = (BarSplitter.BarHolder) parent.getLatestTick();
            ITickData splitterLatestTick = splitterLatestBar.getLatestTick().m_param;
            if (m_checkTickExtraData) {
                m_checkTickExtraData = false;
                if (!(splitterLatestTick instanceof Main.TickExtraData)) {
                    m_averager.removeListener(this);
                    return;
                }
            }

            ITickData latestTick = m_averager.getLatestTick();
            if (latestTick != null) {
                float averagerVal = latestTick.getClosePrice();
                Main.TickExtraData splitterLatestData = (Main.TickExtraData) splitterLatestTick;
                String expectStr = splitterLatestData.m_extra[4];
                float expectVal = Float.parseFloat(expectStr);
                float err = averagerVal - expectVal;

                System.out.println("averagerVal=" + Utils.format8((double) averagerVal)
                        + "; expectVal=" + Utils.format8((double) expectVal)
                        + "; err=" + Utils.format8((double) err)
                );
            }
        }
    }


    //=============================================================================================
    private static class SignalerVerifier extends BaseVerifier {
        private final SimpleMovingBarAverager m_signaler;
        private boolean m_checkTickExtraData = true;

        SignalerVerifier(SimpleMovingBarAverager signaler) {
            m_signaler = signaler;
        }

        @Override public void onChanged(ITimesSeriesData ts, boolean changed) {
            // verifier
            ITimesSeriesData signalerSpitter = m_signaler.getParent();
            ITimesSeriesData fadingAverger = signalerSpitter.getParent();
            ITimesSeriesData averagerSpitter = fadingAverger.getParent();
            ITimesSeriesData parenScaler = averagerSpitter.getParent();
            ITimesSeriesData parentDiffer = parenScaler.getParent();
            ITimesSeriesData parentSplitter = parentDiffer.getParent();
            ITimesSeriesData parentRegressor = parentSplitter.getParent();
            ITimesSeriesData parent = parentRegressor.getParent();
            BarSplitter.BarHolder splitterLatestBar = (BarSplitter.BarHolder) parent.getLatestTick();
            ITickData splitterLatestTick = splitterLatestBar.getLatestTick().m_param;
            if (m_checkTickExtraData) {
                m_checkTickExtraData = false;
                if (!(splitterLatestTick instanceof Main.TickExtraData)) {
                    m_signaler.removeListener(this);
                    return;
                }
            }

            ITickData latestTick = m_signaler.getLatestTick();
            if (latestTick != null) {
                float signalerVal = latestTick.getClosePrice();
                Main.TickExtraData splitterLatestData = (Main.TickExtraData) splitterLatestTick;
                String expectStr = splitterLatestData.m_extra[5];
                float expectVal = Float.parseFloat(expectStr);
                float err = signalerVal - expectVal;

                System.out.println("signalerVal=" + Utils.format8((double) signalerVal)
                        + "; expectVal=" + Utils.format8((double) expectVal)
                        + "; err=" + Utils.format8((double) err)
                );
            }
        }
    }

    
    //=============================================================================================
    private static class PowererVerifier extends BaseVerifier {
        private final Powerer m_powerer;
        private boolean m_checkTickExtraData = true;

        PowererVerifier(Powerer powerer) {
            m_powerer = powerer;
        }

        @Override public void onChanged(ITimesSeriesData ts, boolean changed) {
            // verifier
            ITimesSeriesData signaler = m_powerer.getParent();
            ITimesSeriesData signalerSpitter = signaler.getParent();
            ITimesSeriesData fadingAverger = signalerSpitter.getParent();
            ITimesSeriesData averagerSpitter = fadingAverger.getParent();
            ITimesSeriesData parenScaler = averagerSpitter.getParent();
            ITimesSeriesData parentDiffer = parenScaler.getParent();
            ITimesSeriesData parentSplitter = parentDiffer.getParent();
            ITimesSeriesData parentRegressor = parentSplitter.getParent();
            ITimesSeriesData parent = parentRegressor.getParent();
            BarSplitter.BarHolder splitterLatestBar = (BarSplitter.BarHolder) parent.getLatestTick();
            ITickData splitterLatestTick = splitterLatestBar.getLatestTick().m_param;
            if (m_checkTickExtraData) {
                m_checkTickExtraData = false;
                if (!(splitterLatestTick instanceof Main.TickExtraData)) {
                    m_powerer.removeListener(this);
                    return;
                }
            }

            ITickData latestTick = m_powerer.getLatestTick();
            if (latestTick != null) {
                float powererVal = latestTick.getClosePrice();
                Main.TickExtraData splitterLatestData = (Main.TickExtraData) splitterLatestTick;
                String expectStr = splitterLatestData.m_extra[7];
                float expectVal = Float.parseFloat(expectStr);
                float err = powererVal - expectVal;

                System.out.println("powererVal=" + Utils.format8((double) powererVal)
                        + "; expectVal=" + Utils.format8((double) expectVal)
                        + "; err=" + Utils.format8((double) err)
                );
            }
        }
    }
}
