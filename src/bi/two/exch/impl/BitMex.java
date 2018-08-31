package bi.two.exch.impl;

import bi.two.DataFileType;
import bi.two.chart.ITickData;
import bi.two.chart.TickData;
import bi.two.chart.TickPainter;
import bi.two.chart.TradeData;
import bi.two.exch.*;
import bi.two.exch.Currency;
import bi.two.main2.TicksCacheReader;
import bi.two.main2.TradesPreloader;
import bi.two.ts.TicksTimesSeriesData;
import bi.two.util.Hex;
import bi.two.util.Log;
import bi.two.util.MapConfig;
import bi.two.util.Utils;
import com.google.gson.*;
import org.apache.http.*;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.*;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.conn.ConnectionKeepAliveStrategy;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultConnectionKeepAliveStrategy;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HttpContext;
import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.client.ClientProperties;
import org.glassfish.tyrus.container.jdk.client.JdkClientContainer;
import org.jetbrains.annotations.NotNull;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.websocket.*;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

// based on info from
//  https://testnet.bitmex.com/app/wsAPI
//  https://testnet.bitmex.com/app/restAPI
//  https://testnet.bitmex.com/api/explorer/
// todo: look at https://github.com/ccxt/ccxt
//
// http rate limit - 1req/sec (300 requests per 5 minutes)  not logged in - ratelimit is 150/5minutes.
//
// Our Keep-Alive timeout is 90 seconds.
//
public class BitMex extends BaseExchImpl {
    public static final String ENDPOINT_HOST = "www.bitmex.com";
    //    public static final String ENDPOINT_HOST = "testnet.bitmex.com";

    private static final String WEB_SOCKET_URL = "wss://" + ENDPOINT_HOST + "/realtime"; // wss://www.bitmex.com/realtime
    private static final String CONFIG_FILE = "cfg\\bitmex.properties";
    private static final String API_KEY_KEY = "bitmex_apiKey";
    private static final String API_SECRET_KEY = "bitmex_apiSecret";
    private static final SimpleDateFormat TIMESTAMP_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    public static final String ORDER_ENDPOINT = "/api/v1/order";

    private static final String[] s_supportedCurrencies = new String[]{"xbt", "usd"};

    // debug
    private static final boolean LOG_HEADERS = false;
    private static final boolean LOG_HTTP = true;
    private static final boolean LOG_JSON_TABLE = false;

    // http actions
    private static final String GET = "GET";
    private static final String POST = "POST";
    private static final String DELETE = "DELETE";

    // order types
    public static final String ORDER_TYPE_LIMIT = "Limit";
    public static final String ORDER_TYPE_MARKET = "Market";

    public static final double SATO_DIVIDER = 100000000d;
    public static final int MAX_TRADE_HISTORY_LOAD_COUNT = 500;

    static {
        TIMESTAMP_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT"));
    }

    private Exchange m_exchange; // todo: move to parent
    private String m_apiKey;
    private String m_apiSecret;

    private Session m_session; // todo: create parent BaseWebServiceExch and move there ?
    private Thread m_pingThread;
    private Map<String,Currency> m_currencies = new HashMap<>();
    private Map<String,Pair> m_symbolToPairMap = new HashMap<>();
    private Map<Pair,String> m_pairToSymbolMap = new HashMap<>();
    private Map<String,OrderBook> m_orderBooks = new HashMap<>(); // todo: move to parent ?
    private Map<String, TopQuote> m_topQuotes = new HashMap<>(); // todo: move to parent ?
    private MarginToAccountModel m_marginAccount;

    private final RequestConfig m_requestConfig = RequestConfig.custom()
            .setSocketTimeout(1000)
            .setConnectTimeout(1000)
            .build();

    private final ConnectionKeepAliveStrategy m_keepAliveStrat = new DefaultConnectionKeepAliveStrategy() {
        @Override public long getKeepAliveDuration(HttpResponse response, HttpContext context) {
            long keepAlive = super.getKeepAliveDuration(response, context);
            if (keepAlive == -1) {
                // Keep connections alive 5 seconds if a keep-alive value has not be explicitly set by the server
                keepAlive = 5000;
            }
            return keepAlive;
        }
    };
    private boolean m_gotFirstMargin;
    private boolean m_gotFirstPosition;
    private double m_lastRateLimit; // http requests rate limit [1...0] close to 1 is FINE;  close to 0 is BAD - need pauses between requests

    @Override public int getMaxTradeHistoryLoadCount() { return MAX_TRADE_HISTORY_LOAD_COUNT; }

    public BitMex(Exchange exchange) {
        m_exchange = exchange;

        for (String name : s_supportedCurrencies) {
            String nameLower = name.toLowerCase();
            Currency byName = Currency.getByName(nameLower);
            m_currencies.put(nameLower, byName);
        }
    }

    public static List<TickData> readTicks(MapConfig config, long period) throws IOException {
        String exchangeName = config.getString("exchange");
        Exchange exchange = Exchange.get(exchangeName);
        String pairName = config.getString("pair");
        Pair pair = Pair.getByName(pairName);

        TicksTimesSeriesData<TickData> ticksTs = new TicksTimesSeriesData<TickData>(null);
        final TradesPreloader preloader = new TradesPreloader(exchange, pair, period, config, ticksTs) {
//            @Override protected void onTicksPreloaded() {
//                m_frame.repaint();
//            }
//            @Override protected void onLiveTick() {
//                m_frame.repaint(100);
//            }
        };

        preloader.playOnlyCache();

        List<TickData> ticks = ticksTs.getTicks();
        log("loaded from cache " + ticks.size() + " ticks");
        return ticks;
    }

    public void init(MapConfig config) {
        m_apiKey = config.getString(API_KEY_KEY);
        m_apiSecret = config.getString(API_SECRET_KEY);
    }


    private String pairToSymbol(Pair pair) {
        String symbol = m_pairToSymbolMap.get(pair);
        if (symbol == null) {
            symbol = pair.m_from.m_name.toUpperCase() + pair.m_to.m_name.toUpperCase(); // "XBTUSD"
            m_pairToSymbolMap.put(pair, symbol);
log("pairToSymbol pair=" + pair + "  => " + symbol);
        }
        return symbol;
    }

//    private static Pair symbolToPair(String symbol) {
//        Pair ret = Pair.getByName("btc_usd");
////log("symbolToPair symbol=" + symbol + "  => " + ret);
//        return ret; // todo
//    }

    public static void main(String[] args) {
        Log.s_impl = new Log.StdLog();
        console("main()");

        try {
            MapConfig config = new MapConfig();
            config.load(CONFIG_FILE);
            //config.loadAndEncrypted(file);

            Endpoint endpoint = new Endpoint() {
                @Override public void onOpen(final Session session, EndpointConfig config) {
                    console("onOpen() session=" + session + "; config=" + config);

                    session.addMessageHandler(new MessageHandler.Whole<String>() {
                        private boolean waitForFirstMessage = true;

                        @Override public void onMessage(String message) {
                            console("onMessage() message=" + message);
                            if (waitForFirstMessage) {
                                // getting this as first message
                                // {"info":"Welcome to the BitMEX Realtime API.","version":"1.2.0","timestamp":"2018-03-15T00:29:31.487Z","docs":"https://testnet.bitmex.com/app/wsAPI","limit":{"remaining":39}}

                                waitForFirstMessage = false;
                                // {"op": "subscribe", "args": [<SubscriptionTopic>]}
                                try {
                                    // can subscribe for several topics at the same time
                                    // {"op": "subscribe", "args": ["trade:XBTUSD","instrument:XBTUSD"]}

//                                    requestFullOrderBook(session);
//                                    requestLiveTrades(session);
//                                    requestInstrument(session);
//                                    requestOrderBook10(session);
//                                    requestQuote(session);
//                                    authenticate(session);

                                } catch (Exception e) {
                                    err("send error: " + e, e);
                                }
                            } else {
//                                onMessageX(session, message);
                            }
                        }
                    });
                }

                @Override public void onClose(Session session, CloseReason closeReason) {
                    console("onClose");
                    super.onClose(session, closeReason);
                }

                @Override public void onError(Session session, Throwable thr) {
                    console("onError");
                    super.onError(session, thr);
                }
            };

            console("connectToServer...");
            connectToServer(endpoint);

            Thread.sleep(TimeUnit.DAYS.toMillis(365));
            console("done");

        } catch (Exception e) {
            console("error: " + e);
            e.printStackTrace();
        }
    }

    private void onMessageX(Session session, String message) {
        try {
            console("<< " + message);

            if (message.equals("pong")) {
                return;
            }

            JSONParser parser = new JSONParser();
            JSONObject json = (JSONObject) parser.parse(message);
            log(" json=" + json);
            JSONObject request = (JSONObject) json.get("request");
            log(" request=" + request);
            if (request != null) {
                // {"success":true,                     "request":{"op":"authKey","args":["QGCD0OqdauJZ9LHbvBuq0tHE",1521898505226,"caa8ec1a5cecd030019c22f73a42a0774775653a9a21dd90395ffbafb9e87b98"]}}
                // {"success":true,"subscribe":"margin","request":{"op":"subscribe","args":["margin"]}}
                Boolean success = (Boolean) json.get("success");
                if (success != null) {
                    String subscribe = (String) json.get("subscribe");
                    log(" success=" + success + "; subscribe=" + subscribe);
                    if (success) {
                        if (subscribe != null) {
                            onSubscribed(session, subscribe, success, request);
                        } else {
                            String op = (String) request.get("op"); // operation
                            log("  op=" + op);
                            if (op.equals("authKey")) {
                                onAuthenticated(session, success);
                            } else {
                                console("ERROR: not supported message (op='" + op + "'): " + json);
                            }
                        }
                    } else {
                        console("ERROR: not subscribed: " + json);
                    }
                } else {
                    console("ERROR: not supported message (no success): " + json);
                }
            } else {
                String table = (String) json.get("table");
                if (table != null) {
                    if (table.equals("margin")) {
                        onMargin(session, json);
                    } else if (table.equals("trade")) {
                        onTrade(json);
                    } else if (table.equals("position")) {
                        onPosition(json);
                    } else if (table.equals("execution")) {
                        onExecution(json);
                    } else if (table.equals("order")) {
                        onOrder(json);
                    } else if (table.equals("orderBook10")) {
                        onOrderBook10(json);
                    } else if (table.equals("quote")) {
                        onQuote(json);
                    } else {
                        console("ERROR: not supported table='" + table + "' message: " + json);
                    }
                } else {
                    String info = (String) json.get("info");
                    String version = (String) json.get("version");
                    console("onConnected: info=" + info + "; version=" + version);
                    if ((info != null) && (version != null)) {
                        m_exchange.notifyConnected();
                        // getting this as first message
                        // {"info":"Welcome to the BitMEX Realtime API.","version":"1.2.0","timestamp":"2018-03-15T00:29:31.487Z","docs":"https://testnet.bitmex.com/app/wsAPI","limit":{"remaining":39}}
                        authenticate(session);
                    } else {
                        console("ERROR: not supported message: " + json);
                    }
                }
            }
        } catch (Exception e) {
            err("onMessageX ERROR: " + e, e);
        }
    }

    private static void onSubscribed(Session session, String topic, Boolean success, JSONObject request) throws IOException {
        console("  onSubscribed topic=" + topic + "; success=" + success);
//        if (op.equals("authKey")) {
//            onAuthenticated(session, success);
//        } else {
//            console("ERROR: not supported subscribe message (op='" + op + "'): request=" + request);
//        }
    }

