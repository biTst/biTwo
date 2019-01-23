package bi.two.ts;

import bi.two.DataFileType;
import bi.two.chart.TickData;
import bi.two.exch.Exchange;
import bi.two.util.MapConfig;
import bi.two.util.TimeStamp;
import bi.two.util.Utils;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static bi.two.util.Log.*;

public class DirTradesReader {

    private static final Comparator<File> BY_NAME_FILE_COMPARATOR = new Comparator<File>() {
        @Override public int compare(File f1, File f2) {
            return f1.getName().compareTo(f2.getName());
        }
    };

    static void readTrades(MapConfig config, BaseTicksTimesSeriesData<TickData> ticksTs, Exchange exchange) {
        TimeStamp doneStamp = new TimeStamp();

        String path = config.getPropertyNoComment("dataDir");
        if (path == null) {
            throw new RuntimeException("dataDir is not specified in config");
        }
        File dir = new File(path);
        if (!dir.exists()) {
            throw new RuntimeException("directory does not exists: " + path);
        }
        if (!dir.isDirectory()) {
            throw new RuntimeException("not a directory: " + path);
        }

        TimeZone timezone = exchange.m_schedule.getTimezone();

        SimpleDateFormat sdf = new SimpleDateFormat("ddMMyyyy");
        sdf.setTimeZone(timezone);

        Date dateFrom = parseDate(config, "ticksFrom", sdf);
console("dateFrom: " + dateFrom);
        long ticksFromMillis = (dateFrom != null) ? dateFrom.getTime() : 0;

        long ticksToMillis;
        Date dateTo = parseDate(config, "ticksTo", sdf);
console("dateTo  : " + dateTo);
        if (dateTo != null) {
            ticksToMillis = dateTo.getTime() + TimeUnit.DAYS.toMillis(1); // include the whole day trades
console("dateTo+day: " + new Date(ticksToMillis));

//            Calendar calendar = new GregorianCalendar(timezone, Locale.getDefault());
//            calendar.setTime(dateTo);
//            calendar.add(Calendar.DAY_OF_MONTH, 1);
//            ticksToMillis = calendar.getTimeInMillis();

            if (ticksFromMillis != 0) {
                long fromTo = ticksToMillis - ticksFromMillis;
                String range = Utils.millisToYDHMSStr(fromTo);
                console("dateFromTo range: " + range);
            }
        } else {
            ticksToMillis = Long.MAX_VALUE;
        }

        DataFileType type = DataFileType.obtain(config);

        long lastProcessedTickTime = 0;

        String filePattern = config.getPropertyNoComment("filePattern");
        Pattern pattern = (filePattern != null) ? Pattern.compile(filePattern) : null;

        String dateInNameFormatStr = config.getPropertyNoComment("dateInNameFormat");
        SimpleDateFormat dateInNameFormat = null;
        if (dateInNameFormatStr != null) {
            dateInNameFormat = new SimpleDateFormat(dateInNameFormatStr);
            dateInNameFormat.setTimeZone(timezone);
        }

        ArrayList<File> files = new ArrayList<>();
        collectFilesToProcess(dir, files, pattern, ticksFromMillis, ticksToMillis, dateInNameFormat);
        Collections.sort(files, BY_NAME_FILE_COMPARATOR);
        int size = files.size();
        log("--collected " + size + " files to process");

        TimeStamp iterateStamp = new TimeStamp();
        int filesProcessed = 0;
        for (File file : files) {
            String absolutePath = file.getAbsolutePath();
            log("---readFileTicks: " + absolutePath);
            try {
                lastProcessedTickTime = FileTradesReader.readFileTrades(ticksTs, file, type, lastProcessedTickTime, 0, 0,
                        Long.MAX_VALUE, ticksFromMillis, ticksToMillis);
            } catch (Exception e) {
                throw new RuntimeException("error reading FileTicks: file: " + absolutePath, e);
            }
            filesProcessed++;
            if (iterateStamp.getPassedMillis() > 15000L) {
                console("readDir processed " + filesProcessed + " files form " + size);
                iterateStamp.restart();
            }
        }

        console("readDirTicks() done in " + doneStamp.getPassed() + ";  filesProcessed=" + filesProcessed);

        ticksTs.notifyNoMoreTicks();
    }

    private static Date parseDate(MapConfig config, String key, SimpleDateFormat sdf) {
        Date ret;
        String str = config.getPropertyNoComment(key);
        if (str != null) {
            try {
                ret = sdf.parse(str);
            } catch (ParseException e) {
                String msg = "error parsing key=" + key + " as date(ddMMyyyy) '" + str + "': " + e;
                err(msg, e);
                throw new RuntimeException("parse error: " + msg, e);
            }
        } else {
            ret = null;
        }
        return ret;
    }

    private static void collectFilesToProcess(File dir, List<File> ret, Pattern pattern, long ticksFromMillis, long ticksToMillis, SimpleDateFormat dateInNameFormat) {
        File[] files = dir.listFiles();
        for (File file : files) {
            if (file.isFile()) {
                if (pattern != null) {
                    String name = file.getName(); // SPFB.RTS_160523_160528.csv
                    Matcher matcher = pattern.matcher(name);
                    boolean matches = matcher.matches();
                    if (!matches) {
                        log("skipped file: " + name + "; not matched");
                        continue;
                    }

                    if ((ticksFromMillis > 0) && (ticksToMillis < Long.MAX_VALUE) && (dateInNameFormat != null)) { // pre-check ticks time from file name
                        int groupCount = matcher.groupCount();
                        if (groupCount >= 2) { // got group from pattern
                            String group1 = matcher.group(1);
                            String group2 = matcher.group(2);
                            try {
                                Date date1 = dateInNameFormat.parse(group1);
                                Date date2 = dateInNameFormat.parse(group2);
                                log("date1=" + date1 + "; date2=" + date2);

                                long fileTicksFrom = date1.getTime();
                                if (ticksToMillis < fileTicksFrom) {
                                    log("skipped file: " + name + "; after required time frame");
                                    continue;
                                }

                                long fileTicksTo = date2.getTime() + TimeUnit.DAYS.toMillis(1); // include the whole day trades
                                if (fileTicksTo < ticksFromMillis) {
                                    log("skipped file: " + name + "; before required time frame");
                                    continue;
                                }

                            } catch (ParseException e) {
                                err("ParseException: group1='" + group1 + "'; group2='" + group2 + "'; " + e, e);
                            }
                        }
                    }
                }
                ret.add(file);
            } else if (file.isDirectory()) {
                collectFilesToProcess(file, ret, pattern, ticksFromMillis, ticksToMillis, dateInNameFormat);
            }
        }
    }
}
