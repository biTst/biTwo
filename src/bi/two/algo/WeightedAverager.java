package bi.two.algo;


import bi.two.chart.*;

import java.util.List;

public class WeightedAverager extends TimesSeriesData<TickData>{

    public WeightedAverager(final BarSplitter barSplitter) {
        barSplitter.addListener(new ITimesSeriesData.ITimesSeriesListener() {
            @Override public void onChanged() {
                int counter = 0;
                for (final BarSplitter.BarHolder barHolder : barSplitter.getBars()) {
                    final int finalCounter = counter;
                    barHolder.iterateTicks(new BarSplitter.ITicksProcessor() {
                        private float m_volumeSum;
                        private float m_amount;

                        @Override public void processTick(ITickData tick) {
                            float price = tick.getMaxPrice();
                            float volume = ((TickVolumeData) tick).getVolume();
                            m_volumeSum += volume;
                            m_amount += price * volume;
                        }

                        @Override public void done() {
                            long time = barHolder.getTime();
                            float avg = (m_volumeSum == 0) ? 0 : (m_amount / m_volumeSum);
                            List<TickData> ticks = getTicks();
                            if (finalCounter < ticks.size()) {
                                TickData tickData = ticks.get(finalCounter);
                                tickData.init(time, avg);
                            } else {
                                TickData tickData = new TickData(time, avg);
                                addTick(tickData);
                            }
                        }
                    });
                    counter++;
                }
            }
        });

//        barSplitter.addListener(new ITimesSeriesData.ITimesSeriesListener() {
//            @Override public void onChanged() {
//                int counter = 0;
//                for (final BarSplitter.BarHolder barHolder : barSplitter.getBars()) {
//                    final int finalCounter = counter;
//                    barHolder.iterateTicks(new BarSplitter.ITicksProcessor() {
//                        private float m_volumeSum;
//                        private float m_amount;
//
//                        @Override public void processTick(ITickData tick) {
//                            float price = tick.getMaxPrice();
//                            float volume = ((TickVolumeData) tick).getVolume();
//                            m_volumeSum += volume;
//                            m_amount += price * volume;
//                        }
//
//                        @Override public void done() {
//                            long time = barHolder.getTime();
//                            float avg = (m_volumeSum == 0) ? 0 : (m_amount / m_volumeSum);
//                            List<TickData> ticks = getTicks();
//                            if (finalCounter < ticks.size()) {
//                                TickData tickData = ticks.get(finalCounter);
//                                tickData.init(time, avg);
//                            } else {
//                                TickData tickData = new TickData(time, avg);
//                                ticks.add(tickData);
//                            }
//                        }
//                    });
//                    counter++;
//                }
//            }
//        });
    }
}
