package bi.two.ts;

import bi.two.chart.ITickData;
import bi.two.chart.TickData;
import bi.two.util.TimeStamp;

public class ParallelTimesSeriesDataTest {

    private static final int TICKS_TO_PASS = 2000000;

    public static void main(String[] args) {
        TimeStamp timeStamp = new TimeStamp();
        final TickData[] tickData = new TickData[1];
        TimesSeriesData<TickData> parent = new TimesSeriesData<TickData>(null) {
            @Override public TickData getLatestTick() {
                return tickData[0];
            }
        };
        ParallelTimesSeriesData parallelTs = new ParallelTimesSeriesData(parent, 7) {
            @Override protected void onInnerFinished(InnerTimesSeriesData inner) {
                System.out.println("parallel.inner: thread finished " + inner);
            }
        };
        for (int i = 0; i < 16; i++) {
            int finalI = i;
            parallelTs.getActive().addListener(new ITimesSeriesData.ITimesSeriesListener() {
                long m_expectedTimestamp = 0;
                
                @Override public void onChanged(ITimesSeriesData ts, boolean changed) {
                    ITickData latestTick = ts.getLatestTick();
                    long timestamp = latestTick.getTimestamp();
                    if(m_expectedTimestamp != timestamp) {
                        throw new RuntimeException("m_expectedTimestamp(" + m_expectedTimestamp + ") != timestamp(" + timestamp + ")");
                    }

                    if(timestamp % 100000 == 0) {

                        ParallelTimesSeriesData.InnerTimesSeriesData itsd = (ParallelTimesSeriesData.InnerTimesSeriesData) ts;
                        String log = itsd.log();

                        System.out.println(" child[" + finalI + "] passed:  " + timestamp + "; " + log);
                    }

                    if(timestamp % 100 == 0) {
                        long wait = (long) (Math.random() * 1000000);
                        for (long j = 0; j < wait; j++) {
                            timestamp--;
                        }
                    }

                    m_expectedTimestamp++;
                }

                @Override public void waitWhenFinished() {

                }

                @Override public void notifyFinished() {
                    if (m_expectedTimestamp != TICKS_TO_PASS) {
                        throw new RuntimeException("m_expectedTimestamp(" + m_expectedTimestamp + ") != TICKS_TO_PASS(" + TICKS_TO_PASS + ")");
                    }
                    System.out.println(" child[" + finalI + "] finished: " + m_expectedTimestamp);
                }
            });
        }


        long timestamp = 0;
        while (timestamp < TICKS_TO_PASS) {
            tickData[0] = new TickData(timestamp, timestamp);
            parent.notifyListeners(true);
            timestamp++;
            if(timestamp % 100000 == 0) {
                System.out.println("timestamp = " + timestamp);
            }
            if(timestamp % 30000 == 0) {
                long wait = (long) (Math.random() * 100);
                try {
                    Thread.sleep(wait);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        System.out.println("all passed " + timestamp);

        System.out.println("notifyFinished...");
        parallelTs.notifyFinished();

        System.out.println("waitWhenFinished...");
        parallelTs.waitWhenFinished();
        System.out.println("parallelTs Finished in " + timeStamp.getPassed());
    }
}