    private void onAuthenticated(Session session, Boolean success) throws IOException {
        // {"success":true,
        //  "request":{"op":"authKey","args":["XXXXXXXXXXXXXXXXX",1521077672912,"YYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYY"]}}
        console("onAuthenticated success=" + success);

        if (success) {
            m_exchange.m_live = true; // mark as connected
            m_exchange.notifyAuthenticated();
        }
    }

    @Override public void queryAccount() throws Exception {
        // Updates on your current account balance and margin requirements
        m_gotFirstMargin = false;
        send(m_session, "{\"op\": \"subscribe\", \"args\": [\"margin\"]}");
        console("subscribed for margin");

        // Updates on your positions
        m_gotFirstPosition = false;
        send(m_session, "{\"op\": \"subscribe\", \"args\": [\"position\"]}");
        // {"success":true,"subscribe":"position","request":{"op":"subscribe","args":["position"]}}
        console("subscribed for position");

// todo: actually orders not needed for account calculations
        // Live updates on your orders
        send(m_session, "{\"op\": \"subscribe\", \"args\": [\"order\"]}");
        // {"success":true,"subscribe":"order","request":{"op":"subscribe","args":["order"]}}
        console("subscribed for order");

//        // Individual executions; can be multiple per order
//        send(m_session, "{\"op\": \"subscribe\", \"args\": [\"execution\"]}");
//        // {"success":true,"subscribe":"execution","request":{"op":"subscribe","args":["execution"]}}
//        console("subscribed for execution");

        startPingThread(m_session);
    }

    private void onMargin(final Session session, JSONObject json) throws Exception {
        // {"table":"margin",
        //  "keys":["account","currency"],
        //  "types":{"account":"long","currency":"symbol","riskLimit":"long","prevState":"symbol","state":"symbol","action":"symbol","amount":"long","pendingCredit":"long","pendingDebit":"long",
        //           "confirmedDebit":"long","prevRealisedPnl":"long","prevUnrealisedPnl":"long","grossComm":"long","grossOpenCost":"long","grossOpenPremium":"long","grossExecCost":"long","grossMarkValue":"long",
        //           "riskValue":"long","taxableMargin":"long","initMargin":"long","maintMargin":"long","sessionMargin":"long","targetExcessMargin":"long","varMargin":"long","realisedPnl":"long",
        //           "unrealisedPnl":"long", "indicativeTax":"long","unrealisedProfit":"long","syntheticMargin":"long","walletBalance":"long","marginBalance":"long","marginBalancePcnt":"float",
        //           "marginLeverage":"float","marginUsedPcnt":"float", "excessMargin":"long","excessMarginPcnt":"float","availableMargin":"long","withdrawableMargin":"long","timestamp":"timestamp",
        //           "grossLastValue":"long","commission":"float"},
        //  "foreignKeys":{},
        //  "attributes":{"account":"sorted","currency":"grouped"},
        //  "action":"partial",
        //  "data":[{"account":47464, "currency":"XBt", "riskLimit":1000000000000,
        //           "amount":20000000, "walletBalance":20000000,"marginBalance":20000000,  "excessMargin":20000000, "availableMargin":20000000, "withdrawableMargin":20000000,
        //           "marginBalancePcnt":1, "excessMarginPcnt":1,
        //           "prevState":"", "state":"", "action":"",
        //           "pendingCredit":0,"pendingDebit":0,"confirmedDebit":0,"prevRealisedPnl":0,"prevUnrealisedPnl":0,"grossComm":0,"grossOpenCost":0,"grossOpenPremium":0,"grossExecCost":0,"grossMarkValue":0,
        //           "riskValue":0,"taxableMargin":0,"initMargin":0,"maintMargin":0,"sessionMargin":0,"targetExcessMargin":0,"varMargin":0,"realisedPnl":0,"unrealisedPnl":0,"indicativeTax":0,"unrealisedProfit":0,
        //           "syntheticMargin":0, "marginLeverage":0,"marginUsedPcnt":0,
        //           "timestamp":"2018-03-15T14:30:18.300Z",
        //           "grossLastValue":0,"commission":null}],
        // "filter":{"account":47464},
        // "sendingTime":"2018-03-15T15:11:50.979Z"}
        //
        //
        // {"unrealisedPnl":76440,"maintMargin":184076,
        //  "grossMarkValue":9929115,"riskValue":9929115,"grossLastValue":9929115,
        //  "marginUsedPcnt":0.0092,
        //  "marginLeverage":0.4947504441690382,
        //  "currency":"XBt","account":47464,
        //  "marginBalance":20068936,"timestamp":"2018-07-20T00:16:40.184Z"}

        JSONArray data = (JSONArray) json.get("data");
        int size = data.size();
        console("onMargin() size=" + size + "; gotFirstMargin=" + m_gotFirstMargin + "; data=" + data);

        for (int i = 0; i < size; i++) {
            Object obj = data.get(i);
            JSONObject datum = (JSONObject) obj;
            console(" margin[" + i + "]=" + datum);
            String curr = (String) datum.get("currency");
            Long availableMargin = (Long) datum.get("availableMargin");

            Currency currency = m_currencies.get(curr.toLowerCase());
            console("  curr=" + curr + "; availableMargin=" + availableMargin + "; currency=" + currency);

            if (availableMargin != null) { // if changed
                double doubleValue = availableMargin / SATO_DIVIDER; // marginBalance in satoshi
                m_exchange.m_accountData.setAvailable(currency, doubleValue);
            }
        }

        logAccount();
        m_gotFirstMargin = true;
        notifyAccountListenerIfNeeded();
    }

    private void notifyAccountListenerIfNeeded() throws Exception {
        if (m_gotFirstMargin && m_gotFirstPosition) {
            m_exchange.notifyAccountListener();
        }
    }

    private void startPingThread(final Session session) {
        killPingThreadIfNeeded();
        m_pingThread = new Thread() {
            @Override public void run() {
                try {
                    while (!isInterrupted()) {
                        TimeUnit.SECONDS.sleep(20);
                        send(session, "ping");
                    }
                } catch (Exception e) {
                    err("ping error: " + e, e);
                }
                if (m_pingThread == this) {
                    m_pingThread = null;
                }
                log("ping thread finished: " + this);
            }
        };
        m_pingThread.setName("ping");
        m_pingThread.start();
        log("started ping thread: " + m_pingThread);
    }

    private void killPingThreadIfNeeded() {
        if (m_pingThread != null) {
            killPingThread();
        }
    }

    private void killPingThread() {
        console("killPingThread: " + m_pingThread);
        m_pingThread.interrupt();
    }

    private void authenticate(Session session) throws NoSuchAlgorithmException, InvalidKeyException, IOException {
        // -------------------------------------------------------------------------------------------------------------------------------------
        // signature is hex(HMAC_SHA256(secret, 'GET/realtime' + nonce))
        // nonce must be a number, not a string.
        // Note that an api-expires value is not yet supported. Use an increasing
        // value between 0 and 2^53.

        // // This is up to you, most use microtime but you may have your own scheme so long as it's increasing and doesn't repeat.
        // nonce = int(round(time.time() * 1000))
        // // See signature generation reference at https://www.bitmex.com/app/apiKeys
        // signature = bitmex_signature(API_SECRET, VERB, ENDPOINT, nonce)
        // Generates an API signature.
        // A signature is HMAC_SHA256(secret, verb + path + nonce + data), hex encoded.
        // Verb must be uppercased, url is relative, nonce must be an increasing 64-bit integer
        // and the data, if present, must be JSON without whitespace between keys.
        // message = (verb + path + str(nonce) + data).encode('utf-8')
        // signature = hmac.new(apiSecret.encode('utf-8'), message, digestmod=hashlib.sha256).hexdigest()

        long nounce = System.currentTimeMillis();
        String line = "GET/realtime" + nounce;
        String signature = hmacSHA256(line);

        send(session, "{\"op\": \"authKey\", \"args\": [\"" + m_apiKey + "\", " + nounce + ", \"" + signature + "\"]}");

        // {"success":true,
        //  "request":{"op":"authKey","args":["XXXXXXXXXXXXXXXXX",1521077672912,"YYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYY"]}}
    }

    @NotNull private String hmacSHA256(String line) throws NoSuchAlgorithmException, InvalidKeyException {
        SecretKeySpec keySpec = new SecretKeySpec(m_apiSecret.getBytes(), "HmacSHA256");
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(keySpec);
        byte[] hmacBytes = mac.doFinal(line.getBytes());
        return Hex.bytesToHexLowerCase(hmacBytes);
    }

    private void subscribeQuote(Session session, Pair pair) throws IOException {
        String symbol = pairToSymbol(pair);
        // Top level of the book
        send(session, "{\"op\": \"subscribe\", \"args\": [\"quote:" + symbol + "\"]}");

        // {"table":"quote",
        //  "keys":[],
        //  "types":{"timestamp":"timestamp","symbol":"symbol","bidSize":"long","bidPrice":"float","askPrice":"float","askSize":"long"},
        //  "foreignKeys":{"symbol":"instrument"},
        //  "attributes":{"timestamp":"sorted","symbol":"grouped"},
        //  "action":"partial",
        //  "data":[
        //   {"timestamp":"2018-03-15T01:13:38.638Z",
        //    "symbol":"XBTUSD",
        //    "bidSize":500,
        //    "bidPrice":8115,
        //    "askPrice":8122.5,
        //    "askSize":5669}
        //   ],
        //  "filter":{"symbol":"XBTUSD"}
        // }

        // {"success":true,
        //  "subscribe":"quote:XBTUSD",
        //  "request":{"op":"subscribe","args":["quote:XBTUSD"]}}

        // {"table":"quote",
        //  "action":"insert",
        //  "data":[
        //   {"timestamp":"2018-03-15T01:14:09.158Z",
        //    "symbol":"XBTUSD",
        //    "bidSize":500,
        //    "bidPrice":8115,
        //    "askPrice":8118.5,
        //    "askSize":140000},
        //   {"timestamp":"2018-03-15T01:14:09.376Z","symbol":"XBTUSD","bidSize":500,"bidPrice":8115,"askPrice":8118.5,"askSize":141002}
        //  ]
        // }
    }

    private void requestOrderBook10(Session session, Pair pair) throws IOException {
        String symbol = pairToSymbol(pair);

        // Top 10 levels using traditional full book push
        send(session, "{\"op\": \"subscribe\", \"args\": [\"orderBook10:" + symbol + "\"]}");

        // {"table":"orderBook10",
        //  "keys":["symbol"],
        //  "types":{"symbol":"symbol","bids":"","asks":"","timestamp":"timestamp"},
        //  "foreignKeys":{"symbol":"instrument"},
        //  "attributes":{"symbol":"sorted"},
        //  "action":"partial",
        //  "data":[
        //   {"symbol":"XBTUSD",
        //    "bids":[[8084,2395],[8083.5,500],[8075,826],[8073.5,100],[8069.5,834],[8065.5,5000],[8064.5,32839],[8061.5,30],[8061,825],[8060.5,330]],
        //    "asks":[[8100,6078],[8114.5,400],[8119.5,32839],[8127.5,935368],[8128,300],[8130,10000],[8132,30],[8134.5,831],[8135,10834],[8140,38864]],
        //    "timestamp":"2018-03-15T01:01:05.607Z"
        //   }],
        //  "filter":{"symbol":"XBTUSD"}
        // }

        // {"success":true,
        //  "subscribe":"orderBook10:XBTUSD",
        //  "request":{"op":"subscribe","args":["orderBook10:XBTUSD"]}}

        // {"table":"orderBook10",
        //  "action":"update",
        //  "data":[
        //   {"symbol":"XBTUSD",
        //    "asks":[[8100,6078],[8114.5,400],[8119.5,32839],[8127.5,935368],[8128,300],[8130,10000],[8132,30],[8134.5,831],[8135,10834],[8140,38913]],
        //    "timestamp":"2018-03-15T01:01:11.104Z",
        //    "bids":[[8084,2395],[8083.5,500],[8075,826],[8073.5,100],[8069.5,834],[8065.5,5000],[8064.5,32839],[8061.5,30],[8061,825],[8060.5,330]]
        //   }
        //  ]
        // }
    }

