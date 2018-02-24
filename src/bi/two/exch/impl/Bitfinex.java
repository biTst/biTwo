package bi.two.exch.impl;

import bi.two.chart.TickVolumeData;
import bi.two.chart.TradeTickData;
import bi.two.exch.BaseExchImpl;
import bi.two.util.*;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.URL;
import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

// // https://bitfinex.readme.io/v2/reference
public class Bitfinex extends BaseExchImpl {
    private static final int DEF_TICKS_TO_LOAD = 1000;
    private static final int HTTP_TOO_MANY_REQUESTS = 429;
    private static final String CACHE_FILE_NAME = "bitfinex.trades";
    private static final String DATA_FILE_NAME = "bitfinex.data";
    private static final int MAX_ATTEMPTS = 50;

    private static final NumberFormat VOLUME_FORMAT = NumberFormat.getNumberInstance();
    private static final NumberFormat PRICE_FORMAT = NumberFormat.getNumberInstance();
    static {
        VOLUME_FORMAT.setMaximumFractionDigits(8);
        VOLUME_FORMAT.setGroupingUsed(false);
        PRICE_FORMAT.setMaximumFractionDigits(8);
        PRICE_FORMAT.setGroupingUsed(false);
    }

    private static void console(String s) { Log.console(s); }
    private static void log(String s) { Log.log(s); }
    private static void err(String s, Throwable t) { Log.err(s, t); }

    public static void main(String[] args) {
//        MarketConfig.initMarkets();

        new Thread() {
            @Override public void run() {
                setPriority(Thread.NORM_PRIORITY - 1); // smaller prio
                try {
                    List<TradeTickData> allTicks = readTicks(null, TimeUnit.MINUTES.toMillis(5));

                    logTicks(allTicks, "ALL ticks: ", false);
                } catch (Exception e) {
                    err("Bitfinex.main.error: " + e, e);
                }
            }

        }.start();
    }

