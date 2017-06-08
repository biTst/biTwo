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
            int counter = 0;
            for (final BarSplitter.BarHolder barHolder : m_barSplitter.getBars()) { // iterate all bars

                final int barIndex = counter;
                barHolder.iterateTicks(new BarSplitter.BarHolder.ITicksProcessor() {
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
                        List<TickData> ticks = getTicks(); // my ticks
                        if (barIndex < ticks.size()) { // if known bar changed - update my tick
                            TickData tickData = ticks.get(barIndex);
                            tickData.init(time, avg);
                        } else { // bars number is more than my ticks - add tick
                            TickData tickData = new TickData(time, avg);
                            addTick(tickData);
                        }
                    }
                });
                counter++;
            }
        }
        notifyListeners(changed);
    }
}
