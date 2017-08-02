
package bi.two.algo.impl;

import bi.two.algo.BarSplitter;
import bi.two.algo.BaseAlgo;
import bi.two.calc.BarsSimpleAverager;
import bi.two.chart.*;
import bi.two.ind.RegressionIndicator;
import bi.two.util.MapConfig;
import org.apache.commons.math3.stat.regression.SimpleRegression;

public class RegressionAlgo extends BaseAlgo {
    public static final float DEF_THRESHOLD = 0.0001f;
    public static final String REGRESSION_BARS_NUM = "regression.barsNum";

    private final boolean m_collectValues;
//    public final double m_threshold;
    public final Regressor m_regressor;
    public final BarSplitter m_regressorBars; // buffer to calc diff
    public BarsSimpleAverager m_regressorBarsAvg;
    public final Differ m_differ; // Linear Regression Slope
    public final Scaler m_scaler; // diff scaled by price; lrs = (lrc-lrc[1])/close*1000
    public final FadingAverager m_averager;
    public final SimpleAverager m_signaler;
    public final Powerer m_powerer;

    public RegressionIndicator m_regressionIndicator;


    public RegressionAlgo(MapConfig config, TimesSeriesData tsd) {
        super(null);

        int curveLength = config.getInt(REGRESSION_BARS_NUM); // def = 50;
        int slopeLength = 5;
        int signalLength = 13;
        int barSize = 5 * 60000;

        m_collectValues = config.getBoolean("collect.values");

        m_regressor = new Regressor(tsd, curveLength * barSize);

        m_regressorBars = new BarSplitter(m_regressor, m_collectValues ? 1000 : 2, barSize);
        if (m_collectValues) {
            m_regressorBarsAvg = new BarsSimpleAverager(m_regressorBars);
        }

        m_differ = new Differ(m_regressorBars);

        m_scaler = new Scaler(m_differ, tsd, 1000);

        m_averager = new FadingAverager(m_scaler, slopeLength * barSize);

        m_signaler = new SimpleAverager(m_averager, signalLength * barSize);

        m_powerer = new Powerer(m_averager, m_signaler, 1.0f);

//        m_threshold = config.getFloatOrDefault("threshold", DEF_THRESHOLD);

//        m_regressionIndicator = new RegressionIndicator(config, bs);
//        m_indicators.add(m_regressionIndicator);
//
//        m_regressionIndicator.addListener(this);
//        m_differ.addListener(this);
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

    private double getDirectionAdjusted(Double value) {
        return (value == null)
                ? 0
                : (value > DEF_THRESHOLD)
                    ? 1
                    : (value < -DEF_THRESHOLD)
                        ? -1
                        : 0;
    }

    @Override public TickData getAdjusted() {
        long timestamp = m_regressionIndicator.getTimestamp();
        if (timestamp != 0) {
            Double value = m_regressionIndicator.getValue();
            if (value != null) {
                return new TickData(timestamp, (float) getDirectionAdjusted(value));
            }
        }
        return null;
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

        @Override public ITickData getLastTick() {
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
    public static class FadingAverager extends BufferBased<Float> {
        private long m_startTime;
        private double m_summ;
        private double m_weight;

        public FadingAverager(ITimesSeriesData<ITickData> tsd, long period) {
            super(tsd, period);
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

        @Override public ITickData getLastTick() {
            if (m_filled) {
                if (m_dirty) {
                    R ret = m_splitter.m_newestBar.iterateTicks(this);
                    long timestamp = m_parent.getLastTick().getTimestamp();
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
        private final FadingAverager m_averager;
        private final SimpleAverager m_signaler;
        private final float m_rate;
        public boolean m_dirty;
        public ITickData m_tick;
        public float m_xxx;

        public Powerer(FadingAverager averager, SimpleAverager signaler, float rate) {
            super(signaler);
            m_averager = averager;
            m_signaler = signaler;
            m_rate = rate;
        }

        @Override public ITickData getLastTick() {
            if (m_dirty) {
                m_dirty = false;
                m_tick = new TickData(m_averager.getLastTick().getTimestamp(), m_xxx);
            }
            return m_tick;
        }

        @Override public void onChanged(ITimesSeriesData ts, boolean changed) {
            boolean iAmChanged = false;
            if (changed) {
                ITickData slrsTick = m_averager.getLastTick();
                if (slrsTick != null) {
                    ITickData alrsTick = m_signaler.getLastTick();
                    if (alrsTick != null) {
                        float slrs = slrsTick.getPrice();
                        float alrs = alrsTick.getPrice();

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

        @Override public ITickData getLastTick() {
            if (m_dirty) {
                m_dirty = false;
                m_tick = new TickData(m_parent.getLastTick().getTimestamp(), m_xxx);
            }
            return m_tick;
        }

        @Override public void onChanged(ITimesSeriesData ts, boolean changed) {
            boolean iAmChanged = false;
            if (changed) {
                ITickData srcTick = m_parent.getLastTick();
                if (srcTick != null) {
                    ITickData scaleTick = m_scale.getLastTick();
                    if (scaleTick != null) {
                        float src = srcTick.getPrice();
                        float scale = scaleTick.getPrice();

                        m_xxx = src / scale * m_multiplier;
                        iAmChanged = true;
                        m_dirty = true;
                    }
                }
            }
            super.onChanged(ts, iAmChanged);
        }
    }
}
