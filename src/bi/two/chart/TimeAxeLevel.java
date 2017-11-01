package bi.two.chart;

import bi.two.util.Utils;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

public enum TimeAxeLevel {
    _YEAR(Utils.YEAR_IN_MILLIS, Calendar.YEAR, 1, RoundDown.TO_YEAR, Format.YEAR),
    _6_MONTH(6 * Utils.MONTH_IN_MILLIS, Calendar.MONTH, 6, RoundDown.TO_MONTH, Format.MONTH),
    _3_MONTH(3 * Utils.MONTH_IN_MILLIS, Calendar.MONTH, 3, RoundDown.TO_MONTH, Format.MONTH),
    _1_MONTH(Utils.MONTH_IN_MILLIS, Calendar.MONTH, 1, RoundDown.TO_MONTH, Format.MONTH),
    _14_DAYS(14 * Utils.DAY_IN_MILLIS, Calendar.DAY_OF_MONTH, 14, RoundDown.TO_DAY, Format.DAY),
    _7_DAYS(7 * Utils.DAY_IN_MILLIS, Calendar.DAY_OF_MONTH, 7, RoundDown.TO_DAY, Format.DAY),
    _1_DAY(Utils.DAY_IN_MILLIS, Calendar.DAY_OF_MONTH, 1, RoundDown.TO_DAY, Format.DAY),
    _12_HOURS(12 * Utils.HOUR_IN_MILLIS, Calendar.HOUR_OF_DAY, 12, RoundDown.TO_HOUR, Format.HOUR),
    _6_HOURS(6 * Utils.HOUR_IN_MILLIS, Calendar.HOUR_OF_DAY, 6, RoundDown.TO_HOUR, Format.HOUR),
    _3_HOURS(3 * Utils.HOUR_IN_MILLIS, Calendar.HOUR_OF_DAY, 3, RoundDown.TO_HOUR, Format.HOUR),
    _1_HOUR(Utils.HOUR_IN_MILLIS, Calendar.HOUR_OF_DAY, 1, RoundDown.TO_HOUR, Format.HOUR),
    _30_MIN(30 * Utils.MIN_IN_MILLIS, Calendar.MINUTE, 30, RoundDown.TO_MINUTE, Format.MINUTE),
    _15_MIN(15 * Utils.MIN_IN_MILLIS, Calendar.MINUTE, 15, RoundDown.TO_MINUTE, Format.MINUTE),
    _5_MIN(5 * Utils.MIN_IN_MILLIS, Calendar.MINUTE, 5, RoundDown.TO_MINUTE, Format.MINUTE),
    _1_MIN(Utils.MIN_IN_MILLIS, Calendar.MINUTE, 1, RoundDown.TO_MINUTE, Format.MINUTE),
    ;

    private static final Calendar SHARED_CALENDAR = new GregorianCalendar(TimeZone.getTimeZone("GMT"), Locale.getDefault());

    private final long m_period;
    private final int m_field;
    private final int m_size;
    private final RoundDown m_roundDown;
    private final Format m_format;

    TimeAxeLevel(long period, int field, int size, RoundDown roundDown, Format format) {
        m_period = period;
        m_field = field;
        m_size = size;
        m_roundDown = roundDown;
        m_format = format;
    }

    public long getPeriod() { return m_period; }

    public long roundUp(long time) {
        return roundDownToField(time, true);
    }

    public long roundDown(long time) {
        return roundDownToField(time, false);
    }

    private static long roundDownToStartOfYear(long time, int add) {
        SHARED_CALENDAR.setTimeInMillis(time);
        if (add > 0) {
            SHARED_CALENDAR.add(Calendar.YEAR, add);
        }
        roundDownToStartOfYear();
        return SHARED_CALENDAR.getTimeInMillis();
    }

    private static long roundDownToMonth(long time, int round, int add) {
        SHARED_CALENDAR.setTimeInMillis(time);
        if (round > 1) {
            int month = SHARED_CALENDAR.get(Calendar.MONTH);
            month = (month / round) * round;
            SHARED_CALENDAR.set(Calendar.MONTH, month);
        }
        if (add > 0) {
            SHARED_CALENDAR.add(Calendar.MONTH, add);
        }
        roundDownToStartOfMonth();
        return SHARED_CALENDAR.getTimeInMillis();
    }

