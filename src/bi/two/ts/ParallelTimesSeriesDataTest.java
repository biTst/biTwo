package bi.two.ts;

import bi.two.chart.ITickData;
import bi.two.chart.TickData;
import bi.two.util.TimeStamp;

import static bi.two.util.Log.console;

public class ParallelTimesSeriesDataTest {

    private static final int TICKS_TO_PASS = 2000000;

    public static void main(String[] args) {
        TimeStamp timeStamp = new TimeStamp();
        final TickData[] tickData = new TickData[1];
        TicksTimesSeriesData<TickData> parent = new TicksTimesSeriesData<TickData>(null) {
            @Override public TickData getLatestTick() {
                return tickData[0];
            }
        };
        ParallelTimesSeriesData parallelTs = new ParallelTimesSeriesData(parent, 7, 5, null) {
            @Override protected void onInnerFinished(InnerTimesSeriesData inner) {
                super.onInnerFinished(inner);
                console("parallel.inner: thread finished " + inner);
            }
        };
        for (int i = 0; i < 16; i++) {
            final int finalI = i;
            parallelTs.getActive().addListener(new ITimesSeriesListener() {
                long m_expectedTimestamp = 1;
                
                @Override public void onChanged(ITimesSeriesData ts, boolean changed) {
                    ITickData latestTick = ts.getLatestTick();
                    long timestamp = latestTick.getTimestamp();
                    if(m_expectedTimestamp != timestamp) {
                        throw new RuntimeException("expectedTimestamp(" + m_expectedTimestamp + ") != timestamp(" + timestamp + ")");
                    }

                    if (timestamp % 200000 == 0) {
                        ParallelTimesSeriesData.InnerTimesSeriesData itsd = (ParallelTimesSeriesData.InnerTimesSeriesData) ts;
                        String log = itsd.getLogStr();
                        console(" child[" + finalI + "] passed:  " + timestamp + "; " + log);
                    }

                    if(timestamp % 100 == 0) {
                        long wait = (long) (Math.random() * 1000000);
                        for (long j = 0; j < wait; j++) {
                            timestamp--;
                        }
                    }

                    m_expectedTimestamp++;
                }

                @Override public void waitWhenAllFinish() { /*noop*/ }
                @Override public void onTimeShift(long shift) {
                    throw new RuntimeException("not expected here");
                }

                @Override public void notifyNoMoreTicks() {
                    if (m_expectedTimestamp != (TICKS_TO_PASS + 1)) {
                        throw new RuntimeException("expectedTimestamp(" + m_expectedTimestamp + ") != TICKS_TO_PASS(" + TICKS_TO_PASS + ")");
                    }
                    console(" child[" + finalI + "] finished: " + m_expectedTimestamp);
                }
            });
        }


        long timestamp = 1;
        while (timestamp <= TICKS_TO_PASS) {
            tickData[0] = new TickData(timestamp, timestamp);
            parent.notifyListeners(true);
            timestamp++;
            if(timestamp % 200000 == 0) {
                console("timestamp = " + timestamp);
            }
            if(timestamp % 40000 == 0) {
                long wait = (long) (Math.random() * 100);
                try {
                    Thread.sleep(wait);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        console("all passed " + timestamp);

        console("notifyNoMoreTicks...");
        parallelTs.notifyNoMoreTicks();

        console("waitWhenAllFinish...");
        parallelTs.waitWhenAllFinish();
        console("parallelTs Finished in " + timeStamp.getPassed());
    }
}
