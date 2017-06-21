package bi.two.algo.impl;

import bi.two.algo.BarSplitter;
import bi.two.algo.BaseAlgo;
import bi.two.chart.*;
import bi.two.ind.RegressionIndicator;
import bi.two.util.MapConfig;
import org.apache.commons.math3.stat.regression.SimpleRegression;

public class RegressionAlgo extends BaseAlgo {
    public static final float DEF_THRESHOLD = 0.0001f;

//    private final boolean m_collectValues;
//    public final double m_threshold;
    public final BarSplitter m_lastTicksBuffer;
    public final Regressor m_regressor;
    public final BarSplitter m_barSplitter;
    public final Differ m_differ;
    public final BarSplitter m_avgBuffer;
    public final FadingAverager m_averager;

    public RegressionIndicator m_regressionIndicator;


    public RegressionAlgo(MapConfig config, TimesSeriesData tsd) {
        super(null);

        int barsNum = 25;
        int avgBarsNum = 3;
        int barSize = 60000; // 1 min

        // last ticks buffer
        m_lastTicksBuffer = new BarSplitter(tsd, 1, barsNum * barSize);

        // do without iterateTicks() since only track one BarHolder
        m_regressor = new Regressor(m_lastTicksBuffer);

        m_barSplitter = new BarSplitter(m_regressor, 2, barSize);

        m_differ = new Differ(m_barSplitter);

        m_avgBuffer = new BarSplitter(m_differ, 1, avgBarsNum * barSize);

        m_averager = new FadingAverager(m_avgBuffer);


//        m_collectValues = config.getBoolean("collect.values");
//        m_threshold = config.getFloatOrDefault("threshold", DEF_THRESHOLD);

//        m_regressionIndicator = new RegressionIndicator(config, bs);
//        m_indicators.add(m_regressionIndicator);
//
//        m_regressionIndicator.addListener(this);
        m_differ.addListener(this);
    }

    @Override public void onChanged(ITimesSeriesData ts, boolean changed) {
        super.onChanged(ts, changed);
//        if (m_collectValues) {
//            TickData adjusted = getAdjusted();
//            addNewestTick(adjusted);
//        }
    }

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
    public static class Regressor extends BaseTimesSeriesData<ITickData> {
        private final BarSplitter m_splitter;
        boolean m_initialized = false;
        boolean m_filled = false;
        boolean m_dirty = true;
        private final SimpleRegression m_simpleRegression = new SimpleRegression(true);
        public TickData m_tickData;
        private BarSplitter.BarHolder.ITicksProcessor<Boolean> m_tickIterator;

        public Regressor(BarSplitter splitter) {
            super(splitter);
            m_splitter = splitter;

            m_tickIterator = new BarSplitter.BarHolder.ITicksProcessor<Boolean>() {
                private long m_lastBarTickTime;

                @Override public void start() {
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
            };
        }

        @Override public ITickData getLastTick() {
            if (m_filled) {
                if (m_dirty) {
                    m_simpleRegression.clear();
                    m_splitter.m_newestBar.iterateTicks(m_tickIterator);

                    double value = m_simpleRegression.getIntercept();
                    long timestamp = m_parent.getLastTick().getTimestamp();
                    m_tickData = new TickData(timestamp, (float) value);
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
    public static class FadingAverager extends BaseTimesSeriesData<ITickData> {
        private final BarSplitter m_splitter;
        private final BarSplitter.BarHolder.ITicksProcessor<Float> m_tickIterator;
        private boolean m_initialized;
        public boolean m_dirty;
        public boolean m_filled;
        private TickData m_tickData;

        public FadingAverager(BarSplitter splitter) {
            super(splitter);
            m_splitter = splitter;

            m_tickIterator = new BarSplitter.BarHolder.ITicksProcessor<Float>() {
                private long m_startTime;
                private double m_summ;
                private double m_weight;

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
            };
        }

        @Override public ITickData getLastTick() {
            if (m_filled) {
                if (m_dirty) {
                    Float avg = m_splitter.m_newestBar.iterateTicks(m_tickIterator);
                    long timestamp = m_parent.getLastTick().getTimestamp();
                    m_tickData = new TickData(timestamp, avg);
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
}
