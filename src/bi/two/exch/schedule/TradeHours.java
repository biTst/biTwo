package bi.two.exch.schedule;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

//--------------------------------------------------------------------------------------
public class TradeHours {
    private final Schedule m_schedule;

    private final GregorianCalendar m_calendar;
    private final long m_dayStartMillis;
    final long m_tradeStartMillis;
    final long m_tradeEndMillis;
//    private final String m_dayStartFormatted;
    private final String m_tradeStartFormatted;
    private final String m_tradeEndFormatted;

    @Override public String toString() {
        return "TradeHours[" +
                "from " + m_tradeStartFormatted +
                " to " + m_tradeEndFormatted +
                ']';
    }

    TradeHours(Schedule schedule, long timestamp) {
        m_schedule = schedule;

        TimeZone timezone = schedule.getTimezone();
        m_calendar = new GregorianCalendar(timezone, Locale.getDefault());
        m_calendar.setTimeInMillis(timestamp);

//        Date initTime = m_calendar.getTime();
//System.out.println("TradeHours<> timestamp=" + timestamp + "; initTime=" + initTime.toGMTString());

        DateFormat dateTimeInstance = SimpleDateFormat.getDateTimeInstance(SimpleDateFormat.LONG, SimpleDateFormat.LONG);
        dateTimeInstance.setTimeZone(timezone);
//        String format = dateTimeInstance.format(initTime);
//System.out.println("format=" + format);

        m_calendar.set(Calendar.HOUR_OF_DAY, 0);
        m_calendar.set(Calendar.MINUTE, 0);
        m_calendar.set(Calendar.SECOND, 0);
        m_calendar.set(Calendar.MILLISECOND, 0);
//        Date dayStartTime = m_calendar.getTime();
//        m_dayStartFormatted = dateTimeInstance.format(dayStartTime);
//System.out.println("dayStartFormatted=" + m_dayStartFormatted);
        m_dayStartMillis = m_calendar.getTimeInMillis();

        m_calendar.set(Calendar.HOUR_OF_DAY, schedule.getFromHours());
        m_calendar.set(Calendar.MINUTE, schedule.getFromMinutes());
        Date tradeStartTime = m_calendar.getTime();
        m_tradeStartFormatted = dateTimeInstance.format(tradeStartTime);
//System.out.println("tradeStartFormatted=" + m_tradeStartFormatted);
        m_tradeStartMillis = m_calendar.getTimeInMillis();

        m_calendar.set(Calendar.HOUR_OF_DAY, schedule.getToHours());
        m_calendar.set(Calendar.MINUTE, schedule.getToMinutes());
        Date tradeEndTime = m_calendar.getTime();
        m_tradeEndFormatted = dateTimeInstance.format(tradeEndTime);
//System.out.println("tradeEndFormatted=" + m_tradeEndFormatted);
        m_tradeEndMillis = m_calendar.getTimeInMillis();
    }

    public boolean isInsideOfTradingHours(long timestamp) {
        return (m_tradeStartMillis <= timestamp) && (timestamp < m_tradeEndMillis);
    }

    TradeHours getNextTradeHours() {
        m_calendar.setTimeInMillis(m_dayStartMillis);
        shiftCalendarToNextDay();
        return new TradeHours(m_schedule, m_calendar.getTimeInMillis());
    }

    private void shiftCalendarToNextDay() {
        m_calendar.add(Calendar.DAY_OF_YEAR, 1);

        int dayOfWeek = m_calendar.get(Calendar.DAY_OF_WEEK);
        if (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY) {
            shiftCalendarToNextDay();
        } else {
            Holidays holidays = m_schedule.getHolidays();
            if (holidays != null) {
                DateFormat dateFormat = new SimpleDateFormat("ddMMyyyy");
                TimeZone timezone = m_schedule.getTimezone();
                dateFormat.setTimeZone(timezone);
                Date time = m_calendar.getTime();
                String dateFormatted = dateFormat.format(time);
//System.out.println("dateFormatted=" + dateFormatted);
                boolean holiday = holidays.isHoliday(dateFormatted);
                if (holiday) {
System.out.println("skip holiday=" + dateFormatted);
                    shiftCalendarToNextDay();
                }
            }
        }
    }
}
