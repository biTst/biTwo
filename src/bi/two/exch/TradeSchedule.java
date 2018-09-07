package bi.two.exch;

import bi.two.chart.TickData;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

public class TradeSchedule {
    private static long s_lastTime;
    private static TradeHours s_currentTradeHours;
    private static long s_currentTimeJoin;

    public static void updateTime(TickData tickData) {
        long timestamp = tickData.getTimestamp();
        if (s_currentTradeHours == null) {
            s_currentTradeHours = new TradeHours(timestamp);
        }
        boolean inside = s_currentTradeHours.inside(timestamp);
        if (inside) {
            if (s_currentTimeJoin > 0) {
                timestamp = timestamp - s_currentTimeJoin;
                tickData.setTimestamp(timestamp);
            }
        } else {
            TradeHours nextTradeHours = s_currentTradeHours.getNextTradeHours();
        }


        if(s_lastTime != 0) {
            if(s_lastTime != timestamp) {

            }
        }
        s_lastTime = timestamp;
    }

    private static void initTradeHours(long timestamp) {
        new TradeHours(timestamp);
    }

    public static class TradeHours {
        private final GregorianCalendar m_calendar;
        private final long m_dayStartMillis;
        private final long m_tradeStartMillis;
        private final long m_tradeEndMillis;
        int m_from;
        int m_to;
        TimeZone m_timezone;


        public TradeHours(long timestamp) {
            m_from = 10;
            m_to = 24;
            m_timezone = TimeZone.getTimeZone("GMT+3");

            m_calendar = new GregorianCalendar(m_timezone, Locale.getDefault());
            m_calendar.setTimeInMillis(timestamp);
            Date initTime = m_calendar.getTime();

System.out.println("TradeHours<> timestamp=" + timestamp + "; initTime=" + initTime.toGMTString());

            DateFormat dateTimeInstance = SimpleDateFormat.getDateTimeInstance(SimpleDateFormat.LONG, SimpleDateFormat.LONG);
            dateTimeInstance.setTimeZone(m_timezone);
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

            m_calendar.set(Calendar.HOUR_OF_DAY, m_from);
            Date tradeStartTime = m_calendar.getTime();
            String tradeStartFormat = dateTimeInstance.format(tradeStartTime);
System.out.println("tradeStartFormat=" + tradeStartFormat);
            m_tradeStartMillis = m_calendar.getTimeInMillis();

            m_calendar.set(Calendar.HOUR_OF_DAY, m_to);
            Date tradeEndTime = m_calendar.getTime();
            String tradeEndFormat = dateTimeInstance.format(tradeEndTime);
System.out.println("tradeEndFormat=" + tradeEndFormat);
            m_tradeEndMillis = m_calendar.getTimeInMillis();
        }

        public boolean inside(long timestamp) {
            return (m_tradeStartMillis <= timestamp) && (timestamp < m_tradeEndMillis);
        }

        public TradeHours getNextTradeHours() {
            m_calendar.setTimeInMillis(m_tradeStartMillis);

            return null;
        }
    }
}
