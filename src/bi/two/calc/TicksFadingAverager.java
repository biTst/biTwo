package bi.two.calc;

import bi.two.chart.ITickData;
import bi.two.ts.ITimesSeriesData;

public class TicksFadingAverager extends TicksBufferBased<Double> {
    private long m_oldestTime;
    private double m_sum;
    private double m_weight;

    public TicksFadingAverager(ITimesSeriesData tsd, long period) {
        super(tsd, period);
    }

    @Override public void start() {
        // reset
        m_oldestTime = 0L;
        m_sum = 0;
        m_weight = 0;
    }

    @Override public void processTick(ITickData tick) {
        long timestamp = tick.getTimestamp();
        if (m_oldestTime == 0) {
            m_oldestTime = timestamp;
        }
        double rate = (timestamp - m_oldestTime);
        float value = tick.getClosePrice();
        m_sum += rate*value;
        m_weight += rate;
    }

    @Override public Double done() {
        return (m_weight == 0) ? null : m_sum/m_weight;
    }

    @Override protected float calcTickValue(Double ret) {
        return ret.floatValue();
    }
}
