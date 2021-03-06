package bi.two.ts;

import bi.two.algo.Node;
import bi.two.chart.ITickData;
import bi.two.chart.TickData;
import bi.two.exch.Exchange;
import bi.two.util.Utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static bi.two.util.Log.*;

public class ParallelTimesSeriesData extends BaseTimesSeriesData {
    private static final boolean MONOTONE_TIME_INCREASE_CHECK = STRICT_MONOTONE_TIME_INCREASE_CHECK;

    private final int m_maxParallelSize;
    private final int m_groupTicks;
    private final int m_maxQueue;
    private final Exchange m_exchange;
    private final List<InnerTimesSeriesData> m_array = new ArrayList<>(); // todo: optimize - remake via array
    private final AtomicInteger m_runningCount = new AtomicInteger();
    private int m_activeIndex;
    private int m_ticksEntered = 0;
    private long m_reportCounter;
    private long m_sleepCounter;
    private long m_nanoSumm;
    private long m_nanoCount;

    public ParallelTimesSeriesData(BaseTimesSeriesData ticksTs, int size, int groupTicks, int maxQueue, Exchange exchange) {
        super(ticksTs);
        m_activeIndex = 0;
        m_maxParallelSize = size;
        m_groupTicks = groupTicks;
        m_maxQueue = maxQueue;
        m_exchange = exchange;
    }

    @Override public ITimesSeriesData getActive() {
        if (m_activeIndex == m_array.size()) {
            synchronized (m_runningCount) {
                m_runningCount.incrementAndGet();
                InnerTimesSeriesData inner = new InnerTimesSeriesData(m_activeIndex);
                m_array.add(inner);
            }
        }
        InnerTimesSeriesData ret = m_array.get(m_activeIndex);
        m_activeIndex = (m_activeIndex + 1) % m_maxParallelSize;
        return ret.getScheduledIfNeededTs();
    }

    @Override public ITickData getLatestTick() { throw new RuntimeException("InnerTimesSeriesData should be used"); }
    @Override public void addListener(ITimesSeriesListener listener) { throw new RuntimeException("InnerTimesSeriesData should be used"); }
    @Override public void removeListener(ITimesSeriesListener listener) { throw new RuntimeException("InnerTimesSeriesData should be used"); }

    @Override protected void notifyListeners(boolean changed) {
        ITickData latestTick = changed ? m_parent.getLatestTick() : null;
        if (latestTick != null) {
            m_ticksEntered++;
            addNewestTick(latestTick);
        }
    }

    private void addNewestTick(ITickData latestTick) {
        //                         todo: make configurable
        if ((++m_reportCounter) == 500000) { // log queue states every half million ticks
            m_reportCounter = 0;
            StringBuilder sb = new StringBuilder("entered=" + m_ticksEntered + "; buffers");
            for (InnerTimesSeriesData innerTsd : m_array) {
                int size = innerTsd.m_queue.size();
                sb.append(" ").append(innerTsd.m_innerIndex).append("=").append(size).append(";");
                innerTsd.addNewestTick(latestTick);
            }

            double nanoAvg = ((double) m_nanoSumm) / m_nanoCount;
            m_nanoSumm = 0;
            m_nanoCount = 0;
            sb.append(" avg=").append(Utils.format5(nanoAvg)).append(";");

            String memStat = Utils.memStat();

            sb.append("  mem(f/u/t/m): ").append(memStat);

            console(sb.toString());
            return;
        }

        if ((++m_sleepCounter) == 40001) {
            m_sleepCounter = 0;
            int maxSize = 0;
            int minSize = Integer.MAX_VALUE;
            // iteration optimized for speed
            //noinspection ForLoopReplaceableByForEach
            for (int i = 0, arraySize = m_array.size(); i < arraySize; i++) {
                InnerTimesSeriesData innerTsd = m_array.get(i);
                innerTsd.addNewestTick(latestTick);
                int size = innerTsd.m_queue.size();
                maxSize = Math.max(maxSize, size);
                minSize = Math.min(minSize, size);
            }
            if (maxSize > 2000) {
                long sleep;
                if (m_array.size() == 1) { // single - no need to sleep much
                    sleep = 10;
                } else {
                    if (maxSize > m_maxQueue / 4) {
                        if (maxSize > m_maxQueue / 3) {
                            if (maxSize > m_maxQueue / 2) {
                                if (maxSize > m_maxQueue) {
                                    sleep = 1000;
                                } else { sleep = 500; }
                            } else { sleep = 250; }
                        } else { sleep = 100; }
                    } else { sleep = 20; }
                }
                try {
                    log("sleep " + sleep + "ms; maxSize=" + maxSize + "; minSize=" + minSize);
                    Thread.sleep(sleep);
                } catch (InterruptedException e) { /*noop*/ }
            }
        } else {
            long nanoTime1 = System.nanoTime();
            // iteration optimized for speed
            //noinspection ForLoopReplaceableByForEach
            for (int i = 0, arraySize = m_array.size(); i < arraySize; i++) {
                InnerTimesSeriesData innerTsd = m_array.get(i);
                innerTsd.addNewestTick(latestTick);
            }
            long nanoTime2 = System.nanoTime();
            long nanoDiff = nanoTime2-nanoTime1;
            m_nanoSumm += nanoDiff;
            m_nanoCount++;
        }
    }

