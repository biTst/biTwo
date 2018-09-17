package bi.two.ts;

import bi.two.chart.ITickData;
import bi.two.chart.TickData;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import static bi.two.util.Log.console;
import static bi.two.util.Log.err;

public class ParallelTimesSeriesData extends BaseTimesSeriesData {
    private final int m_maxParallelSize;
    private int m_activeIndex;
    private final List<InnerTimesSeriesData> m_array = new ArrayList<>();
    private final AtomicInteger m_runningCount = new AtomicInteger();
private int m_ticksEntered = 0;

    public ParallelTimesSeriesData(BaseTimesSeriesData ticksTs, int size) {
        super(ticksTs);
        m_activeIndex = 0;
        m_maxParallelSize = size;
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
            addNewestTick(latestTick);
m_ticksEntered++;
        }
    }

    private void addNewestTick(ITickData latestTick) {
if ((m_ticksEntered % 2000000) == 0) {
    StringBuilder sb = new StringBuilder("entered=" + m_ticksEntered + "; buffers");
    for (InnerTimesSeriesData innerTsd : m_array) {
        int size = innerTsd.m_queue.size();
        sb.append(" [" + innerTsd.m_innerIndex + "]=" + size + ";");
        innerTsd.addNewestTick(latestTick);
    }
    console(sb.toString());
    return;
}

        if ((m_ticksEntered % 4000) == 0) {
            int maxSize = 0;
            int minSize = Integer.MAX_VALUE;
            for (int i = 0, arraySize = m_array.size(); i < arraySize; i++) {
                InnerTimesSeriesData innerTsd = m_array.get(i);
                innerTsd.addNewestTick(latestTick);
                int size = innerTsd.m_queue.size();
                maxSize = Math.max(maxSize, size);
                minSize = Math.min(minSize, size);
            }
            if (maxSize > 32000) {
                try {
//console("sleep 150");
                    Thread.sleep(150);
                } catch (InterruptedException e) { /*noop*/ }
            } else if (maxSize > 16000) {
                try {
//console("sleep 50");
                    Thread.sleep(50);
                } catch (InterruptedException e) { /*noop*/ }
            } else if (maxSize > 8000) {
                try {
//console("sleep 5");
                    Thread.sleep(5);
                } catch (InterruptedException e) { /*noop*/ }
            }
        } else {
            for (int i = 0, arraySize = m_array.size(); i < arraySize; i++) {
                InnerTimesSeriesData innerTsd = m_array.get(i);
                innerTsd.addNewestTick(latestTick);
            }
        }
    }

    // no more ticks - call from parent
    @Override public void notifyNoMoreTicks() {
console("NoMoreTicks in parallelTS, ticksEntered="+m_ticksEntered);
        TickData marker = new TickData(0, 0);
        addNewestTick(marker);
    }

    @Override public void waitWhenFinished() {
console("parallel.waitWhenFinished");
        synchronized (m_runningCount) {
            while (true) {
                int count = m_runningCount.get();
console("parallel.waitWhenFinished count="+count);
                if (count == 0) {
console(" all finished - exit");
                    return; // all finished - exit
                }
                try {
console(" wait more");
                    m_runningCount.wait();
                } catch (InterruptedException e) {
                    err("InterruptedException: " + e, e);
                    return;
                }
            }
        }
    }

    protected void onInnerFinished(InnerTimesSeriesData inner) {
        synchronized (m_runningCount) {
            m_runningCount.decrementAndGet();
            m_runningCount.notify();
        }
console("parallel.inner: thread finished " + inner);
    }


    //=============================================================================================
    protected class InnerTimesSeriesData extends BaseTimesSeriesData implements Runnable {
        private LinkedBlockingQueue<ITickData> m_queue = new LinkedBlockingQueue<>();

        private final int m_innerIndex;
        private ITickData m_currentTickData;
private int m_ticksProcessed = 0;

        @Override public String toString() {
            return "Inner-" + m_innerIndex;
        }

        InnerTimesSeriesData(int index) {
            m_innerIndex = index;
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
                    ITickData tick = m_queue.take(); // waiting if necessary until an element becomes available.
                    long timestamp = tick.getTimestamp();
                    if (timestamp == 0) { // special marker
                        notifyNoMoreTicks();
                        break;
                    } else {
m_ticksProcessed++;
                        m_currentTickData = tick;
                        notifyListeners(true);
                    }
                }
            } catch (InterruptedException e) {
                err("error: " + e, e);
            }
console("finish inner["+m_innerIndex+"]: ticksProcessed="+m_ticksProcessed);
            onInnerFinished(this);
        }

//        @Override public void notifyNoMoreTicks() { throw new RuntimeException("InnerTimesSeriesData should be used"); }

        public String getLogStr() {
            return "size=" + m_queue.size();
        }

        public void addNewestTick(ITickData latestTick) {
            try {
                m_queue.put(latestTick);
            } catch (InterruptedException e) {
                err("error: " + e, e);
            }
        }
    }
}
