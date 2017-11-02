package bi.two.exch;

import java.util.*;

// exch working schedule
public enum Schedule {
    crypto,
    forex {
        @Override public Date getNextTradeCloseTime(long tickTime) {
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
                    System.out.println("one shift is not enough: init: " + initTime.toGMTString() + "; bounded=" + boundedTime.toGMTString() + "; shifted=" + shiftedTime.toGMTString());
                }
            }
            return calendar.getTime();
        }
    },
    us_stocks
    ;

    public Date getNextTradeCloseTime(long tickTime) {
        return null;
    }
}
