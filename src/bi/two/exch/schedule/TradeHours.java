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

    @Override public String toString() {
        return "TradeHours{" +
                "from " + m_dayStartMillis +
                " to " + m_tradeStartMillis +
                " schedule=" + m_schedule +
                '}';
    }

    TradeHours(Schedule schedule, long timestamp) {
        m_schedule = schedule;

        TimeZone timezone = schedule.getTimezone();
        m_calendar = new GregorianCalendar(timezone, Locale.getDefault());
        m_calendar.setTimeInMillis(timestamp);
        Date initTime = m_calendar.getTime();

System.out.println("TradeHours<> timestamp=" + timestamp + "; initTime=" + initTime.toGMTString());

        DateFormat dateTimeInstance = SimpleDateFormat.getDateTimeInstance(SimpleDateFormat.LONG, SimpleDateFormat.LONG);
        dateTimeInstance.setTimeZone(timezone);
        String format = dateTimeInstance.format(initTime);
System.out.println("format=" + format);

        int hour = m_calendar.get(Calendar.HOUR_OF_DAY);
System.out.println("hour=" + hour);

        m_calendar.set(Calendar.HOUR_OF_DAY, 0);
        m_calendar.set(Calendar.MINUTE, 0);
        m_calendar.set(Calendar.SECOND, 0);
        m_calendar.set(Calendar.MILLISECOND, 0);
        Date dayStartTime = m_calendar.getTime();
        String dayStartFormat = dateTimeInstance.format(dayStartTime);
System.out.println("dayStartFormat=" + dayStartFormat);
        m_dayStartMillis = m_calendar.getTimeInMillis();

        m_calendar.set(Calendar.HOUR_OF_DAY, schedule.getFromHours());
        m_calendar.set(Calendar.MINUTE, schedule.getFromMinutes());
        Date tradeStartTime = m_calendar.getTime();
        String tradeStartFormat = dateTimeInstance.format(tradeStartTime);
System.out.println("tradeStartFormat=" + tradeStartFormat);
        m_tradeStartMillis = m_calendar.getTimeInMillis();

        m_calendar.set(Calendar.HOUR_OF_DAY, schedule.getToHours());
        m_calendar.set(Calendar.MINUTE, schedule.getToMinutes());
        Date tradeEndTime = m_calendar.getTime();
        String tradeEndFormat = dateTimeInstance.format(tradeEndTime);
System.out.println("tradeEndFormat=" + tradeEndFormat);
        m_tradeEndMillis = m_calendar.getTimeInMillis();
    }

    public boolean inside(long timestamp) {
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
                DateFormat dateFormat = SimpleDateFormat.getDateInstance(SimpleDateFormat.MEDIUM);
                TimeZone timezone = m_schedule.getTimezone();
                dateFormat.setTimeZone(timezone);
                String format = dateFormat.format(m_calendar.getTime());
System.out.println("dateFormat=" + format);
                boolean holiday = holidays.isHoliday(format);
                if(holiday) {
                    shiftCalendarToNextDay();
                }
            }
        }
    }
}
