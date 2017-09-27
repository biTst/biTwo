package bi.two.ts;

import bi.two.chart.ITickData;
import bi.two.chart.TickData;

import java.util.ArrayList;
import java.util.List;

public class ParallelTimesSeriesData extends BaseTimesSeriesData {
    private final int m_size;
    private final List<InnerTimesSeriesData> m_array = new ArrayList<>();
    private int m_activeIndex;
    private final Object m_emptyLock = new Object();
    private int m_emptyCount;

    public ParallelTimesSeriesData(TimesSeriesData<TickData> ticksTs, int size) {
        super(ticksTs);
        m_activeIndex = 0;
        m_size = size;
        m_emptyCount = size;
    }

    @Override public ITimesSeriesData getActive() {
        if (m_activeIndex == m_array.size()) {
            m_array.add(new InnerTimesSeriesData(m_activeIndex));
        }
        InnerTimesSeriesData ret = m_array.get(m_activeIndex);
        m_activeIndex = (m_activeIndex + 1) % m_size;
        return ret;
    }

    @Override public ITickData getLatestTick() {
        throw new RuntimeException("InnerTimesSeriesData should be used");
    }

    @Override public void addListener(ITimesSeriesListener listener) {
        throw new RuntimeException("InnerTimesSeriesData should be used");
    }

    @Override public void removeListener(ITimesSeriesListener listener) {
        throw new RuntimeException("InnerTimesSeriesData should be used");
    }

    @Override protected void notifyListeners(boolean changed) {
        ITickData latestTick = changed ? m_parent.getLatestTick() : null;

        waitSomeEmpty();

        for (InnerTimesSeriesData inner : m_array) {
            inner.enqueue(latestTick);
        }
    }

    @Override public void notifyFinished() {
        for (InnerTimesSeriesData inner : m_array) {
            inner.notifyFinished();
        }
    }

    private void waitSomeEmpty() {
        synchronized (m_emptyLock) {
            while (m_emptyCount == 0) {
                try {
                    m_emptyLock.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void waitAllEmpty() {
        synchronized (m_emptyLock) {
//            System.out.println("waitAllEmpty() emptyCount=" + m_emptyCount + "; size=" + m_size);
            while (m_emptyCount != m_size) {
                try {
                    m_emptyLock.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
//                System.out.println(" waitAllEmpty: emptyCount=" + m_emptyCount + "; size=" + m_size);
            }
        }
    }

    @Override public void waitWhenFinished() {
        waitAllEmpty();
    }

    private void onNonEmpty(InnerTimesSeriesData inner) {
        synchronized (m_emptyLock) {
            m_emptyCount--;
            m_emptyLock.notify();
        }
    }

    private void onEmpty(InnerTimesSeriesData inner) {
        synchronized (m_emptyLock) {
            m_emptyCount++;
            m_emptyLock.notify();
        }
    }


    //=============================================================================================
    private class InnerTimesSeriesData extends BaseTimesSeriesData implements Runnable {
        private final Object m_lock = new Object();
        private final ArrayList<ITickData> m_ticks = new ArrayList<>();
        private final int m_index;
        private int m_newestTickIndex = -1;
        private int m_oldestTickIndex = 0;
        private ITickData m_currentTickData;
        private boolean m_outerFinished;

        @Override public String toString() {
            return "Inner-" + m_index;
        }

        InnerTimesSeriesData(int index) {
            m_index = index;
            new Thread(this, "parallel-" + index).start();
        }

        @Override public ITickData getLatestTick() {
            return m_currentTickData;
        }

        @Override public void run() {
            try {
                while (true) {
                    boolean changed;
                    synchronized (m_lock) {
                        if (isEmpty()) { // empty
                            if (m_outerFinished) {
                                break;
                            }
                            m_lock.wait();
                            continue; // restart cycle, but do not notify
                        } else { // non-empty
                            ITickData currentTickData = m_ticks.get(m_oldestTickIndex);
                            m_oldestTickIndex++;
                            changed = (currentTickData != null);
                            if (changed) {
                                m_currentTickData = currentTickData;
                            }
                            if (isEmpty()) {
                                onEmpty(this);
                            }
                        }
                    }
                    notifyListeners(changed);
                }
                super.notifyFinished();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
//            System.out.println("parallel.inner: thread finished");
        }

        void enqueue(ITickData latestTick) {
            synchronized (m_lock) {
                boolean wasEmpty = isEmpty();
                if (m_oldestTickIndex > 10) { // shift
                    for(int from = m_oldestTickIndex, to = 0; from <= m_newestTickIndex; from++, to++) {
                        ITickData currentTickData = m_ticks.get(from);
                        m_ticks.set(to, currentTickData);
                    }
                    m_newestTickIndex -= m_oldestTickIndex;
                    m_oldestTickIndex = 0;
                }
                m_newestTickIndex++;
                if (m_newestTickIndex >= m_ticks.size()) {
                    m_ticks.add(latestTick);
                } else {
                    m_ticks.set(m_newestTickIndex, latestTick);
                }
                if (wasEmpty) {
                    onNonEmpty(this);
                }
                m_lock.notify();
            }
        }

        boolean isEmpty() {
            synchronized (m_lock) {
                boolean isEmpty = m_newestTickIndex < m_oldestTickIndex;
                return isEmpty;
            }
        }

        @Override public void notifyFinished() {
            m_outerFinished = true;
//            System.out.println("parallel.inner: all ticks was read");
            synchronized (m_lock) {
                m_lock.notify();
            }
        }
    }
}
