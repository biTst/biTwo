
package bi.two.algo.impl;

import bi.two.Main;
import bi.two.algo.BarSplitter;
import bi.two.algo.BaseAlgo;
import bi.two.chart.*;
import bi.two.ind.RegressionIndicator;
import bi.two.util.MapConfig;
import bi.two.util.Utils;
import org.apache.commons.math3.stat.regression.SimpleRegression;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class RegressionAlgo extends BaseAlgo {
    public static final float DEF_THRESHOLD = 0.1f;
    public static final float DEF_SMOOTHER = 3.0f;
    public static final int DEF_SLOPE_LEN = 5;
    public static final int DEF_SIGNAL_LEN = 13;

    public static final String COLLECT_LAVUES_KEY = "collect.values";
    public static final String REGRESSION_BARS_NUM_KEY = "regression.barsNum";
    public static final String THRESHOLD_KEY = "regression.threshold";
    public static final String SMOOTHER_KEY = "regression.smoother";
    public static final String SLOPE_LEN_KEY = "regression.slopeLength";
    public static final String SIGNAL_LEN_KEY = "regression.signalLength";

    public final HashMap<String,Regressor2> s_regressorsCache = new HashMap<>();
    public final HashMap<String,BarSplitter> s_regressorBarsCache = new HashMap<>();
    public final HashMap<String,Differ> s_differCache = new HashMap<>();
    public final HashMap<String,Scaler> s_scalerCache = new HashMap<>();
    public final HashMap<String,ExpotentialMovingBarAverager> s_averagerCache = new HashMap<>();
    public final HashMap<String,SimpleMovingBarAverager> s_signalerCache = new HashMap<>();
    public final HashMap<String,Powerer> s_powererCache = new HashMap<>();

    private final boolean m_collectValues;
    public final int m_curveLength;
    public final float m_threshold;
    public final float m_smootherLevel;
    public final Regressor2 m_regressor;
    public final BarSplitter m_regressorBars; // buffer to calc diff
//    public BarsSimpleAverager m_regressorBarsAvg;
    public final Differ m_differ; // Linear Regression Slope
    public final Scaler m_scaler; // diff scaled by price; lrs = (lrc-lrc[1])/close*1000
    public final ExpotentialMovingBarAverager m_averager;
    public final SimpleMovingBarAverager m_signaler;
    public final Powerer m_powerer;
    public final BaseTimesSeriesData m_smoother;
    public final Adjuster m_adjuster;

    public RegressionIndicator m_regressionIndicator;

    public String log() {
        List ticks = getTicks();
        int size = ticks.size();
        return "RegressionAlgo[ticksNum=" + size +
                "\n regressor=" + m_regressor.log() +
                "\n regressorBars=" + m_regressorBars.log() +
                "\n averager=" + m_averager.log() +
                "\n signaler=" + m_signaler.log() +
                "\n]";
    }


    public RegressionAlgo(MapConfig config, TimesSeriesData tsd) {
        super(null);

        m_curveLength = config.getInt(REGRESSION_BARS_NUM_KEY); // def = 50;
        m_threshold = config.getFloatOrDefault(THRESHOLD_KEY, DEF_THRESHOLD);
        m_smootherLevel = config.getFloatOrDefault(SMOOTHER_KEY, DEF_SMOOTHER);
        int slopeLength = config.getIntOrDefault(SLOPE_LEN_KEY, DEF_SLOPE_LEN);
        int signalLength = config.getIntOrDefault(SIGNAL_LEN_KEY, DEF_SIGNAL_LEN);
        long barSize = TimeUnit.MINUTES.toMillis(5); // 5min

        m_collectValues = config.getBoolean("collect.values");

//        long regressorPeriod = m_curveLength * barSize;
//        String key = tsd.hashCode() + "." + regressorPeriod;
//        Regressor regressor = s_regressorsCache.get(key);
//        if (regressor == null) {
//            regressor = new Regressor(tsd, regressorPeriod);
//            s_regressorsCache.put(key, regressor);
//        }
//        m_regressor = regressor;

        String key = tsd.hashCode() + "." + m_curveLength + "." + barSize;
        Regressor2 regressor = s_regressorsCache.get(key);
        if (regressor == null) {
            regressor = new Regressor2(tsd, m_curveLength, barSize);
            s_regressorsCache.put(key, regressor);
//            regressor.addListener(new RegressorVerifier(regressor));
        }
        m_regressor = regressor;

        key = key + "." + barSize;
        BarSplitter regressorBars = s_regressorBarsCache.get(key);
        if (regressorBars == null) {
            regressorBars = new BarSplitter(m_regressor, m_collectValues ? 100 : 2, barSize);
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

        ExpotentialMovingBarAverager averager = s_averagerCache.get(key);
        if (averager == null) {
            averager = new ExpotentialMovingBarAverager(m_scaler, slopeLength, barSize);
            s_averagerCache.put(key, averager);
        }
        m_averager = averager;
//        m_averager.addListener(new AveragerVerifier(m_averager));

        SimpleMovingBarAverager signaler = s_signalerCache.get(key);
        if (signaler == null) {
            signaler = new SimpleMovingBarAverager(m_averager, signalLength, barSize);
            s_signalerCache.put(key, signaler);
        }
        m_signaler = signaler;
//        m_averager.addListener(new SignalerVerifier(m_signaler));

        Powerer powerer = s_powererCache.get(key);
        if (powerer == null) {
            powerer = new Powerer(m_averager, m_signaler, 1.0f);
            s_powererCache.put(key, powerer);
        }
        m_powerer = powerer;
//        m_powerer.addListener(new PowererVerifier(m_powerer));

//        m_smoother = new ZeroLagExpotentialMovingBarAverager(m_powerer, 100, barSize/10);
//        m_smoother = new Regressor2(m_powerer, 70, barSize/10);
        m_smoother = new Regressor(m_powerer, (long) (m_smootherLevel * barSize));

        m_adjuster = new Adjuster(m_smoother, m_threshold);

//        m_regressionIndicator = new RegressionIndicator(config, bs);
//        m_indicators.add(m_regressionIndicator);
//
//        m_regressionIndicator.addListener(this);
        m_adjuster.addListener(this);
    }

//    @Override public void onChanged(ITimesSeriesData ts, boolean changed) {
//        super.onChanged(ts, changed);
////        if (m_collectValues) {
////            TickData adjusted = getAdjusted();
////            addNewestTick(adjusted);
////        }
//    }

    @Override public double getDirectionAdjusted() { // [-1 ... 1]
        Double value = m_regressionIndicator.getValue();
        return getDirectionAdjusted(value);
    }

    private static double getDirectionAdjusted(Double value) {
        return (value == null)
                ? 0
                : (value > DEF_THRESHOLD)
                    ? 1
                    : (value < -DEF_THRESHOLD)
                        ? -1
                        : 0;
    }

    @Override public ITickData getAdjusted() {
        ITickData lastTick = m_adjuster.getLatestTick();
        return lastTick;
    }


    // -----------------------------------------------------------------------------
    public static class Regressor extends BufferBased<Boolean> {
        private final SimpleRegression m_simpleRegression = new SimpleRegression(true);
        private long m_lastBarTickTime;

        public Regressor(ITimesSeriesData<ITickData> tsd, long period) {
            super(tsd, period);
        }

        @Override public void start() {
            m_simpleRegression.clear();
            m_lastBarTickTime = 0;// reset
        }

        @Override public void processTick(ITickData tick) {
            long timestamp = tick.getTimestamp();
            if (m_lastBarTickTime == 0) {
                m_lastBarTickTime = timestamp;
            }

            float price = tick.getMaxPrice();
            m_simpleRegression.addData(m_lastBarTickTime - timestamp, price);
        }

        @Override public Boolean done() {
            return null;
        }

        @Override protected float calcTickValue(Boolean ret) {
            double value = m_simpleRegression.getIntercept();
            return (float) value;
        }

        public String log() {
            return "Regressor["
                    + "\nsplitter=" + m_splitter.log()
                    + "\n]";
        }
    }


    // -----------------------------------------------------------------------------
    public static class Regressor2 extends BaseTimesSeriesData<ITickData>
            implements ITicksProcessor<BarSplitter.BarHolder, Float> {
        public final BarSplitter m_splitter;
        private final SimpleRegression m_simpleRegression = new SimpleRegression(true);
        private long m_lastBarTickTime;
        private boolean m_initialized;
        protected boolean m_filled;
        private boolean m_dirty;
        private TickData m_tickData;

        public Regressor2(ITimesSeriesData<ITickData> tsd, int barsNum, long barSize) {
            m_splitter = new BarSplitter(tsd, barsNum, barSize);
            setParent(m_splitter);
        }

        @Override public void init() {
            m_simpleRegression.clear();
            m_lastBarTickTime = 0;// reset
        }

        @Override public void processTick(BarSplitter.BarHolder barHolder) {
            BarSplitter.TickNode latestTickNode = barHolder.getLatestTick();
            if (latestTickNode != null) { // have ticks in bar ?
                ITickData latestTick = latestTickNode.m_param;

                long timestamp = latestTick.getTimestamp();
                if (m_lastBarTickTime == 0) {
                    m_lastBarTickTime = timestamp;
                }

                float price = latestTick.getMaxPrice();
                m_simpleRegression.addData(m_lastBarTickTime - timestamp, price);
            }
        }

        @Override public Float done() {
            double value = m_simpleRegression.getIntercept();
            return (float) value;
        }

        @Override public ITickData getLatestTick() {
            if (m_filled) {
                if (m_dirty) {
                    calculateLatestTick();
                }
                return m_tickData;
            }
            return null;
        }

        protected void calculateLatestTick() {
            Float regression = m_splitter.iterateTicks( this);

            long timestamp = m_parent.getLatestTick().getTimestamp();
            m_tickData = new TickData(timestamp, regression);
            m_dirty = false;
        }

        @Override public void onChanged(ITimesSeriesData ts, boolean changed) {
            boolean iAmChanged = false;
            if (changed) {
                if (!m_initialized) {
                    m_initialized = true;
                    m_splitter.getOldestTick().addBarHolderListener(new BarSplitter.BarHolder.IBarHolderListener() {
                        @Override public void onTickEnter(ITickData tickData) { m_filled = true; }
                        @Override public void onTickExit(ITickData tickData) {}
                    });
                }
                m_dirty = true;
                iAmChanged = m_filled;
            }
            super.onChanged(this, iAmChanged); // notifyListeners
        }

        public String log() {
            return "Regressor2["
                    + "\nsplitter=" + m_splitter.log()
                    + "\n]";
        }
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
    public static class ExpotentialMovingBarAverager extends BaseTimesSeriesData<ITickData> {
        public static final double DEF_THRESHOLD = 0.99657;

        private final BarSplitter m_barSplitter;
        private final List<Double> m_multipliers = new ArrayList<>();
        private final BarsProcessor m_barsProcessor = new BarsProcessor();
        private boolean m_dirty;
        private boolean m_filled;
        private boolean m_initialized;
        private TickData m_tickData;

        ExpotentialMovingBarAverager(ITimesSeriesData<ITickData> tsd, int length, long barSize) {
            this( tsd,  length,  barSize, DEF_THRESHOLD);
        }

        ExpotentialMovingBarAverager(ITimesSeriesData<ITickData> tsd, int length, long barSize, double threshold) {
            super();
            int barsNum = 0;
            double alpha = 2.0 / (length + 1); // 0.33
            double rate = 1 - alpha; // 0.66
            double sum = 0;
            while (sum < threshold) {
                double multiplier = alpha * Math.pow(rate, barsNum);
                m_multipliers.add(multiplier);
                barsNum++;
                sum += multiplier;
            }
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

                    m_barSplitter.getOldestTick().addBarHolderListener(new BarSplitter.BarHolder.IBarHolderListener() {
                        @Override public void onTickEnter(ITickData tickData) {
                            m_filled = true;
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
                    Double ret = m_barSplitter.iterateTicks(m_barsProcessor);
                    long timestamp = m_parent.getLatestTick().getTimestamp();
                    m_tickData = new TickData(timestamp, ret.floatValue());
                    m_dirty = false;
                }
                return m_tickData;
            }
            return null;
        }

        public String log() {
            return "FadingAverager["
                    + "\n splitter=" + m_barSplitter.log()
                    + "\n]";
        }

        //-------------------------------------------------------------------------------------
        private class BarsProcessor implements ITicksProcessor<BarSplitter.BarHolder, Double> {
            private int index = 0;
            private double ret = 0;

            @Override public void init() {
                index = 0;
                ret = 0;
            }

            @Override public void processTick(BarSplitter.BarHolder barHolder) {
                BarSplitter.TickNode latestNode = barHolder.getLatestTick();
                if (latestNode != null) { // sometimes bars may have no ticks inside
                    ITickData latestTick = latestNode.m_param;
                    float closePrice = latestTick.getClosePrice();
                    double multiplier = m_multipliers.get(index);
                    index++;
                    ret += closePrice * multiplier;
                }
            }

            @Override public Double done() {
                return ret;
            }
        }
    }


    //----------------------------------------------------------
    public static class SimpleMovingBarAverager extends BaseTimesSeriesData<ITickData> {
        private final BarSplitter m_barSplitter;
        private boolean m_initialized;
        private boolean m_dirty;
        private boolean m_filled;
        private final BarsProcessor m_barsProcessor = new BarsProcessor();
        private TickData m_tickData;

        public SimpleMovingBarAverager(ITimesSeriesData<ITickData> tsd, int signalLength, long barSize) {
            super();
            m_barSplitter = new BarSplitter(tsd, signalLength, barSize);
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

                    m_barSplitter.getOldestTick().addBarHolderListener(new BarSplitter.BarHolder.IBarHolderListener() {
                        @Override public void onTickEnter(ITickData tickData) {
                            m_filled = true;
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
                    sum += closePrice;
                    count++;
                }
            }

            @Override public Float done() {
                return sum/count;
            }
        }
    }


    //----------------------------------------------------------
    public static abstract class BufferBased<R>
            extends BaseTimesSeriesData<ITickData>
            implements BarSplitter.BarHolder.ITicksProcessor<R> {
        final BarSplitter m_splitter;
        private boolean m_initialized;
        private boolean m_dirty;
        private boolean m_filled;
        private TickData m_tickData;

        protected abstract float calcTickValue(R ret);

        public BufferBased(ITimesSeriesData<ITickData> tsd, long period) {
            m_splitter = new BarSplitter(tsd, 1, period);
            setParent(m_splitter);
        }

        @Override public ITickData getLatestTick() {
            if (m_filled) {
                if (m_dirty) {
                    R ret = m_splitter.m_newestBar.iterateTicks(this);
                    long timestamp = m_parent.getLatestTick().getTimestamp();
                    m_tickData = new TickData(timestamp, calcTickValue(ret));
                    m_dirty = false;
                }
                return m_tickData;
            }
            return null;
        }

        @Override public void onChanged(ITimesSeriesData ts, boolean changed) {
            boolean iAmChanged = false;
            if (changed) {
                if (!m_initialized) {
                    m_initialized = true;
                    m_splitter.m_newestBar.addBarHolderListener(new BarSplitter.BarHolder.IBarHolderListener() {
                        @Override public void onTickEnter(ITickData tickData) {
                            m_dirty = true;
                        }

                        @Override public void onTickExit(ITickData tickData) {
                            m_filled = true;
                        }
                    });
                }
                iAmChanged = m_filled && m_dirty;
            }
            super.onChanged(this, iAmChanged); // notifyListeners
        }
    }


    //----------------------------------------------------------
    public static class Powerer extends BaseTimesSeriesData<ITickData> {
        private final ExpotentialMovingBarAverager m_averager;
        private final SimpleMovingBarAverager m_signaler;
        private final float m_rate;
        private boolean m_dirty;
        private ITickData m_tick;
        private float m_xxx;

        public Powerer(ExpotentialMovingBarAverager averager, SimpleMovingBarAverager signaler, float rate) {
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
    public static class Adjuster extends BaseTimesSeriesData<ITickData> {
        private boolean m_dirty;
        private ITickData m_tick;
        private float m_threshold;
        private float m_xxx;
        private float m_min;
        private float m_max;
        private float m_zero = 0;
        private float m_last = 0;

        Adjuster(BaseTimesSeriesData<ITickData> parent, float threshold) {
            super(parent);
            m_threshold = threshold;
            m_max = m_threshold;
            m_min = - m_threshold;
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
                    float delta = value - m_last;
                    if (delta != 0) { // value changed
//System.out.println("=== value=" + value + "; delta=" + delta + "; m_min=" + m_min + "; zero=" + m_zero + "; m_max=" + m_max);

                        if ((value < m_max) && (value < m_min)) { // between
                            if (delta > 0) { // going up
                                m_max = Math.max(m_max - delta, m_threshold); // lower ceil, not less than threshold
                                if (value > 0) {
                                    m_min = Math.min(m_min + delta, -m_threshold); // not more than -threshold
                                    m_zero += delta / 2;
                                }
                            } else if (delta < 0) { // going down
                                m_min = Math.min(m_min - delta, -m_threshold); // raise floor, not more than -threshold
                                if (value < 0) {
                                    m_max = Math.max(m_max + delta, m_threshold); // not less than threshold
                                    m_zero += delta / 2;
                                }
                            }
                        }

                        if (value > m_max) { // strong UP
                            m_max = value; // ceil
                            m_zero = m_max / 2;
                            m_min = -m_threshold;
                        } else if (value < m_min) { // strong DOWN
                            m_min = value; // floor
                            m_zero = m_min / 2;
                            m_max = m_threshold;
                        }
                        m_last = value;


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
    }


    //----------------------------------------------------------
    public static class Scaler extends BaseTimesSeriesData<ITickData> {
        private final BaseTimesSeriesData<ITickData> m_price;
        private final float m_multiplier;
        private boolean m_dirty;
        private ITickData m_tick;
        private float m_scaled;

        Scaler(BaseTimesSeriesData<ITickData> differ, BaseTimesSeriesData<ITickData> price, float multiplier) {
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


    //----------------------------------------------------------
    public static class ZeroLagExpotentialMovingBarAverager extends BaseTimesSeriesData<ITickData> {
        private final ExpotentialMovingBarAverager m_ema1;
        private final ExpotentialMovingBarAverager m_ema2;
        private TickData m_tickData;

        ZeroLagExpotentialMovingBarAverager(ITimesSeriesData<ITickData> tsd, int length, long barSize) {
            super();
            m_ema1 = new ExpotentialMovingBarAverager(tsd, length, barSize);
            m_ema2 = new ExpotentialMovingBarAverager(m_ema1, length, barSize);
            setParent(m_ema2);
        }

        @Override public void onChanged(ITimesSeriesData ts, boolean changed) {
            boolean iAmChanged = false;
            if (changed) {
                ITickData ema1Tick = m_ema1.getLatestTick();
                ITickData ema2Tick = m_ema2.getLatestTick();

                float ema1 = ema1Tick.getClosePrice();
                float ema2 = ema2Tick.getClosePrice();
                float d = ema1 - ema2;
                float zlema = ema1 + d;

                if ((m_tickData == null) || (m_tickData.getClosePrice() != zlema)) {
                    long timestamp = m_parent.getLatestTick().getTimestamp();
                    m_tickData = new TickData(timestamp, zlema);
                    iAmChanged = true;
                }
            }
            super.onChanged(this, iAmChanged); // notifyListeners
        }

        @Override public ITickData getLatestTick() {
            return m_tickData;
        }

        public String log() {
            return "ZLEMA[]";
        }
    }


    //=============================================================================================
    private static class RegressorVerifier implements ITimesSeriesListener {
        private final Regressor2 m_regressor;
        private boolean m_checkTickExtraData;

        RegressorVerifier(Regressor2 regressor) {
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
    private static class DifferVerifier implements ITimesSeriesListener {
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
    private static class ScalerVerifier implements ITimesSeriesListener {
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
    private static class AveragerVerifier implements ITimesSeriesListener {
        private final ExpotentialMovingBarAverager m_averager;
        private boolean m_checkTickExtraData = true;

        AveragerVerifier(ExpotentialMovingBarAverager averager) {
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
    private static class SignalerVerifier implements ITimesSeriesListener {
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
    private static class PowererVerifier implements ITimesSeriesListener {
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
