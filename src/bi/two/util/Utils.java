package bi.two.util;

import java.text.DecimalFormat;

public class Utils {
    public static final long MIN_IN_MILLIS = 60 * 1000L;
    public static final long HOUR_IN_MILLIS = 60 * MIN_IN_MILLIS;
    public static final long DAY_IN_MILLIS = 24 * HOUR_IN_MILLIS;
    public static final long MONTH_IN_MILLIS = 30 * DAY_IN_MILLIS;
    public static final long YEAR_IN_MILLIS = 365 * DAY_IN_MILLIS;

    public static final float INVALID_PRICE = Float.MAX_VALUE;

    public static final DecimalFormat X_YYYYY = new DecimalFormat("0.00000");
    public static final DecimalFormat X_YYYYYYYY = new DecimalFormat("0.00000000");
    public static final DecimalFormat X_YYYYYYYYYYYY = new DecimalFormat("0.000000000000");

    public static String format12(double value) { return X_YYYYYYYYYYYY.format(value); }
    public static String format8(double value) { return X_YYYYYYYY.format(value); }
    public static String format5(double value) { return X_YYYYY.format(value); }

    public static String millisToDHMSStr(long millis) {
        StringBuilder res = new StringBuilder();
        long millisec = millis % 1000;
        res.append(millisec).append("ms");
        long sec = millis / 1000;
        if(sec > 0) {
            long secNum = sec % 60;
            res.insert(0, "sec ");
            res.insert(0, secNum);
            long minutes = sec / 60;
            if( minutes > 0 ) {
                long minutesNum = minutes % 60;
                res.insert(0, "min ");
                res.insert(0, minutesNum);
                long hours = minutes / 60;
                if( hours > 0 ) {
                    long hoursNum = hours % 24;
                    res.insert(0, "h ");
                    res.insert(0, hoursNum);
                    long days = hours / 24;
                    if( days > 0 ) {
                        res.insert(0, "d ");
                        res.insert(0, days);
                    }
                }
            }
        }
        return res.toString();
    }
}
