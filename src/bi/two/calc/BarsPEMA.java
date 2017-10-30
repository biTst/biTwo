package bi.two.calc;

import bi.two.chart.ITickData;
import bi.two.chart.TickData;
import bi.two.ts.BaseTimesSeriesData;
import bi.two.ts.ITimesSeriesData;

//----------------------------------------------------------
// PEMA: Pentuple exponential moving average
//  PEMA = 8*MA1 - 28*MA2 + 56*MA3 - 70*MA4 + 56*MA5 - 28*MA6 + 8*MA7 - MA8;
// MA1=Moving Average(Price),
// MA2=Moving Average(MA1),
// MA3=Moving Average(MA2),
// MA4=Moving Average(MA3),
// MA5=Moving Average(MA4).
// MA6=Moving Average(MA5).
// MA7=Moving Average(MA6).
// MA8=Moving Average(MA7).
public class BarsPEMA extends BaseTimesSeriesData<ITickData> {
    private final BarsEMA m_ema1;
    private final BarsEMA m_ema2;
    private final BarsEMA m_ema3;
    private final BarsEMA m_ema4;
    private final BarsEMA m_ema5;
    private final BarsEMA m_ema6;
    private final BarsEMA m_ema7;
    private final BarsEMA m_ema8;
    private TickData m_tickData;

    public BarsPEMA(ITimesSeriesData<ITickData> tsd, float length, long barSize) {
        super();
        m_ema1 = new BarsEMA(tsd, length, barSize);
        m_ema2 = new BarsEMA(m_ema1, length, barSize);
        m_ema3 = new BarsEMA(m_ema2, length, barSize);
        m_ema4 = new BarsEMA(m_ema3, length, barSize);
        m_ema5 = new BarsEMA(m_ema4, length, barSize);
        m_ema6 = new BarsEMA(m_ema5, length, barSize);
        m_ema7 = new BarsEMA(m_ema6, length, barSize);
        m_ema8 = new BarsEMA(m_ema7, length, barSize);
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
                        ITickData ema4Tick = m_ema3.getLatestTick();
                        if (ema4Tick != null) {
                            ITickData ema5Tick = m_ema3.getLatestTick();
                            if (ema5Tick != null) {
                                ITickData ema6Tick = m_ema3.getLatestTick();
                                if (ema6Tick != null) {
                                    ITickData ema7Tick = m_ema3.getLatestTick();
                                    if (ema7Tick != null) {
                                        ITickData ema8Tick = m_ema3.getLatestTick();
                                        if (ema8Tick != null) {
                                            float ema1 = ema1Tick.getClosePrice();
                                            float ema2 = ema2Tick.getClosePrice();
                                            float ema3 = ema3Tick.getClosePrice();
                                            float ema4 = ema4Tick.getClosePrice();
                                            float ema5 = ema5Tick.getClosePrice();
                                            float ema6 = ema6Tick.getClosePrice();
                                            float ema7 = ema7Tick.getClosePrice();
                                            float ema8 = ema8Tick.getClosePrice();
                                            float pema = 8 * ema1 - 28 * ema2 + 56 * ema3 - 70 * ema4 + 56 * ema5 - 28 * ema6 + 8 * ema7 - ema8;

                                            if ((m_tickData == null) || (m_tickData.getClosePrice() != pema)) {
                                                long timestamp = m_parent.getLatestTick().getTimestamp();
                                                m_tickData = new TickData(timestamp, pema);
                                                iAmChanged = true;
                                            }
                                        }
                                    }
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
        return "PEMA[]";
    }
}
