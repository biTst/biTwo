package bi.two.exch.impl;

import bi.two.chart.TickVolumeData;
import bi.two.exch.BaseExchImpl;
import bi.two.util.Post;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.PushbackInputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class Bitfinex extends BaseExchImpl {
    public static void main(String[] args) {
//        MarketConfig.initMarkets();

        new Thread() {
            @Override public void run() {
                execute();
            }
        }.start();
    }

    private static void execute() {
        try {
            // https://bitfinex.readme.io/v2/reference#rest-public-trades
//        QUERY           PARAMS
//        limit   int32   Number of records       120
//        start   int32   Millisecond start time  0
//        end     int32   Millisecond end time    0
//        sort    int32   if = 1 it sorts results returned with old > new     -1

            Map<String, String> sArray = new HashMap<String, String>();
            sArray.put("limit", "10");
//            sArray.put("sort", "1");
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
                    readAll(con);
                } else {
                    throw new Exception("ERROR: unexpected ResponseCode: " + responseCode);
                }
            } finally {
                con.disconnect();
            }
        } catch (Exception e) {
            System.out.println("error: " + e);
            e.printStackTrace();
        }
    }

    protected static void readAll(HttpURLConnection con) throws IOException {
        PushbackInputStream pbis = new PushbackInputStream(new BufferedInputStream(con.getInputStream()));
        // [[49448138,1501893566000,-0.74891095,2863.8],[49448101,1501893563000,-0.65,2863.8],   ,[49447948,1501893542000,0.03284317,2864.9]]
        try {
            if (readChar(pbis, '[')) {
                while (true) {
                    TickVolumeData tvd = readTick(pbis);
                    System.out.println(" tick: " + tvd);

                    int ch = pbis.read();
                    if (ch == ']') { // EndOfArray
                        break;
                    }
                    if (ch != ',') {
                        throw new RuntimeException("expected ,");
                    }
                }
            } else {
                throw new RuntimeException("expected [");
            }
        } finally {
            pbis.close();
        }
    }

    private static TickVolumeData readTick(PushbackInputStream pbis) throws IOException {
        // [49448138,1501893566000,-0.74891095,2863.8]
        if(readChar(pbis, '[')) {
            skipDigits(pbis); // trade number ?
            if(readChar(pbis, ',')) {
                long millis = readLong(pbis);
                if(readChar(pbis, ',')) {
                    float size = readFloat(pbis);
                    if(readChar(pbis, ',')) {
                        float price = readFloat(pbis);
                        if(readChar(pbis, ']')) {
                            TickVolumeData tvd = new TickVolumeData(millis, price, size);
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
