package bi.two.calc;

import bi.two.chart.ITickData;
import bi.two.chart.TickData;
import bi.two.ts.BaseTimesSeriesData;
import bi.two.ts.ITimesSeriesData;

//----------------------------------------------------------
// Lag = (Period-1)/2
// EmaData = Data + (Data - Data(Lagdaysago))
// ZLEMA = EMA(EmaData,Period)
//
//  but actually here implemented DEMA:
// Double exponential moving average
// DEMA = 2*EMA - EMA(EMA)
public class BarsDEMA extends BaseTimesSeriesData<ITickData> {
    private final BarsEMA m_ema1;
    private final BarsEMA m_ema2;
    private TickData m_tickData;

    BarsDEMA(ITimesSeriesData<ITickData> tsd, int length, long barSize) {
        super();
        m_ema1 = new BarsEMA(tsd, length, barSize);
        m_ema2 = new BarsEMA(m_ema1, length, barSize);
        setParent(m_ema2);
    }

    @Override public void onChanged(ITimesSeriesData ts, boolean changed) {
        boolean iAmChanged = false;
        if (changed) {
            ITickData ema1Tick = m_ema1.getLatestTick();
            if (ema1Tick != null) {
                ITickData ema2Tick = m_ema2.getLatestTick();
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
        return "DEMA[]";
    }
}
