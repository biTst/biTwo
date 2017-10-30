package bi.two.calc;

import bi.two.algo.BarSplitter;
import bi.two.chart.ITickData;

//----------------------------------------------------------
public class BarsSMA extends BarsBasedCalculator {

    @Override protected BarTicksProcessor createBarTicksProcessor() {
        return new BarTicksProcessor() {
            private float m_sum;
            private int m_count;

            @Override public void start() {
                m_sum = 0;
                m_count = 0;
            }

            @Override public void processTick(ITickData tick) {
                float price = tick.getMaxPrice();
                m_sum += price;
                m_count++;
            }

            @Override protected float calcValue() {
                return (m_count == 0) ? 0 : (m_sum / m_count);
            }
        };
    }

    public BarsSMA(final BarSplitter barSplitter) {
        super(barSplitter);
    }
}