    private void requestFullOrderBook(Session session, Pair pair) throws IOException {
        String symbol = pairToSymbol(pair);
        // -------------------------------------------------------------------------------------------------------------------------------------
        // Full level 2 orderBook
        send(session, "{\"op\": \"subscribe\", \"args\": [\"orderBookL2:" + symbol + "\"]}");

        // {"success":true,
        //  "subscribe":"orderBookL2:XBTUSD",
        //  "request":{"op":"subscribe","args":["orderBookL2:XBTUSD"]}}

        // for orderBookL2:XBTUSD:
        // {"table":"orderBookL2",
        //  "keys":["symbol","id","side"],
        //  "types":{"symbol":"symbol","id":"long","side":"symbol","size":"long","price":"float"},
        //  "foreignKeys":{"symbol":"instrument","side":"side"},
        //  "attributes":{"symbol":"grouped","id":"sorted"},
        //  "action":"partial",
        //  "data":[
        //    {"symbol":"XBTUSD","id":15504648350,"side":"Sell","size":1191000,"price":953516.5},
        //    {"symbol":"XBTUSD","id":15588500000,"side":"Sell","size":900,"price":115000},
        //    ...
        //    {"symbol":"XBTUSD","id":15599999800,"side":"Buy","size":4,"price":2},
        //    {"symbol":"XBTUSD","id":15599999900,"side":"Buy","size":23,"price":1}
        //         ],
        //  "filter":{"symbol":"XBTUSD"}}
        //
        // {"table":"orderBookL2",
        //  "action":"update",
        //  "data":[
        //    {"symbol":"XBTUSD","id":15599178650,"side":"Sell","size":30}
        // ]}
        //
        // {"table":"orderBookL2",
        //  "action":"insert",
        //  "data":[
        //    {"symbol":"XBTUSD","id":15599178450,"side":"Sell","size":100,"price":8215.5}
        // ]}
        //
        // {"table":"orderBookL2",
        //  "action":"delete",
        //  "data":[
        //    {"symbol":"XBTUSD","id":15599184350,"side":"Buy"}
        // ]}
    }


    private void requestInstrument(Session session, Pair pair) throws IOException {
        String symbol = pairToSymbol(pair);

        // Instrument updates including turnover and bid/ask
        send(session, "{\"op\": \"subscribe\", \"args\": [\"instrument:" + symbol + "\"]}");

        // {"success":true,
        //  "subscribe":
        //  "instrument:XBTUSD",
        //  "request":{"op":"subscribe","args":["instrument:XBTUSD"]}}

        // {"table":"instrument",
        //  "keys":["symbol"],
        //  "types":{"symbol":"symbol","rootSymbol":"symbol","state":"symbol","typ":"symbol","listing":"timestamp","front":"timestamp","expiry":"timestamp","settle":"timestamp","relistInterval":"timespan",
        //           "inverseLeg":"symbol","sellLeg":"symbol","buyLeg":"symbol","optionStrikePcnt":"float","optionStrikeRound":"float","optionStrikePrice":"float","optionMultiplier":"float",
        //           "positionCurrency":"symbol","underlying":"symbol","quoteCurrency":"symbol","underlyingSymbol":"symbol","reference":"symbol","referenceSymbol":"symbol","calcInterval":"timespan",
        //           "publishInterval":"timespan","publishTime":"timespan","maxOrderQty":"long","maxPrice":"float","lotSize":"long","tickSize":"float","multiplier":"long","settlCurrency":"symbol",
        //           "underlyingToPositionMultiplier":"long","underlyingToSettleMultiplier":"long","quoteToSettleMultiplier":"long","isQuanto":"boolean","isInverse":"boolean","initMargin":"float",
        //           "maintMargin":"float","riskLimit":"long","riskStep":"long","limit":"float","capped":"boolean","taxed":"boolean","deleverage":"boolean","makerFee":"float","takerFee":"float",
        //           "settlementFee":"float","insuranceFee":"float","fundingBaseSymbol":"symbol","fundingQuoteSymbol":"symbol","fundingPremiumSymbol":"symbol","fundingTimestamp":"timestamp",
        //           "fundingInterval":"timespan","fundingRate":"float","indicativeFundingRate":"float","rebalanceTimestamp":"timestamp","rebalanceInterval":"timespan","openingTimestamp":"timestamp",
        //           "closingTimestamp":"timestamp","sessionInterval":"timespan","prevClosePrice":"float","limitDownPrice":"float","limitUpPrice":"float","bankruptLimitDownPrice":"float",
        //           "bankruptLimitUpPrice":"float","prevTotalVolume":"long","totalVolume":"long","volume":"long","volume24h":"long","prevTotalTurnover":"long","totalTurnover":"long","turnover":"long",
        //           "turnover24h":"long","prevPrice24h":"float","vwap":"float","highPrice":"float","lowPrice":"float","lastPrice":"float","lastPriceProtected":"float","lastTickDirection":"symbol",
        //           "lastChangePcnt":"float","bidPrice":"float","midPrice":"float","askPrice":"float","impactBidPrice":"float","impactMidPrice":"float","impactAskPrice":"float","hasLiquidity":"boolean",
        //           "openInterest":"long","openValue":"long","fairMethod":"symbol","fairBasisRate":"float","fairBasis":"float","fairPrice":"float","markMethod":"symbol","markPrice":"float",
        //           "indicativeTaxRate":"float","indicativeSettlePrice":"float","optionUnderlyingPrice":"float","settledPrice":"float","timestamp":"timestamp"},
        //  "foreignKeys":{"inverseLeg":"instrument","sellLeg":"instrument","buyLeg":"instrument"},
        //  "attributes":{"symbol":"grouped"},
        //  "action":"partial",
        //  "data":[
        //   {"symbol":"XBTUSD","rootSymbol":"XBT","state":"Open","typ":"FFWCSX","listing":"2016-05-04T12:00:00.000Z","front":"2016-05-04T12:00:00.000Z","expiry":null,"settle":null,"relistInterval":null,
        //    "inverseLeg":"","sellLeg":"","buyLeg":"","optionStrikePcnt":null,"optionStrikeRound":null,"optionStrikePrice":null,"optionMultiplier":null,"positionCurrency":"USD","underlying":"XBT",
        //    "quoteCurrency":"USD","underlyingSymbol":"XBT=","reference":"BMEX","referenceSymbol":".BXBT","calcInterval":null,"publishInterval":null,"publishTime":null,"maxOrderQty":10000000,"maxPrice":1000000,
        //    "lotSize":1,"tickSize":0.5,"multiplier":-100000000,"settlCurrency":"XBt","underlyingToPositionMultiplier":null,"underlyingToSettleMultiplier":-100000000,"quoteToSettleMultiplier":null,"isQuanto":false,
        //    "isInverse":true,"initMargin":0.01,"maintMargin":0.005,"riskLimit":20000000000,"riskStep":10000000000,"limit":null,"capped":false,"taxed":true,"deleverage":true,
        //    "makerFee":-0.00025,"takerFee":0.00075,
        //    "settlementFee":0,"insuranceFee":0,"fundingBaseSymbol":".XBTBON8H","fundingQuoteSymbol":".USDBON8H","fundingPremiumSymbol":".XBTUSDPI8H","fundingTimestamp":"2018-03-15T04:00:00.000Z",
        //    "fundingInterval":"2000-01-01T08:00:00.000Z","fundingRate":0.000936,"indicativeFundingRate":-0.001663,"rebalanceTimestamp":null,"rebalanceInterval":null,"openingTimestamp":"2018-03-15T00:00:00.000Z",
        //    "closingTimestamp":"2018-03-15T02:00:00.000Z","sessionInterval":"2000-01-01T02:00:00.000Z","prevClosePrice":8703.56,"limitDownPrice":null,"limitUpPrice":null,"bankruptLimitDownPrice":null,
        //    "bankruptLimitUpPrice":null,"prevTotalVolume":21161970891,"totalVolume":21169296049,"volume":7325158,"volume24h":273596678,"prevTotalTurnover":327244123427376,"totalTurnover":327334584642676,
        //    "turnover":90461215300,"turnover24h":3189886567890,"prevPrice24h":9413,"vwap":8577.0649,"highPrice":9750,"lowPrice":7880,"lastPrice":8130,"lastPriceProtected":8130,"lastTickDirection":"PlusTick",
        //    "lastChangePcnt":-0.1363,"bidPrice":8125,"midPrice":8127.5,"askPrice":8130,"impactBidPrice":8124.7969,"impactMidPrice":8136,"impactAskPrice":8147.3032,"hasLiquidity":true,"openInterest":65148459,
        //    "openValue":801912381831,"fairMethod":"FundingRate","fairBasisRate":1.02492,"fairBasis":2.96,"fairPrice":8123.96,"markMethod":"FairPrice","markPrice":8123.96,"indicativeTaxRate":0,
        //    "indicativeSettlePrice":8121,"optionUnderlyingPrice":null,"settledPrice":null,"timestamp":"2018-03-15T00:53:08.474Z"}],"filter":{"symbol":"XBTUSD"}}

        // {"table":"instrument",
        //  "action":"update",
        //  "data":[{"symbol":"XBTUSD","indicativeSettlePrice":8114.41,"timestamp":"2018-03-15T00:53:10.000Z"}]}
    }

    private void requestLiveTrades(Session session, Pair pair) throws IOException {
        String symbol = pairToSymbol(pair);
        // -------------------------------------------------------------------------------------------------------------------------------------
        // Live trades
        send(session, "{\"op\": \"subscribe\", \"args\": [\"trade:" + symbol + "\"]}");

        // {"success":true,
        //  "subscribe":"trade:XBTUSD",
        //  "request":{"op":"subscribe","args":["trade:XBTUSD"]}
        // }
    }

