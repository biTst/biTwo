package bi.two.ts;

import bi.two.DataFileType;
import bi.two.chart.TickData;
import bi.two.chart.TradeTickData;
import bi.two.exch.ExchPairData;
import bi.two.exch.impl.Bitfinex;
import bi.two.exch.impl.CexIo;
import bi.two.util.MapConfig;
import bi.two.util.TimeStamp;
import bi.two.util.Utils;

import java.io.*;
import java.nio.channels.Channels;
import java.nio.charset.Charset;
import java.util.List;
import java.util.concurrent.TimeUnit;

public enum TickReader {
    FILE("file") {
        @Override public void readTicks(MapConfig config, TimesSeriesData<TickData> ticksTs, Runnable callback, ExchPairData pairData) throws Exception {
            long joinTicks = config.getLongOrDefault("join.ticks", 1000L);

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
                    if (m_wasRead > lastBytesToProcess) {
                        System.out.println("too many reads");
                    }
                    if (m_wasRead > m_nextReport) {
                        long currentTimeMillis = System.currentTimeMillis();
                        if (currentTimeMillis - m_lastReportTime > 20000) {
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

            readFileTicks(reader, ticksTs, callback, dataFileType, skipBytes, joinTicks);
        }

        private void readFileTicks(Reader reader, TimesSeriesData<TickData> ticksTs, Runnable callback,
                                   String dataFileType, boolean skipBytes, long joinTicks) throws IOException {
            TimeStamp ts = new TimeStamp();
            BufferedReader br = new BufferedReader(reader, 256 * 1024);
            try {
                if (skipBytes) { // after bytes skipping we may point to the middle of line
                    br.readLine(); // skip to the end of line
                }

                TickJoiner joiner = new TickJoiner(ticksTs, joinTicks);
                DataFileType type = DataFileType.get(dataFileType);
                float lastClosePrice = 0;
                String line;
                while ((line = br.readLine()) != null) {
                    // System.out.println("line = " + line);
                    TickData tickData = type.parseLine(line);
                    if (tickData != null) {
                        float closePrice = tickData.getClosePrice();
                        if (lastClosePrice != 0) {
                            float rate = closePrice / lastClosePrice;
                            if (rate < 0.5 || rate > 1.5) {
                                continue; // skip too big price drops
                            }
                        }
                        lastClosePrice = closePrice;
                        joiner.addNewestTick(tickData);
                        if (callback != null) {
                            callback.run();
                        }
                    }
                }
                joiner.finish();
                System.out.println("ticksTs: all ticks was read in " + ts.getPassed());
                ticksTs.notifyNoMoreTicks();
            } finally {
                br.close();
            }
        }
    },
    BITFINEX("bitfinex") {
        @Override public void readTicks(MapConfig config, TimesSeriesData<TickData> ticksTs, Runnable callback, ExchPairData pairData) throws Exception {
//            long period = TimeUnit.HOURS.toMillis(10);
            long period = TimeUnit.DAYS.toMillis(365);
            List<TradeTickData> ticks = Bitfinex.readTicks(config, period);
            feedTicks(ticksTs, callback, ticks);
        }
    },
    CEX("cex") {
        @Override public void readTicks(MapConfig config, TimesSeriesData<TickData> ticksTs, Runnable callback, ExchPairData pairData) throws Exception {
//            long period = TimeUnit.MINUTES.toMillis(200);
            long period = TimeUnit.DAYS.toMillis(365);
            List<TradeTickData> ticks = CexIo.readTicks(period);
            feedTicks(ticksTs, callback, ticks);
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

    private static void feedTicks(TimesSeriesData<TickData> ticksTs, Runnable callback, List<TradeTickData> ticks) {
        TimeStamp doneTs = new TimeStamp();
        TimeStamp ts = new TimeStamp();
        int size = ticks.size();
        for (int i = size - 1, count = 0; i >= 0; i--, count++) {
            TradeTickData tick = ticks.get(i);
            ticksTs.addNewestTick(tick);
            if (callback != null) {
                callback.run();
            }
            if (ts.getPassedMillis() > 30000) {
                ts.restart();
                System.out.println("feedTicks() " + count + " from " + size + " (" + (((float) count) / size) + ") total " + doneTs.getPassed());
            }
        }
        System.out.println("feedTicks() done in " + doneTs.getPassed());
        ticksTs.notifyNoMoreTicks();
    }
}
