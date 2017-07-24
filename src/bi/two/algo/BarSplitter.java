package bi.two.algo;

import bi.two.chart.ITickData;
import bi.two.chart.ITimesSeriesData;
import bi.two.chart.TickPainter;
import bi.two.chart.TimesSeriesData;
import bi.two.util.Utils;

import java.util.ArrayList;
import java.util.List;

public class BarSplitter extends TimesSeriesData<BarSplitter.BarHolder> {
    public static final int BARS_NUM = 20;
    private static final long DEF_PERIOD = 60000L;

    private final ITimesSeriesData m_source;
    public int m_barsNum;
    public final long m_period;
    public long m_lastTickTime;
    public BarSplitter.BarHolder m_newestBar;

    public BarSplitter(ITimesSeriesData iTicksData) {
        this(iTicksData, BARS_NUM, DEF_PERIOD);
    }

    public BarSplitter(ITimesSeriesData<ITickData> iTicksData, int barsNum, long period) {
        super(iTicksData);
        m_source = iTicksData;
        m_barsNum = barsNum;
        m_period = period;
    }

    public List<BarHolder> getBars() { return getTicks(); }

    public void setBarsNum(int barsNum) {
        if (m_lastTickTime != 0L) {
            throw new RuntimeException("to late to initiate barsNum");
        }
        if (barsNum > m_barsNum) {
            m_barsNum = barsNum;
        }
    }

    public void addTickDirect(ITickData tick) {
        onTick(true, tick);
    }

    @Override public void onChanged(ITimesSeriesData ts, boolean changed) {
        ITickData tick = changed ? m_source.getLastTick() : null;
        onTick(changed, tick);
    }

    private void onTick(boolean changed, ITickData tick) {
        if(changed) {
            long timestamp = tick.getTimestamp();
            List<BarHolder> barHolders = getTicks();
            if (m_lastTickTime == 0L) {
                long timeShift = timestamp;
                BarHolder prevBar = null;

                for (int i = 0; i < m_barsNum; ++i) {
                    BarHolder bar = new BarHolder(timeShift, m_period);
                    barHolders.add(bar);
                    timeShift -= m_period;
                    if (prevBar != null) {
                        prevBar.setOlderBar(bar);
                    }
                    prevBar = bar;
                }

                m_newestBar = getLastTick();
                m_newestBar.put(tick);
            } else {
                long timeShift = timestamp - m_newestBar.m_time;
                m_newestBar.put(tick);
                if (timeShift > 0L) {
                    for (int index = 0; index < m_barsNum; ++index) {
                        BarHolder newerBar = barHolders.get(index);
                        int nextIndex = index + 1;
                        BarHolder olderBar = nextIndex == m_barsNum ? null : barHolders.get(nextIndex);
                        newerBar.leave(timeShift, olderBar);
                    }
                }
            }

            m_lastTickTime = timestamp;
        }
        notifyListeners(changed);
    }

    //---------------------------------------------------------------
    public static class TickNode extends Node<ITickData> {
        public TickNode(TickNode prev, ITickData tick, TickNode next) {
            super(prev, tick, next);
        }
    }

    //---------------------------------------------------------------
    public static class BarHolder implements ITickData {
        private final long m_period;
        private long m_time;
        private long m_oldestTime;
        private TickNode m_latestTick;
        private TickNode m_oldestTick;
        private BarHolder m_olderBar;
        private float m_minPrice = Utils.INVALID_PRICE;
        private float m_maxPrice = Utils.INVALID_PRICE;
        private boolean m_dirty; // == changed
        private List<IBarHolderListener> m_listeners;

        public BarHolder(long time, long period) {
            m_time = time;
            m_period = period;
            m_oldestTime = time - period + 1L;
        }

        public long getTime() { return m_time; }
        public TickNode getLatestTick() { return m_latestTick; }
        public void setOlderBar(BarHolder olderBar) { m_olderBar = olderBar; }
        public boolean isValid() { return (m_latestTick != null) && (m_oldestTick != null); }
        public BarHolder getOlderBar() { return m_olderBar; }
        public long getTimestamp() { return m_time; }
        public long getBarSize() { return m_period; }
        public ITickData getOlderTick() { return m_olderBar; }