    public static List<TradeTickData> readTicks(MapConfig config, long period) throws Exception {
        log("readTicks() need period=" + Utils.millisToYDHMSStr(period));

        boolean emptyCache = true;
        long newestCacheTickTime = 0;
        List<TradeTickData> cacheTicks = readCache();
        if (cacheTicks != null) {
            if (cacheTicks.size() > 0) {
                emptyCache = false;
                TickVolumeData newestTick = cacheTicks.get(0);
                newestCacheTickTime = newestTick.getTimestamp();
            }
        }

        boolean downloadTicks = (config == null) || config.getBoolean("download.ticks");
        boolean downloadNewestTicks = downloadTicks && ((config == null) || config.getBoolean("download.newest.ticks"));
        log(" downloadTicks=" + downloadTicks + "; downloadNewestTicks=" + downloadNewestTicks);

        List<TradeTickData> allTicks = new ArrayList<>();
        if (!downloadNewestTicks) {
            allTicks.addAll(cacheTicks);
        }

        if (downloadTicks) {
            boolean merged = !downloadNewestTicks;
            int reads = 0;
            long timestamp = downloadNewestTicks ? 0 : allTicks.get(allTicks.size() - 1).getTimestamp();
            while(true) {
                int size = allTicks.size();
                TickVolumeData newestTick = allTicks.get(0);
                TickVolumeData oldestTick = allTicks.get(size - 1);
                long oldestTickTimestamp = oldestTick.getTimestamp();
                long newestTickTimestamp = newestTick.getTimestamp();

                long allPeriod = newestTickTimestamp - oldestTickTimestamp;
                if (allPeriod > period) {
                    log("have required ticks. need " + Utils.millisToYDHMSStr(period) + "; have=" + Utils.millisToYDHMSStr(allPeriod));
                    break;
                }

                log("read: " + reads + "; allTicks.size=" + allTicks.size());
                if (reads > 0) {
                    Thread.sleep(4000); // do not DDoS
                }
                timestamp = readAndLog(allTicks, timestamp);
                reads++;

                // refresh
                size = allTicks.size();
                newestTick = allTicks.get(0);
                oldestTick = allTicks.get(size - 1);
                oldestTickTimestamp = oldestTick.getTimestamp();
                newestTickTimestamp = newestTick.getTimestamp();

                if (downloadNewestTicks && (oldestTickTimestamp < newestCacheTickTime)) { //
                    log("merge with cache: oldestTickTimestamp=" + oldestTickTimestamp
                            + "; newestCacheTickTime=" + newestCacheTickTime);
                    mergeTicksWithCache(allTicks, cacheTicks);

                    // refresh
                    size = allTicks.size();
                    newestTick = allTicks.get(0);
                    newestTickTimestamp = newestTick.getTimestamp();
                    oldestTick = allTicks.get(size - 1);
                    oldestTickTimestamp = oldestTick.getTimestamp();
                    timestamp = oldestTickTimestamp;
                    merged = true;
                }

                if (emptyCache || merged) {
                    writeCache(allTicks);
                    writeData(allTicks);
                }
            }

            writeCache(allTicks);
            writeData(allTicks);
        }

        logTicks(allTicks, "ALL ticks: ", false);

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
                log("ERROR merge with cache: index=" + i + "; cacheTickTimestamp=" + cacheTickTimestamp
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
                    log("merged " + (cacheTicksSize - i - 1) + " ticks from cache");
                    logTicks(allTicks, "MERGED ticks: ", false);
                    return;
                }
            }
        }
    }

    private static List<TradeTickData> readCache() throws Exception {
        File file = new File(CACHE_FILE_NAME);
        if (file.exists()) {
            FileInputStream fis = new FileInputStream(file);
            List<TradeTickData> ticks = readAllTicks(fis);

            logTicks(ticks, "CACHE: ", false);
            if (ticks.size() > 0) {
                return ticks;
            }
        }
        return null;
    }

    // cache ticks are stored in reverse order - as getting from bitfinex server
    private static void writeCache(List<TradeTickData> allTicks) throws Exception {
        File file = new File(CACHE_FILE_NAME);
        FileWriter fw = new FileWriter(file);
        try {
            BufferedWriter bw = new BufferedWriter(fw);
            try {
                // [[49448138,1501893566000,-0.74891095,2863.8],[49448101,1501893563000,-0.65,2863.8],   ,[49447948,1501893542000,0.03284317,2864.9]]
                bw.write("[\n");
                for (int i = 0, allTicksSize = allTicks.size(); i < allTicksSize; i++) {
                    TradeTickData tick = allTicks.get(i);
                    bw.write('[');
                    writeTick(bw, tick);
                    bw.write("]");
                    if (i < allTicksSize - 1) {
                        bw.write(",");
                    }
                    bw.write("\n");
                }
                bw.write("]");
            } finally {
                bw.flush();
                bw.close();
            }
        } finally {
            fw.close();
        }
    }

    private static void writeData(List<TradeTickData> allTicks) throws Exception {
        File file = new File(DATA_FILE_NAME);
        FileWriter fw = new FileWriter(file);
        try {
            BufferedWriter bw = new BufferedWriter(fw);
            try {
                // [[49448138,1501893566000,-0.74891095,2863.8],[49448101,1501893563000,-0.65,2863.8],   ,[49447948,1501893542000,0.03284317,2864.9]]
                for (int i = allTicks.size() - 1; i >= 0; i--) {
                    TradeTickData tick = allTicks.get(i);
                    writeTick(bw, tick);
                    bw.write("\n");
                }
                bw.write("]");
            } finally {
                bw.flush();
                bw.close();
            }
        } finally {
            fw.close();
        }
    }

    private static void writeTick(BufferedWriter bw, TradeTickData tick) throws IOException {
        long tradeId = tick.getTradeId();
        bw.write(Long.toString(tradeId));
        bw.write(',');

        long timestamp = tick.getTimestamp();
        bw.write(Long.toString(timestamp));
        bw.write(',');

        float volume = tick.getVolume();
        String volumeStr = VOLUME_FORMAT.format(volume);
        bw.write(volumeStr);
        bw.write(',');

        float price = tick.getClosePrice();
        String priceStr = PRICE_FORMAT.format(price);
        bw.write(priceStr);
    }


    private static long readAndLog(List<TradeTickData> allTicks, long timestamp) throws Exception {
        List<TradeTickData> ticks = execute(timestamp);

        int size = ticks.size();
        log(" got " + size + " ticks");
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
        log(" got " + toDelete + " ticks with timestamp=" + timestamp + " at the end of allTicks. deleted");

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
        String timePeriod = Utils.millisToYDHMSStr(newestTickTimestamp - oldestTickTimestamp);
        log(" " + prefix + ": size=" + size + "; time from " + new Date(oldestTickTimestamp) + " to " + new Date(newestTickTimestamp) + "; timePeriod=" + timePeriod);
        if (logArray) {
            for (int i = 0; i < size; i++) {
                TickVolumeData tick = ticks.get(i);
                log(" tick[" + i + "]: " + tick);
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

        int retryAfterTimeout = 0;
        int timeout = 1000;
        int timeoutStep = 2000;
        String error = "";
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
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
                    log("ERROR: HTTP_TOO_MANY_REQUESTS: retryAfter=" + retryAfter);
                    int sec = Integer.parseInt(retryAfter);
                    retryAfterTimeout = sec * 1000;
                } else if (responseCode == HttpsURLConnection.HTTP_GATEWAY_TIMEOUT) {
                    String msg = "HTTP_GATEWAY_TIMEOUT error";
                    log(msg);
                    error = msg;
                } else {
                    String msg = "ERROR: unexpected ResponseCode: " + responseCode + "; responseMessage=" + con.getResponseMessage();
                    error = msg;
                    log(msg);
                }
            } catch (Exception e) {
                String msg = "ERROR: " + e;
                error = msg;
                err(msg, e);
                retryAfterTimeout = 5000;
            } finally {
                con.disconnect();
            }
            int toWait = timeout + retryAfterTimeout;
            log(" sleep " + toWait + "ms...");
            Thread.sleep(toWait);
            timeout += timeoutStep;
            retryAfterTimeout = 0;
        }
        throw new Exception(error);
    }

    private static List<TradeTickData> readAllTicks(InputStream inputStream) throws IOException {
        StreamParser sp = new StreamParser(new BufferedInputStream(inputStream));
        // [[49448138,1501893566000,-0.74891095,2863.8],[49448101,1501893563000,-0.65,2863.8],   ,[49447948,1501893542000,0.03284317,2864.9]]
        try {
            if (sp.readChar('[')) {
                List<TradeTickData> ticks = new ArrayList<>();
                while (true) {
                    TradeTickData tvd = readTick(sp);
                    ticks.add(tvd);

                    int ch = sp.read();
                    if (ch == '\n') { // skip NL if happens
                        ch = sp.read();
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
            sp.close();
        }
    }

    private static TradeTickData readTick(StreamParser sp) throws IOException {
        // [49448138,1501893566000,-0.74891095,2863.8]
        sp.readChar('\n'); // skip NL if happens
        if (sp.readChar('[')) {
            long tradeId = sp.readLong();
            if (sp.readChar(',')) {
                long millis = sp.readLong();
                if (sp.readChar(',')) {
                    float size = sp.readFloat();
                    if (sp.readChar(',')) {
                        float price = sp.readFloat();
                        if (sp.readChar(']')) {
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
            String line = sp.readLine();
            log("line=" + line);
            throw new RuntimeException("expected [");
        }
    }
}
