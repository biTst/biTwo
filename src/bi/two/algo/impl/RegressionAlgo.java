
package bi.two.algo.impl;

import bi.two.Main;
import bi.two.algo.BarSplitter;
import bi.two.algo.BaseAlgo;
import bi.two.chart.*;
import bi.two.ind.RegressionIndicator;
import bi.two.util.MapConfig;
import bi.two.util.Utils;
import org.apache.commons.math3.stat.regression.SimpleRegression;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class RegressionAlgo extends BaseAlgo {
    public static final float DEF_THRESHOLD = 0.1f;
    public static final int DEF_SLOPE_LEN = 5;
    public static final int DEF_SIGNAL_LEN = 13;

    public static final String COLLECT_LAVUES_KEY = "collect.values";
    public static final String REGRESSION_BARS_NUM_KEY = "regression.barsNum";
    public static final String THRESHOLD_KEY = "regression.threshold";
    public static final String SLOPE_LEN_KEY = "regression.slopeLength";
    public static final String SIGNAL_LEN_KEY = "regression.signalLength";

    public final HashMap<String,Regressor2> s_regressorsCache = new HashMap<>();
    public final HashMap<String,BarSplitter> s_regressorBars = new HashMap<>();

    private final boolean m_collectValues;
    public final int m_curveLength;
    public final float m_threshold;
    public final Regressor2 m_regressor;
    public final BarSplitter m_regressorBars; // buffer to calc diff
//    public BarsSimpleAverager m_regressorBarsAvg;
    public final Differ m_differ; // Linear Regression Slope
    public final Scaler m_scaler; // diff scaled by price; lrs = (lrc-lrc[1])/close*1000
    public final FadingBarAverager m_averager;
    public final SimpleAverager m_signaler;
    public final Powerer m_powerer;
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

            regressor.addListener(new RegressorVerifier(regressor));
        }
        m_regressor = regressor;

        key = key + "." + barSize;
        BarSplitter regressorBars = s_regressorBars.get(key);
        if (regressorBars == null) {
            regressorBars = new BarSplitter(m_regressor, m_collectValues ? 100 : 2, barSize);
            s_regressorBars.put(key, regressorBars);
        }
        m_regressorBars = regressorBars;