        public void setOlderTick(ITickData older) { m_olderBar = (BarHolder)older; }

        public float getMinPrice() {
            if(m_dirty) {
                recalcMinMax();
            }
            return m_minPrice;
        }

        public float getMaxPrice() {
            if(m_dirty) {
                recalcMinMax();
            }
            return m_maxPrice;
        }

        @Override public float getPrice() { throw new RuntimeException("not implemented"); }

        private void recalcMinMax() {
            float minPrice = Utils.INVALID_PRICE;
            float maxPrice = Utils.INVALID_PRICE;
            TickNode lastTick = m_latestTick;
            TickNode oldestTick = m_oldestTick;

            for (TickNode tickNode = lastTick; tickNode != null; tickNode = (TickNode) tickNode.m_prev) {
                ITickData tick = tickNode.m_param;
                float min = tick.getMinPrice();
                float max = tick.getMaxPrice();
                if (tickNode == lastTick) {
                    minPrice = min;
                    maxPrice = max;
                } else {
                    minPrice = Math.min(minPrice, min);
                    maxPrice = Math.max(maxPrice, max);
                }
                if (tickNode == oldestTick) {
                    break;
                }
            }

            m_maxPrice = maxPrice;
            m_minPrice = minPrice;
            m_dirty = false;
        }

        public TickPainter getTickPainter() {
            throw new RuntimeException("not implemented");
        }

        public void put(ITickData tickData) {
            TickNode tickNode = new TickNode(m_latestTick, tickData, null);
            if(m_latestTick != null) {
                m_latestTick.m_next = tickNode;
            }
            put(tickNode);
        }

        private void put(TickNode tickNode) {
            m_latestTick = tickNode;
            if(m_oldestTick == null) {
                m_oldestTick = tickNode;
            }
            m_dirty = true;
            if (m_listeners != null) {
                ITickData param = tickNode.m_param;
                for (IBarHolderListener listener : m_listeners) {
                    listener.onTickEnter(param);
                }
            }
        }

        public void leave(long timeShift, BarHolder olderBarHolder) {
            m_time += timeShift;
            m_oldestTime += timeShift;

            while (m_oldestTick != null) {
                ITickData oldestTickData = m_oldestTick.m_param;
                long timestamp = oldestTickData.getTimestamp();
                long diff = m_oldestTime - timestamp;
                if (diff <= 0L) {
                    break;
                }

                if (m_listeners != null) {
                    for (IBarHolderListener listener : m_listeners) {
                        listener.onTickExit(oldestTickData);
                    }
                }

                if (olderBarHolder != null) {
                    olderBarHolder.put(m_oldestTick);
                }

                m_dirty = true;

                if (m_oldestTick == m_latestTick) {
                    m_oldestTick = null;
                    m_latestTick = null;
                    break;
                }

                m_oldestTick = (TickNode) m_oldestTick.m_next;
            }
        }

        public <Ret> Ret iterateTicks(ITicksProcessor<Ret> iTicksProcessor) {
            TickNode lastTick = m_latestTick;
            TickNode oldestTick = m_oldestTick;

            iTicksProcessor.start(); // before iteration

            for (TickNode tickNode = lastTick; tickNode != null; tickNode = (TickNode) tickNode.m_prev) {
                ITickData tick = tickNode.m_param;
                iTicksProcessor.processTick(tick);
                if (tickNode == oldestTick) {
                    break;
                }
            }
            Ret done = iTicksProcessor.done();  // after iteration
            return done;
        }

        public void addBarHolderListener(IBarHolderListener listener) {
            if (m_listeners == null) {
                m_listeners = new ArrayList<IBarHolderListener>();
            }
            m_listeners.add(listener);
        }

        //----------------------------------------------------------------------
        public interface IBarHolderListener {
            void onTickEnter(ITickData tickData);
            void onTickExit(ITickData tickData);
        }

        //----------------------------------------------------------------------
        public interface ITicksProcessor<Ret> {
            void start();
            void processTick(ITickData tick);
            Ret done();
        }
    }
}
