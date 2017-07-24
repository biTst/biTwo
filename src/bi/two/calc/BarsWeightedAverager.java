package bi.two.calc;


import bi.two.algo.BarSplitter;
import bi.two.chart.ITickData;
import bi.two.chart.TickVolumeData;

public class BarsWeightedAverager extends BarsBasedCalculator {

    @Override protected BarTicksProcessor createBarTicksProcessor() {
        return new BarTicksProcessor() {
            private float m_volumeSum;
            private float m_amount;

            @Override public void start() {
                m_volumeSum = 0;
                m_amount = 0;
            }

            @Override public void processTick(ITickData tick) {
                float price = tick.getMaxPrice();
                float volume = ((TickVolumeData) tick).getVolume();
                m_volumeSum += volume;
                m_amount += price * volume;
            }

            @Override protected float calcValue() {
                return (m_volumeSum == 0) ? 0 : (m_amount / m_volumeSum);
            }
        };
    }

    public BarsWeightedAverager(final BarSplitter barSplitter) {
        super(barSplitter);
    }
}
