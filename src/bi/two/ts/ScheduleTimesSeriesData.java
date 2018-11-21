package bi.two.ts;

import bi.two.chart.BaseTickData;
import bi.two.chart.ITickData;
import bi.two.exch.schedule.Schedule;
import bi.two.exch.schedule.TradeHours;
import bi.two.exch.schedule.TradeSchedule;
import bi.two.util.Log;
import bi.two.util.Utils;

public class ScheduleTimesSeriesData extends BaseTimesSeriesData<ITickData> {
    private static final boolean MONOTONE_TIME_INCREASE_CHECK = STRICT_MONOTONE_TIME_INCREASE_CHECK;

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
            boolean afterTradeEnd = (m_tradeEndMillis < timestamp);
            if (afterTradeEnd) {
                TradeHours timestampTradeHours = m_tradeSchedule.getTradeHours(timestamp);
                boolean inside = timestampTradeHours.isInsideOfTradingHours(timestamp);
                if (!inside) {
                    String dateTime = m_tradeSchedule.formatLongDateTime(timestamp);
                    throw new RuntimeException("next trade is not inside of trading day: dateTime=" + dateTime + "; timestampTradeHours=" + timestampTradeHours);
                }
                if (m_currTradeHours != null) {
                    long tradePause = timestampTradeHours.m_tradeStartMillis - m_tradeEndMillis;
                    if (tradePause < 0) {
                        String dateTime = m_tradeSchedule.formatLongDateTime(timestamp);
                        throw new RuntimeException("negative tradePause=" + tradePause + "("+ Utils.millisToYDHMSStr(tradePause)+"); dateTime=" + dateTime + "; currTradeHours=" + m_currTradeHours + "; timestampTradeHours=" + timestampTradeHours);
                    }
                    TradeHours nextDayTradeHours = m_currTradeHours.getNextDayTradeHours();
                    if (nextDayTradeHours == timestampTradeHours) { // expected next trade day
                        onTimeShift(tradePause); // report valid shift if next trade day
                    } else {
                        Log.console("currTradeHours=" + m_currTradeHours);
                        Log.console("currTradeHours.getNextDayTradeHours=" + nextDayTradeHours);
                        Log.console("timestamp=" + m_tradeSchedule.formatLongDateTime(timestamp));
                        Log.console("timestampTradeHours=" + timestampTradeHours);
                        throw new RuntimeException("not expected next trade day");
                    }
                }
                m_currTradeHours = timestampTradeHours;
                m_tradeEndMillis = timestampTradeHours.m_tradeEndMillis;
            }
            if (MONOTONE_TIME_INCREASE_CHECK) {
                boolean inside = m_currTradeHours.isInsideOfTradingHours(timestamp);
                if (!inside) {
                    throw new RuntimeException("timestamp is not inside of trading day: timestamp=" + timestamp + "; currTradeHours=" + m_currTradeHours);
                }
            }
            m_lastTick = latestTick;
            super.onChanged(ts, changed);
        }
    }

    @Override public void onTimeShift(long shift) {
        if (m_lastTick != null) {
            m_lastTick = new BaseTickData(m_lastTick.getTimestamp() + shift, m_lastTick.getClosePrice());
        }
        notifyOnTimeShift(shift);
        // todo: remove override
//        super.onTimeShift(shift);
    }
}
