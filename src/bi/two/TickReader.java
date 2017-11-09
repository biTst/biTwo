package bi.two;

import bi.two.chart.TickData;
import bi.two.chart.TradeTickData;
import bi.two.exch.ExchPairData;
import bi.two.exch.impl.Bitfinex;
import bi.two.ts.TimesSeriesData;
import bi.two.util.MapConfig;
import bi.two.util.Utils;

import java.io.*;
import java.nio.channels.Channels;
import java.nio.charset.Charset;
import java.util.List;
import java.util.concurrent.TimeUnit;

public enum TickReader {
    FILE("file") {
        @Override public void readTicks(MapConfig config, TimesSeriesData<TickData> ticksTs, Runnable callback, ExchPairData pairData) throws Exception {
            String path = config.getPropertyNoComment("dataFile");
            File file = new File(path);
            RandomAccessFile randomAccessFile = new RandomAccessFile(file, "r");
            long fileLength = file.length();
            System.out.println("fileLength = " + fileLength);

            final long lastBytesToProcess = config.getLong("process.bytes");

            boolean skipBytes = (lastBytesToProcess > 0);
            if (skipBytes) {
                long toSkipBytes = fileLength - lastBytesToProcess;
                if (toSkipBytes > 0) {
                    randomAccessFile.seek(toSkipBytes);
                }
            }

            InputStream is = Channels.newInputStream(randomAccessFile.getChannel());
            Reader reader = new InputStreamReader(is, Charset.forName("UTF-8")) {
                private static final long REPORT_BLOCK_SIZE = 10000;

                private long m_wasRead = 0;
                private long m_startTime = System.currentTimeMillis();
                private long m_nextReport = REPORT_BLOCK_SIZE;
                private long m_lastReportTime;

                @Override public int read(char[] cbuf, int off, int len) throws IOException {
                    int read = super.read(cbuf, off, len);
                    m_wasRead += read;
                    if(m_wasRead > lastBytesToProcess) {
                        System.out.println("too many reads");
                    }
                    if (m_wasRead > m_nextReport) {
                        long currentTimeMillis = System.currentTimeMillis();
                        if(currentTimeMillis - m_lastReportTime > 20000) {
                            long took = currentTimeMillis - m_startTime;
                            double fraction = ((double) m_wasRead) / lastBytesToProcess;
                            long projectedTotal = (long) (took / fraction);
                            long projectedRemained = projectedTotal - took;
                            System.out.println("was read=" + m_wasRead + "bytes; from=" + lastBytesToProcess + "; " + Utils.format5(fraction)
                                    + ": total: " + Utils.millisToYDHMSStr(projectedTotal) + "; remained=" + Utils.millisToYDHMSStr(projectedRemained));
                            m_lastReportTime = currentTimeMillis;
                        }
                        m_nextReport += REPORT_BLOCK_SIZE;
                    }
                    return read;
                }
            };

            String dataFileType = config.getProperty("dataFile.type");

            readFileTicks(reader, ticksTs, callback, dataFileType, skipBytes);
        }

        private void readFileTicks(Reader reader, TimesSeriesData<TickData> ticksTs, Runnable callback,
                                   String dataFileType, boolean skipBytes) throws IOException {
            BufferedReader br = new BufferedReader(reader, 256 * 1024);
            try {
                if (skipBytes) { // after bytes skipping we may point to the middle of line
                    br.readLine(); // skip to the end of line
                }

                DataFileType type = DataFileType.get(dataFileType);

                float lastClosePrice = 0;
                String line;
                while ((line = br.readLine()) != null) {
                    // System.out.println("line = " + line);
                    TickData tickData = type.parseLine(line);

                    float closePrice = tickData.getClosePrice();
                    if (lastClosePrice != 0) {
                        float rate = closePrice / lastClosePrice;
                        if (rate < 0.5 || rate > 1.5) {
                            continue; // skip too big price drops
                        }
                    }
                    lastClosePrice = closePrice;

                    ticksTs.addNewestTick(tickData);
                    if (callback != null) {
                        callback.run();
                    }
                }
//                System.out.println("ticksTs: all ticks was read");
                ticksTs.notifyNoMoreTicks();
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
                if (callback != null) {
                    callback.run();
                }
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
