package bi.two.util;

import java.text.DecimalFormat;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utils {
    public static final long MIN_IN_MILLIS = TimeUnit.MINUTES.toMillis(1);
    public static final long HOUR_IN_MILLIS = TimeUnit.HOURS.toMillis(1);
    public static final long DAY_IN_MILLIS = TimeUnit.DAYS.toMillis(1);
    public static final long MONTH_IN_MILLIS = 30 * DAY_IN_MILLIS;
    public static final long YEAR_IN_MILLIS = 365 * DAY_IN_MILLIS;
    // todo: similar like DAY_IN_MILLIS
    public static final long ONE_DAY_IN_MILLIS = TimeUnit.DAYS.toMillis(1);

    public static final float INVALID_PRICE = Float.MAX_VALUE;

    public static final DecimalFormat X_YYYYY = new DecimalFormat("0.00000");
    public static final DecimalFormat X_YYYYYYYY = new DecimalFormat("0.00000000");
    public static final DecimalFormat X_YYYYYYYYYYYY = new DecimalFormat("0.000000000000");


    public static String format12(Double value) { return format(X_YYYYYYYYYYYY, value); }
    public static String format8(Double value) { return format(X_YYYYYYYY, value); }
    public static String format5(Double value) { return format(X_YYYYY, value); }
    private static String format(DecimalFormat format, Double value) {
        return (value == null) ? "null" : format.format(value);
    }

    public static String millisToYDHMSStr(long millis) {
        StringBuilder res = new StringBuilder();
        long millisec = millis % 1000;
        res.append(millisec).append("ms");
        long sec = millis / 1000;
        if (sec > 0) {
            long secNum = sec % 60;
            res.insert(0, "sec ");
            res.insert(0, secNum);
            long minutes = sec / 60;
            if (minutes > 0) {
                long minutesNum = minutes % 60;
                res.insert(0, "min ");
                res.insert(0, minutesNum);
                long hours = minutes / 60;
                if (hours > 0) {
                    long hoursNum = hours % 24;
                    res.insert(0, "h ");
                    res.insert(0, hoursNum);
                    long days = hours / 24;
                    if (days > 0) {
                        long daysNum = days % 365;
                        res.insert(0, "d ");
                        res.insert(0, daysNum);
                        long years = days / 365;
                        if (years > 0) {
                            res.insert(0, "y ");
                            res.insert(0, years);
                        }
                    }
                }
            }
        }
        return res.toString();
    }

    // supports: "0", "-1M", "2w", "+3d", "-4h", "-5m", "5mim", "-6s", "6sec", "100ms"
    public static long toMillis(String str) {
        long delta;
        if ("0".equals(str)) {
            delta = 0;
        } else {
            Pattern p = Pattern.compile("^([\\+\\-]?)(\\d+)([a-zA-Z]*)$");
            Matcher m = p.matcher(str);
            if (m.matches()) {
                String sign = m.group(1);
                String count = m.group(2);
                String suffix = m.group(3);

                if (suffix.equals("M")) { // month
                    delta = 2592000000L; // 30L * 24L * 60L * 60L * 1000L;
                } else if (suffix.equals("w")) { // week
                    delta = 7 * ONE_DAY_IN_MILLIS;
                } else if (suffix.equals("d")) { // days
                    delta = ONE_DAY_IN_MILLIS;
                } else if (suffix.equals("h")) { // hours
                    delta = 60 * 60 * 1000;
                } else if (suffix.equals("m") || suffix.equals("min")) { // minutes
                    delta = 60 * 1000;
                } else if (suffix.equals("s") || suffix.equals("sec")) { // seconds
                    delta = 1000;
                } else if (suffix.equals("ms") || (suffix.length() == 0)) { // milli-seconds
                    delta = 1;
                } else {
                    throw new RuntimeException("unsupported suffix '" + suffix + "' in pattern: " + str);
                }
                delta *= Integer.parseInt(count);
                if (sign.equals("-")) {
                    delta *= -1;
                }
            } else {
                throw new RuntimeException("unsupported pattern: " + str);
            }
        }
        return delta;
    }

    public static String replaceAll(String str, String search, String replacement) {
        if (str.contains(search)) {
            int length = search.length();
            StringBuilder sb = new StringBuilder(str);
            int ind;
            while ((ind = sb.indexOf(search)) != -1) {
                sb.replace(ind, ind + length, replacement);
            }
            return sb.toString();
        }
        return str;
    }

    public static boolean equals(String str1, String str2) {
        return str1 == null
                ? (str2 == null)
                : (str2 != null) && str1.equals(str2);
    }
}
