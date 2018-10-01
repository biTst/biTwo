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
        long bytesToSkip = config.getLongOrDefault("skip.bytes", 0l);
        long bytesToProcess = config.getLongOrDefault("process.bytes", 0l);

        File file = new File(path);

        DataFileType type = DataFileType.obtain(config);
        TradeSchedule tradeSchedule = TradeSchedule.obtain(config);

        readFileTrades(tradesTs, callback, file, type, tradeSchedule, 0, bytesToSkip, bytesToProcess);

        console("readFileTicks() done in " + doneTs.getPassed());

        tradesTs.notifyNoMoreTicks();
    }

    public static long readFileTrades(BaseTicksTimesSeriesData<TickData> tradesTs, Runnable callback, File file, DataFileType type,
                                      TradeSchedule tradeSchedule, long lastProcessedTickTime, long bytesToSkip, long bytesToProcess) throws IOException {
        RandomAccessFile randomAccessFile = new RandomAccessFile(file, "r");
        long fileLength = file.length();
        log("fileLength = " + fileLength + "; bytesToSkip=" + bytesToSkip + "; bytesToProcess=" + bytesToProcess);

        long seekPosition;
        final long maxReadBytes;
        if (bytesToSkip == 0) {
            seekPosition = 0;
        } else {
            if (Math.abs(bytesToSkip) > fileLength) {
                throw new RuntimeException("unable to skip " + bytesToSkip + " bytes, file len=" + fileLength + ": " + file.getAbsolutePath());
            }
            seekPosition = (bytesToSkip > 0) ? bytesToSkip : fileLength + bytesToSkip;
        }
        if (bytesToProcess > 0) {
            long endPosition = seekPosition + bytesToProcess;
            if (endPosition > fileLength) {
                throw new RuntimeException("unable to skip " + bytesToSkip + " bytes, and read " + bytesToProcess + " bytes; file len=" + fileLength + ": " + file.getAbsolutePath());
            }
            maxReadBytes = bytesToProcess;
        } else { // all
            maxReadBytes = fileLength - seekPosition;
        }

        boolean resetLine = (seekPosition != 0); // seek position may point in the middle of string - skip first line
        if (resetLine) {
            randomAccessFile.seek(seekPosition);
        }

        InputStream is = Channels.newInputStream(randomAccessFile.getChannel());
//        Reader reader = new InputStreamReader(is, Charset.forName("UTF-8"));
        Reader reader = new InputStreamReader(is, Charset.forName("UTF-8")) {
            private long m_wasRead = 0;

            @Override public int read(char[] cbuf, int off, int len) throws IOException {
                if (maxReadBytes <= m_wasRead) {
                    return -1; // EOF
                }
                int read = super.read(cbuf, off, len);
                long totalRead = m_wasRead + read;
                if (maxReadBytes < totalRead) {
                    long extra = totalRead - maxReadBytes;
                    read -= extra;
                    totalRead -= extra;
                }
                m_wasRead = totalRead;
                return read;
            }
        };

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
        BufferedReader br = new BufferedReader(reader, 2 * 1024 * 1024); // 2 MB
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
            RuntimeException lastLineError = null;
            while ((line = br.readLine()) != null) {
                // System.out.println("line = " + line);
                if (lastLineError != null) {
                    throw lastLineError;
                }
                try {
                    TickData tickData = type.parseLine(line);
                    if (tickData != null) {
                        readCounter++;
                        long timestamp = tickData.getTimestamp();
                        if (timestamp > lastProcessedTickTime) { // skip tick already processed outside
                            if (timestamp < lastTickTime) {
                                throw new RuntimeException("backward tick: lastTickTime=" + lastTickTime + "; timestamp=" + timestamp);
                            }
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
                } catch (Exception e) {
                    // just save error for now. rethrow if nez line ok. if latest line is partial - ok - will have no more lines and exit loop
                    lastLineError = new RuntimeException("Error processing line: '" + line + "' : " + lastLineError, lastLineError);
                }
            }
            console("ticksTs: ticks stat: " + readCounter + " read; " + processCounter + " processed; " + skipCounter + " skipped in " + ts.getPassed());
        } finally {
            br.close();
        }
        return lastTickTime;
    }
}
