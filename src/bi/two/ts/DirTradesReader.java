package bi.two.ts;

import bi.two.DataFileType;
import bi.two.chart.TickData;
import bi.two.exch.schedule.TradeSchedule;
import bi.two.util.MapConfig;
import bi.two.util.TimeStamp;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;

import static bi.two.util.Log.console;
import static bi.two.util.Log.log;

public class DirTradesReader {
    static void readTrades(MapConfig config, BaseTicksTimesSeriesData<TickData> ticksTs, Runnable callback) {
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

        DataFileType type = DataFileType.obtain(config);
        TradeSchedule tradeSchedule = TradeSchedule.obtain(config);

        long lastProcessedTickTime = 0;
        int filesProcessed = 0;
        File[] files = dir.listFiles();
        Arrays.sort(files, new Comparator<File>() {
            @Override public int compare(File f1, File f2) {
                return f1.getName().compareTo(f2.getName());
            }
        });
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
                    lastProcessedTickTime = FileTradesReader.readFileTrades(config, ticksTs, callback, file, type, tradeSchedule, lastProcessedTickTime);
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
}
