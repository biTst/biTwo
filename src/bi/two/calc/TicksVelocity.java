package bi.two.calc;

import bi.two.chart.ITickData;
import bi.two.ts.ITimesSeriesData;

public class TicksVelocity extends TicksBufferBased<Double> {
    private ITickData m_first;
    private ITickData m_last;

    public TicksVelocity(ITimesSeriesData tsd, long period) {
        super(tsd, period);
    }

    @Override public void start() {
        // reset
        m_first = null;
        m_last = null;
    }

    @Override public void processTick(ITickData tick) {
        if(m_first == null) {
            m_first = tick;
        }
        m_last = tick;
    }

    @Override public Double done() {
        if (m_first != null) {
            float first = m_first.getClosePrice();
            float last = m_last.getClosePrice();
            float diff = first - last;
            float mid = (first + last) / 2;
            float speed = diff / mid;
            return Double.valueOf(speed);
        }
        return null;
    }

    @Override protected float calcTickValue(Double ret) {
        return ret.floatValue();
    }
}
