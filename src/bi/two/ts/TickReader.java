package bi.two.ts;

import bi.two.DataFileType;
import bi.two.chart.TickData;
import bi.two.exch.impl.BitMex;
import bi.two.exch.impl.Bitfinex;
import bi.two.exch.impl.CexIo;
import bi.two.exch.schedule.TradeSchedule;
import bi.two.util.Log;
import bi.two.util.MapConfig;
import bi.two.util.ReverseListIterator;
import bi.two.util.TimeStamp;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

public enum TickReader {
    DIR("dir") {
        @Override public void readTicks(MapConfig config, BaseTicksTimesSeriesData<TickData> ticksTs, Runnable callback) throws Exception {
            TimeStamp doneTs = new TimeStamp();

            String path = config.getPropertyNoComment("dataDir");
            File dir = new File(path);
            if (!dir.exists()) {
                throw new RuntimeException("directory does not exists: " + path);
            }
            if (!dir.isDirectory()) {
                throw new RuntimeException("not a directory: " + path);
            }

            String filePattern = config.getPropertyNoComment("filePattern");

            DataFileType type = DataFileType.init(config);
            TradeSchedule tradeSchedule = TradeSchedule.init(config);

            long lastProcessedTickTime = 0;
            int filesProcessed = 0;
            File[] files = dir.listFiles();
            Arrays.sort(files, Comparator.comparing(File::getName));
            for (File file : files) {
                String absolutePath = file.getAbsolutePath();
                if (file.isFile()) {
                    if (filePattern != null) {
                        String name = file.getName();
                        boolean matches = name.matches(filePattern);
                        if (!matches) {
                            log("skipped file: " + name + "; not matched");
                            continue;
                        }
                    }

                    log("readFileTicks: " + absolutePath);
                    try {
                        lastProcessedTickTime = FileTickReader.readFileTicks(config, ticksTs, callback, file, type, tradeSchedule, lastProcessedTickTime);
                    } catch (Exception e) {
                        throw new RuntimeException("error reading FileTicks: file: " + absolutePath, e);
                    }
                    filesProcessed++;
                } else {
                    console("skipped subdirectory: " + absolutePath);
                }
            }

            console("readDirTicks() done in " + doneTs.getPassed() + ";  filesProcessed=" + filesProcessed);

            ticksTs.notifyNoMoreTicks();
        }
    },
    FILE("file") {
        @Override public void readTicks(MapConfig config, BaseTicksTimesSeriesData<TickData> ticksTs, Runnable callback) throws Exception {
            FileTickReader.readFileTicks( config, ticksTs, callback);
        }
    },
    BITFINEX("bitfinex") {
        @Override public void readTicks(MapConfig config, BaseTicksTimesSeriesData<TickData> ticksTs, Runnable callback) throws Exception {
//            long period = TimeUnit.HOURS.toMillis(10);
            long period = TimeUnit.DAYS.toMillis(365);
            List<TickData> ticks = Bitfinex.readTicks(config, period);
            feedTicks(ticksTs, callback, ticks);
        }
    },
    CEX("cex") {
        @Override public void readTicks(MapConfig config, BaseTicksTimesSeriesData<TickData> ticksTs, Runnable callback) throws Exception {
//            long period = TimeUnit.MINUTES.toMillis(200);
            long period = TimeUnit.DAYS.toMillis(365);
            List<TickData> ticks = CexIo.readTicks(period);
            feedTicks(ticksTs, callback, ticks);
        }
    },
    BITMEX("bitmex") {
        @Override public void readTicks(MapConfig config, BaseTicksTimesSeriesData<TickData> ticksTs, Runnable callback) throws Exception {
            long period = TimeUnit.DAYS.toMillis(365);
            TicksTimesSeriesData<TickData> fileTs = BitMex.readTicks(config, period);
            int size = fileTs.getTicksNum();
            Iterable<TickData> iterable = fileTs.getReverseTicksIterable();
            feedTicks(ticksTs, callback, iterable, size);
        }
    },
    ;

    private final String m_name;

    private static void console(String s) { Log.console(s); }
    private static void log(String s) { Log.log(s); }

    TickReader(String name) {
        m_name = name;
    }

    public static TickReader get(String name) {
        for (TickReader tickReader : values()) {
            if (tickReader.m_name.equals(name)) {
                return tickReader;
            }
        }
        throw new RuntimeException("Unknown TickReader '" + name + "'");
    }

    public void readTicks(MapConfig config, BaseTicksTimesSeriesData<TickData> ticksTs, Runnable callback) throws Exception {
        throw new RuntimeException("must be overridden");
    }

    private static void feedTicks(BaseTicksTimesSeriesData<TickData> ticksTs, Runnable callback, List<TickData> ticks) {
        int size = ticks.size();
        ReverseListIterator<TickData> iterator = new ReverseListIterator<>(ticks);
        feedTicks(ticksTs, callback, iterator, size);
    }

    private static void feedTicks(BaseTicksTimesSeriesData<TickData> ticksTs, Runnable callback, Iterable<TickData> iterator, int size) {
        TimeStamp doneTs = new TimeStamp();
        TimeStamp ts = new TimeStamp();
        int count = 0;
        for (TickData tick : iterator) {
            ticksTs.addNewestTick(tick);
            if (callback != null) {
                callback.run();
            }
            if (ts.getPassedMillis() > 30000) {
                ts.restart();
                console("feedTicks() " + count + " from " + size + " (" + (((float) count) / size) + ") total " + doneTs.getPassed());
            }
            count++;
        }
        console("feedTicks() done in " + doneTs.getPassed());
        ticksTs.notifyNoMoreTicks();
    }
}
