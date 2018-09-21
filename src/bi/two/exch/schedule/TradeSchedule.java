package bi.two.exch.schedule;

import bi.two.util.MapConfig;
import org.jetbrains.annotations.NotNull;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

// ticks following schedule
public class TradeSchedule {
    protected final Schedule m_schedule;
    private final Map<String, TradeHours> m_tradeHours = new HashMap<>();
    private final Map<Long, TradeHours> m_fastTradeHours = new HashMap<>();
//    private TradeHours m_currentTradeHours;
//    private long m_currentTimeJoin;
    private DateFormat m_yyyyMmDdFormatter;
    private DateFormat m_longDateTimeFormatter;

    public TradeSchedule(Schedule schedule) {
        m_schedule = schedule;
    }

//    public void updateTickTimeToSchedule(TickData tickData) {
//        // todo: remove
//        long timestamp = tickData.getTimestamp();
//        long timestampUpdated = updateTimestampToSchedule(timestamp);
//        tickData.setTimestamp(timestampUpdated);
//    }

//    public long updateTimestampToSchedule(long timestamp) {
//        // todo: remove
//        if (m_currentTradeHours == null) {
//            m_currentTradeHours = new TradeHours(this, timestamp);
//        }
//        boolean inside = m_currentTradeHours.isInsideOfTradingHours(timestamp);
//        if (!inside) {
//            TradeHours nextTradeHours = m_currentTradeHours.getNextDayTradeHours();
//            inside = nextTradeHours.isInsideOfTradingHours(timestamp);
//            if (!inside) {
//                DateFormat dateTimeInstance = SimpleDateFormat.getDateTimeInstance(SimpleDateFormat.LONG, SimpleDateFormat.LONG);
//                TimeZone timezone = m_schedule.getTimezone();
//                dateTimeInstance.setTimeZone(timezone);
//                String formatted = dateTimeInstance.format(new Date(timestamp));
//                throw new RuntimeException("timestamp '" + formatted + "' is not inside of current and next trade day hours: current=" + m_currentTradeHours + "; next=" + nextTradeHours + "; schedule=" + m_schedule);
//            }
//            long nextTradeStartTime = nextTradeHours.m_tradeStartMillis;
//            long currentTradeEndTime = m_currentTradeHours.m_tradeEndMillis;
//            long join = nextTradeStartTime - currentTradeEndTime;
//            m_currentTimeJoin += join;
//            m_currentTradeHours = nextTradeHours;
//        }
//        return timestamp - m_currentTimeJoin;
//    }

    public static TradeSchedule obtain(MapConfig config) {
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

    @NotNull public TradeHours getTradeHours(long timestamp) {
        TradeHours tradeHours = m_fastTradeHours.get(timestamp);
        if (tradeHours == null) {
            String dateStr = formatYyyyMmDd(timestamp);
            tradeHours = m_tradeHours.get(dateStr);
            if (tradeHours == null) {
                tradeHours = new TradeHours(this, timestamp);
                m_tradeHours.put(dateStr, tradeHours);
            }
            m_fastTradeHours.put(timestamp, tradeHours);
        }
        return tradeHours;
    }

    public String formatYyyyMmDd(long timestamp) {
        return formatMillis(timestamp, getYyyyMmDdFormatter());
    }

    public String formatYyyyMmDd(Date date) {
        return formatDate(date, getYyyyMmDdFormatter());
    }

    public String formatLongDateTime(long timestamp) {
        return formatMillis(timestamp, getLongDateTimeFormatter());
    }

    public String formatLongDateTime(Date date) {
        return formatDate(date, getLongDateTimeFormatter());
    }

    @NotNull private String formatMillis(long timestamp, DateFormat dateFormat) {
        Date date = new Date(timestamp);
        return formatDate(date, dateFormat);
    }

    private String formatDate(Date date, DateFormat dateFormat) {
        synchronized (dateFormat) {
            return dateFormat.format(date);
        }
    }

    @NotNull private synchronized DateFormat getYyyyMmDdFormatter() {
        if (m_yyyyMmDdFormatter == null) {
            m_yyyyMmDdFormatter = new SimpleDateFormat("yyyyMMdd");
            TimeZone timezone = m_schedule.getTimezone();
            m_yyyyMmDdFormatter.setTimeZone(timezone);
        }
        return m_yyyyMmDdFormatter;
    }

    @NotNull private synchronized DateFormat getLongDateTimeFormatter() {
        if (m_longDateTimeFormatter == null) {
            m_longDateTimeFormatter = SimpleDateFormat.getDateTimeInstance(SimpleDateFormat.LONG, SimpleDateFormat.LONG);
            TimeZone timezone = m_schedule.getTimezone();
            m_longDateTimeFormatter.setTimeZone(timezone);
        }
        return m_longDateTimeFormatter;
    }
}