    // no more ticks - call from parent
    @Override public void notifyNoMoreTicks() {
        log("NoMoreTicks in parallelTS, ticksEntered=" + m_ticksEntered);
        TickData marker = new TickData(0, 0);
        addNewestTick(marker);

        StringBuilder sb = new StringBuilder("NoMoreTicks: entered=" + m_ticksEntered + "; buffers");
        for (InnerTimesSeriesData innerTsd : m_array) {
            innerTsd.m_queue.flush(); // ticks are reported in blocks from innerTs, flush remained/collected
            int size = innerTsd.m_queue.size();
            sb.append(" [").append(innerTsd.m_innerIndex).append("]=").append(size).append(";");
        }
        console(sb.toString());

        super.notifyNoMoreTicks();
    }

    @Override public void waitWhenAllFinish() {
        log("parallel.waitWhenAllFinish");
        synchronized (m_runningCount) {
            while (true) {
                int count = m_runningCount.get();
                log("parallel.waitWhenAllFinish count=" + count);
                if (count == 0) {
                    log(" all finished - exit");
                    return; // all finished - exit
                }
                try {
                    log(" wait more");
                    m_runningCount.wait();
                    count = m_runningCount.get();
                    log("  count after wait = " + count);
                } catch (InterruptedException e) {
                    err("InterruptedException: " + e, e);
                    return;
                }
            }
        }
    }

    protected void onInnerFinished(InnerTimesSeriesData inner) {
        synchronized (m_runningCount) {
            int remained = m_runningCount.decrementAndGet();
            m_runningCount.notify();
            log("parallel.inner: thread finished " + inner + "; remained=" + remained);
        }
    }


    //=============================================================================================
    protected class InnerTimesSeriesData extends BaseTimesSeriesData implements Runnable {
        private final IQueue<ITickData> m_queue;
        private final int m_innerIndex;
        private final BaseTimesSeriesData m_scheduledIfNeededTs;
        private ITickData m_currentTickData;

        BaseTimesSeriesData getScheduledIfNeededTs() { return m_scheduledIfNeededTs; }

        @Override public String toString() {
            return "Inner-" + m_innerIndex;
        }

        InnerTimesSeriesData(int index) {
            m_innerIndex = index;
//            m_queue = new LightQueue<>();    // seems uses 10% less memory
            m_queue = new ArrayQueue<>();   // 1% faster

            if ((m_exchange != null) && m_exchange.hasSchedule()) {
                m_scheduledIfNeededTs = new ScheduleTimesSeriesData(this, m_exchange.m_schedule);
            } else {
                m_scheduledIfNeededTs = this;
            }

            String name = "parallel-" + index;
            Thread thread = new Thread(this, name);
            thread.setPriority(Thread.NORM_PRIORITY - 1); // smaller prio
            thread.start();
        }

        @Override public ITickData getLatestTick() {
            return m_currentTickData;
        }

        @Override public void run() {
            try {
                while (true) {
                    ITickData tick = m_queue.getFromHead();

                    long timestamp = tick.getTimestamp();
                    if (timestamp == 0) { // special marker
                        m_queue.clean();
                        notifyNoMoreTicks();
                        break;
                    } else {
                        if (MONOTONE_TIME_INCREASE_CHECK) {
                            if (m_currentTickData != null) {
                                long currTimestamp = m_currentTickData.getTimestamp();
                                if (timestamp < currTimestamp) {
                                    throw new RuntimeException("non-monotonic time increase");
                                }
                            }
                        }
                        m_currentTickData = tick;
                        notifyListeners(true);
                    }
                }
            } catch (InterruptedException e) {
                err("error: " + e, e);
            }
            log("finish inner[" + m_innerIndex + "]");
            onInnerFinished(this);
        }

        String getLogStr() {
            return "size=" + m_queue.size();
        }

        public void addNewestTick(ITickData latestTick) {
            m_queue.addToTail(latestTick);
        }

        @Override public void onTimeShift(long shift) {
            super.onTimeShift(shift);
        }

        @Override protected void notifyOnTimeShift(long shift) {
            m_currentTickData.onTimeShift(shift);
            super.notifyOnTimeShift(shift);
        }
    }


    //=============================================================================================
    interface IQueue <X> {
        void addToTail(X x);
        X getFromHead() throws InterruptedException;
        void clean();
        void flush();
        int size();
    }


    //=============================================================================================
    private class ArrayQueue<X extends ITickData> implements IQueue<X> {
        private ITickData[] m_in = new ITickData[m_groupTicks];
        private int m_inPosition;