    private void onQuote(JSONObject json) throws Exception {
        // {"table":"quote",
        //  "action":"insert",
        //  "data":[
        //   {"timestamp":"2018-03-15T01:14:09.158Z",
        //    "symbol":"XBTUSD",
        //    "bidSize":500,
        //    "bidPrice":8115,
        //    "askPrice":8118.5,
        //    "askSize":140000},
        //   {"timestamp":"2018-03-15T01:14:09.376Z","symbol":"XBTUSD","bidSize":500,"bidPrice":8115,"askPrice":8118.5,"askSize":141002}
        //  ]
        // }

        JSONArray data = (JSONArray) json.get("data");
        int size = data.size();
        console(" onQuote: got " + size + " quotes");
        for (int i = 0; i < size; i++) {
            JSONObject obj = (JSONObject) data.get(i);
//            console("   [" + i + "]:" + obj);

            String symbol = (String) obj.get("symbol");
            Number bidSize = (Number) obj.get("bidSize");
            Number bidPrice = (Number) obj.get("bidPrice");
            Number askSize = (Number) obj.get("askSize");
            Number askPrice = (Number) obj.get("askPrice");
//            console("    symbol=" + symbol + "; bidSize=" + bidSize + "; bidPrice=" + bidPrice + "; askPrice=" + askPrice + "; askSize=" + askSize);

            TopQuote topQuote = m_topQuotes.get(symbol);
            if (topQuote != null) {
                topQuote.update(bidSize, bidPrice, askSize, askPrice);
            } else {
                throw new RuntimeException("no topQuote for symbol=" + symbol + "; available keys: " + m_topQuotes.keySet());
            }
        }
    }

    private void onOrderBook10(JSONObject json) throws Exception {
        // {"table":"orderBook10",
        //  "action":"partial",
        //  "keys":["symbol"],
        //  "types":{"symbol":"symbol","bids":"","asks":"","timestamp":"timestamp"},
        //  "foreignKeys":{"symbol":"instrument"},
        //  "attributes":{"symbol":"sorted"},
        //  "filter":{"symbol":"XBTUSD"},
        //  "data":[{"symbol":"XBTUSD",
        //           "bids":[[7682.5,7215],[7682,379],[7681.5,200],[7681,2799],[7680.5,200],[7680,299],[7679.5,967],[7679,299],[7678.5,200],[7678,299]],
        //           "asks":[[7683,220107],[7683.5,100],[7684,100],[7684.5,3468],[7685,2120],[7685.5,200],[7686,38594],[7686.5,200],[7687,114077],[7687.5,750]],
        //           "timestamp":"2018-07-24T00:55:38.260Z"}]}

        JSONArray data = (JSONArray) json.get("data");
        int size = data.size();
        console(" onOrderBook10: got " + size + " books");
        for (int i = 0; i < size; i++) {
            JSONObject obj = (JSONObject) data.get(i);
//            console("   [" + i + "]:" + obj);

            String symbol = (String) obj.get("symbol");
            JSONArray bids = (JSONArray) obj.get("bids");
            JSONArray asks = (JSONArray) obj.get("asks");
//            console("    symbol:" + symbol);
//            console("     bids:" + bids);
//            console("     asks:" + asks);

            // "symbol":"XBTUSD"    [[7682,379],[7679.5,967]]
            OrderBook orderBook = m_orderBooks.get(symbol);
            if (orderBook != null) {
                List<OrderBook.OrderBookEntry> aBids = parseBook(bids);
                List<OrderBook.OrderBookEntry> aAsks = parseBook(asks);

//                console(" input orderBook[" + orderBook.m_pair + "]: " + orderBook);
                orderBook.update(aBids, aAsks);
//                console(" updated orderBook[" + orderBook.m_pair + "]: " + orderBook.toString(2));
            } else {
                throw new RuntimeException("no orderBook for symbol=" + symbol + "; available keys: " + m_orderBooks.keySet());
            }
        }
    }

    private List<OrderBook.OrderBookEntry> parseBook(JSONArray jsonArray) { // [[7682,379],[7679.5,967]]
//        console("     parseBook:" + jsonArray);
        List<OrderBook.OrderBookEntry> ret = new ArrayList<>();
        for (Object obj : jsonArray) {
            JSONArray level = (JSONArray) obj;
            Number price = (Number) level.get(0);
            Number size = (Number) level.get(1);
            OrderBook.OrderBookEntry entry = new OrderBook.OrderBookEntry(price.doubleValue(), size.doubleValue());
            ret.add(entry);
//            console("      price=" + price + "; .class=" + price.getClass());
//            console("      size=" + size + "; .class=" + size.getClass());
        }
        return ret;
    }

    private void logAccount() {
        console("______accountData=" + m_exchange.m_accountData);
    }

    private void onExecution(JSONObject json) {
        // {"table":"execution",
        //  "action":"partial",
        //  "keys":["execID"],
        //  "types":{"execID":"guid","orderID":"guid","clOrdID":"symbol","clOrdLinkID":"symbol","account":"long","symbol":"symbol","side":"symbol","lastQty":"long",
        //           "lastPx":"float","underlyingLastPx":"float","lastMkt":"symbol","lastLiquidityInd":"symbol","simpleOrderQty":"float","orderQty":"long","price":"float",
        //           "displayQty":"long","stopPx":"float","pegOffsetValue":"float","pegPriceType":"symbol","currency":"symbol","settlCurrency":"symbol","execType":"symbol",
        //           "ordType":"symbol","timeInForce":"symbol","execInst":"symbol","contingencyType":"symbol","exDestination":"symbol","ordStatus":"symbol","triggered":"symbol",
        //           "workingIndicator":"boolean","ordRejReason":"symbol","simpleLeavesQty":"float","leavesQty":"long","simpleCumQty":"float","cumQty":"long","avgPx":"float",
        //           "commission":"float","tradePublishIndicator":"symbol","multiLegReportingType":"symbol","text":"symbol","trdMatchID":"guid","execCost":"long","execComm":"long",
        //           "homeNotional":"float","foreignNotional":"float","transactTime":"timestamp","timestamp":"timestamp"},
        //  "foreignKeys":{"symbol":"instrument","side":"side","ordStatus":"ordStatus"},
        //  "attributes":{"execID":"grouped","account":"grouped","execType":"grouped","transactTime":"sorted"},
        //  "filter":{"account":47464},
        //  "data":[]
        // }

        // Symbol	Qty     Exec Qty	Remaining	Exec Price	Price	Value	    Type	OrderID	    Time
        // XBTUSD	100	    -100	    0	        7300.0	    Market	0.0136 XBT	Market	52a088c	    Jul 21, 2018, 2:24:13 AM

        // {"symbol":"XBTUSD","triggered":"","clOrdLinkID":"","execInst":"","homeNotional":null,"pegOffsetValue":null,"pegPriceType":"","execID":"7af484fd-6be7-8c60-95d6-6ee81f0c946b",
        // "orderQty":100, "leavesQty":100, "cumQty":0, "price":7300, "ordType":"Market","currency":"USD", "side":"Sell"
        // "contingencyType":"","foreignNotional":null,"lastMkt":"","simpleCumQty":0,"execCost":null,"execComm":null,"settlCurrency":"XBt","ordRejReason":"",
        // "trdMatchID":"00000000-0000-0000-0000-000000000000", "commission":null,"text":"Submission from testnet.bitmex.com","execType":"New",
        // "timeInForce":"ImmediateOrCancel","timestamp":"2018-07-20T23:24:13.935Z","ordStatus":"New","simpleOrderQty":null,"orderID":"52a088c2-e9c2-276d-d8ca-bb2208a946ac",
        // "lastPx":null, "tradePublishIndicator":"","displayQty":null,"simpleLeavesQty":0.0137,"clOrdID":"","lastQty":null,"avgPx":null,
        // "multiLegReportingType":"SingleSecurity","workingIndicator":true,"lastLiquidityInd":"","transactTime":"2018-07-20T23:24:13.935Z","exDestination":"XBME","account":47464,
        // "underlyingLastPx":null,"stopPx":null}

        JSONArray data = (JSONArray) json.get("data");
        int size = data.size();
        console("  got " + size + " executions");
        for (int i = 0; i < size; i++) {
            JSONObject obj = (JSONObject) data.get(i);
            console("   [" + i + "]:" + obj);

            Long orderQty = (Long) obj.get("orderQty");
            Long cumQty = (Long) obj.get("cumQty");
            Long leavesQty = (Long) obj.get("leavesQty");
            Long price = (Long) obj.get("price");
            String ordType = (String) obj.get("ordType");
            String currency = (String) obj.get("currency");
            String side = (String) obj.get("side");
            console("    side" + side + "; orderQty=" + orderQty + "; currency=" + currency + "; cumQty=" + cumQty + "; leavesQty=" + leavesQty + "; price=" + price + "; ordType=" + ordType );
        }
    }

