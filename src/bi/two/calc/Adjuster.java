package bi.two.calc;

import bi.two.algo.BaseAlgo;
import bi.two.chart.ITickData;
import bi.two.chart.TickData;
import bi.two.ts.BaseTimesSeriesData;
import bi.two.ts.ITimesSeriesData;
import bi.two.ts.TicksTimesSeriesData;

import static bi.two.util.Log.console;

public class Adjuster extends BaseTimesSeriesData<ITickData> {
    private final float m_dropLevel;
    private boolean m_dirty;
    private ITickData m_tick;
    private float m_strongThreshold;
    private float m_xxx;
    private float m_min;
    private float m_max;
    private float m_zero;
    private float m_lastValue;

    public Adjuster(BaseTimesSeriesData<ITickData> parent, float strongThreshold, float dropLevel) {
        super(parent);
        m_strongThreshold = strongThreshold;
        m_max = m_strongThreshold;
        m_min = -m_strongThreshold;
        m_dropLevel = dropLevel;
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
                float delta = value - m_lastValue;

                if (delta != 0) { // value changed
                    m_lastValue = value;
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
                        console("ERROR: m_xxx=" + m_xxx + "; value=" + value + "; m_min=" + m_min + "; zero=" + m_zero + "; m_max=" + m_max);
                    }

                    iAmChanged = true;
                    m_dirty = true;
                }
            }
        }
        super.onChanged(ts, iAmChanged);
    }

    public TicksTimesSeriesData<TickData> getMinTs() {
        return new BaseAlgo.JoinNonChangedInnerTimesSeriesData(this) {
            @Override protected Float getValue() {
                return m_min;
            }
        };
    }

    public TicksTimesSeriesData<TickData> getMaxTs() {
        return new BaseAlgo.JoinNonChangedInnerTimesSeriesData(this) {
            @Override protected Float getValue() {
                return m_max;
            }
        };
    }

    public TicksTimesSeriesData<TickData> getZeroTs() {
        return new BaseAlgo.JoinNonChangedInnerTimesSeriesData(this) {
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
