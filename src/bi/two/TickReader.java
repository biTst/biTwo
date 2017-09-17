package bi.two;

import bi.two.chart.TickData;
import bi.two.chart.TimesSeriesData;
import bi.two.chart.TradeTickData;
import bi.two.exch.ExchPairData;
import bi.two.exch.impl.Bitfinex;
import bi.two.util.MapConfig;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

public enum TickReader {
    FILE("file") {
        @Override public void readTicks(MapConfig config, TimesSeriesData<TickData> ticksTs, Runnable callback, ExchPairData pairData) throws Exception {
            String path = config.getProperty("dataFile");
            File file = new File(path);
            long fileLength = file.length();
            System.out.println("fileLength = " + fileLength);

            FileReader fileReader = new FileReader(file);

            long lastBytesToProcess = config.getLong("process.bytes");
            boolean skipBytes = (lastBytesToProcess > 0);
            if (skipBytes) {
                fileReader.skip(fileLength - lastBytesToProcess);
            }

            String dataFileType = config.getProperty("dataFile.type");

            readFileTicks(fileReader, ticksTs, callback, dataFileType, skipBytes);
        }

        private void readFileTicks(FileReader fileReader, TimesSeriesData<TickData> ticksTs, Runnable callback,
                                   String dataFileType, boolean skipBytes) throws IOException {
            BufferedReader br = new BufferedReader(fileReader, 1024 * 1024);
            try {
                if (skipBytes) { // after bytes skipping we may point to the middle of line
                    br.readLine(); // skip to the end of line
                }

                DataFileType type = DataFileType.get(dataFileType);

                String line;
                while ((line = br.readLine()) != null) {
                    // System.out.println("line = " + line);
                    TickData tickData = type.parseLine(line);
                    ticksTs.addNewestTick(tickData);
                    callback.run();
                }
            } finally {
                br.close();
            }
        }
    },
    BITFINEX("bitfinex") {
        @Override public void readTicks(MapConfig config, TimesSeriesData<TickData> ticksTs, Runnable callback, ExchPairData pairData) throws Exception {
            List<TradeTickData> ticks = Bitfinex.readTicks(TimeUnit.MINUTES.toMillis(5 * 100));
            for (int i = ticks.size() - 1; i >= 0; i--) {
                TradeTickData tick = ticks.get(i);
                ticksTs.addNewestTick(tick);
                callback.run();
            }
        }
    };

    private final String m_name;

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

    public void readTicks(MapConfig config, TimesSeriesData<TickData> ticksTs, Runnable callback, ExchPairData pairData) throws Exception {
        throw new RuntimeException("must be overridden");
    }
}
