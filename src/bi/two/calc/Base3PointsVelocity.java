package bi.two.calc;

import bi.two.algo.BarSplitter;
import bi.two.chart.ITickData;
import bi.two.chart.TickData;
import bi.two.ts.BaseTimesSeriesData;
import bi.two.ts.ITimesSeriesData;

abstract class Base3PointsVelocity extends BaseTimesSeriesData<ITickData> {
    public static final boolean MONOTONE_TIME_INCREASE_CHECK = STRICT_MONOTONE_TIME_INCREASE_CHECK;

    private final BarSplitter m_splitter;
    final float m_multiplier;
    private boolean m_initialized;
    private boolean m_dirty;
    private boolean m_filled;
    private BarSplitter.BarHolder m_newestBar;
    private BarSplitter.BarHolder m_olderBar;
    private TickData m_tickData;

    protected abstract Float calcVelocity(long x1, float y1, long x2, float y2, long x3, float y3);

    Base3PointsVelocity(ITimesSeriesData tsd, long period, float multiplier) {
        m_splitter = new BarSplitter(tsd, 2, period); // 2 bars only
        m_multiplier = multiplier;
        setParent(m_splitter);
    }

    @Override public void onChanged(ITimesSeriesData ts, boolean changed) {
        boolean iAmChanged = false;
        if (changed) {
            if (!m_initialized) {
                BarSplitter.BarHolder newestBar = m_splitter.m_newestBar;
                if (newestBar != null) {
                    BarSplitter.BarHolder olderBar = newestBar.getOlderBar();
                    if (olderBar != null) {
                        m_initialized = true;
                        m_newestBar = newestBar;
                        m_olderBar = olderBar;
                        newestBar.addBarHolderListener(new BarSplitter.BarHolder.IBarHolderListener() {
                            @Override public void onTickEnter(ITickData tickData) {
                                m_dirty = true;
                            }
                            @Override public void onTickExit(ITickData tickData) {}
                        });
                        olderBar.addBarHolderListener(new BarSplitter.BarHolder.IBarHolderListener() {
                            @Override public void onTickEnter(ITickData tickData) {
                                m_dirty = true;
                            }
                            @Override public void onTickExit(ITickData tickData) {
                                m_dirty = true;
                                m_filled = true;
                            }
                        });
                    } else {
                        return; // not initialized
                    }
                } else {
                    return; // not initialized
                }
            }
            iAmChanged = m_dirty && m_filled;
        }
        super.onChanged(this, iAmChanged); // notifyListeners
    }

    @Override public ITickData getLatestTick() {
        if (m_filled && m_dirty) {
            BarSplitter.TickNode newest = m_newestBar.getLatestTick();
            if (newest != null) {
                BarSplitter.TickNode oldest = m_olderBar.getOldestTick();
                if (oldest != null) {
                    BarSplitter.TickNode mid = m_olderBar.getLatestTick();
                    if (oldest == mid) { // older bar has only one tick ?
                        // try get  tick from newest bar
                        mid = m_newestBar.getOldestTick();
                        if (mid == newest) { // newest bar has only one tick too ?
                            mid = null; // skip calc
                        }
                    }
                    if (mid != null) {
                        ITickData newestTick = newest.m_param;
                        long x3 = newestTick.getTimestamp();
                        float y3 = newestTick.getClosePrice();
                        ITickData midTick = mid.m_param;
                        long x2 = midTick.getTimestamp();
                        float y2 = midTick.getClosePrice();
                        ITickData oldestTick = oldest.m_param;
                        long x1 = oldestTick.getTimestamp();
                        float y1 = oldestTick.getClosePrice();

                        if (MONOTONE_TIME_INCREASE_CHECK) {
                            if (x1 > x2) {
                                throw new RuntimeException("non-monotone time increase: x1(" + x1 + ") > x2(" + x2 + ")");
                            }
                            if (x2 > x3) {
                                throw new RuntimeException("non-monotone time increase: x2(" + x2 + ") > x3(" + x3 + ")");
                            }
                        }

                        Float velocity = calcVelocity(x1, y1, x2, y2, x3, y3);
                        if (velocity != null) {
                            long timestamp = m_parent.getLatestTick().getTimestamp();
                            m_tickData = new TickData(timestamp, velocity);
                            m_dirty = false;
                        }
                    }
                }
            }
        }
        return m_tickData;
    }

    @Override public void onTimeShift(long shift) {
        // todo: call super only;   +recheck
        notifyOnTimeShift(shift);
        if (m_tickData != null) {
            m_tickData.onTimeShift(shift);
        }
    }
}
