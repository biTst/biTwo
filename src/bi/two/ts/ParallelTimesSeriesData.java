package bi.two.ts;

import bi.two.Main;
import bi.two.algo.Node;
import bi.two.chart.ITickData;
import bi.two.chart.TickData;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static bi.two.util.Log.*;

public class ParallelTimesSeriesData extends BaseTimesSeriesData {
    private final int m_maxParallelSize;
    private final int m_groupTicks;
    private int m_activeIndex;
    private final List<InnerTimesSeriesData> m_array = new ArrayList<>(); // todo: optimize - remake via array
    private final AtomicInteger m_runningCount = new AtomicInteger();
    private int m_ticksEntered = 0;
    private long m_reportCounter;
    private long m_sleepCounter;
    private long m_nanoSumm;
    private long m_nanoCount;

    public ParallelTimesSeriesData(BaseTimesSeriesData ticksTs, int size, int groupTicks) {
        super(ticksTs);
        m_activeIndex = 0;
        m_maxParallelSize = size;
        m_groupTicks = groupTicks;
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
        return ret;
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
        if ((++m_reportCounter) == 500000) { // log queue states
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
            sb.append(" avg=").append(nanoAvg).append(";");

            long freeMemory1 = Runtime.getRuntime().freeMemory();
            long totalMemory1 = Runtime.getRuntime().totalMemory();
            long maxMemory1 = Runtime.getRuntime().maxMemory();
            long usedMemory1 = totalMemory1 - freeMemory1;

            sb.append("  mem(f/u/t/m): ").append(Main.formatMemory(freeMemory1))
                    .append("/").append(Main.formatMemory(usedMemory1))
                    .append("/").append(Main.formatMemory(totalMemory1))
                    .append("/").append(Main.formatMemory(maxMemory1));

            console(sb.toString());
            return;
        }

        if ((++m_sleepCounter) == 40001) {
            m_sleepCounter = 0;
            int maxSize = 0;
            int minSize = Integer.MAX_VALUE;
            for (int i = 0, arraySize = m_array.size(); i < arraySize; i++) {
                InnerTimesSeriesData innerTsd = m_array.get(i);
                innerTsd.addNewestTick(latestTick);
                int size = innerTsd.m_queue.size();
                maxSize = Math.max(maxSize, size);
                minSize = Math.min(minSize, size);
            }
            if (maxSize > 2000) {
                long sleep;
                if (maxSize > 3000) {
                    if (maxSize > 5000) {
                        if (maxSize > 7000) {
                            if (maxSize > 10000) {
                                sleep = 1000;
                            } else {
                                sleep = 500;
                            }
                        } else {
                            sleep = 250;
                        }
                    } else {
                        sleep = 100;
                    }
                } else {
                    sleep = 20;
                }
                try {
                    log("sleep " + sleep + "ms; maxSize=" + maxSize + "; minSize=" + minSize);
                    Thread.sleep(sleep);
                } catch (InterruptedException e) { /*noop*/ }
            }
        } else {
            long nanoTime1 = System.nanoTime();
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
    }

    @Override public void waitWhenAllFinish() {
        log("parallel.waitWhenAllFinish");
        synchronized (m_runningCount) {
            while (true) {
                int count = m_runningCount.get();
                log("parallel.waitWhenAllFinish count="+count);
                if (count == 0) {
                    log(" all finished - exit");
                    return; // all finished - exit
                }
                try {
                    log(" wait more");
                    m_runningCount.wait();
                    count = m_runningCount.get();
                    log("  count after wait = " + count );
                } catch (InterruptedException e) {
                    err("InterruptedException: " + e, e);
                    return;
                }
            }
        }
    }

    protected void onInnerFinished(InnerTimesSeriesData inner) {
        int remained;
        synchronized (m_runningCount) {
            remained = m_runningCount.decrementAndGet();
            m_runningCount.notify();
            log("parallel.inner: thread finished " + inner + "; remained=" + remained);
        }
    }


    //=============================================================================================
    protected class InnerTimesSeriesData extends BaseTimesSeriesData implements Runnable {
//        private LinkedBlockingQueue<ITickData> m_queue = new LinkedBlockingQueue<>();
//        private ConcurrentLinkedQueue<ITickData> m_queue = new ConcurrentLinkedQueue<>();
        private final LightQueue<ITickData> m_queue;
        private final int m_innerIndex;
        private ITickData m_currentTickData;

        @Override public String toString() {
            return "Inner-" + m_innerIndex;
        }

        InnerTimesSeriesData(int index) {
            m_innerIndex = index;
            m_queue = new LightQueue<>();
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
//                    ITickData tick = m_queue.take(); // waiting if necessary until an element becomes available.

//                    ITickData tick = m_queue.poll();
//                    if (tick == null) {
//                        Thread.sleep(100);
//                        continue;
//                    }

                    ITickData tick = m_queue.getFromHead();

                    long timestamp = tick.getTimestamp();
                    if (timestamp == 0) { // special marker
                        notifyNoMoreTicks();
                        break;
                    } else {
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

//            m_queue.add(latestTick);

//            try {
//                m_queue.put(latestTick);
//            } catch (InterruptedException e) {
//                err("error: " + e, e);
//            }
        }
    }


    //=============================================================================================
    private class LightQueue <X extends ITickData> {
        private volatile Node<X ,Node> m_head;
        private volatile Node<X ,Node> m_tail;
        private volatile Node<X ,Node> m_pool;
        private int m_size;
        private int m_count;

        public int size() { return m_size; }

        void addToTail(X x) {
            synchronized (this) {
                Node<X, Node> node;
                if (m_pool == null) {
                    node = new Node<>(null, x, null); // unlinked
                } else {
                    node = m_pool;
                    node.m_param = x;
                    m_pool = m_pool.m_next;
                    node.m_prev=null;
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

        void flush() {
            synchronized (this) {
                notify();
            }
        }

        X getFromHead() throws InterruptedException {
            synchronized (this) {
                while (true) {
                    if (m_head != null) {
                        X ret = m_head.m_param;

                        m_head.m_next = m_pool;
                        m_pool = m_head;

                        Node prev = m_head.m_prev;
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
    }
}
