package bi.two.exch.schedule;

import java.util.*;

import static bi.two.util.Log.console;

// exch working schedule
public enum Schedule {
    crypto(),
    forex() {
        @Override public long getNextTradeCloseTime(long tickTime) {
            Calendar calendar = new GregorianCalendar(TimeZone.getTimeZone("GMT"), Locale.getDefault());
            calendar.setTimeInMillis(tickTime);
            Date initTime = calendar.getTime();

            calendar.set(Calendar.DAY_OF_WEEK, Calendar.FRIDAY);
            calendar.set(Calendar.HOUR_OF_DAY, 17);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            Date boundedTime = calendar.getTime();

            long shiftedMillis = calendar.getTimeInMillis();
            if (shiftedMillis < tickTime) {
                calendar.add(Calendar.DAY_OF_YEAR, 7); // add week
                Date shiftedTime = calendar.getTime();
                shiftedMillis = shiftedTime.getTime();
                if (shiftedMillis < tickTime) {
                    console("one shift is not enough: init: " + initTime.toGMTString() + "; bounded=" + boundedTime.toGMTString() + "; shifted=" + shiftedTime.toGMTString());
                }
            }
            return calendar.getTimeInMillis();
        }
    },
    us_stocks(),

    // Derivatives market is open from 10:00am until 11:50pm MSK
    // The evening trading session is held from 7:00 pm – 11:50 pm MSK
    moex(10, 0, 23, 50, TimeZone.getTimeZone("GMT+3"), Holidays.ru)
    ;

    private final TimeZone m_timezone;
    private int m_fromHours;
    private int m_fromMinutes;
    private int m_toHours;
    private int m_toMinutes;
    private Holidays m_holidays;

    Schedule() {
        this(-1, -1, -1, -1, TimeZone.getTimeZone("GMT"), null); // GMT by def
    }

    Schedule(int fromHours, int fromMinutes, int toHours, int toMinutes, TimeZone timezone, Holidays holidays) {
        m_fromHours = fromHours;
        m_fromMinutes = fromMinutes;
        m_toHours = toHours;
        m_toMinutes = toMinutes;
        m_timezone = timezone;
        m_holidays = holidays;
    }

    @Override public String toString() {
        return "Schedule[" +
                "from " + m_fromHours +
                ":" + m_fromMinutes +
                " to " + m_toHours +
                ":" + m_toMinutes +
                " " + m_timezone.getDisplayName() +
                ", holidays=" + m_holidays.name() +
                ']';
    }

    public long getNextTradeCloseTime(long tickTime) { return 0; }

    public TimeZone getTimezone() { return m_timezone; }
    public int getFromHours() { return m_fromHours; }
    public int getFromMinutes() { return m_fromMinutes; }
    public int getToHours() { return m_toHours; }
    public int getToMinutes() { return m_toMinutes; }
    public Holidays getHolidays() { return m_holidays; }
}
