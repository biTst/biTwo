package bi.two.calc;

import bi.two.chart.ITickData;
import bi.two.chart.TickData;
import bi.two.ts.BaseTimesSeriesData;
import bi.two.ts.ITimesSeriesData;

//----------------------------------------------------------
// TEMA: Triple exponential moving average
//  TEMA = 3*EMA - 3*EMA(EMA) + EMA(EMA(EMA))
public class TicksTFadingAverager extends BaseTimesSeriesData<ITickData> {
    private final TicksFadingAverager m_fa1;
    private final TicksFadingAverager m_fa2;
    private final TicksFadingAverager m_fa3;
    private TickData m_tickData;

    public TicksTFadingAverager(ITimesSeriesData<ITickData> tsd, long period) {
        super();
        m_fa1 = new TicksFadingAverager(tsd, period);
        m_fa2 = new TicksFadingAverager(m_fa1, period);
        m_fa3 = new TicksFadingAverager(m_fa2, period);
        setParent(m_fa3);
    }

    @Override public void onChanged(ITimesSeriesData ts, boolean changed) {
        boolean iAmChanged = false;
        if (changed) {
            ITickData ema1Tick = m_fa1.getLatestTick();
            if (ema1Tick != null) {
                ITickData ema2Tick = m_fa2.getLatestTick();
                if (ema2Tick != null) {
                    ITickData ema3Tick = m_fa3.getLatestTick();
                    if (ema3Tick != null) {
                        float ema1 = ema1Tick.getClosePrice();
                        float ema2 = ema2Tick.getClosePrice();
                        float ema3 = ema3Tick.getClosePrice();
                        float tema = 3 * ema1 - 3 * ema2 + ema3;

                        if ((m_tickData == null) || (m_tickData.getClosePrice() != tema)) {
                            long timestamp = m_parent.getLatestTick().getTimestamp();
                            m_tickData = new TickData(timestamp, tema);
                            iAmChanged = true;
                        }
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
        return "TFA[]";
    }
}
