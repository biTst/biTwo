package bi.two.ts;

import bi.two.chart.ITickData;
import bi.two.exch.schedule.Schedule;
import bi.two.exch.schedule.TradeHours;
import bi.two.exch.schedule.TradeSchedule;

public class ScheduleTimesSeriesData extends BaseTimesSeriesData<ITickData> {
    private final TradeSchedule m_tradeSchedule;
    private ITickData m_lastTick;
    private TradeHours m_currTradeHours;
    private long m_tradeEndMillis = 0;

    public ScheduleTimesSeriesData(ITimesSeriesData tsd, Schedule schedule) {
        super(tsd);
        m_tradeSchedule = new TradeSchedule(schedule);
    }

    @Override public ITickData getLatestTick() {
        return m_lastTick;
    }

    @Override public void onChanged(ITimesSeriesData<ITickData> ts, boolean changed) {
        if (changed) {
            ITickData latestTick = ts.getLatestTick();
            long timestamp = latestTick.getTimestamp();
            if (m_tradeEndMillis <= timestamp) {
                TradeHours nextTradeHours = m_tradeSchedule.getTradeHours(timestamp);
                boolean inside = nextTradeHours.isInsideOfTradingHours(timestamp);
                if (!inside) {
                    String dateTime = m_tradeSchedule.formatLongDateTime(timestamp);
                    throw new RuntimeException("next trade is not inside of trading day: dateTime=" + dateTime + "; nextTradeHours=" + nextTradeHours);
                }
                if (m_currTradeHours != null) {
                    long tradePause = nextTradeHours.m_tradeStartMillis - m_tradeEndMillis;
                    if (tradePause < 0) {
                        String dateTime = m_tradeSchedule.formatLongDateTime(timestamp);
                        throw new RuntimeException("negative tradePause=" + tradePause + "; dateTime=" + dateTime + "; currTradeHours=" + m_currTradeHours + "; nextTradeHours=" + nextTradeHours);
                    }
                    TradeHours nextDayTradeHours = m_currTradeHours.getNextDayTradeHours();
                    if (nextDayTradeHours == nextTradeHours) { // expected next trade day
                        onTimeShift(tradePause); // report valid shift if next trade day
                    }
                }
                m_currTradeHours = nextTradeHours;
                m_tradeEndMillis = nextTradeHours.m_tradeEndMillis;
            }
            m_lastTick = latestTick;
            super.onChanged(ts, changed);
        }
    }

    @Override public void onTimeShift(long shift) {
        notifyOnTimeShift(shift);
        // todo: remove override
//        super.onTimeShift(shift);
    }
}
