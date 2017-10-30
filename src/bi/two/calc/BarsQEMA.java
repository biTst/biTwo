package bi.two.calc;

import bi.two.chart.ITickData;
import bi.two.chart.TickData;
import bi.two.ts.BaseTimesSeriesData;
import bi.two.ts.ITimesSeriesData;

//----------------------------------------------------------
// QEMA: Quadruple exponential moving average
//  QEMA = 5*MA1 - 10*MA2 + 10*MA3 - 5*MA4 + MA5
// MA1=Moving Average(Price),
// MA2=Moving Average(MA1),
// MA3=Moving Average(MA2),
// MA4=Moving Average(MA3),
// MA5=Moving Average(MA4).
public class BarsQEMA extends BaseTimesSeriesData<ITickData> {
    private final BarsEMA m_ema1;
    private final BarsEMA m_ema2;
    private final BarsEMA m_ema3;
    private final BarsEMA m_ema4;
    private final BarsEMA m_ema5;
    private TickData m_tickData;

    public BarsQEMA(ITimesSeriesData<ITickData> tsd, float length, long barSize) {
        super();
        m_ema1 = new BarsEMA(tsd, length, barSize);
        m_ema2 = new BarsEMA(m_ema1, length, barSize);
        m_ema3 = new BarsEMA(m_ema2, length, barSize);
        m_ema4 = new BarsEMA(m_ema3, length, barSize);
        m_ema5 = new BarsEMA(m_ema4, length, barSize);
        setParent(m_ema5);
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
                        ITickData ema4Tick = m_ema3.getLatestTick();
                        if (ema4Tick != null) {
                            ITickData ema5Tick = m_ema3.getLatestTick();
                            if (ema5Tick != null) {
                                float ema1 = ema1Tick.getClosePrice();
                                float ema2 = ema2Tick.getClosePrice();
                                float ema3 = ema3Tick.getClosePrice();
                                float ema4 = ema4Tick.getClosePrice();
                                float ema5 = ema5Tick.getClosePrice();
                                float qema = 5 * ema1 - 10 * ema2 + 10 * ema3  - 5 * ema4 + ema5;

                                if ((m_tickData == null) || (m_tickData.getClosePrice() != qema)) {
                                    long timestamp = m_parent.getLatestTick().getTimestamp();
                                    m_tickData = new TickData(timestamp, qema);
                                    iAmChanged = true;
                                }
                            }
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
        return "QEMA[]";
    }
}