        private ITickData[] m_shared = new ITickData[m_groupTicks];
        private int m_sharedSize;
        private Class<? extends ITickData[]> m_sharedClass = m_shared.getClass();

        private ITickData[] m_out = new ITickData[0];
        private int m_outPosition;
        private int m_outLength;
        private volatile int m_size;
        private long m_lastTimestamp;

        @Override public int size() { return m_size; }

        @Override public void addToTail(X x) {
            if (MONOTONE_TIME_INCREASE_CHECK) {
                long timestamp = x.getTimestamp();
                if (timestamp != 0) { // not no-more-ticks-marker
                    long diff = timestamp - m_lastTimestamp;
                    if (diff < 0) {
                        throw new RuntimeException("non-monotone time increase");
                    }
                    m_lastTimestamp = timestamp;
                }
            }

            m_in[m_inPosition++] = x;
            m_size++;
            if (m_inPosition >= m_groupTicks) {
                synchronized (this) {
                    moveInToShared();
                    notify();
                }
            }
        }

        @Override public X getFromHead() throws InterruptedException {
            if (m_outLength <= m_outPosition) { // all out was read
                synchronized (this) {
                    while (true) {
                        if (m_sharedSize > 0) {
//                            ITickData[] out = m_out;
                            m_out = m_shared;
                            m_outLength = m_sharedSize; // m_out.length;
                            m_outPosition = 0;

//                            m_shared = out; // todo: reuse buffer - breaks test for now.
//                            m_sharedSize = 0;
                            m_shared = new ITickData[m_groupTicks];
                            m_sharedSize = 0;
                            break;
                        }
                        wait();
                    }
                }
            }
            X ret = (X) m_out[m_outPosition++];
            m_size--;
            return ret;
        }

        @Override public void flush() {
            synchronized (this) {
                if (m_inPosition > 0) {
                    moveInToShared();
                }
                notify();
            }
        }

        @Override public void clean() {
            // no object pool
            m_in = null;
            m_inPosition = -1;
            m_shared = null;
            m_sharedSize = -1;
            m_out = null;
            m_outPosition = -1;
            m_outLength = -1;
        }

        private void moveInToShared() {
            ITickData[] shared = m_shared;
            int addCount = m_inPosition;
            int sharedSize = m_sharedSize;
            int newSharedSize = sharedSize + addCount;
            int currentSharedLen = shared.length;
            if (currentSharedLen < newSharedSize) { // grow
                shared = Arrays.copyOf(shared, newSharedSize, m_sharedClass);
                m_shared = shared;
            }
            System.arraycopy(m_in, 0, shared, sharedSize, addCount);
            m_sharedSize = newSharedSize;
            m_inPosition = 0;
        }
    }


    private static boolean DO_POOL = true;
    //=============================================================================================
    private class LightQueue <X extends ITickData> implements IQueue<X> {

        private volatile Node<X ,Node> m_head;
        private volatile Node<X ,Node> m_tail;
        private volatile Node<X ,Node> m_pool;
        private int m_size;
        private int m_count;

        public int size() { return m_size; }

        public void addToTail(X x) {
            synchronized (this) {
                Node<X, Node> node;
                if (DO_POOL) {
                    if (m_pool == null) {
                        node = new Node<>(null, x, null); // unlinked
                    } else {
                        node = m_pool;
                        node.m_param = x;
                        m_pool = m_pool.m_next;
                        node.m_prev = null;
                    }
                } else {
                    node = new Node<>(null, x, null); // unlinked
                }

                node.m_next = m_tail;
                if (m_tail != null) {
                    m_tail.m_prev = node;
                } else {
                    m_head = node;
                }
                m_tail = node;
                m_size++;

                if (m_count == 0) {
                    notify();
                    m_count = m_groupTicks;
                } else {
                    m_count--;
                }
            }
        }

        public X getFromHead() throws InterruptedException {
            synchronized (this) {
                while (true) {
                    if (m_head != null) {
                        X ret = m_head.m_param;

                        if (DO_POOL) {
                            m_head.m_next = m_pool;
                            m_pool = m_head;
                        }

                        Node prev = m_head.m_prev;
                        m_head.m_prev = null;
                        if (prev == null) { // no more elements
                            m_head = null;
                            m_tail = null;
                        } else {
                            m_head = prev;
                            prev.m_next = null;
                        }
                        m_size--;
                        return ret;
                    }
                    wait();
                }
            }
        }

        public void flush() {
            synchronized (this) {
                if (DO_POOL) {
                    cleanupPool();
                }
                notify();
            }
        }

        public void clean() {
            if (DO_POOL) {
                cleanupPool();
            }
        }

        private void cleanupPool() {
            Node nod = m_pool;
            while (nod != null) {
                Node next = nod.m_next;
                nod.m_next = null;
                nod = next;
            }
            m_pool = null;
        }
    }
}
