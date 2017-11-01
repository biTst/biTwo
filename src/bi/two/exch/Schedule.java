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
System.out.println("calendar=" + initTime.toGMTString());

            calendar.set(Calendar.DAY_OF_WEEK, Calendar.FRIDAY);
            calendar.set(Calendar.HOUR_OF_DAY, 17);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            Date boundedTime = calendar.getTime();
System.out.println(" bounded=" + boundedTime.toGMTString());

            long shiftedMillis = calendar.getTimeInMillis();
            if (shiftedMillis < tickTime) {
                calendar.add(Calendar.DAY_OF_YEAR, 7); // add week
                Date shiftedTime = calendar.getTime();
                shiftedMillis = shiftedTime.getTime();
                if (shiftedMillis < tickTime) {
                    System.out.println("one shift is not enough: init: " + initTime.toGMTString() + "; bounded=" + boundedTime.toGMTString() + "; shifted=" + shiftedTime.toGMTString());
                }
            }

//            int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
//            boolean beforeThisWeekTradeClose = (dayOfWeek >= Calendar.MONDAY) && (dayOfWeek <= Calendar.FRIDAY);
//            if (beforeThisWeekTradeClose) {
//                calendar.set(Calendar.DAY_OF_WEEK, Calendar.SATURDAY);
//            } else {
//                calendar.add(Calendar.DAY_OF_YEAR, (dayOfWeek == Calendar.SATURDAY) ? 7 : 6); // to next week SATURDAY
//            }
System.out.println(" NextTradeCloseTime=" + calendar.getTime().toGMTString());

            return calendar.getTime();
        }
    },
    us_stocks
    ;

    public Date getNextTradeCloseTime(long tickTime) {
        return null;
    }
}
