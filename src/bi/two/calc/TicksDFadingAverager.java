package bi.two.calc;

import bi.two.chart.ITickData;
import bi.two.chart.TickData;
import bi.two.ts.BaseTimesSeriesData;
import bi.two.ts.ITimesSeriesData;

//----------------------------------------------------------
// Double exponential moving average
// DEMA = 2*EMA - EMA(EMA)
public class TicksDFadingAverager extends BaseTimesSeriesData<ITickData> {
    private final TicksFadingAverager m_fa1;
    private final TicksFadingAverager m_fa2;
    private TickData m_tickData;

    public TicksDFadingAverager(ITimesSeriesData<ITickData> tsd, long period) {
        super();
        m_fa1 = new TicksFadingAverager(tsd, period);
        m_fa2 = new TicksFadingAverager(m_fa1, period);
        setParent(m_fa2);
    }

    @Override public void onChanged(ITimesSeriesData ts, boolean changed) {
        boolean iAmChanged = false;
        if (changed) {
            ITickData ema1Tick = m_fa1.getLatestTick();
            if (ema1Tick != null) {
                ITickData ema2Tick = m_fa2.getLatestTick();
                if (ema2Tick != null) {
                    float ema1 = ema1Tick.getClosePrice();
                    float ema2 = ema2Tick.getClosePrice();
                    float dema = 2 * ema1 - ema2;

                    if ((m_tickData == null) || (m_tickData.getClosePrice() != dema)) {
                        long timestamp = m_parent.getLatestTick().getTimestamp();
                        m_tickData = new TickData(timestamp, dema);
                        iAmChanged = true;
                    }
                }
            }
        }
        super.onChanged(this, iAmChanged); // notifyListeners
    }

    @Override public ITickData getLatestTick() {
        return m_tickData;
    }

    public String log() {
        return "DFA[]";
    }
}
