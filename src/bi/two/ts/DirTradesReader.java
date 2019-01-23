package bi.two.ts;

import bi.two.DataFileType;
import bi.two.chart.TickData;
import bi.two.util.MapConfig;
import bi.two.util.TimeStamp;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static bi.two.util.Log.console;
import static bi.two.util.Log.log;

public class DirTradesReader {

    private static final Comparator<File> BY_NAME_FILE_COMPARATOR = new Comparator<File>() {
        @Override public int compare(File f1, File f2) {
            return f1.getName().compareTo(f2.getName());
        }
    };

    static void readTrades(MapConfig config, BaseTicksTimesSeriesData<TickData> ticksTs) {
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

        DataFileType type = DataFileType.obtain(config);

        long lastProcessedTickTime = 0;

        String filePattern = config.getPropertyNoComment("filePattern");
        Pattern pattern = (filePattern != null) ? Pattern.compile(filePattern) : null;

        ArrayList<File> files = new ArrayList<>();
        collectFilesToProcess(dir, files, pattern);
        Collections.sort(files, BY_NAME_FILE_COMPARATOR);
        int size = files.size();
        log("--collected " + size + " files to process");

        TimeStamp iterateStamp = new TimeStamp();
        int filesProcessed = 0;
        for (File file : files) {
            String absolutePath = file.getAbsolutePath();
            log("---readFileTicks: " + absolutePath);
            try {
                lastProcessedTickTime = FileTradesReader.readFileTrades(ticksTs, file, type, lastProcessedTickTime, 0, 0, Long.MAX_VALUE);
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

    private static void collectFilesToProcess(File dir, List<File> ret, Pattern pattern) {
        File[] files = dir.listFiles();
        for (File file : files) {
            if (file.isFile()) {
                if (pattern != null) {
                    String name = file.getName();
                    Matcher matcher = pattern.matcher(name);
                    boolean matches = matcher.matches();
                    if (!matches) {
                        log("skipped file: " + name + "; not matched");
                        continue;
                    }
                }
                ret.add(file);
            } else if (file.isDirectory()) {
                collectFilesToProcess(file, ret, pattern);
            }
        }
    }
}
