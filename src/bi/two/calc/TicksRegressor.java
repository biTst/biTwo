package bi.two.calc;

import bi.two.chart.ITickData;
import bi.two.ts.ITimesSeriesData;
import org.apache.commons.math3.stat.regression.SimpleRegression;

public class TicksRegressor extends TicksBufferBased<Boolean> {
    private final SimpleRegression m_simpleRegression = new SimpleRegression(true);
    private long m_lastBarTickTime;

    public TicksRegressor(ITimesSeriesData<ITickData> tsd, long period) {
        super(tsd, period);
    }

    @Override public void start() {
        m_simpleRegression.clear();
        m_lastBarTickTime = 0;// reset
    }

    @Override public void processTick(ITickData tick) {
        long timestamp = tick.getTimestamp();
        if (m_lastBarTickTime == 0) {
            m_lastBarTickTime = timestamp;
        }

        float price = tick.getMaxPrice();
        m_simpleRegression.addData(m_lastBarTickTime - timestamp, price);
    }

    @Override public Boolean done() {
        return null;
    }

    @Override protected float calcTickValue(Boolean ret) {
        double value = m_simpleRegression.getIntercept();
        return (float) value;
    }

    public String log() {
        return "Regressor["
                + "\nsplitter=" + m_splitter.log()
                + "\n]";
    }
}
