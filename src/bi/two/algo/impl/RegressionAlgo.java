package bi.two.algo.impl;

import bi.two.algo.BarSplitter;
import bi.two.algo.BaseAlgo;
import bi.two.chart.*;
import bi.two.ind.RegressionIndicator;
import bi.two.util.MapConfig;
import org.apache.commons.math3.stat.regression.SimpleRegression;

public class RegressionAlgo extends BaseAlgo {
    public static final float DEF_THRESHOLD = 0.0001f;

    private final boolean m_collectValues;
    public final double m_threshold;
    public final BarSplitter m_lastTicksBuffer;
    public final Regressor m_regressor;
    public final BarSplitter m_barSplitter;

    public RegressionIndicator m_regressionIndicator;


    public RegressionAlgo(MapConfig config, TimesSeriesData tsd) {
        super(null);

        int barsNum = 20;
        int barSize = 60000;

        // last ticks buffer
        m_lastTicksBuffer = new BarSplitter(tsd, 1, barsNum * barSize);

        // do without iterateTicks() since only track one BarHolder
        m_regressor = new Regressor(m_lastTicksBuffer);

        m_barSplitter = new BarSplitter(m_regressor, 2, barSize);

        BaseTimesSeriesData<ITickData> differ = new BaseTimesSeriesData<ITickData>(m_barSplitter) {
            public boolean m_initialized;
            public boolean m_filled;
            public boolean m_dirty;
            public BarSplitter.BarHolder m_newestBar;
            public BarSplitter.BarHolder m_secondBar;
            public TickData m_tickData;

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
                        return m_tickData;
                    }
                }
                return null;
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
        };

        m_collectValues = config.getBoolean("collect.values");
        m_threshold = config.getFloatOrDefault("threshold", DEF_THRESHOLD);

//        m_regressionIndicator = new RegressionIndicator(config, bs);
//        m_indicators.add(m_regressionIndicator);
//
//        m_regressionIndicator.addListener(this);
        differ.addListener(this);
    }

    @Override public void onChanged(ITimesSeriesData ts, boolean changed) {
        notifyListeners(changed);
        if (m_collectValues) {
            TickData adjusted = getAdjusted();
            addNewestTick(adjusted);
        }
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
        private long m_lastBarTickTime;

        public Regressor(BarSplitter splitter) {
            super(splitter);
            m_splitter = splitter;

            m_tickIterator = new BarSplitter.BarHolder.ITicksProcessor<Boolean>() {
                @Override public void processTick(ITickData tick) {
                    long timestamp = tick.getTimestamp();
                    if(m_lastBarTickTime == 0) {
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
                    m_lastBarTickTime = 0;// reset
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
            if(changed) {
                if(!m_initialized) {
                    m_initialized = true;
                    m_splitter.m_newestBar.addBarHolderListener(new BarSplitter.BarHolder.IBarHolderListener() {
                        @Override public void onTickEnter(ITickData tickData) {
//                            long timestamp = tickData.getTimestamp();
//                            float price = tickData.getMaxPrice();
//                            m_simpleRegression.addData(timestamp, price);
                            m_dirty = true;
                        }

                        @Override public void onTickExit(ITickData tickData) {
//                            long timestamp = tickData.getTimestamp();
//                            float price = tickData.getMaxPrice();
//                            m_simpleRegression.removeData(timestamp, price);
//                            m_dirty = true;
                            m_filled = true;
                        }
                    });
                }
                iAmChanged = m_filled && m_dirty;
            }
            super.onChanged(this, iAmChanged); // notifyListeners
        }


        public TimesSeriesData<TickData> getTS() {
            return new RegressorTimesSeriesData(this);
        }

        //----------------------------------------------------------
        public class RegressorTimesSeriesData extends JoinNonChangedTimesSeriesData {
            public RegressorTimesSeriesData(ITimesSeriesData parent) {
                super(parent);
            }

            @Override protected ITickData getTickValue() {
                return Regressor.this.getLastTick();
            }
        }
    }
}