    private void onPosition(JSONObject json) throws Exception {
        // {"table":"position",
        //  "action":"partial",
        //  "keys":["account","symbol","currency"],
        //  "types":{"account":"long","symbol":"symbol","currency":"symbol","underlying":"symbol","quoteCurrency":"symbol","commission":"float","initMarginReq":"float","maintMarginReq":"float","riskLimit":"long","leverage":"float","crossMargin":"boolean","deleveragePercentile":"float","rebalancedPnl":"long","prevRealisedPnl":"long","prevUnrealisedPnl":"long","prevClosePrice":"float","openingTimestamp":"timestamp","openingQty":"long","openingCost":"long","openingComm":"long","openOrderBuyQty":"long","openOrderBuyCost":"long","openOrderBuyPremium":"long","openOrderSellQty":"long","openOrderSellCost":"long","openOrderSellPremium":"long","execBuyQty":"long","execBuyCost":"long","execSellQty":"long","execSellCost":"long","execQty":"long","execCost":"long","execComm":"long","currentTimestamp":"timestamp","currentQty":"long","currentCost":"long","currentComm":"long","realisedCost":"long","unrealisedCost":"long","grossOpenCost":"long","grossOpenPremium":"long","grossExecCost":"long","isOpen":"boolean","markPrice":"float","markValue":"long","riskValue":"long","homeNotional":"float","foreignNotional":"float","posState":"symbol","posCost":"long","posCost2":"long","posCross":"long","posInit":"long","posComm":"long","posLoss":"long","posMargin":"long","posMaint":"long","posAllowance":"long","taxableMargin":"long","initMargin":"long","maintMargin":"long","sessionMargin":"long","targetExcessMargin":"long","varMargin":"long","realisedGrossPnl":"long","realisedTax":"long","realisedPnl":"long","unrealisedGrossPnl":"long","longBankrupt":"long","shortBankrupt":"long","taxBase":"long","indicativeTaxRate":"float","indicativeTax":"long","unrealisedTax":"long","unrealisedPnl":"long","unrealisedPnlPcnt":"float","unrealisedRoePcnt":"float","simpleQty":"float","simpleCost":"float","simpleValue":"float","simplePnl":"float","simplePnlPcnt":"float","avgCostPrice":"float","avgEntryPrice":"float","breakEvenPrice":"float","marginCallPrice":"float","liquidationPrice":"float","bankruptPrice":"float","timestamp":"timestamp","lastPrice":"float","lastValue":"long"},
        //  "foreignKeys":{"symbol":"instrument"},
        //  "attributes":{"account":"sorted","symbol":"grouped","currency":"grouped","underlying":"grouped","quoteCurrency":"grouped"},
        //  "filter":{"account":47464},
        //  "data":[{"account":47464,
        //           "symbol":"XBTUSD", "currency":"XBt", "underlying":"XBT",
        //           "quoteCurrency":"USD",
        //           "commission":0.00075,
        //           "initMarginReq":0.01,
        //           "maintMarginReq":0.005,
        //           "riskLimit":20000000000,
        //           "leverage":100,
        //           "crossMargin":true,
        //           "deleveragePercentile":1,
        //           "rebalancedPnl":0, "prevRealisedPnl":0, "prevUnrealisedPnl":0,
        //           "prevClosePrice":7433.83, "markPrice":7454.76, "lastPrice":7454.76,
        //           "openingQty":735, "currentQty":735, "simpleCost":735,
        //           "openingCost":-10005555, "posCost":-10005555, "posCost2":-10005555,
        //           "openingComm":2115, "currentComm":2115,
        //           "openOrderBuyQty":0, "openOrderBuyCost":0, "openOrderBuyPremium":0, "openOrderSellQty":0, "openOrderSellCost":0, "openOrderSellPremium":0,
        //           "execBuyQty":0, "execBuyCost":0, "execSellQty":0, "execSellCost":0 ,"execQty":0, "execCost":0, "execComm":0, "realisedCost":0, "grossOpenCost":0,
        //           "grossOpenPremium":0,"grossExecCost":0, "posCross":0, "posLoss":0, "posAllowance":0, "taxableMargin":0,"initMargin":0, "sessionMargin":0,
        //           "targetExcessMargin":0,"varMargin":0,"realisedGrossPnl":0,"realisedTax":0, "longBankrupt":0,"shortBankrupt":0, "indicativeTaxRate":0,"indicativeTax":0,"unrealisedTax":0,
        //           "currentCost":-10005555, "unrealisedCost":-10005555,
        //           "isOpen":true,
        //           "markValue":-9859290, "lastValue":-9859290,
        //           "riskValue":9859290,
        //           "homeNotional":0.0985929,
        //           "foreignNotional":-735,
        //           "posState":"",
        //           "posInit":100056,
        //           "posComm":7580,
        //           "posMargin":107636,
        //           "posMaint":57608,
        //           "maintMargin":253901,
        //           "realisedPnl":-2115,
        //           "unrealisedGrossPnl":146265, "unrealisedPnl":146265, "taxBase":146265,
        //           "unrealisedPnlPcnt":0.0146,
        //           "unrealisedRoePcnt":1.4618,
        //           "simpleQty":0.1,
        //           "simpleValue":746,
        //           "simplePnl":11,
        //           "simplePnlPcnt":0.015,
        //           "avgCostPrice":7346, "avgEntryPrice":7346, "breakEvenPrice":7348,
        //           "marginCallPrice":2456, "liquidationPrice":2456, "bankruptPrice":2452,
        //           "openingTimestamp":"2018-07-20T10:00:00.000Z", "currentTimestamp":"2018-07-20T10:16:35.063Z", "timestamp":"2018-07-20T10:16:35.063Z",
        // }]}

        JSONArray data = (JSONArray) json.get("data");
        int size = data.size();
        console(" onPosition() size=" + size);
        for (int i = 0; i < size; i++) {
            JSONObject obj = (JSONObject) data.get(i);
            console("  [" + i + "]:" + obj);

            String symbol = (String) obj.get("symbol");
            Long currentQty = (Long) obj.get("currentQty");
            Long openOrderSellQty = (Long) obj.get("openOrderSellQty"); // usd
            Long openOrderSellCost = (Long) obj.get("openOrderSellCost"); // sato
            Number openOrderBuyQty = (Number) obj.get("openOrderBuyQty"); // usd
            Long openOrderBuyCost = (Long) obj.get("openOrderBuyCost"); // sato
            Pair pairByName = getPairFromSymbol(symbol);
            Currency from = pairByName.m_from;
            Currency to = pairByName.m_to;
            console("   symbol=" + symbol + "; currentQty=" + currentQty
                    + "; openOrderBuyQty=" + openOrderBuyQty + "; openOrderBuyCost=" + openOrderBuyCost
                    + "; openOrderSellQty=" + openOrderSellQty + "; openOrderSellCost=" + openOrderSellCost
                    + "; pairByName=" + pairByName + "; from=" + from + "; to=" + to);

            if (currentQty > 0) {
                throw new RuntimeException("not supported positive position: " + currentQty);
            }

            // Positions
            // Symbol	Size	Value	    Entry Price	Mark Price	Liq. Price	    Margin	            Unrealised PNL (ROE %)	Realised PNL
            // XBTUSD   -733    0.1000 XBT  7344.00	    7328.87	    100000000.0     +0.1000 XBT (1.00x) 0.0002 XBT (0.21%)      0.0062 XBT

            // "action":"partial"
            // {"symbol":"XBTUSD","lastValue":10002518,"breakEvenPrice":7337.5,"avgCostPrice":7344,"posLoss":0,"openOrderSellQty":760,"avgEntryPrice":7344,"taxBase":681276,
            // "foreignNotional":733,  "simpleCost":-733,  "currentQty":-733,
            // "execComm":54078,"riskLimit":20000000000,"prevUnrealisedPnl":0,"longBankrupt":0,"marginCallPrice":100000000,
            // "unrealisedCost":9981261,"posComm":0,"posMaint":0,"simplePnlPcnt":0.002,"execSellCost":19960323,"realisedCost":-660019,"posInit":9981261,"grossExecCost":0,
            // "posAllowance":0,"targetExcessMargin":0,"shortBankrupt":0,"indicativeTax":0,"maintMargin":10002518,"riskValue":20002598,"execBuyCost":20470860,
            // "grossOpenPremium":0,"currentCost":9321242,"indicativeTaxRate":0,"underlying":"XBT","quoteCurrency":"USD","initMarginReq":1,"isOpen":true,"posCross":0,
            // "currentTimestamp":"2018-07-20T21:17:10.053Z","simpleValue":-731.5,"prevClosePrice":7341.78,"unrealisedPnlPcnt":0.0021,
            // "execQty":37,"taxableMargin":0,"openingCost":9831779,"realisedGrossPnl":660019,"leverage":1,"posState":"","openOrderSellPremium":0,"simpleQty":-0.0998,"openingQty":-770,
            // "homeNotional":-0.10002518,"liquidationPrice":100000000,"openOrderBuyQty":0,"unrealisedPnl":21257,"execCost":-510537,"unrealisedGrossPnl":21257,"markPrice":7328.4,
            // "posMargin":9981261,"unrealisedTax":0,"crossMargin":false,"deleveragePercentile":1,"openOrderBuyCost":0,"posCost":9981261,"currency":"XBt","commission":7.5E-4,
            // "sessionMargin":0,"maintMarginReq":0.005,"bankruptPrice":100000000,"openOrderSellCost":-10000080,"markValue":10002518,"timestamp":"2018-07-20T21:17:10.053Z",
            // "realisedPnl":622889,"varMargin":0,"realisedTax":0,"rebalancedPnl":-630374,"openOrderBuyPremium":0,"posCost2":9981261,"openingTimestamp":"2018-07-20T20:00:00.000Z",
            // "currentComm":37130,"execSellQty":1466,"grossOpenCost":10000080,"prevRealisedPnl":447714,"execBuyQty":1503,"initMargin":10022581,
            // "unrealisedRoePcnt":0.0021,"simplePnl":1.5,"account":47464,"openingComm":-16948,"lastPrice":7328.4}


            // Symbol	Size	Value	    Entry Price	Mark Price	Liq. Price	    Margin	            Unrealised PNL (ROE %)	Realised PNL
            // XBTUSD   -844    0.1152 XBT  7337.30	    7322.25	    100000000.0     +0.1152 XBT (1.00x) 0.0002 XBT (0.21%)      0.0000 XBT

            // {"symbol":"XBTUSD","lastValue":11510472,"breakEvenPrice":7331,"avgCostPrice":7337.2955,"posLoss":0,"openOrderSellQty":0,"avgEntryPrice":7337.2955,"taxBase":667642,
            // "foreignNotional":844,  "simpleCost":-844,  "currentQty":-844,
            // "execComm":1141,"riskLimit":20000000000,"prevUnrealisedPnl":0,"longBankrupt":0,"marginCallPrice":100000000,"unrealisedCost":11502849,"posComm":0,
            // "posMaint":0,"simplePnlPcnt":6.0E-4,"execSellCost":1521588,"realisedCost":-660019,"posInit":11502849,"grossExecCost":1521588,"posAllowance":0,"targetExcessMargin":0,
            // "shortBankrupt":0,"indicativeTax":0,"maintMargin":11510472,"riskValue":11510472,"execBuyCost":0,"grossOpenPremium":0,"currentCost":10842830,"indicativeTaxRate":0,
            // "underlying":"XBT","quoteCurrency":"USD","initMarginReq":1,"isOpen":true,"posCross":0,"currentTimestamp":"2018-07-20T23:08:20.055Z","simpleValue":-843.5,"prevClosePrice":7285.57,
            // "unrealisedPnlPcnt":7.0E-4,"execQty":-111,"taxableMargin":0,"openingCost":9321242,"realisedGrossPnl":660019,"leverage":1,"posState":"","openOrderSellPremium":0,
            // "simpleQty":-0.115,"openingQty":-733,"homeNotional":-0.11510472,"liquidationPrice":100000000,"openOrderBuyQty":0,"unrealisedPnl":7623,"execCost":1521588,"unrealisedGrossPnl":7623,
            // "markPrice":7332.19,"posMargin":11502849,"unrealisedTax":0,"crossMargin":false,"deleveragePercentile":1,"openOrderBuyCost":0,"posCost":11502849,"currency":"XBt","commission":7.5E-4,
            // "sessionMargin":0,"maintMarginReq":0.005,"bankruptPrice":100000000,"openOrderSellCost":0,"markValue":11510472,"timestamp":"2018-07-20T23:08:20.055Z","realisedPnl":621748,
            // "varMargin":0,"realisedTax":0,"rebalancedPnl":-630374,"openOrderBuyPremium":0,"posCost2":11502849,"openingTimestamp":"2018-07-20T22:00:00.000Z",
            // "currentComm":38271,"execSellQty":111,"grossOpenCost":0,"prevRealisedPnl":447714,"execBuyQty":0,"initMargin":0,"unrealisedRoePcnt":7.0E-4,"simplePnl":0.5,"account":47464,
            // "openingComm":37130,"lastPrice":7332.19}

            // "action":"update"
            // {"account":47464,"symbol":"XBTUSD","currency":"XBt","currentTimestamp":"2018-07-21T11:38:25.054Z","markPrice":7297.83,"timestamp":"2018-07-21T11:38:25.054Z",
            //  "lastPrice":7297.83,"currentQty":-1055,"simpleQty":-0.144,"liquidationPrice":4166666.5}]}

            m_exchange.m_accountData.setAvailable(to, -currentQty);

            if (openOrderSellCost != null) {
                double allocatedFrom = -openOrderSellCost / SATO_DIVIDER;
                log("_____allocatedFrom=" + allocatedFrom);
                m_exchange.m_accountData.setAllocated(from, allocatedFrom);
            }

            if (openOrderBuyQty != null) {
                double allocatedTo = openOrderBuyQty.doubleValue();
                log("_____allocatedTo=" + allocatedTo);
                m_exchange.m_accountData.setAllocated(to, allocatedTo);
            }

            logAccount();
        }

        m_gotFirstPosition = true;
        notifyAccountListenerIfNeeded();
    }

    private Pair getPairFromSymbol(String symbol) {
        Pair pair = m_symbolToPairMap.get(symbol);
        if (pair == null) {
            String from = symbol.substring(0, 3).toLowerCase();
            String to = symbol.substring(3, 6).toLowerCase();
            String key = from + '_' + to;
            Pair byName = Pair.getByName(key);
            m_symbolToPairMap.put(symbol, byName);
            pair = byName;
        }
        return pair;
    }

