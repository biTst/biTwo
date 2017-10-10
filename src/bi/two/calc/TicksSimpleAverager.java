package bi.two.calc;

import bi.two.chart.ITickData;
import bi.two.ts.ITimesSeriesData;

public class TicksSimpleAverager extends TicksBufferBased<Double> {
    private double m_sum;
    private int m_count;

    public TicksSimpleAverager(ITimesSeriesData tsd, long period) {
        super(tsd, period);
    }

    @Override public void start() {
        // reset
        m_sum = 0;
        m_count = 0;
    }

    @Override public void processTick(ITickData tick) {
        float value = tick.getClosePrice();
        m_sum += value;
        m_count ++;
    }

    @Override public Double done() {
        return (m_count == 0) ? 0 : m_sum/m_count;
    }

    @Override protected float calcTickValue(Double ret) {
        return ret.floatValue();
    }
}