//        if (m_collectValues) {
//            m_regressorBarsAvg = new BarsSimpleAverager(m_regressorBars);
//        }

        m_differ = new Differ(m_regressorBars);
        m_differ.addListener(new DifferVerifier(m_differ));

        m_scaler = new Scaler(m_differ, tsd, 1000);
        m_averager = new FadingBarAverager(m_scaler, slopeLength,  barSize);
        m_signaler = new SimpleAverager(m_averager, signalLength * barSize);
        m_powerer = new Powerer(m_averager, m_signaler, 1.0f);
        m_adjuster = new Adjuster(m_powerer, m_threshold);

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
        private boolean m_filled;
        private boolean m_dirty;
        private TickData m_tickData;

        public Regressor2(ITimesSeriesData<ITickData> tsd, int barsNum, long barSize) {
            m_splitter = new BarSplitter(tsd, barsNum, barSize);
            setParent(m_splitter);
        }

        @Override public void processTick(BarSplitter.BarHolder barHolder) {
            BarSplitter.TickNode latestTickNode = barHolder.getLatestTick();
            if(latestTickNode != null) { // have ticks in bar ?
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
                    m_simpleRegression.clear();
                    m_lastBarTickTime = 0;// reset
                    Float regression = m_splitter.iterateTicks( this);

                    long timestamp = m_parent.getLatestTick().getTimestamp();
                    m_tickData = new TickData(timestamp, regression);
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
    public static class FadingBarAverager extends BufferBased<Float> {
        private long m_startTime;
        private double m_summ;
        private double m_weight;

        public FadingBarAverager(ITimesSeriesData<ITickData> tsd, int length, long barSize) {
            super(tsd, length * barSize);
        }

        @Override public void start() {
            m_startTime = 0;// reset
            m_summ = 0;
            m_weight = 0;
        }

        @Override public void processTick(ITickData tick) {
            long timestamp = tick.getTimestamp();
            if (m_startTime == 0) {
                m_startTime = timestamp - m_splitter.m_period;
            }

            float price = tick.getMaxPrice();
            long rate = timestamp - m_startTime;
            m_summ += (price * rate);
            m_weight += rate;
        }

        @Override public Float done() {
            float ret = (float) (m_summ / m_weight);
            return ret;
        }

        @Override protected float calcTickValue(Float ret) {
            return ret;
        }

        public String log() {
            return "FadingAverager["
                    + "\n splitter=" + m_splitter.log()
                    + "\n]";
        }
    }


    //----------------------------------------------------------
    public static class SimpleAverager extends BufferBased<Float> {
        private double m_summ;
        private int m_weight;

        public SimpleAverager(ITimesSeriesData<ITickData> tsd, long period) {
            super(tsd, period);
        }

        @Override public void start() {
            m_summ = 0;
            m_weight = 0;
        }

        @Override public void processTick(ITickData tick) {
            float price = tick.getMaxPrice();
            m_summ += price;
            m_weight++;
        }

        @Override public Float done() {
            float ret = (float) (m_summ / m_weight);
            return ret;
        }

        @Override protected float calcTickValue(Float ret) {
            return ret;
        }

        public String log() {
            return "SimpleAverager["
                    + "\n splitter=" + m_splitter.log()
                    + "\n]";
        }
    }


    //----------------------------------------------------------
    public static abstract class BufferBased<R>
            extends BaseTimesSeriesData<ITickData>
            implements BarSplitter.BarHolder.ITicksProcessor<R> {
        public final BarSplitter m_splitter;
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
        private final FadingBarAverager m_averager;
        private final SimpleAverager m_signaler;
        private final float m_rate;
        public boolean m_dirty;
        public ITickData m_tick;
        public float m_xxx;

        public Powerer(FadingBarAverager averager, SimpleAverager signaler, float rate) {
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
        public boolean m_dirty;
        public ITickData m_tick;
        public float m_threshold;
        public float m_xxx;
        private float m_max;
        private float m_min;
        private float m_last = 0;

        public Adjuster(BaseTimesSeriesData<ITickData> parent, float threshold) {
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
                    float price = tick.getClosePrice();
                    if ((price > m_min) && (price < m_max)) { // between
                        float delta = price - m_last;
                        if (delta > 0) {
                            m_max -= delta;
                            m_max = Math.max(m_max, m_threshold); // not less than threshold
                        } else if (delta < 0) {
                            m_min -= delta;
                            m_min = Math.min(m_min, -m_threshold); // mot more than -threshold
                        }
                    }
                    if (price > m_max) {
                        m_max = price;
                        m_min = -m_threshold;
                    } else if (price < m_min) {
                        m_min = price;
                        m_max = m_threshold;
                    }
                    m_last = price;

                    m_xxx = ((price - m_min) / (m_max - m_min)) * 2 - 1; // [-1 .. 1]

                    if(Math.abs(m_xxx) > 1) {
System.out.println("ERROR: m_xxx=" + m_xxx + "; m_min=" + m_min + "; price=" + price + "; m_max=" + m_max);
                    }

                    iAmChanged = true;
                    m_dirty = true;
                }
            }
            super.onChanged(ts, iAmChanged);
        }
    }

    //----------------------------------------------------------
    public static class Scaler extends BaseTimesSeriesData<ITickData> {
        private final BaseTimesSeriesData<ITickData> m_scale;
        private final float m_multiplier;
        public boolean m_dirty;
        public ITickData m_tick;
        public float m_xxx;

        public Scaler(BaseTimesSeriesData<ITickData> tsd, BaseTimesSeriesData<ITickData> scale, float multiplier) {
            super(tsd);
            m_scale = scale;
            m_multiplier = multiplier;
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
                ITickData srcTick = m_parent.getLatestTick();
                if (srcTick != null) {
                    ITickData scaleTick = m_scale.getLatestTick();
                    if (scaleTick != null) {
                        float src = srcTick.getClosePrice();
                        float scale = scaleTick.getClosePrice();

                        m_xxx = src / scale * m_multiplier;
                        iAmChanged = true;
                        m_dirty = true;
                    }
                }
            }
            super.onChanged(ts, iAmChanged);
        }
    }

    //=============================================================================================
    private static class RegressorVerifier implements ITimesSeriesListener {
        private final Regressor2 m_regressor;
        boolean m_checkTickExtraData;

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
//                float regressVal = latestTick.getClosePrice();
//                Main.TickExtraData splitterLatestData = (Main.TickExtraData) splitterLatestTick;
//                float closePrice = splitterLatestBar.getClosePrice();
//                String expectStr = splitterLatestData.m_extra[1];
//                float expectVal = Float.parseFloat(expectStr);
//                float err = (regressVal - expectVal) / expectVal;

//                System.out.println("regressVal=" + regressVal
//                        + "; closePrice=" + closePrice
//                        + "; expectVal=" + expectVal
//                        + "; err=" + Utils.format8((double) err)
//                );
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
}