    private void onTrade(JSONObject json) throws ParseException {
        // {"table":"trade",
        //  "action":"partial",
        //  "keys":[],
        //  "types":{"timestamp":"timestamp","symbol":"symbol","side":"symbol","size":"long","price":"float","tickDirection":"symbol","trdMatchID":"guid","grossValue":"long","homeNotional":"float","foreignNotional":"float"},
        //  "foreignKeys":{"symbol":"instrument","side":"side"},
        //  "attributes":{"timestamp":"sorted","symbol":"grouped"},
        //  "data":[
        //   {"symbol":"XBTUSD",
        //    "side":"Buy",
        //    "size":3000, "foreignNotional":3000
        //    "price":8121,
        //    "grossValue":36942000,
        //    "homeNotional":0.36942,
        //    "timestamp":"2018-03-15T00:47:51.389Z", "tickDirection":"MinusTick", "trdMatchID":"92658e51-e4b5-3378-254b-ec2a85cb9a9e",
        //    }],
        //  "filter":{"symbol":"XBTUSD"}
        // }

        // {"table":"trade",
        //  "action":"insert",
        //  "data":[
        //   {"symbol":"XBTUSD",
        //    "side":"Buy",
        //    "size":229,  "foreignNotional":229
        //    "price":8120,
        //    "grossValue":2820135,
        //    "homeNotional":0.02820135,
        //    "timestamp":"2018-03-15T00:47:57.027Z", "tickDirection":"MinusTick", "trdMatchID":"f47a0a0f-0067-db07-c551-4f71da87d5ed",
        //   }]}

        String symbol = null;
        ExchPairData.TradesData trades = null;
        JSONArray data = (JSONArray) json.get("data");
        int size = data.size();
        console("  got " + size + " trades");
        for (int i = 0; i < size; i++) {
            JSONObject obj = (JSONObject) data.get(i);
            log("   [" + i + "]:" + obj);
            TradeData td = parseTrade(obj);
            String s = (String) obj.get("symbol");
            if (!Utils.equals(s, symbol)) {
                symbol = (String) obj.get("symbol");
                Pair pair = getPairFromSymbol(symbol);
                ExchPairData pairData = m_exchange.getPairData(pair);
                trades = pairData.getTrades();
            }
            trades.onTrade(td);
        }
    }

    private TradeData parseTrade(JSONObject obj) throws ParseException {
        //   {"symbol":"XBTUSD",
        //    "side":"Buy",
        //    "price":8120,
        //    "size":229,  "foreignNotional":229  <in XBT>
        //    "grossValue":2820135, <in satoshi>  "homeNotional":0.02820135, <in BTC>
        //    "timestamp":"2018-03-15T00:47:57.027Z", "tickDirection":"MinusTick", "trdMatchID":"f47a0a0f-0067-db07-c551-4f71da87d5ed",
        //   }

        String side = (String) obj.get("side");
        Number size = (Number) obj.get("homeNotional");
        Number price = (Number) obj.get("price");
        String timestampStr = (String) obj.get("timestamp");

        boolean isBuy = side.equals("Buy");
        OrderSide orderSide = OrderSide.get(isBuy);
        Date date = TIMESTAMP_FORMAT.parse(timestampStr);
        long timestamp = date.getTime();
        console("    side=" + side + "; size=" + size + "; price=" + price + "; timestampStr=" + timestampStr + "; isBuy=" + isBuy + "; orderSide=" + orderSide + "; date=" + date + "; timestamp=" + timestamp);

        return new TradeData(timestamp, price.floatValue(), size.floatValue(), orderSide);
    }

    private static void connectToServer(Endpoint endpoint) throws DeploymentException, IOException, URISyntaxException {
        ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

        // latest libs adds Origin="http://ws.cex.io" header to initial request
        // cex.io server returns with {"ok":"error","data":{"error":"Incorrect origin in request header."}}
        // removing "Origin" header fixes the problem
//        ClientEndpointConfig cec = ClientEndpointConfig.Builder.create()
//                .configurator(new ClientEndpointConfig.Configurator() {
//                    @Override public void beforeRequest(Map<String, List<String>> headers) {
//                        headers.remove("Origin");
//                    }
//                    @Override public void afterResponse(HandshakeResponse hr) { /*noop*/ }
//                })
//                .build();

        // works with compile 'org.glassfish.tyrus.bundles:tyrus-standalone-client:1.13.1'
        // ClientManager client = ClientManager.createClient();
        // works with compile 'org.glassfish.tyrus:tyrus-container-jdk-client:1.13.1'
        ClientManager client = ClientManager.createClient(JdkClientContainer.class.getName());

        client.getProperties().put(ClientProperties.RECONNECT_HANDLER, new ReconnectHandler());

        client.connectToServer(endpoint, cec, new URI(WEB_SOCKET_URL));
    }

    private static void send(Session session, String str) throws IOException {
        console(">> send: " + str);
//        m_rateLimiter.enter();
        RemoteEndpoint.Basic basicRemote = session.getBasicRemote();
        basicRemote.sendText(str);
    }

    @Override public void subscribeTrades(ExchPairData.TradesData tradesData) throws IOException {
        requestLiveTrades(m_session, tradesData.m_pair);
    }

    @Override public void connect(Exchange.IExchangeConnectListener listener) throws Exception {
        m_exchange.m_connectListener = listener;

        Endpoint endpoint = new Endpoint() {
            @Override public void onOpen(final Session session, EndpointConfig config) {
                log("Endpoint.onOpen");
                try {
                    m_session = session;
                    session.addMessageHandler(new MessageHandler.Whole<String>() {
                        @Override public void onMessage(String message) {
                            onMessageX(session, message);
                        }
                    });
                } catch (Exception e) {
                    err("Endpoint.onOpen ERROR: " + e, e);
                }
            }

            @Override public void onClose(Session session, CloseReason closeReason) {
                console("Endpoint.onClose: " + closeReason);

                m_exchange.m_live = false; // mark as disconnected

                killPingThreadIfNeeded();

//                m_exchange.m_threadPool.submit(new Runnable() {
//                    @Override public void run() {
                        m_exchange.onDisconnected();
                        m_exchange.notifyDisconnected();
//                    }
//                });
            }

            @Override public void onError(Session session, Throwable thr) {
                err("Endpoint.onError: " + thr, thr);
            }
        };
        console("connectToServer...");
        connectToServer(endpoint);
        console("session isOpen=" + m_session.isOpen());
    }

    @Override public void subscribeTopQuote(TopQuote topQuote) throws Exception {
        Pair pair = topQuote.m_pair;
        String key = pairToSymbol(pair);
        console("subscribeTopQuote() pair=" + pair + "; key=" + key);

        m_topQuotes.put(key, topQuote);

        subscribeQuote(m_session, pair);
    }

    @Override public void subscribeOrderBook(OrderBook orderBook, int depth) throws Exception {
        if (depth != 10) {
            throw new RuntimeException("subscribeOrderBook error: unsupported depth=" + depth);
        }

        Pair pair = orderBook.m_pair;
        String key = pairToSymbol(pair);
        console("subscribeOrderBook() pair=" + pair + "; key=" + key);

        m_orderBooks.put(key, orderBook);

        requestOrderBook10(m_session, pair);
    }

    @Override public void submitOrder(OrderData orderData) throws Exception {
        log("BitMex.submitOrder() orderData=" + orderData);
        String orderSize = orderData.formatSize(orderData.m_amount);
        String orderPrice = orderData.formatPrice(orderData.m_price);
        OrderType orderType = orderData.m_type;
        OrderSide orderSide = orderData.m_side;
        String clientOrderId = orderData.m_clientOrderId;
        log(" clientOrderId=" + clientOrderId + "; orderType=" + orderType + "; orderSize=" + orderSize + "; orderPrice=" + orderPrice + "; orderSide=" + orderSide);
        if (clientOrderId == null) {
            throw new RuntimeException("no clientOrderId. orderData=" + orderData);
        }
        orderData.m_submitTime = System.currentTimeMillis();
        placeOrder(clientOrderId, orderData.m_pair, orderType, orderSize, orderPrice, orderSide);
        orderData.m_status = OrderStatus.SUBMITTED;
        orderData.notifyListeners();
    }

    private void placeOrder(String clientOrderId, Pair pair, OrderType orderType, String orderSize, String orderPrice, OrderSide orderSide) throws Exception {
        // curl -X POST
        // --header 'Content-Type: application/x-www-form-urlencoded'
        // --header 'Accept: application/json'
        // --header 'X-Requested-With: XMLHttpRequest'
        // -d 'symbol=XBTUSD&side=Sell&simpleOrderQty=0.05&clOrdID=my_clOrdID&ordType=Market' 'https://testnet.bitmex.com/api/v1/order'

        console("placeOrder() pair=" + pair + "; orderType=" + orderType + "; orderSize=" + orderSize + "; orderPrice=" + orderPrice + "; orderSide=" + orderSide + "; clientOrderId=" + clientOrderId);

        String symbol = pairToSymbol(pair);
        String side = orderSide.isBuy() ? "Buy" : "Sell";
        boolean isMarket = (orderType == OrderType.MARKET);
        String type = isMarket ? ORDER_TYPE_MARKET : ORDER_TYPE_LIMIT;
        log(" symbol=" + symbol + "; side=" + side + "; isMarket=" + isMarket + "; type=" + type);

        List<NameValuePair> nvps = new ArrayList<>();
        nvps.add(new BasicNameValuePair("symbol", symbol));
        nvps.add(new BasicNameValuePair("side", side));
        nvps.add(new BasicNameValuePair("simpleOrderQty", orderSize));
        nvps.add(new BasicNameValuePair("clOrdID", clientOrderId));
        nvps.add(new BasicNameValuePair("ordType", type));
        if (!isMarket) { // LMT
            nvps.add(new BasicNameValuePair("price", orderPrice));
        }
console("  nvps=" + nvps);

        JsonElement json = loadJson(POST, ORDER_ENDPOINT, nvps);
console("   placeOrder json=" + json);
    }

