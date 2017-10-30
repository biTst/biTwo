package bi.two.calc;

import bi.two.chart.ITickData;
import bi.two.chart.TickData;
import bi.two.ts.BaseTimesSeriesData;
import bi.two.ts.ITimesSeriesData;

//----------------------------------------------------------
// TEMA: Triple exponential moving average
//  TEMA = 3*EMA - 3*EMA(EMA) + EMA(EMA(EMA))
public class BarsTEMA extends BaseTimesSeriesData<ITickData> {
    private final BarsEMA m_ema1;
    private final BarsEMA m_ema2;
    private final BarsEMA m_ema3;
    private TickData m_tickData;

    BarsTEMA(ITimesSeriesData<ITickData> tsd, int length, long barSize) {
        super();
        m_ema1 = new BarsEMA(tsd, length, barSize);
        m_ema2 = new BarsEMA(m_ema1, length, barSize);
        m_ema3 = new BarsEMA(m_ema2, length, barSize);
        setParent(m_ema3);
    }

    @Override public void onChanged(ITimesSeriesData ts, boolean changed) {
        boolean iAmChanged = false;
        if (changed) {
            ITickData ema1Tick = m_ema1.getLatestTick();
            if (ema1Tick != null) {
                ITickData ema2Tick = m_ema2.getLatestTick();
                if (ema2Tick != null) {
                    ITickData ema3Tick = m_ema3.getLatestTick();
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
        return "TEMA[]";
    }
}
