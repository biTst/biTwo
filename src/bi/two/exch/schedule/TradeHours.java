package bi.two.exch.schedule;

import org.jetbrains.annotations.NotNull;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

import static bi.two.util.Log.log;

// precalculated trading hours for particular day
public class TradeHours {
    private final TradeSchedule m_tradeSchedule;

    private final GregorianCalendar m_calendar;
    private final long m_dayStartMillis;
    public final long m_tradeStartMillis;
    public final long m_tradeEndMillis;
//    private final String m_dayStartFormatted;
    private final String m_tradeStartFormatted;
    private final String m_tradeEndFormatted;

    @Override public String toString() {
        return "TradeHours[" +
                "from " + m_tradeStartFormatted +
                " to " + m_tradeEndFormatted +
                ']';
    }

    TradeHours(TradeSchedule tradeSchedule, long timestamp) {
        m_tradeSchedule = tradeSchedule;

        Schedule schedule = tradeSchedule.m_schedule;
        TimeZone timezone = schedule.getTimezone();
        m_calendar = new GregorianCalendar(timezone, Locale.getDefault());
        m_calendar.setTimeInMillis(timestamp);

//        Date initTime = m_calendar.getTime();
//System.out.println("TradeHours<> timestamp=" + timestamp + "; initTime=" + initTime.toGMTString());

//        String format = m_longDateTimeFormatter.format(initTime);
//System.out.println("format=" + format);

        m_calendar.set(Calendar.HOUR_OF_DAY, 0);
        m_calendar.set(Calendar.MINUTE, 0);
        m_calendar.set(Calendar.SECOND, 0);
        m_calendar.set(Calendar.MILLISECOND, 0);
//        Date dayStartTime = m_calendar.getTime();
//        m_dayStartFormatted = m_longDateTimeFormatter.format(dayStartTime);
//System.out.println("dayStartFormatted=" + m_dayStartFormatted);
        m_dayStartMillis = m_calendar.getTimeInMillis();

        m_calendar.set(Calendar.HOUR_OF_DAY, schedule.getFromHours());
        m_calendar.set(Calendar.MINUTE, schedule.getFromMinutes());
        Date tradeStartTime = m_calendar.getTime();
        m_tradeStartFormatted = tradeSchedule.formatLongDateTime(tradeStartTime);
//System.out.println("tradeStartFormatted=" + m_tradeStartFormatted);
        m_tradeStartMillis = m_calendar.getTimeInMillis();

        m_calendar.set(Calendar.HOUR_OF_DAY, schedule.getToHours());
        m_calendar.set(Calendar.MINUTE, schedule.getToMinutes());
        Date tradeEndTime = m_calendar.getTime();
        m_tradeEndFormatted = tradeSchedule.formatLongDateTime(tradeEndTime);
//System.out.println("tradeEndFormatted=" + m_tradeEndFormatted);
        m_tradeEndMillis = m_calendar.getTimeInMillis();
    }

    public boolean isInsideOfTradingHours(long timestamp) {
        return (m_tradeStartMillis <= timestamp) && (timestamp < m_tradeEndMillis);
    }

    @NotNull public TradeHours getNextDayTradeHours() {
        m_calendar.setTimeInMillis(m_dayStartMillis);
        shiftCalendarToNextDay();
        return m_tradeSchedule.getTradeHours(m_calendar.getTimeInMillis());
    }

    private void shiftCalendarToNextDay() {
        m_calendar.add(Calendar.DAY_OF_YEAR, 1);

        int dayOfWeek = m_calendar.get(Calendar.DAY_OF_WEEK);
        if ((dayOfWeek == Calendar.SATURDAY) || (dayOfWeek == Calendar.SUNDAY)) {
            shiftCalendarToNextDay();
        } else {
            Schedule schedule = m_tradeSchedule.m_schedule;
            Holidays holidays = schedule.getHolidays();
            if (holidays != null) {
                DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
                TimeZone timezone = schedule.getTimezone();
                dateFormat.setTimeZone(timezone);
                Date time = m_calendar.getTime();
                String dateFormatted = dateFormat.format(time);
                boolean holiday = holidays.isHoliday(dateFormatted);
                if (holiday) {
                    log("skip holiday=" + dateFormatted);
                    shiftCalendarToNextDay();
                }
            }
        }
    }
}
