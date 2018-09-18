package bi.two.exch.schedule;

import bi.two.chart.TickData;
import bi.two.util.MapConfig;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

// ticks following schedule
public class TradeSchedule {
    private final Schedule m_schedule;
    private TradeHours m_currentTradeHours;
    private long m_currentTimeJoin;

    public TradeSchedule(Schedule schedule) {
        m_schedule = schedule;
    }

    public void updateTickTimeToSchedule(TickData tickData) {
        long timestamp = tickData.getTimestamp();
        long timestampUpdated = updateTimestampToSchedule(timestamp);
        tickData.setTimestamp(timestampUpdated);
    }

    public long updateTimestampToSchedule(long timestamp) {
        if (m_currentTradeHours == null) {
            m_currentTradeHours = new TradeHours(m_schedule, timestamp);
        }
        boolean inside = m_currentTradeHours.isInsideOfTradingHours(timestamp);
        if (!inside) {
            TradeHours nextTradeHours = m_currentTradeHours.getNextTradeHours();
            inside = nextTradeHours.isInsideOfTradingHours(timestamp);
            if (!inside) {
                DateFormat dateTimeInstance = SimpleDateFormat.getDateTimeInstance(SimpleDateFormat.LONG, SimpleDateFormat.LONG);
                TimeZone timezone = m_schedule.getTimezone();
                dateTimeInstance.setTimeZone(timezone);
                String formatted = dateTimeInstance.format(new Date(timestamp));
                throw new RuntimeException("timestamp '" + formatted + "' is not inside of current and next trade day hours: current=" + m_currentTradeHours + "; next=" + nextTradeHours + "; schedule=" + m_schedule);
            }
            long nextTradeStartTime = nextTradeHours.m_tradeStartMillis;
            long currentTradeEndTime = m_currentTradeHours.m_tradeEndMillis;
            long join = nextTradeStartTime - currentTradeEndTime;
            m_currentTimeJoin += join;
            m_currentTradeHours = nextTradeHours;
        }
        return timestamp - m_currentTimeJoin;
    }

    public static TradeSchedule init(MapConfig config) {
        TradeSchedule tradeSchedule;
        String scheduleName = config.getProperty("schedule");
        if (scheduleName != null) {
            Schedule schedule = Schedule.valueOf(scheduleName);
            tradeSchedule = new TradeSchedule(schedule);
        } else {
            tradeSchedule = null;
        }
        return tradeSchedule;
    }
}
