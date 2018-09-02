package bi.two.ts;

import bi.two.chart.ITickData;

import java.util.ArrayList;
import java.util.List;

public class ParallelTimesSeriesData extends BaseTimesSeriesData {
    private static final int INIT_DATA_LEN = 1000;

    private final int m_maxParallelSize;
    private final List<InnerTimesSeriesData> m_array = new ArrayList<>();
    private int m_activeIndex;

    private volatile ITickData[] m_ticksArray = new ITickData[INIT_DATA_LEN];
    private volatile int m_ticksArrayLen = INIT_DATA_LEN;
    private volatile int m_firstTickIndex = 0; // no ticks at start; overflow with m_lastTickIndex meant no ticks
    private volatile int m_lastTickIndex = -1; // no ticks at start; overflow with m_firstTickIndex meant no ticks
    private volatile int m_availableTicksNum = 0; // no ticks at start
    private volatile boolean m_isOuterFinished = false; // End of ticks
    private int m_counter; // number of processed ticks
    private boolean m_manyTicks; // flag that many non processed ticks in buffer

    public ParallelTimesSeriesData(BaseTimesSeriesData ticksTs, int size) {
        super(ticksTs);
        m_activeIndex = 0;
        m_maxParallelSize = size;
    }

    @Override public ITimesSeriesData getActive() {
        if (m_activeIndex == m_array.size()) {
            m_array.add(new InnerTimesSeriesData(m_activeIndex));
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
        synchronized (this) {
            int newLastTickIndex = m_lastTickIndex + 1;
            if (newLastTickIndex >= m_ticksArray.length) { // no more space in array
                // try shift first
                if (m_firstTickIndex > INIT_DATA_LEN) {
                    int shift = m_firstTickIndex;
                    if (m_availableTicksNum > 0) {
                        System.arraycopy(m_ticksArray, shift, m_ticksArray, 0, m_availableTicksNum);
                    }
                    for (InnerTimesSeriesData innerTsd : m_array) {
                        innerTsd.m_currentTickIndex -= shift;
                    }
                    m_firstTickIndex -= shift;
                    m_lastTickIndex -= shift;
                    newLastTickIndex -= shift;
                } else {
                    // increase data array size
                    int newTicksArrayLen = m_ticksArrayLen + INIT_DATA_LEN;
                    ITickData[] newDataArray = new ITickData[newTicksArrayLen];
                    System.arraycopy(m_ticksArray, 0, newDataArray, 0, m_ticksArrayLen);
                    m_ticksArray = newDataArray;
                    m_ticksArrayLen = newTicksArrayLen;
                }
            }
            m_ticksArray[newLastTickIndex] = latestTick;
            m_lastTickIndex = newLastTickIndex;
            m_availableTicksNum++;
            notifyAll();

            m_counter++;
            if ((m_counter % INIT_DATA_LEN) == 0) {
                int max = -1;
                for (InnerTimesSeriesData innerTsd : m_array) {
                    int innerTickIndex = innerTsd.m_currentTickIndex;
                    if (innerTickIndex > max) {
                        max = innerTickIndex;
                    }
                }
                int extraTicks = m_lastTickIndex - max;
                if (extraTicks > INIT_DATA_LEN) {
                    try {
                        m_manyTicks = true;
                        wait(1000);
                        m_manyTicks = false;
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        } // synchronized (this)
    }

    private void updateFirstTickIndexIfNeeded(int processedTickIndex) {
        if (processedTickIndex == m_firstTickIndex) { // tick at firstTickIndex was processed
            int min = Integer.MAX_VALUE;
            for (InnerTimesSeriesData innerTsd : m_array) {
                int innerTickIndex = innerTsd.m_currentTickIndex;
                if (innerTickIndex < min) {
                    min = innerTickIndex;
                }
            }
            int abandoned = min - m_firstTickIndex;
            if (abandoned > 0) {
                m_firstTickIndex += abandoned;
                m_availableTicksNum -= abandoned;
            }
        }
    }


    // no more ticks - call from parent
    @Override public void notifyNoMoreTicks() {
        synchronized (this) {
            m_isOuterFinished = true;
            notifyAll();
        }
    }

    @Override public void waitWhenFinished() {
        synchronized (this) {
            while (true) {
                boolean allInnerFinished = true;
                for (InnerTimesSeriesData inner : m_array) {
                    if (!inner.m_isInnerFinished) {
                        allInnerFinished = false;
                        break;
                    }
                }

                if (allInnerFinished) {
                    break;
                }

                try {
                    wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    protected void onInnerFinished(InnerTimesSeriesData inner) {
//        System.out.println("parallel.inner: thread finished " + inner);
    }


    //=============================================================================================
    protected class InnerTimesSeriesData extends BaseTimesSeriesData implements Runnable {
        private final int m_innerIndex;
        private volatile int m_currentTickIndex = 0;
        private ITickData m_currentTickData;
        private boolean m_isInnerFinished;

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
                iterate();
                super.notifyNoMoreTicks();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            onInnerFinished(this);
        }

        private void iterate() throws InterruptedException {
            ParallelTimesSeriesData outer = ParallelTimesSeriesData.this;
            while (true) {
                ITickData tick;
                synchronized (outer) {
                    while (m_lastTickIndex < m_currentTickIndex) {
                        // no ticks - need wait
                        if (outer.m_isOuterFinished) {
                            m_isInnerFinished = true;
                            outer.notifyAll();
                            return;
                        }
                        if (m_manyTicks) {
                            outer.notifyAll(); // notify waiting feeder
                        }
                        outer.wait();
                    }
                    int currentTickIndex = m_currentTickIndex;
                    tick = m_ticksArray[currentTickIndex];
                    m_currentTickIndex++;
                    updateFirstTickIndexIfNeeded(currentTickIndex);
                } // synchronized (outer)
                if (tick != null) {
                    m_currentTickData = tick;
                    notifyListeners(true);
                } else {
                    notifyListeners(false);
                }
            }
        }

        @Override public void notifyNoMoreTicks() { throw new RuntimeException("InnerTimesSeriesData should be used"); }

        public String log() {
            return "currentTickIndex=" + m_currentTickIndex;
        }
    }
}