    private static long roundDownToDay(long time, int round, int add) {
        SHARED_CALENDAR.setTimeInMillis(time);
        if (round > 1) {
            int day = SHARED_CALENDAR.get(Calendar.DAY_OF_MONTH);
            day = (day / round) * round;
            SHARED_CALENDAR.set(Calendar.DAY_OF_MONTH, day);
        }
        if (add > 0) {
            SHARED_CALENDAR.add(Calendar.DAY_OF_MONTH, add);
        }
        roundDownToStartOfDay();
        return SHARED_CALENDAR.getTimeInMillis();
    }

    private static long roundDownToHour(long time, int round, int add) {
        SHARED_CALENDAR.setTimeInMillis(time);
        if (round > 1) {
            int hour = SHARED_CALENDAR.get(Calendar.HOUR_OF_DAY);
            hour = (hour / round) * round;
            SHARED_CALENDAR.set(Calendar.HOUR_OF_DAY, hour);
        }
        if (add > 0) {
            SHARED_CALENDAR.add(Calendar.HOUR_OF_DAY, add);
        }
        roundDownToStartOfHour();
        return SHARED_CALENDAR.getTimeInMillis();
    }

    private static long roundDownToMinute(long time, int round, int add) {
        SHARED_CALENDAR.setTimeInMillis(time);
        if (round > 1) {
            int minute = SHARED_CALENDAR.get(Calendar.MINUTE);
            minute = (minute / round) * round;
            SHARED_CALENDAR.set(Calendar.MINUTE, minute);
        }
        if (add > 0) {
            SHARED_CALENDAR.add(Calendar.MINUTE, add);
        }
        roundDownToStartOfMinute();
        return SHARED_CALENDAR.getTimeInMillis();
    }

    private long roundDownToField(long millis, boolean add) {
        SHARED_CALENDAR.setTimeInMillis(millis);
        if (m_size > 1) {
            int field = SHARED_CALENDAR.get(m_field);
            int round = (field / m_size) * m_size;
            if (add) {
                round += m_size;
            }
            if (field != round) {
                SHARED_CALENDAR.set(m_field, round);
            }
        } else {
            if (add) {
                SHARED_CALENDAR.add(m_field, m_size);
            }
        }
        m_roundDown.roundDown();
        return SHARED_CALENDAR.getTimeInMillis();
    }

    private static void roundDownToStartOfYear() {
        SHARED_CALENDAR.set(Calendar.MONTH, 0);
        roundDownToStartOfMonth();
    }

    private static void roundDownToStartOfMonth() {
        SHARED_CALENDAR.set(Calendar.DAY_OF_MONTH, 0);
        roundDownToStartOfDay();
    }

    private static void roundDownToStartOfDay() {
        SHARED_CALENDAR.set(Calendar.HOUR_OF_DAY, 0);
        roundDownToStartOfHour();
    }

    private static void roundDownToStartOfHour() {
        SHARED_CALENDAR.set(Calendar.MINUTE, 0);
        roundDownToStartOfMinute();
    }

    private static void roundDownToStartOfMinute() {
        SHARED_CALENDAR.set(Calendar.SECOND, 0);
        SHARED_CALENDAR.set(Calendar.MILLISECOND, 0);
    }

    public long add(long millis) {
        SHARED_CALENDAR.setTimeInMillis(millis);
        SHARED_CALENDAR.add(m_field, m_size);
        return SHARED_CALENDAR.getTimeInMillis();
    }

    public String format(long time) {
        return m_format.format(time);
    }

    //------------------------------------------------
    private enum Format {
        YEAR("yyyy"),
        MONTH("MMM"),
        DAY("dMMM"),
        HOUR("HH:mm"),
        MINUTE("mm");

        private final SimpleDateFormat m_format;

        Format(String pattern) {
            m_format = new SimpleDateFormat(pattern);
            m_format.setTimeZone(TimeZone.getTimeZone("GMT"));
        }

        public String format(long time) {
            return m_format.format(time);
        }
    }

    //------------------------------------------------
    private enum RoundDown {
        TO_YEAR {
            public void roundDown() {
                roundDownToStartOfYear();
            }
        },
        TO_MONTH {
            public void roundDown() {
                roundDownToStartOfMonth();
            }
        },
        TO_DAY {
            public void roundDown() {
                roundDownToStartOfDay();
            }
        },
        TO_HOUR {
            public void roundDown() {
                roundDownToStartOfHour();
            }
        },
        TO_MINUTE {
            public void roundDown() {
                roundDownToStartOfMinute();
            }
        };

        public void roundDown() {
            throw new RuntimeException("must be overridden");
        }
    }
}
