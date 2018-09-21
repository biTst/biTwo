package bi.two.ts;

import bi.two.DataFileType;
import bi.two.chart.TickData;
import bi.two.exch.schedule.TradeHours;
import bi.two.exch.schedule.TradeSchedule;
import bi.two.util.MapConfig;
import bi.two.util.TimeStamp;

import java.io.*;
import java.nio.channels.Channels;
import java.nio.charset.Charset;

import static bi.two.util.Log.console;
import static bi.two.util.Log.log;

public class FileTradesReader {

    public static void readFileTrades(MapConfig config, BaseTicksTimesSeriesData<TickData> tradesTs, Runnable callback) throws IOException {
        TimeStamp doneTs = new TimeStamp();

        String path = config.getPropertyNoComment("dataFile");
        long bytesToProcess = config.getLong("process.bytes");

        File file = new File(path);

        DataFileType type = DataFileType.obtain(config);
        TradeSchedule tradeSchedule = TradeSchedule.obtain(config);

        readFileTrades(tradesTs, callback, file, type, tradeSchedule, 0, bytesToProcess);

        console("readFileTicks() done in " + doneTs.getPassed());

        tradesTs.notifyNoMoreTicks();
    }

    public static long readFileTrades(BaseTicksTimesSeriesData<TickData> tradesTs, Runnable callback, File file, DataFileType type,
                                      TradeSchedule tradeSchedule, long lastProcessedTickTime, long bytesToProcess) throws IOException {
        RandomAccessFile randomAccessFile = new RandomAccessFile(file, "r");
        long fileLength = file.length();
        log("fileLength = " + fileLength);

        final long lastBytesToProcess = (bytesToProcess == 0) ? Long.MAX_VALUE : bytesToProcess;

        boolean resetLine = false;
        boolean skipBytes = (lastBytesToProcess > 0);
        if (skipBytes) {
            long toSkipByteNum = fileLength - lastBytesToProcess;
            if (toSkipByteNum > 0) {
                randomAccessFile.seek(toSkipByteNum);
                resetLine = true;
            }
        }

        InputStream is = Channels.newInputStream(randomAccessFile.getChannel());
        Reader reader = new InputStreamReader(is, Charset.forName("UTF-8"));

//        Reader reader = new InputStreamReader(is, Charset.forName("UTF-8")) {
//            private static final long REPORT_BLOCK_SIZE = 10000;
//
//            private long m_wasRead = 0;
//            private long m_startTime = System.currentTimeMillis();
//            private long m_nextReport = REPORT_BLOCK_SIZE;
//            private long m_lastReportTime;
//
//            @Override public int read(char[] cbuf, int off, int len) throws IOException {
//                int read = super.read(cbuf, off, len);
//                m_wasRead += read;
//                if (m_wasRead > lastBytesToProcess) {
//                    console("too many reads");
//                }
//                if (m_wasRead > m_nextReport) {
//                    long currentTimeMillis = System.currentTimeMillis();
//                    if (currentTimeMillis - m_lastReportTime > 20000) {
//                        long took = currentTimeMillis - m_startTime;
//                        double fraction = ((double) m_wasRead) / lastBytesToProcess;
//                        long projectedTotal = (long) (took / fraction);
//                        long projectedRemained = projectedTotal - took;
//                        console("was read=" + m_wasRead + "bytes; from=" + lastBytesToProcess + "; " + Utils.format5(fraction)
//                                + ": total: " + Utils.millisToYDHMSStr(projectedTotal) + "; remained=" + Utils.millisToYDHMSStr(projectedRemained));
//                        m_lastReportTime = currentTimeMillis;
//                    }
//                    m_nextReport += REPORT_BLOCK_SIZE;
//                }
//                return read;
//            }
//        };

        return readFileTrades(reader, tradesTs, callback, resetLine, type, tradeSchedule, lastProcessedTickTime); // reader closed inside
    }

    private static long readFileTrades(Reader reader, BaseTicksTimesSeriesData<TickData> ticksTs, Runnable callback,
                                       boolean resetFirstLine, DataFileType type, TradeSchedule tradeSchedule, long lastProcessedTickTime) throws IOException {
        long lastTickTime = 0;
        TimeStamp ts = new TimeStamp();
        BufferedReader br = new BufferedReader(reader, 256 * 1024);
        try {
            if (resetFirstLine) { // after bytes skipping we may point to the middle of line
                br.readLine(); // skip to the end of line
            }

            TradeHours currTradeHours = null;
            long eotdMillis = (tradeSchedule == null) ? Long.MAX_VALUE : 0; // pre-calculated End Of Trading Day

            float lastClosePrice = 0;
            String line;
            int readCounter = 0;
            int processCounter = 0;
            int skipCounter = 0;
            while ((line = br.readLine()) != null) {
                // System.out.println("line = " + line);
                TickData tickData = type.parseLine(line);
                if (tickData != null) {
                    readCounter++;
                    long timestamp = tickData.getTimestamp();
                    if (timestamp > lastProcessedTickTime) { // skip tick already processed outside
                        float closePrice = tickData.getClosePrice();
                        if (lastClosePrice != 0) {
                            float rate = closePrice / lastClosePrice;
                            if ((rate < 0.5) || (rate > 1.5)) {
                                continue; // skip errors: too big price jumps/drops
                            }
                        }

                        if (eotdMillis <= timestamp) { // tick is after current trade day
                            TradeHours nextTradeHours = tradeSchedule.getTradeHours(timestamp);
                            boolean inside = nextTradeHours.isInsideOfTradingHours(timestamp);
                            if (!inside) {
                                String dateTime = tradeSchedule.formatLongDateTime(timestamp);
                                throw new RuntimeException("next trade is not inside of trading day: dateTime=" + dateTime + "; nextTradeHours=" + nextTradeHours);
                            }
                            if (currTradeHours != null) {
                                long tradePause = nextTradeHours.m_tradeStartMillis - currTradeHours.m_tradeEndMillis;
                                if (tradePause < 0) {
                                    String dateTime = tradeSchedule.formatLongDateTime(timestamp);
                                    throw new RuntimeException("negative tradePause=" + tradePause + "; dateTime=" + dateTime + "; currTradeHours=" + currTradeHours + "; nextTradeHours=" + nextTradeHours);
                                }

                                TradeHours nextDayTradeHours = currTradeHours.getNextDayTradeHours();
                                if (nextDayTradeHours.equals(nextTradeHours)) { // expected next trade day
                                    ticksTs.shiftTime(tradePause);
                                }
                            }
                            currTradeHours = nextTradeHours;
                            eotdMillis = currTradeHours.m_tradeEndMillis;
                        }

                        lastClosePrice = closePrice;

                        ticksTs.addNewestTick(tickData);
//                        if (callback != null) {    // todo: use ProxyTimesSeriesData
//                            callback.run();
//                        }
                        processCounter++;
                        lastTickTime = timestamp;
                    } else {
                        skipCounter++;
                    }
                }
            }
            console("ticksTs: ticks stat: " + readCounter + " read; " + processCounter + " processed; " + skipCounter + " skipped in " + ts.getPassed());
        } finally {
            br.close();
        }
        return lastTickTime;
    }
}