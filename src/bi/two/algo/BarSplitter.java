package bi.two.algo;

import bi.two.chart.BaseTickData;
import bi.two.chart.ITickData;
import bi.two.chart.TickPainter;
import bi.two.ts.ITimesSeriesData;
import bi.two.ts.TicksTimesSeriesData;
import bi.two.util.Utils;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class BarSplitter extends TicksTimesSeriesData<BarSplitter.BarHolder> {
    public static final boolean MONOTONE_TIME_INCREASE_CHECK = STRICT_MONOTONE_TIME_INCREASE_CHECK;
    public static final int BARS_NUM = 20;
    private static final long DEF_PERIOD = 60000L;

    public final long m_period;
    private final boolean m_cloneTicks;
    public int m_barsNum;
    public long m_lastTickTime;
    public BarSplitter.BarHolder m_newestBar;
    private boolean m_muteListeners;

    public BarSplitter(ITimesSeriesData<? extends ITickData> iTicksData) {
        this(iTicksData, BARS_NUM, DEF_PERIOD);
    }

    public BarSplitter(ITimesSeriesData<? extends ITickData> iTicksData, int barsNum, long period) {
        this(iTicksData, barsNum, period, true);
    }

    public BarSplitter(ITimesSeriesData<? extends ITickData> iTicksData, int barsNum, long period, boolean cloneTicks) {
        super(iTicksData);
        m_barsNum = barsNum;
        m_period = period;
        m_cloneTicks = cloneTicks;
    }

    public Iterable<BarHolder> getBarsIterable() { return getTicksIterable(); }

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
        ITickData tick = changed ? m_parent.getLatestTick() : null;
        onTick(changed && (tick != null), tick);
    }

    public String log() {
        int size = getTicksNum();
        StringBuilder sb = new StringBuilder();
        sb.append("BarSplitter[holdersNum=").append(size);
        for (int i = 0; i < size; i++) {
            BarHolder barHolder = getTick(i);
            sb.append("\n holder[").append(i).append("]=").append(barHolder.log());
        }
        sb.append("\n]");
        return sb.toString();
    }

    private void onTick(boolean changed, ITickData tick) {
        if (changed) {
            long timestamp = tick.getTimestamp();
            ITickData tickClone = m_cloneTicks ? new BaseTickData(tick) : tick;
            if ((m_lastTickTime == 0L)  // init on first tick
                    || (m_newestBar==null)) { // or if newestBar not yet initialized
                long timeShift = timestamp;

                m_muteListeners = true; // do not notify listeners on first bars creation
                for (int i = 0; i < m_barsNum; ++i) {
                    BarHolder bar = new BarHolder(timeShift, m_period);
                    addOlderTick(bar);
                    timeShift -= m_period;
                }
                m_muteListeners = false;

                m_newestBar = getLatestTick();
                m_newestBar.put(tickClone);
            } else {
                long timeShift = timestamp - m_newestBar.m_time;
                if (MONOTONE_TIME_INCREASE_CHECK) {
                    if (timeShift < 0L) {
                        throw new RuntimeException("non-monotone time increase: timeShift=" + timeShift);
                    }
                }
                m_newestBar.put(tickClone);
                if (timeShift > 0L) {
                    for (int index = 0; index < m_barsNum; ++index) {
                        BarHolder newerBar = getTick(index);
                        int nextIndex = index + 1;
                        BarHolder olderBar = (nextIndex == m_barsNum) ? null : getTick(nextIndex);
                        newerBar.shiftTime(timeShift, olderBar);
                    }
                }
            }

            m_lastTickTime = timestamp;
        }
        notifyListeners(changed);
    }

    @Override protected void notifyListeners(boolean changed) {
        if (m_muteListeners) {
            return;
        }
        super.notifyListeners(changed);
    }

    @Override public void notifyNoMoreTicks() {
        super.notifyNoMoreTicks();

        // cleanup
        BarHolder bar = m_newestBar;
        m_newestBar = null;
        while (bar != null) {
            bar.cleanup();
            BarHolder olderBar = bar.m_olderBar;
            bar.m_olderBar = null;
            bar = olderBar;
        }
        super.cleanup();
    }

    @Override public void onTimeShift(long shift) {
        m_lastTickTime += shift;

        BarHolder bar = m_newestBar;
        while (bar != null) {
            bar.onTimeShift(shift);
            bar = bar.m_olderBar;
        }

        if (m_newestBar != null) {
            TickNode node = m_newestBar.m_latestTick;
            while (node != null) {
                node.onTimeShift(shift);
                node = node.m_prev; // older
            }
        }

        // todo: remove - just call super
        notifyOnTimeShift(shift);
//        super.onTimeShift(shift);
    }

    public float calcFillRate(long expectedTicksStep) {
        return m_newestBar.calcFillRate(expectedTicksStep);
    }

    //---------------------------------------------------------------
    public static class TickNode extends Node<ITickData,TickNode> {
        TickNode(TickNode prev, ITickData tick, TickNode next) {
            super(prev, tick, next);
        }

        public void onTimeShift(long shift) {
            m_param.onTimeShift(shift);
        }

        @Override public String toString() { return "TickNode[" + "param=" + m_param + ']'; }
    }

    //---------------------------------------------------------------
    public static class BarHolder implements ITickData {
        private final long m_period;
        private long m_time;
        private long m_oldestTime;
        public TickNode m_latestTick;
        public TickNode m_oldestTick;
        private BarHolder m_olderBar;
        private float m_minPrice = Utils.INVALID_PRICE;
        private float m_maxPrice = Utils.INVALID_PRICE;
        private boolean m_dirty; // == changed
        private List<IBarHolderListener> m_listeners;
        private int m_ticksCount = 0;

        public void cleanup() {
            TickNode node = m_latestTick;
            while (node != null) {
                TickNode prev = node.m_prev;
                node.m_param = null;
                node.m_next = null;
                node.m_prev = null;
                node = prev;
            }
            m_latestTick = null;
            m_oldestTick = null;
        }


        public BarHolder(long time, long period) {
            m_time = time;
            m_period = period;
            m_oldestTime = time - period + 1L;
        }

        public long getTime() { return m_time; }
        public TickNode getLatestTick() { return m_latestTick; }
        public TickNode getOldestTick() { return m_oldestTick; }
        void setOlderBar(BarHolder olderBar) { m_olderBar = olderBar; }
        public boolean isValid() { return (m_latestTick != null) && (m_oldestTick != null); }
        public BarHolder getOlderBar() { return m_olderBar; }
        public long getTimestamp() { return m_time; }
        public long getBarSize() { return m_period; }
        public ITickData getOlderTick() { return m_olderBar; }

        public void setOlderTick(ITickData older) { m_olderBar = (BarHolder)older; }

        public float getMinPrice() {
            if (m_dirty) {
                recalcMinMax();
            }
            return m_minPrice;
        }

        public float getMaxPrice() {
            if (m_dirty) {
                recalcMinMax();
            }
            return m_maxPrice;
        }

        @Override public float getClosePrice() {
            if (m_latestTick != null) {
                ITickData tickData = m_latestTick.m_param;
                return tickData.getClosePrice();
            }
            return Float.NaN;
        }

        private void recalcMinMax() {
            float minPrice = Utils.INVALID_PRICE;
            float maxPrice = Utils.INVALID_PRICE;
            TickNode lastTick = m_latestTick;
            TickNode oldestTick = m_oldestTick;

            for (TickNode tickNode = lastTick; tickNode != null; tickNode = tickNode.m_prev) {
                ITickData tick = tickNode.m_param;
                float min = tick.getMinPrice();
                float max = tick.getMaxPrice();
                if (tickNode == lastTick) {
                    minPrice = min;
                    maxPrice = max;
                } else {
                    minPrice = (minPrice <= min) ? minPrice : min;  //  Math.min(minPrice, min);
                    maxPrice = (maxPrice >= max) ? maxPrice : max;  //  Math.max(maxPrice, max);
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

        void put(ITickData tickData) {
            TickNode tickNode = new TickNode(m_latestTick, tickData, null);
            if (m_latestTick != null) {
                m_latestTick.m_next = tickNode;
            }
            put(tickNode);
        }

        private void put(TickNode tickNode) {
            m_latestTick = tickNode;
            if (m_oldestTick == null) {
                m_oldestTick = tickNode;
            }
            m_dirty = true;
            m_ticksCount++;
            if (m_listeners != null) {
                ITickData param = tickNode.m_param;
                for (IBarHolderListener listener : m_listeners) {
                    listener.onTickEnter(param);
                }
            }
        }

        void shiftTime(long timeShift, BarHolder olderBarHolder) {
            m_time += timeShift;
            m_oldestTime += timeShift;

            while (m_oldestTick != null) {
                ITickData oldestTickData = m_oldestTick.m_param;
                long timestamp = oldestTickData.getTimestamp();
                long diff = m_oldestTime - timestamp;
                if (diff <= 0L) {
                    break; // oldest bar tick still in bar frame
                }

                // oldest bar tick is out of bar frame
                TickNode tickToLeave = m_oldestTick;

                if (m_oldestTick == m_latestTick) { // it was only 1 tick in bar
                    m_oldestTick = null; // now no ticks in bar
                    m_latestTick = null;
                } else { // update bar oldest tick
                    m_oldestTick = m_oldestTick.m_next;
                }

                if (olderBarHolder == null) { // tick leaves all holders - destroy cross links
                    TickNode next = tickToLeave.m_next;
                    if (next != null) {
                        next.m_prev = null;
                        tickToLeave.m_next = null;
                    }
                }

                m_ticksCount--;
                m_dirty = true;

                if (m_listeners != null) {
                    for (IBarHolderListener listener : m_listeners) {
                        listener.onTickExit(oldestTickData);
                    }
                }

                if (olderBarHolder != null) { // move tick into older bar
                    olderBarHolder.put(tickToLeave);
                }
            }
        }

        public <Ret> Ret iterateTicks(ITicksProcessor<Ret> iTicksProcessor) {
            TickNode lastTick = m_latestTick;
            TickNode oldestTick = m_oldestTick;

            iTicksProcessor.start(); // before iteration

            for (TickNode tickNode = lastTick; tickNode != null; tickNode = tickNode.m_prev) {
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
                m_listeners = new CopyOnWriteArrayList<>();
            }
            m_listeners.add(listener);
        }

        public void removeBarHolderListener(IBarHolderListener listener) {
            if ((m_listeners == null) || !m_listeners.remove(listener)) {
                throw new RuntimeException("removing non added listener: " + listener);
            }
            if (m_listeners.isEmpty()) {
                m_listeners = null;
            }
        }

        public String log() {
            return "BarHolder[ticksCount=" + m_ticksCount + "]";
        }

        public void onTimeShift(long shift) {
            m_time += shift;
            m_oldestTime += shift;
            m_dirty = true;
        }

        public float calcFillRate(long expectedTicksStep) {
            TickNode lastTick = m_latestTick;

            long summ = 0;
            for (TickNode tickNode = lastTick; tickNode != null; tickNode = tickNode.m_prev) {
                TickNode prevTickNode = tickNode.m_prev;
                if (prevTickNode == null) {
                    break;
                }

                ITickData tick = tickNode.m_param;
                ITickData prevTick = prevTickNode.m_param;

                long timestamp = tick.getTimestamp();
                long prevTimestamp = prevTick.getTimestamp();

                long diff = timestamp - prevTimestamp;
                if (diff > expectedTicksStep) {
                    summ += diff;
                }
            }
            float fillRate = ((float) (m_period - summ)) / m_period;
            return Math.max(0, fillRate);
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
