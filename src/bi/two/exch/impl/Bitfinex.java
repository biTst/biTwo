package bi.two.exch.impl;

import bi.two.chart.TickVolumeData;
import bi.two.chart.TradeTickData;
import bi.two.exch.BaseExchImpl;
import bi.two.util.Post;
import bi.two.util.Utils;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.PushbackInputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

public class Bitfinex extends BaseExchImpl {
    private static final int DEF_TICKS_TO_LOAD = 1000;

    public static void main(String[] args) {
//        MarketConfig.initMarkets();

        new Thread() {
            @Override public void run() {
                try {
                    List<TickVolumeData> allTicks = new ArrayList<>();

                    int timestamp = 0;
                    long oldestTickTimestamp = readAndLog(allTicks, timestamp);

//                    Thread.sleep(1000); // do not DDoS
//                    oldestTickTimestamp = readAndLog(allTicks, oldestTickTimestamp);

                    logTicks(allTicks, "ALL ticks: ", false);
                } catch (Exception e) {
                    System.out.println("error: " + e);
                    e.printStackTrace();
                }
            }

        }.start();
    }

    public static List<TickVolumeData> readTicks(long period) throws Exception {
        System.out.println("readTicks() period=" + period);

        List<TickVolumeData> allTicks = new ArrayList<>();

        int reads = 0;
        long timestamp = 0;
        while(true) {
            if (reads > 0) {
                Thread.sleep(1000); // do not DDoS
            }
            timestamp = readAndLog(allTicks, timestamp);
            reads++;

            int size = allTicks.size();
            TickVolumeData newestTick = allTicks.get(0);
            TickVolumeData oldestTick = allTicks.get(size - 1);
            long oldestTickTimestamp = oldestTick.getTimestamp();
            long newestTickTimestamp = newestTick.getTimestamp();
            long allPeriod = newestTickTimestamp - oldestTickTimestamp;
            if(allPeriod > period) {
                break;
            }
        }
        logTicks(allTicks, "ALL ticks: ", false);
        return allTicks;
    }
    
    private static long readAndLog(List<TickVolumeData> allTicks, long timestamp) throws Exception {
        List<TickVolumeData> ticks = execute(timestamp);

        int size = ticks.size();
        System.out.println(" got " + size + " ticks");
        if (size == 0) {
            throw new Exception("no ticks");
        }

        long oldestTickTimestamp = logTicks(ticks, "ticks: ", false);

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

        return oldestTickTimestamp;
    }

    private static long logTicks(List<TickVolumeData> ticks, String prefix, boolean logArray) {
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
        return oldestTickTimestamp;
    }

    private static List<TickVolumeData> execute(long timestamp) throws Exception {
            // https://bitfinex.readme.io/v2/reference#rest-public-trades
//        QUERY           PARAMS
//        limit   int32   Number of records       120
//        start   int32   Millisecond start time  0
//        end     int32   Millisecond end time    0
//        sort    int32   if = 1 it sorts results returned with old > new     -1

        Map<String, String> sArray = new HashMap<String, String>();
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
            int contentLength = con.getContentLength();
System.out.println("responseCode=" + responseCode + "; contentLength=" + contentLength);

            if (responseCode == HttpsURLConnection.HTTP_OK) {
                List<TickVolumeData> ticks = readAllTicks(con);
                return ticks;
            } else {
                throw new Exception("ERROR: unexpected ResponseCode: " + responseCode);
            }
        } finally {
            con.disconnect();
        }
    }

    protected static List<TickVolumeData> readAllTicks(HttpURLConnection con) throws IOException {
        PushbackInputStream pbis = new PushbackInputStream(new BufferedInputStream(con.getInputStream()));
        // [[49448138,1501893566000,-0.74891095,2863.8],[49448101,1501893563000,-0.65,2863.8],   ,[49447948,1501893542000,0.03284317,2864.9]]
        try {
            if (readChar(pbis, '[')) {
                List<TickVolumeData> ticks = new ArrayList<>();
                while (true) {
                    TickVolumeData tvd = readTick(pbis);
                    ticks.add(tvd);

                    int ch = pbis.read();
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
