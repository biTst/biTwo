package bi.two.ts;

import bi.two.DataFileType;
import bi.two.chart.TickData;
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

        String path = config.getString("dataFile");
        long bytesToSkip = config.getLongOrDefault("skip.bytes", 0l);
        long bytesToProcess = config.getLongOrDefault("process.bytes", 0l);
        long ticksToProcess = config.getLongOrDefault("process.ticks", Long.MAX_VALUE);

        File file = new File(path);

        DataFileType type = DataFileType.obtain(config);

        readFileTrades(tradesTs, callback, file, type, 0, bytesToSkip, bytesToProcess, ticksToProcess);

        console("readFileTicks() done in " + doneTs.getPassed());

        tradesTs.notifyNoMoreTicks();
    }

    public static long readFileTrades(BaseTicksTimesSeriesData<TickData> tradesTs, Runnable callback, File file, DataFileType type,
                                      long lastProcessedTickTime, long bytesToSkip, long bytesToProcess, long ticksToProcess) throws IOException {


//        FileInputStream fis = new FileInputStream(new File("ip.bin"));
//        FileChannel channel = fis.getChannel();
//        ByteBuffer bb = ByteBuffer.allocateDirect(64*1024);
//        bb.clear();
//        int[] ipArr = new int [(int)channel.size()/4];
//        System.out.println("File size: "+channel.size()/4);
//        long len = 0;
//        int offset = 0;
//        while ((len = channel.read(bb))!= -1){
//            bb.flip();
//            //System.out.println("Offset: "+offset+"\tlen: "+len+"\tremaining:"+bb.hasRemaining());
//            bb.asIntBuffer().get(ipArr,offset,(int)len/4);
//            offset += (int)len/4;
//            bb.clear();
//        }

//        Path path = Paths.get("src/main/resources/shakespeare.txt");
//        try (BufferedReader reader = Files.newBufferedReader(path, Charset.forName("UTF-8"))) {
//            reader.skip(123123);
//            String currentLine;
//            while ((currentLine = reader.readLine()) != null) {//while there is content on the current line
//                System.out.println(currentLine); // print the current line
//            }
//        } catch (IOException ex) {
//            ex.printStackTrace(); //handle an exception here
//        }

//        Path path = Paths.get("src/main/resources/shakespeare.txt");
//        try {
//            Files.lines(path)
//                    .filter(line -> line.startsWith("Love"))
//                    .forEach(System.out::println);//print each line
//        } catch (IOException ex) {
//            ex.printStackTrace();//handle exception here
//        }

//        RandomAccessFile aFile = new RandomAccessFile("test.txt","r");
//        FileChannel inChannel = aFile.getChannel();
//        long fileSize = inChannel.size();
//        ByteBuffer buffer = ByteBuffer.allocate((int) fileSize);
//        inChannel.read(buffer);
//        buffer.flip();
//        for (int i = 0; i < fileSize; i++){
//            System.out.print((char) buffer.get());
//        }
//        inChannel.close();
//        aFile.close();

//        RandomAccessFile aFile = new RandomAccessFile("test.txt", "r");
//        FileChannel inChannel = aFile.getChannel();
//        ByteBuffer buffer = ByteBuffer.allocate(1024);
//        while(inChannel.read(buffer) > 0){
//            buffer.flip();
//            for (int i = 0; i < buffer.limit(); i++){
//                System.out.print((char) buffer.get());
//            }
//            buffer.clear(); // do something with the data and clear/compact it.
//        }
//        inChannel.close();
//        aFile.close();

//        RandomAccessFile aFile = new RandomAccessFile("test.txt", "r");
//        FileChannel inChannel = aFile.getChannel();
//        MappedByteBuffer buffer = inChannel.map(FileChannel.MapMode.READ_ONLY, 0, inChannel.size());
//        buffer.load();
//        for (int i = 0; i < buffer.limit(); i++){
//            System.out.print((char) buffer.get());
//        }
//        buffer.clear(); // do something with the data and clear/compact it.
//        inChannel.close();
//        aFile.close();

        RandomAccessFile randomAccessFile = new RandomAccessFile(file, "r");
        long fileLength = file.length();
        log("fileLength = " + fileLength + "; bytesToSkip=" + bytesToSkip + "; bytesToProcess=" + bytesToProcess + "; ticksToProcess=" + ticksToProcess);

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

        String name = file.getName();
        return readFileTrades(reader, tradesTs, callback, resetLine, type, lastProcessedTickTime, ticksToProcess, name); // reader closed inside
    }

    private static long readFileTrades(Reader reader, BaseTicksTimesSeriesData<TickData> ticksTs, Runnable callback,
                                       boolean resetFirstLine, DataFileType type, long lastProcessedTickTime, long ticksToProcess, String perfix) throws IOException {
        long lastTickTime = 0;
        TimeStamp ts = new TimeStamp();
        BufferedReader br = new BufferedReader(reader, 2 * 1024 * 1024); // 2 MB
        try {
            if (resetFirstLine) { // after bytes skipping we may point to the middle of line
                br.readLine(); // skip to the end of line
            }

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

                            lastClosePrice = closePrice;

                            ticksTs.addNewestTick(tickData);
    //                        if (callback != null) {    // todo: use ProxyTimesSeriesData
    //                            callback.run();
    //                        }
                            processCounter++;
                            if (processCounter == ticksToProcess) {
                                log("processed all requested " + ticksToProcess + " ticks - exit");
                                break;
                            }
                            lastTickTime = timestamp;
                        } else {
                            skipCounter++;
                        }
                    }
                } catch (Exception e) {
                    // just save error for now. rethrow if next line is ok. if latest line is partial - ok - will have no more lines and exit loop
                    lastLineError = new RuntimeException("Error processing line: '" + line + "' : " + e, e);
                }
            }
            console(perfix + " ticksTs: ticks stat: " + readCounter + " read; " + processCounter + " processed; " + skipCounter + " skipped in " + ts.getPassed());
        } finally {
            br.close();
        }
        return lastTickTime;
    }
}
