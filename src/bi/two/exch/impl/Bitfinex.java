package bi.two.exch.impl;

import bi.two.chart.TickVolumeData;
import bi.two.chart.TradeTickData;
import bi.two.exch.BaseExchImpl;
import bi.two.util.Post;
import bi.two.util.Utils;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.URL;
import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class Bitfinex extends BaseExchImpl {
    private static final int DEF_TICKS_TO_LOAD = 100;
    private static final int HTTP_TOO_MANY_REQUESTS = 429;
    public static final String CACHE_FILE_NAME = "bitfinex.trades";

    public static void main(String[] args) {
//        MarketConfig.initMarkets();

        new Thread() {
            @Override public void run() {
                try {
                    List<TradeTickData> allTicks = readTicks(TimeUnit.MINUTES.toMillis(5));

                    logTicks(allTicks, "ALL ticks: ", false);
                } catch (Exception e) {
                    System.out.println("error: " + e);
                    e.printStackTrace();
                }
            }

        }.start();
    }

    public static List<TradeTickData> readTicks(long period) throws Exception {
        System.out.println("readTicks() period=" + Utils.millisToDHMSStr(period));

        long newestCacheTickTime = 0;
        List<TradeTickData> cacheTicks = readCache(period);
        if (cacheTicks != null) {
            TickVolumeData newestTick = cacheTicks.get(0);
            newestCacheTickTime = newestTick.getTimestamp();
        }

        List<TradeTickData> allTicks = new ArrayList<>();

        int reads = 0;
        long timestamp = 0;
        while(true) {
            System.out.println("read: " + reads + "; allTicks.size=" + allTicks.size());
            if (reads > 0) {
                Thread.sleep(2000); // do not DDoS
            }
            timestamp = readAndLog(allTicks, timestamp);
            reads++;

            int size = allTicks.size();
            TickVolumeData newestTick = allTicks.get(0);
            TickVolumeData oldestTick = allTicks.get(size - 1);
            long oldestTickTimestamp = oldestTick.getTimestamp();
            long newestTickTimestamp = newestTick.getTimestamp();

            if (oldestTickTimestamp < newestCacheTickTime) { //
                System.out.println("merge with cache: oldestTickTimestamp=" + oldestTickTimestamp
                        + "; newestCacheTickTime=" + newestCacheTickTime);
                mergeTicksWithCache(allTicks, cacheTicks);

                // refresh
                size = allTicks.size();
                newestTick = allTicks.get(0);
                oldestTick = allTicks.get(size - 1);
                oldestTickTimestamp = oldestTick.getTimestamp();
                newestTickTimestamp = newestTick.getTimestamp();
            }

            long allPeriod = newestTickTimestamp - oldestTickTimestamp;
            if (allPeriod > period) {
                break;
            }
        }
        logTicks(allTicks, "ALL ticks: ", false);

        writeCache(allTicks);
        
        return allTicks;
    }

    private static void mergeTicksWithCache(List<TradeTickData> allTicks, List<TradeTickData> cacheTicks) {
        TradeTickData oldestTick = allTicks.get(allTicks.size()-1);
        long oldestTickTimestamp = oldestTick.getTimestamp();
        long oldestTickTradeId = oldestTick.getTradeId();
        for (int i = 0, cacheTicksSize = cacheTicks.size(); i < cacheTicksSize; i++) {
            TradeTickData cacheTick = cacheTicks.get(i);
            long cacheTickTimestamp = cacheTick.getTimestamp();
            if (cacheTickTimestamp < oldestTickTimestamp) {
                System.out.println("ERROR merge with cache: index=" + i + "; cacheTickTimestamp=" + cacheTickTimestamp
                        + "; oldestTickTimestamp=" + oldestTickTimestamp);
                return;
            }
            if (cacheTickTimestamp == oldestTickTimestamp) {
                long cacheTickTradeId = cacheTick.getTradeId();
                if (oldestTickTradeId == cacheTickTradeId) {
                    // got oldest tick in cache
                    for (int j = i + 1; j < cacheTicksSize; j++) { // do merge
                        cacheTick = cacheTicks.get(j);
                        allTicks.add(cacheTick);
                    }
                    System.out.println("merged " + (cacheTicksSize - i - 1) + " ticks from cache");
                    logTicks(allTicks, "MERGED ticks: ", false);
                    return;
                }
            }
        }
    }

    private static List<TradeTickData> readCache(long period) throws Exception {
        File file = new File(CACHE_FILE_NAME);
        if (file.exists()) {
            FileInputStream fis = new FileInputStream(file);
            List<TradeTickData> ticks = readAllTicks(fis);

            logTicks(ticks, "CACHE: ", false);

            if (ticks.size() > 0) {
                TickVolumeData newestTick = ticks.get(0);
                long newestTickTimestamp = newestTick.getTimestamp();
                long needTime = System.currentTimeMillis() - period;
                if (needTime < newestTickTimestamp) {
                    System.out.println(" can use cache: needTime=" + needTime + "; newestTickTimestamp=" + newestTickTimestamp);
                    return ticks;
                }
            }
        }
        return null;
    }

    private static void writeCache(List<TradeTickData> allTicks) throws Exception {
        File file = new File(CACHE_FILE_NAME);
//        if (file.exists()) {
//            file.delete();
//        }
        FileWriter fw = new FileWriter(file);
        try {
            BufferedWriter bw = new BufferedWriter(fw);
            try {
                NumberFormat volumeFormat = NumberFormat.getInstance();
                volumeFormat.setMaximumFractionDigits(8);
                volumeFormat.setGroupingUsed(false);
                NumberFormat priceFormat = NumberFormat.getInstance();
                priceFormat.setMaximumFractionDigits(4);
                priceFormat.setGroupingUsed(false);
                // [[49448138,1501893566000,-0.74891095,2863.8],[49448101,1501893563000,-0.65,2863.8],   ,[49447948,1501893542000,0.03284317,2864.9]]
                bw.write("[\n");
                for (int i = 0, allTicksSize = allTicks.size(); i < allTicksSize; i++) {
                    TradeTickData tick = allTicks.get(i);
                    bw.write('[');

                    long tradeId = tick.getTradeId();
                    bw.write(Long.toString(tradeId));
                    bw.write(',');

                    long timestamp = tick.getTimestamp();
                    bw.write(Long.toString(timestamp));
                    bw.write(',');

                    float volume = tick.getVolume();
                    String volumeStr = volumeFormat.format(volume);
                    bw.write(volumeStr);
                    bw.write(',');

                    float price = tick.getPrice();
                    String priceStr = priceFormat.format(price);
                    bw.write(priceStr);

                    bw.write("]");
                    if (i < allTicksSize - 1) {
                        bw.write(",");
                    }
                    bw.write("\n");
                }
                bw.write("]");
            } finally {
                bw.flush();
            }
        } finally {
            fw.close();
        }
    }


    private static long readAndLog(List<TradeTickData> allTicks, long timestamp) throws Exception {
        List<TradeTickData> ticks = execute(timestamp);

        int size = ticks.size();
        System.out.println(" got " + size + " ticks");
        if (size == 0) {
            throw new Exception("no ticks");
        }

        logTicks(ticks, "ticks: ", false);

        TickVolumeData oldestTick = ticks.get(size - 1);
        long oldestTickTimestamp = oldestTick.getTimestamp();

        // glue ticks
        int toDelete = 0;
        for (TickVolumeData tick : allTicks) {
            long millis = tick.getTimestamp();
            if (millis == timestamp) {
                toDelete++;
            }
        }
        int allTicksSize = allTicks.size();
        for (int i = 1; i <= toDelete; i++) {
            allTicks.remove(allTicksSize - i);
        }
        System.out.println(" got " + toDelete + " ticks with timestamp=" + timestamp + " at the end of allTicks. deleted");

        allTicks.addAll(ticks);
        logTicks(allTicks, "glued", false);

        return oldestTickTimestamp;
    }

    private static void logTicks(List<TradeTickData> ticks, String prefix, boolean logArray) {
        int size = ticks.size();
        TickVolumeData newestTick = ticks.get(0);
        TickVolumeData oldestTick = ticks.get(size - 1);
        long oldestTickTimestamp = oldestTick.getTimestamp();
        long newestTickTimestamp = newestTick.getTimestamp();
        String timePeriod = Utils.millisToDHMSStr(newestTickTimestamp - oldestTickTimestamp);
        System.out.println(" " + prefix + ": size=" + size + "; time from " + new Date(newestTickTimestamp) + " to " + new Date(oldestTickTimestamp) + "; timePeriod=" + timePeriod);
        if (logArray) {
            for (int i = 0; i < size; i++) {
                TickVolumeData tick = ticks.get(i);
                System.out.println(" tick[" + i + "]: " + tick);
            }
        }
    }

    private static List<TradeTickData> execute(long timestamp) throws Exception {
            // https://bitfinex.readme.io/v2/reference#rest-public-trades
//        QUERY           PARAMS
//        limit   int32   Number of records       120
//        start   int32   Millisecond start time  0
//        end     int32   Millisecond end time    0
//        sort    int32   if = 1 it sorts results returned with old > new     -1

        Map<String, String> sArray = new HashMap<>();
        sArray.put("limit", Integer.toString(DEF_TICKS_TO_LOAD));
//            sArray.put("sort", "1");
        sArray.put("end", Long.toString(timestamp));
        String postData = Post.createHttpPostString(sArray, false);

        String pair = "tBTCUSD";
        String address = "https://api.bitfinex.com/v2/trades/" + pair + "/hist?" + postData;
        URL url = new URL(address);
        HttpsURLConnection con = (HttpsURLConnection) url.openConnection();
        try {
            con.setRequestMethod("GET");
            con.setUseCaches(false);

            int responseCode = con.getResponseCode();

            if (responseCode == HttpsURLConnection.HTTP_OK) {
                List<TradeTickData> ticks = readAllTicks(con.getInputStream());
                return ticks;
            } else if (responseCode == HTTP_TOO_MANY_REQUESTS) {
                String retryAfter = con.getHeaderField("Retry-After");
                throw new Exception("ERROR: HTTP_TOO_MANY_REQUESTS: retryAfter=" + retryAfter);
            } else {
                throw new Exception("ERROR: unexpected ResponseCode: " + responseCode);
            }
        } finally {
            con.disconnect();
        }
    }

    private static List<TradeTickData> readAllTicks(InputStream inputStream) throws IOException {
        PushbackInputStream pbis = new PushbackInputStream(new BufferedInputStream(inputStream));
        // [[49448138,1501893566000,-0.74891095,2863.8],[49448101,1501893563000,-0.65,2863.8],   ,[49447948,1501893542000,0.03284317,2864.9]]
        try {
            if (readChar(pbis, '[')) {
                List<TradeTickData> ticks = new ArrayList<>();
                while (true) {
                    TradeTickData tvd = readTick(pbis);
                    ticks.add(tvd);

                    int ch = pbis.read();
                    if (ch == '\n') { // skip NL if happens
                        ch = pbis.read();
                    }
                    if (ch == ']') { // EndOfArray
                        break;
                    }
                    if (ch != ',') {
                        throw new RuntimeException("expected ,");
                    }
                }
                return ticks;
            } else {
                throw new RuntimeException("expected [");
            }
        } finally {
            pbis.close();
        }
    }

    private static TradeTickData readTick(PushbackInputStream pbis) throws IOException {
        // [49448138,1501893566000,-0.74891095,2863.8]
        readChar(pbis, '\n'); // skip NL if happens
        if(readChar(pbis, '[')) {
            long tradeId = readLong(pbis);
            if(readChar(pbis, ',')) {
                long millis = readLong(pbis);
                if(readChar(pbis, ',')) {
                    float size = readFloat(pbis);
                    if(readChar(pbis, ',')) {
                        float price = readFloat(pbis);
                        if(readChar(pbis, ']')) {
                            TradeTickData tvd = new TradeTickData(tradeId, millis, price, size);
                            return tvd;
                        } else {
                            throw new RuntimeException("expected ]");
                        }
                    } else {
                        throw new RuntimeException("expected 3rd ,");
                    }
                } else {
                    throw new RuntimeException("expected 2nd ,");
                }
            } else {
                throw new RuntimeException("expected ,");
            }
        } else {
            String line = readLine(pbis);
            System.out.println("line=" + line);
            throw new RuntimeException("expected [");
        }
    }

    private static double readDouble(PushbackInputStream pbis) throws IOException {
        StringBuilder sb = readNumber(pbis);
        return Double.parseDouble(sb.toString());
    }

    private static float readFloat(PushbackInputStream pbis) throws IOException {
        StringBuilder sb = readNumber(pbis);
        return Float.parseFloat(sb.toString());
    }

    private static StringBuilder readNumber(PushbackInputStream pbis) throws IOException {
        StringBuilder sb = new StringBuilder();
        int read;
        while ((read = pbis.read()) != -1) {
            if (Character.isDigit(read) || (read=='.') || (read=='-')) {
                sb.append((char)read);
            } else {
                pbis.unread(read);
                break;
            }
        }
        return sb;
    }

    private static long readLong(PushbackInputStream pbis) throws IOException {
        StringBuilder sb = new StringBuilder();
        int read;
        while ((read = pbis.read()) != -1) {
            if (!Character.isDigit(read)) {
                pbis.unread(read);
                break;
            }
            sb.append((char)read);
        }
        return Long.parseLong(sb.toString());
    }

    private static String readLine(PushbackInputStream pbis) throws IOException {
        StringBuilder sb = new StringBuilder();
        int read;
        while ((read = pbis.read()) != -1) {
            if (read == '\n' || read == '\r' ) {
                pbis.unread(read);
                break;
            }
            sb.append((char)read);
        }
        return sb.toString();
    }

    private static boolean readChar(PushbackInputStream pbis, char c) throws IOException {
        int ch = pbis.read();
        boolean got = (ch == c);
        if (!got) {
            pbis.unread(ch);
        }
        return got;
    }

    private static void skipDigits(PushbackInputStream pbis) throws IOException {
        int read;
        while ((read = pbis.read()) != -1) {
            if (!Character.isDigit(read)) {
                pbis.unread(read);
                return;
            }
        }
    }
}