    private void onOrder(JSONObject json) throws Exception {
        // {"table":"order",
        //  "action":"partial",
        //  "keys":["orderID"],
        //  "types":{"orderID":"guid","clOrdID":"symbol","clOrdLinkID":"symbol","account":"long","symbol":"symbol","side":"symbol","simpleOrderQty":"float","orderQty":"long",
        //           "price":"float","displayQty":"long","stopPx":"float","pegOffsetValue":"float","pegPriceType":"symbol","currency":"symbol","settlCurrency":"symbol",
        //           "ordType":"symbol","timeInForce":"symbol","execInst":"symbol","contingencyType":"symbol","exDestination":"symbol","ordStatus":"symbol","triggered":"symbol",
        //           "workingIndicator":"boolean","ordRejReason":"symbol","simpleLeavesQty":"float","leavesQty":"long","simpleCumQty":"float","cumQty":"long","avgPx":"float",
        //           "multiLegReportingType":"symbol","text":"symbol","transactTime":"timestamp","timestamp":"timestamp"},
        //  "foreignKeys":{"symbol":"instrument","side":"side","ordStatus":"ordStatus"},
        //  "attributes":{"orderID":"grouped","account":"grouped","ordStatus":"grouped","workingIndicator":"grouped"},
        //  "filter":{"account":47464},
        //  "data":[]
        // }

        // {"symbol":"XBTUSD","triggered":"","clOrdLinkID":"","execInst":"","pegOffsetValue":null,"pegPriceType":"","contingencyType":"","simpleCumQty":0,
        //  "settlCurrency":"XBt","ordRejReason":"","price":7511,"orderQty":111,"currency":"USD","text":"Submission from testnet.bitmex.com","timeInForce":"GoodTillCancel",
        //  "timestamp":"2018-07-21T11:48:31.595Z","ordStatus":"New","side":"Sell","simpleOrderQty":null,"orderID":"470d8f33-e6b6-14e9-850f-49f6cc696b97","leavesQty":111,
        //  "cumQty":0,"displayQty":null,"simpleLeavesQty":0.0148,"clOrdID":"","avgPx":null,"multiLegReportingType":"SingleSecurity","workingIndicator":true,
        //  "transactTime":"2018-07-21T11:48:31.595Z","exDestination":"XBME","account":47464,"stopPx":null,"ordType":"Limit"}

        String action = (String) json.get("action");
        JSONArray data = (JSONArray) json.get("data");
        int size = data.size();
        console("onOrder() size=" + size);
        for (int i = 0; i < size; i++) {
            JSONObject obj = (JSONObject) data.get(i);
            console("   [" + i + "]:" + obj);

            String orderID = (String) obj.get("orderID");
            String clOrdID = (String) obj.get("clOrdID");
            String side = (String) obj.get("side");
            String ordType = (String) obj.get("ordType"); // ordType=Limit
            String symbol = (String) obj.get("symbol");
            Number price = (Number) obj.get("price"); // if ordType=Market => price=null
            Long orderQty = (Long) obj.get("orderQty");
            Long cumQty = (Long) obj.get("cumQty");
            Number leavesQty = (Number) obj.get("leavesQty");
            Number simpleOrderQty = (Number) obj.get("simpleOrderQty");
            Number simpleCumQty = (Number) obj.get("simpleCumQty");
            Number simpleLeavesQty = (Number) obj.get("simpleLeavesQty");
            String ordStatus = (String) obj.get("ordStatus");
            Pair pair = getPairFromSymbol(symbol);
            Currency from = pair.m_from;
            Currency to = pair.m_to;
            console("    orderID=" + orderID + "; clOrdID=" + clOrdID + "; side=" + side
                    + "; ordType=" + ordType + "; price=" + price + "; ordStatus=" + ordStatus
                    + "; orderQty=" + orderQty + "; cumQty=" + cumQty + "; leavesQty=" + leavesQty
                    + "; simpleOrderQty=" + simpleOrderQty + "; simpleCumQty=" + simpleCumQty + "; simpleLeavesQty=" + simpleLeavesQty
                    + "; symbol=" + symbol + "; pair=" + pair + "; from=" + from + "; to=" + to);

            ExchPairData pairData = m_exchange.getPairData(pair);
            LiveOrdersData liveOrders = pairData.getLiveOrders();
console("    pairData=" + pairData + "; liveOrders=" + liveOrders);

            if (action.equals("partial")) {  // orders snapshot
                onOrderSnapshot(orderID, clOrdID, side, ordType, price, orderQty, cumQty, leavesQty, simpleOrderQty, simpleCumQty, simpleLeavesQty, pair, liveOrders);
            } else if (action.equals("insert")) { // new order,   "ordStatus":"New"
                OrderData orderData = liveOrders.m_clientOrders.get(clOrdID);
                console("    orderData=" + orderData);

                if (orderData == null) {
                    console("non known order, CONCURRENT?: symbol=" + symbol + "; clOrdID=" + clOrdID);

                    onOrderSnapshot(orderID, clOrdID, side, ordType, price, orderQty, cumQty, leavesQty, simpleOrderQty, simpleCumQty, simpleLeavesQty, pair, liveOrders);
                } else {
                    orderData.m_orderId = orderID;

                    liveOrders.addOrder(orderData);
                    liveOrders.notifyListener();

                    orderData.notifyListeners();
                }
            } else if (action.equals("update")) { // order update.
                if (ordStatus != null) {
                    OrderData orderData = liveOrders.m_orders.get(orderID);
console("    update for orderData=" + orderData);

                    if (orderData == null) {
                        throw new RuntimeException("non existing order: symbol=" + symbol + "; orderID=" + orderID + "; clientOrders.keys: " + liveOrders.m_orders.keySet());
                    }

                    OrderSide theSide = (side != null)
                            ? OrderSide.valueOf(side.toUpperCase())
                            : orderData.m_side;
                    Currency allocateCurrency = (theSide == OrderSide.SELL) ? from : to;
                    console("     theSide=" + theSide + "; allocateCurrency=" + allocateCurrency);
                    if (ordStatus.equals("Canceled")) { // ordStatus=Canceled
                        orderData.m_status = OrderStatus.CANCELLED;
                        orderData.notifyListeners();

                        removeLiveOrder(liveOrders, orderData);
                    } else {
                        Number filledNumber = (theSide == OrderSide.SELL) // sell XBT
                                ? simpleCumQty // sell XBT
                                : cumQty; // sell USD
                        console("     filledNumber=" + filledNumber);
                        if (filledNumber != null) {
                            double filledValue = filledNumber.doubleValue();
                            double orderFilled = orderData.m_filled;
                            double deAllocateVal = filledValue - orderFilled;
                            console("     order.filled=" + orderFilled + " -> filledValue=" + filledValue + "; deAllocateVal=" + deAllocateVal);
                            if (filledValue < orderFilled) {
                                throw new RuntimeException("new filled (" + filledValue + ") < current filled (" + orderFilled + ")");
                            }

                            boolean isFilled = false;
                            if (ordStatus.equals("PartiallyFilled")) { // ordStatus=PartiallyFilled
                                orderData.m_status = OrderStatus.PARTIALLY_FILLED;
                            } else if (ordStatus.equals("Filled")) { // ordStatus=Filled
                                orderData.m_status = OrderStatus.FILLED;
                                isFilled = true;
                            } else {
                                throw new RuntimeException("not expected ordStatus: " + ordStatus);
                            }
                            orderData.m_filled = filledValue;
                            orderData.notifyListeners();

                            if (isFilled) {
                                removeLiveOrder(liveOrders, orderData);
                            }
                        } else {
                            console("     no filled field in order update - ignore");
                        }
                    }
console("    after update: orderData=" + orderData);
                } else {
                    console("     no ordStatus in order update - ignore");
                }
            } else {
                throw new RuntimeException("not expected order action: " + action);
            }
        }
    }

    private void removeLiveOrder(LiveOrdersData liveOrders, OrderData orderData) {
        liveOrders.removeOrder(orderData);
        liveOrders.notifyListener();
    }

    private void onOrderSnapshot(String orderID, String clOrdID, String side, String ordType, Number price, Long orderQty,
                                 Long cumQty, Number leavesQty, Number simpleOrderQty, Number simpleCumQty, Number simpleLeavesQty,
                                 Pair pair, LiveOrdersData liveOrders) {
        if (side.length() == 0) { // for close positions order we are getting "side":"" - can not do anything
            console("warning: no order side - seems position close order");
            return;
        }

        OrderSide theSide = OrderSide.valueOf(side.toUpperCase());
        Exchange exchange = m_exchange;
        OrderType orderType = parseOrderType(ordType);

        double theOrderQty;
        double theLeavesQty;
        double theCumQty;
//        if (theSide == OrderSide.SELL) { // sell XBT
            theLeavesQty = simpleLeavesQty.doubleValue();
            theOrderQty = (simpleOrderQty == null) ? theLeavesQty : simpleOrderQty.doubleValue();
            theCumQty = simpleCumQty.doubleValue();
//        } else { // sell USD
//            theOrderQty = orderQty.doubleValue();
//            theCumQty = cumQty.doubleValue();
//            theLeavesQty = leavesQty.doubleValue();
//        }
        console("     theSide=" + theSide + "; orderType=" + orderType + "; theOrderQty=" + theOrderQty + "; theCumQty=" + theCumQty + "; theLeavesQty=" + theLeavesQty);

        double doubleValue = (price == null) ? 0 : price.doubleValue(); // no price for MKT order
        OrderData orderData = new OrderData(exchange, orderID, pair, theSide, orderType, doubleValue, theOrderQty);
        orderData.m_clientOrderId = clOrdID;
        orderData.m_filled = theCumQty;
        orderData.m_status = (theCumQty > 0)
                ? (theLeavesQty > 0)  ? OrderStatus.PARTIALLY_FILLED : OrderStatus.FILLED
                : OrderStatus.SUBMITTED;
        console("     orderData=" + orderData);
        liveOrders.addOrder(orderData);
        liveOrders.notifyListener();
    }

    private OrderType parseOrderType(String ordType) {
        switch (ordType) {
            case ORDER_TYPE_LIMIT: return OrderType.LIMIT;
            case ORDER_TYPE_MARKET: return OrderType.MARKET;
        }
        throw new RuntimeException("unsupported orderType=" + ordType);
    }

    @Override public void cancelOrder(OrderData orderData) throws Exception {
        // curl -X DELETE
        // --header 'Content-Type: application/x-www-form-urlencoded'
        // --header 'Accept: application/json'
        // --header 'X-Requested-With: XMLHttpRequest'
        // -d 'orderID=1234'
        // 'https://testnet.bitmex.com/api/v1/order'
        String orderId = orderData.m_orderId;
        console("cancelOrder() orderId=" + orderId);

        List<NameValuePair> nvps = new ArrayList<>();
        nvps.add(new BasicNameValuePair("orderID", orderId));
        console("  nvps=" + nvps);

        JsonElement json = loadJson(DELETE, ORDER_ENDPOINT, nvps);
        console("got json=" + json);
    }

//    private void addAllocated(Currency allocateCurrency, double deAllocateVal) {
//        double all = m_exchange.m_accountData.getAllocated(allocateCurrency);
//        all += deAllocateVal;
//        m_exchange.m_accountData.setAllocated(allocateCurrency, all);
//// todo: update available accordingly
//        console("   accountData=" + m_exchange.m_accountData);
//    }

    @Override public TicksCacheReader getTicksCacheReader(MapConfig config) {
        String cacheDir = config.getPropertyNoComment("cache.dir");
console("BitMex<> cacheDir=" + cacheDir);
        if (cacheDir != null) {
            File dir = new File(cacheDir);
            if (dir.isDirectory()) {
                return new TicksCacheReader(DataFileType.SIMPLE, dir);
            } else {
                throw new RuntimeException("cache.dir "
                        + (dir.exists() ? "is not exist" : "is not a dir")
                        + ": " + cacheDir);
            }
        } else {
            throw new RuntimeException("cache.dir is not defined");
        }
    }

    @Override public List<? extends ITickData> loadTrades(Pair pair, long timestamp, Direction direction, int tradesNum) throws Exception {
        String symbol = pairToSymbol(pair);

        Date date = new Date(timestamp);
        String time = TIMESTAMP_FORMAT.format(date);
        String count = Integer.toString(tradesNum);
        console("loadTrades timestamp=" + timestamp + "; start=" + time + "; symbol=" + symbol + "; count=" + count);

        List<NameValuePair> nvps = new ArrayList<>();
        nvps.add(new BasicNameValuePair("symbol", symbol));
        nvps.add(new BasicNameValuePair("count", count));

        if (direction == Direction.forward) {
            nvps.add(new BasicNameValuePair("startTime", time));
            nvps.add(new BasicNameValuePair("reverse", "false"));
        } else {
            nvps.add(new BasicNameValuePair("endTime", time));
            nvps.add(new BasicNameValuePair("reverse", "true"));
        }

        JsonArray table = loadTable(nvps);

        List<Tr> trs = create(table, new FromJsonCreator<Tr>() {
            @Override public Tr create(JsonObject json) {
                try {
                    return Tr.createFrom(json);
                } catch (Exception e) {
                    return null; // eat single trade parse error - logged inside createFrom()
                }
            }
        });
        return trs;
    }

