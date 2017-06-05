package bi.two.algo;

import bi.two.chart.*;
import bi.two.util.Utils;

import java.util.List;

public class BarSplitter extends TimesSeriesData<BarSplitter.BarHolder> {
    public static final int BARS_NUM = 20;
    private static final long DEF_PERIOD = 60000L;

    private final ITicksData m_source;
    private int m_barsNum;
    private final long m_period;
    private long m_lastTickTime;
    private BarSplitter.BarHolder m_latestBar;

    public BarSplitter(ITicksData iTicksData) {
        this(iTicksData, BARS_NUM, DEF_PERIOD);
    }

    public BarSplitter(ITicksData iTicksData, int barsNum, long period) {
        m_source = iTicksData;
        if(iTicksData != null) {
            iTicksData.addListener(this);
        }

        m_barsNum = barsNum;
        m_period = period;
    }

    public int getBarsNum() { return m_barsNum; }
    public List<BarHolder> getBars() { return getTicks(); }
    public long getLastTickTime() { return m_lastTickTime; }

    public void setBarsNum(int barsNum) {
        if (m_lastTickTime != 0L) {
            throw new RuntimeException("to late to initiate barsNum");
        }
        if (barsNum > m_barsNum) {
            m_barsNum = barsNum;
        }
    }

    public void onTick(ITickData tickData) {
        long timestamp = tickData.getTimestamp();
        List<BarSplitter.BarHolder> barHolders = getTicks();
        if (m_lastTickTime == 0L) {
            long timeShift = timestamp;
            BarSplitter.BarHolder prevBar = null;

            for (int i = 0; i < m_barsNum; ++i) {
                BarSplitter.BarHolder bar = new BarSplitter.BarHolder(timeShift, m_period);
                barHolders.add(bar);
                timeShift -= m_period;
                if (prevBar != null) {
                    prevBar.setOlderBar(bar);
                }
                prevBar = bar;
            }

            m_latestBar = getTicks().get(0);
            m_latestBar.put(tickData);
        } else {
            long timeShift = timestamp - m_latestBar.m_time;
            m_latestBar.put(tickData);
            if (timeShift > 0L) {
                for (int index = 0; index < m_barsNum; ++index) {
                    BarSplitter.BarHolder newerBar = barHolders.get(index);
                    int nextIndex = index + 1;
                    BarSplitter.BarHolder olderBar = nextIndex == m_barsNum ? null : barHolders.get(nextIndex);
                    newerBar.leave(timeShift, olderBar);
                }
            }
        }

        m_lastTickTime = timestamp;
        notifyListeners();
    }

    public void onChanged(ITimesSeriesData ts) {
        ITickData tick = m_source.getTicks().get(0);
        onTick(tick);
    }

    //---------------------------------------------------------------
    static class TickNode extends Node<ITickData> {
        public TickNode(BarSplitter.TickNode prev, ITickData tick, BarSplitter.TickNode next) {
            super(prev, tick, next);
        }
    }

    //---------------------------------------------------------------
    static class BarHolder implements ITickData {
        private final long m_period;
        private long m_time;
        private long m_oldestTime;
        private BarSplitter.TickNode m_latestTick;
        private BarSplitter.TickNode m_oldestTick;
        private BarSplitter.BarHolder m_olderBar;
        private float m_minPrice = Utils.INVALID_PRICE;
        private float m_maxPrice = 0.0F;
        private boolean m_invalid;

        public BarHolder(long time, long period) {
            m_time = time;
            m_period = period;
            m_oldestTime = time - period + 1L;
        }

        public long getTime() { return m_time; }
        public BarSplitter.TickNode getLatestTick() { return m_latestTick; }
        public void setOlderBar(BarSplitter.BarHolder olderBar) { m_olderBar = olderBar; }
        public boolean isValid() { return (m_latestTick != null) && (m_oldestTick != null); }
        public BarSplitter.BarHolder getOlderBar() { return m_olderBar; }
        public long getTimestamp() { return m_time; }
        public long getBarSize() { return m_period; }
        public ITickData getOlderTick() { return m_olderBar; }

        public void setOlderTick(ITickData older) { m_olderBar = (BarSplitter.BarHolder)older; }

        public float getMinPrice() {
            if(m_invalid) {
                recalcMinMax();
            }
            return m_minPrice;
        }

        public float getMaxPrice() {
            if(m_invalid) {
                recalcMinMax();
            }
            return m_maxPrice;
        }

        private void recalcMinMax() {
            float minPrice = Utils.INVALID_PRICE;
            float maxPrice = 0.0F;
            BarSplitter.TickNode lastTick = m_latestTick;
            BarSplitter.TickNode oldestTick = m_oldestTick;

            for(BarSplitter.TickNode tickNode = lastTick; tickNode != null; tickNode = (BarSplitter.TickNode)tickNode.m_prev) {
                ITickData tick = tickNode.m_param;
                float max = tick.getMaxPrice();
                maxPrice = Math.max(maxPrice, max);
                float min = tick.getMinPrice();
                minPrice = Math.min(minPrice, min);
                if(tickNode == oldestTick) {
                    break;
                }
            }

            m_maxPrice = maxPrice;
            m_minPrice = minPrice;
            m_invalid = false;
        }

        public TickPainter getTickPainter() {
            throw new RuntimeException("not implemented");
        }

        public void put(ITickData tickData) {
            BarSplitter.TickNode tickNode = new BarSplitter.TickNode(m_latestTick, tickData, null);
            if(m_latestTick != null) {
                m_latestTick.m_next = tickNode;
            }
            put(tickNode);
        }

        private void put(BarSplitter.TickNode tickNode) {
            m_latestTick = tickNode;
            if(m_oldestTick == null) {
                m_oldestTick = tickNode;
            }
            m_invalid = true;
        }

        public void leave(long timeShift, BarSplitter.BarHolder olderBarHolder) {
            m_time += timeShift;
            m_oldestTime += timeShift;

            while (m_oldestTick != null) {
                long timestamp = m_oldestTick.m_param.getTimestamp();
                long diff = m_oldestTime - timestamp;
                if (diff <= 0L) {
                    break;
                }

                if (olderBarHolder != null) {
                    olderBarHolder.put(m_oldestTick);
                }

                m_invalid = true;

                if (m_oldestTick == m_latestTick) {
                    m_oldestTick = null;
                    m_latestTick = null;
                    break;
                }

                m_oldestTick = (BarSplitter.TickNode) m_oldestTick.m_next;
            }

        }

        public void iterateTicks(ITicksProcessor iTicksProcessor) {
            BarSplitter.TickNode lastTick = m_latestTick;
            BarSplitter.TickNode oldestTick = m_oldestTick;

            for(BarSplitter.TickNode tickNode = lastTick; tickNode != null; tickNode = (BarSplitter.TickNode)tickNode.m_prev) {
                ITickData tick = tickNode.m_param;
                iTicksProcessor.processTick(tick);
                if(tickNode == oldestTick) {
                    break;
                }
            }
            iTicksProcessor.done();
        }

        //----------------------------------------------------------------------
        public interface ITicksProcessor {
            void processTick(ITickData var1);
            void done();
        }
    }
}
