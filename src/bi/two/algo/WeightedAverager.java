package bi.two.algo;


import bi.two.chart.*;

import java.util.List;

public class WeightedAverager extends TimesSeriesData<TickData>{

    private final BarSplitter m_barSplitter;

    public WeightedAverager(final BarSplitter barSplitter) {
        super(barSplitter);
        m_barSplitter = barSplitter;
    }

    @Override public void onChanged(ITimesSeriesData ts, boolean changed) {
        if(changed) {
            boolean anyNotified = false;
            int counter = 0;
            for (final BarSplitter.BarHolder barHolder : m_barSplitter.getBars()) { // iterate all bars

                final int barIndex = counter;
                Boolean notified = barHolder.iterateTicks(new BarSplitter.BarHolder.ITicksProcessor<Boolean>() {
                    private float m_volumeSum;
                    private float m_amount;

                    @Override public void start() {}

                    @Override public void processTick(ITickData tick) {
                        float price = tick.getMaxPrice();
                        float volume = ((TickVolumeData) tick).getVolume();
                        m_volumeSum += volume;
                        m_amount += price * volume;
                    }

                    @Override public Boolean done() {
                        boolean notified;
                        long time = barHolder.getTime();
                        float avg = (m_volumeSum == 0) ? 0 : (m_amount / m_volumeSum);
                        List<TickData> ticks = getTicks(); // my ticks
                        if (barIndex < ticks.size()) { // if known bar changed - update my tick
                            TickData tickData = ticks.get(barIndex);
                            tickData.init(time, avg);
                            notified = false;
                        } else { // bars number is more than my ticks - add tick
                            TickData tickData = new TickData(time, avg);
                            addTick(tickData); // will notify listeners inside
                            notified = true;
                        }
                        return notified;
                    }
                });
                anyNotified |= notified;
                counter++;
            }
            if(!anyNotified) {
                notifyListeners(true);
            }
        } else {
            notifyListeners(changed);
        }
    }
}