    private JsonArray loadTable(List<NameValuePair> nvps) throws Exception {
        JsonElement json = loadJson(GET, "/api/v1/trade", nvps);

        if (json instanceof JsonArray) {
            JsonArray array = (JsonArray) json;
            if (LOG_JSON_TABLE) {
                for (JsonElement next : array) {
                    console(next.toString());
                }
            }
            return array;
        } else {
            JsonArray asJsonArray = json.getAsJsonArray();
            console(json.toString());
            return asJsonArray;
        }
    }

    private <X extends Object> List<X> create(JsonArray table, FromJsonCreator<X> creator) {
        List<X> ret = new ArrayList<>();
        for (JsonElement next : table) {
            JsonObject jsonObject = next.getAsJsonObject();
            X x = creator.create(jsonObject);
            if (x != null) {
                ret.add(x);
            }
        }
        return ret;
    }

    private JsonElement loadJson(String httpActionName, String endpointPath, List<NameValuePair> nvps) throws Exception {

        HttpRequestBase httpRequest;
        URIBuilder builder = new URIBuilder()
                .setScheme("https")
                .setHost(ENDPOINT_HOST)
                .setPath(endpointPath);
        if (httpActionName.equals(POST) || httpActionName.equals(DELETE)) {
            UrlEncodedFormEntity postEntity = new UrlEncodedFormEntity(nvps);
            if (LOG_HTTP) {
                console("  postEntity=" + postEntity + "; isRepeatable=" + postEntity.isRepeatable());
            }
            int postLength = (int) postEntity.getContentLength();
            ByteArrayOutputStream baos = new ByteArrayOutputStream(postLength);
            postEntity.writeTo(baos);
            String postData = baos.toString();
            if (LOG_HTTP) {
                console("  postData=" + postData);
            }
            long expires = System.currentTimeMillis() / 1000L + 3600L; // set expires one hour in the future
            String expiresStr = Long.toString(expires);
            String message = httpActionName + endpointPath + expiresStr + postData;
            String signatureString = hmacSHA256(message);
            if (LOG_HTTP) {
                console("  expiresStr=" + expiresStr + "; message=" + message + "; signatureString=" + signatureString);
            }

            URI uri = builder.build();
            HttpEntityEnclosingRequestBase httpAction;
            if (httpActionName.equals(POST)) {
                httpAction = new HttpPost(uri);
            } else { // DELETE
                httpAction = new HttpDeletePost(uri);
            }

            httpAction.addHeader("X-Requested-With", "XMLHttpRequest");

            httpAction.addHeader("api-expires", expiresStr);
            httpAction.addHeader("api-key", m_apiKey);
            httpAction.addHeader("api-signature", signatureString);

            httpAction.setEntity(postEntity);

            httpRequest = httpAction;
        } else if (httpActionName.equals(GET)) { // for GET - add params to URL
            // https://testnet.bitmex.com/api/v1/trade?symbol=XBTUSD&count=100&reverse=false
            builder = builder.addParameters(nvps);
            URI uri = builder.build();
            httpRequest = new HttpGet(uri);
        } else {
            throw new RuntimeException("unsupported httpAction=" + httpActionName);
        }
        httpRequest.addHeader("Accept", "application/json");
        httpRequest.setConfig(m_requestConfig);

        console("execute " + httpRequest);

        int DEFAULT_TIMEOUT = 5000;
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(DEFAULT_TIMEOUT) // the time to establish the connection with the remote host
                .setConnectionRequestTimeout(DEFAULT_TIMEOUT)
                .setSocketTimeout(DEFAULT_TIMEOUT) // the time waiting for data – after the connection was established; maximum time of inactivity between two data packets
                .build();

        // see https://hc.apache.org/httpcomponents-client-ga/tutorial/html/fundamentals.html
        //     https://hc.apache.org/httpcomponents-client-ga/examples.html
        CloseableHttpClient httpclient = HttpClients.custom()
                .setKeepAliveStrategy(m_keepAliveStrat)
                .setDefaultRequestConfig(requestConfig)
                .build();
        //CloseableHttpClient httpclient = HttpClients.createDefault();

        try {
            CloseableHttpResponse response = httpclient.execute(httpRequest);
            try {
                if (LOG_HTTP) {
                    StatusLine statusLine = response.getStatusLine();
                    console("ProtocolVersion=" + response.getProtocolVersion()+ "; StatusLine='" + statusLine
                            + "'; StatusCode=" + statusLine.getStatusCode() + "; ReasonPhrase=" + statusLine.getReasonPhrase());
                }

                if (LOG_HEADERS) {
                    HeaderIterator it = response.headerIterator();
                    while (it.hasNext()) {
                        console("Header: " + it.next());
                    }
                }

                // Header: X-RateLimit-Limit: 150
                // Header: X-RateLimit-Remaining: 149
                Header rateLimitLimit = response.getFirstHeader("X-RateLimit-Limit");
                Header rateLimitRemaining = response.getFirstHeader("X-RateLimit-Remaining");
                if ((rateLimitLimit != null) && (rateLimitRemaining != null)) {
                    HeaderElement[] elements = rateLimitLimit.getElements();
                    HeaderElement element = elements[0];
                    String limitStr = element.getName();
                    int limit = Integer.parseInt(limitStr);

                    elements = rateLimitRemaining.getElements();
                    element = elements[0];
                    String remainingStr = element.getName();
                    int remaining = Integer.parseInt(remainingStr);

                    m_lastRateLimit = ((double) remaining) / limit;
                    console("X-RateLimit: " + remaining + " of " + limit + " => " + m_lastRateLimit);
                }

                HttpEntity entity = response.getEntity();
//                if (LOG_HTTP) {
//                    console("entity=" + entity);
//                }

                if (entity != null) {
                    long contentLength = entity.getContentLength();
                    ContentType contentType = ContentType.getOrDefault(entity);
                    Charset charset = contentType.getCharset();
                    if (LOG_HTTP) {
                        console("contentLength=" + contentLength + "; contentType=" + contentType + "; charset=" + charset);
                    }
                    InputStream is = entity.getContent();
                    Reader reader = new InputStreamReader(is, charset);

                    try {
                        Gson gson = new GsonBuilder().create();
                        JsonElement json = gson.fromJson(reader, JsonElement.class);
                        return json;
                    } finally {
                        reader.close();
                    }
                }
                throw new RuntimeException("empty response from server");
            } finally {
                response.close();
            }
        } finally {
            httpclient.close();
        }
    }

//    private <T> List<T> parseTable(Reader reader) {
//        Gson gson = new GsonBuilder().create();
//        List<T> list = gson.fromJson(reader, new TypeToken<List<T>>(){}.getType());
//        return list;
//    }


    //---------------------------------------------------------------------------------
    private static class ReconnectHandler extends ClientManager.ReconnectHandler {
        public static long s_reconnectTimeout = 2; // number of seconds

        private int m_counter;
        private int m_connectFailure;

        /** @return When true is returned, client container will reconnect. */
        @Override public boolean onDisconnect(CloseReason closeReason) {
            m_counter++;
            m_connectFailure = 0;
            console("onDisconnect() closeReason=" + closeReason + "; Reconnecting... (reconnect count: " + m_counter + ")");
            return true;
        }

        /** Called when there is a connection failure
         * @return When true is returned, client container will reconnect. */
        @Override public boolean onConnectFailure(Exception exception) {
            m_counter++;
            m_connectFailure++;

            err("onConnectFailure() exception=" + exception, exception);
            log("### Reconnecting... (connectFailure:" + m_connectFailure + "; reconnect count: " + m_counter + ")");

            try {
                TimeUnit.SECONDS.sleep(m_connectFailure);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            return true;
        }

        /** Get reconnect delay.
         * @return When positive value is returned, next connection attempt will be made after that number of seconds. */
        @Override public long getDelay() {
            return s_reconnectTimeout;
        }
    }


    //---------------------------------------------------------------------------------------------
    public static class Tr implements ITickData {
        public long m_timestamp;
        public double m_price;
//        public long size;
//        public long grossValue;
//        public double homeNotional;
//        public double foreignNotional;

        // size / price = homeNotional

        @Override public String toString() {
            return "Tr{" +
                    "timestamp='" + m_timestamp + '\'' +
                    ", price=" + m_price +
//                    ", size=" + size +
//                    ", grossValue=" + grossValue +
//                    ", homeNotional=" + homeNotional +
//                    ", foreignNotional=" + foreignNotional +
                    '}';
        }

        @Override public long getTimestamp() { return m_timestamp; }
        @Override public float getClosePrice() { return (float) m_price; }
        @Override public float getMinPrice() { return (float) m_price; }
        @Override public float getMaxPrice() { return (float) m_price; }
        @Override public long getBarSize() { return 0; }
        @Override public TickPainter getTickPainter() { return TickPainter.TICK; }
        @Override public ITickData getOlderTick() { return null; }
        @Override public void setOlderTick(ITickData last) { /*noop*/ }
        @Override public boolean isValid() { return Utils.isInvalidPriceDouble(m_price); }

        public static Tr createFrom(JsonObject json) {
            Tr ret = new Tr();

            JsonElement timestamp = json.get("timestamp");
            String timestampStr = timestamp.getAsString();
            try {
                Date date = TIMESTAMP_FORMAT.parse(timestampStr);
                ret.m_timestamp = date.getTime();
                ret.m_price = json.get("price").getAsDouble();

                return ret;
            } catch (ParseException e) {
                throw new RuntimeException("timestamp parse error: timestamp=" + timestamp + "; '" + timestampStr + "'; json=" + json + ": " + e, e);
            }
        }
    }

//                        "timestamp": "2016-05-05T04:50:46.067Z",
//                            "symbol": "XBTUSD",
//                            "side": "Buy",
//                            "size": 1377,
//                            "price": 447.43,
//                            "tickDirection": "ZeroPlusTick",
//                            "trdMatchID": "07b3bf2e-b40f-7c24-6c51-3bd110fec715",
//                            "grossValue": 307758123,
//                            "homeNotional": 3.07758123,
//                            "foreignNotional": 1377



    //---------------------------------------------------------------------------------------------------
    private interface FromJsonCreator<X> {
        X create(JsonObject next);
    }

    // -----------------------------------------------------------------------------------------------------------
    private static class MarginToAccountModel {

    }


    // -----------------------------------------------------------------------------------------------------------
    public class HttpDeletePost extends HttpEntityEnclosingRequestBase {

        private final static String METHOD_NAME = "DELETE";

        public HttpDeletePost() {
            super();
        }

        public HttpDeletePost(final URI uri) {
            super();
            setURI(uri);
        }

        public HttpDeletePost(final String uri) {
            super();
            setURI(URI.create(uri));
        }

        @Override public String getMethod() {
            return METHOD_NAME;
        }

    }
}
