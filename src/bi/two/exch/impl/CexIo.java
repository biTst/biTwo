package bi.two.exch.impl;

import bi.two.chart.TradeTickData;
import bi.two.exch.*;
import bi.two.util.Hex;
import bi.two.util.MapConfig;
import bi.two.util.Utils;
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
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

// based on info from
//  https://cex.io/websocket-api
//  https://cex.io/rest-api
//  to check: https://github.com/zackurben/cex.io-api-java  https://github.com/joshho/cex.io-api-java
public class CexIo extends BaseExchImpl {
    private static final String URL = "wss://ws.cex.io/ws/";
    private static final String[] s_supportedCurrencies = new String[]{ "BTC", "USD", "EUR", "GBP", "ETH", "XRP", "BCH", "BTG", "DASH", "ZEC", "RUB", };
    private static final Map<String,Long> m_currencyUnitsMap = new HashMap<>();

    public static long s_reconnectTimeout = 2;
    private static RateLimiter m_rateLimiter = new RateLimiter();

    private final Exchange m_exchange; // todo: move to parent
    private final String m_apiKey;
    private final String m_apiSecret;
    private Session m_session;
    private Map<String,OrderBook> m_orderBooks = new HashMap<>(); // todo: move to parent ?
    private List<Currency> m_currencies = new ArrayList<>();
    private Map<String,LiveOrdersData> m_liveOrdersRequestsMap = new HashMap<>();
    private Map<String,OrderData> m_submitOrderRequestsMap = new HashMap<>();
    private Map<String,OrderData> m_cancelOrderRequestsMap = new HashMap<>();

    static {
        // Current balance per currency:
        // 1 USD = 100 units, 1 EUR = 100 units, 1 GBP = 100 units, 1 RUB = 100 units, 1 BTC = 100000000 units, 1 LTC = 100000000 units, 1 GHS = 100000000 units, 1 ETH = 1000000 units

        m_currencyUnitsMap.put("USD", 100L);
        m_currencyUnitsMap.put("EUR", 100L);
        m_currencyUnitsMap.put("GBP", 100L);
        m_currencyUnitsMap.put("RUB", 100L);
        m_currencyUnitsMap.put("BTC", 100000000L);
        m_currencyUnitsMap.put("BCH", 100000000L);
        m_currencyUnitsMap.put("LTC", 100000000L);
        m_currencyUnitsMap.put("GHS", 100000000L);
        m_currencyUnitsMap.put("BTG", 100000000L);
        m_currencyUnitsMap.put("DASH", 100000000L);
        m_currencyUnitsMap.put("ETH", 1000000L);
        m_currencyUnitsMap.put("XRP", 1000000L);

//        m_currencyUnitsMap.put("ZEC", 1000000L);
    }

    public CexIo(MapConfig config, Exchange exchange) {
        m_exchange = exchange;
        m_apiKey = config.getString("cex_apiKey");
        m_apiSecret = config.getString("cex_apiSecret");

        for (String name : s_supportedCurrencies) {
            Currency byName = Currency.get(name.toLowerCase());
            if (byName != null) {
                m_currencies.add(byName);
            }
        }
    }

    public static void main(String[] args) {
        log("main()");
        try {
            Endpoint endpoint = new Endpoint() {
                @Override public void onOpen(Session session, EndpointConfig config) {
                    log("onOpen() session=" + session + "; config=" + config);

                    session.addMessageHandler(new MessageHandler.Whole<String>() {
                        @Override
                        public void onMessage(String message) {
                            log("onMessage() message=" + message);
                        }
                    });
                }

                @Override public void onClose(Session session, CloseReason closeReason) {
                    log("onClose");
                    super.onClose(session, closeReason);
                }

                @Override public void onError(Session session, Throwable thr) {
                    log("onError");
                    super.onError(session, thr);
                }
            };

            log("connectToServer...");
            connectToServer(endpoint);

            Thread.sleep(TimeUnit.DAYS.toMillis(365));
            log("done");

        } catch (Exception e) {
            log("error: " + e);
            e.printStackTrace();
        }

        //        test();
    }

    private static void connectToServer(Endpoint endpoint) throws DeploymentException, IOException, URISyntaxException {
        // ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();

        // latest libs adds Origin="http://ws.cex.io" header to initial request
        // cex.io server returns with {"ok":"error","data":{"error":"Incorrect origin in request header."}}
        // removing "Origin" header fixes the problem
        ClientEndpointConfig cec = ClientEndpointConfig.Builder.create()
                .configurator(new ClientEndpointConfig.Configurator() {
                    @Override public void beforeRequest(Map<String, List<String>> headers) {
                        headers.remove("Origin");
                    }
                    @Override public void afterResponse(HandshakeResponse hr) { /*noop*/ }
                })
                .build();

        // works with compile 'org.glassfish.tyrus.bundles:tyrus-standalone-client:1.13.1'
        // ClientManager client = ClientManager.createClient();
        // works with compile 'org.glassfish.tyrus:tyrus-container-jdk-client:1.13.1'
        ClientManager client = ClientManager.createClient(JdkClientContainer.class.getName());

        client.getProperties().put(ClientProperties.RECONNECT_HANDLER, new ReconnectHandler());

        client.connectToServer(endpoint, cec, new URI(URL));
    }

    private static void test() {
        String apiSecret = "1IuUeW4IEWatK87zBTENHj1T17s";
        String apiKey = "1WZbtMTbMbo2NsW12vOz9IuPM";
        long timestamp = 1448034533; // 1448034533 means Fri Nov 20 2015 17:48:53 GMT+0200 (EET)
        String signature = "7d581adb01ad22f1ed38e1159a7f08ac5d83906ae1a42fe17e7d977786fe9694"; // expected signature

        String sign = createSignature(timestamp, apiSecret, apiKey);
        log("signature=" + sign);
        log(" expected=" + signature);
        log("   equals=" + signature.equals(sign));
    }

    private void onMessageX(Session session, String message) {
        log("<< received: " + message);
        try {
            JSONParser parser = new JSONParser();
            JSONObject jsonObject = (JSONObject) parser.parse(message);
            String e = (String) jsonObject.get("e");
            if (Utils.equals(e, "connected")) {
                onConnected(session);
            } else if (Utils.equals(e, "auth")) {
                onAuth(session, jsonObject);
            } else if (Utils.equals(e, "ping")) {
                onPing(session, jsonObject);
            } else if (Utils.equals(e, "tick")) {
                onTick(session, jsonObject);
            } else if (Utils.equals(e, "ticker")) {
                onTicker(session, jsonObject);
            } else if (Utils.equals(e, "get-balance")) {
                onGetBalance(session, jsonObject);
            } else if (Utils.equals(e, "order-book-subscribe")) {
                onOrderBookSubscribe(session, jsonObject);
            } else if (Utils.equals(e, "order-book-unsubscribe")) {
                onOrderBookUnsubscribe(session, jsonObject);
            } else if (Utils.equals(e, "open-orders")) {
                processOpenOrders(jsonObject);
            } else if (Utils.equals(e, "place-order")) {
                onPlaceOrder(jsonObject);
            } else if (Utils.equals(e, "cancel-replace-order")) {
                onCancelReplaceOrder(jsonObject);
            } else if (Utils.equals(e, "cancel-order")) {
                onCancelOrder(jsonObject);
            } else if (Utils.equals(e, "order")) {
                onOrder(jsonObject);
            } else if (Utils.equals(e, "balance")) {
                onBalance(session, jsonObject);
            } else if (Utils.equals(e, "obalance")) {
                onObalance(session, jsonObject);
            } else if (Utils.equals(e, "tx")) {
                onTx(session, jsonObject);
            } else if (Utils.equals(e, "md")) {
                onMd(session, jsonObject);
            } else if (Utils.equals(e, "md_groupped")) {
                onMdGroupped(session, jsonObject);
            } else if (Utils.equals(e, "md_update")) {
                onMdUpdate(session, jsonObject);
            } else if (Utils.equals(e, "history")) {
                onHistory(session, jsonObject);
            } else if (Utils.equals(e, "history-update")) {
                onHistoryUpdate(session, jsonObject);
            } else if (Utils.equals(e, "ohlcv24")) {
                onOhlcv24(session, jsonObject);
            } else if (Utils.equals(e, "disconnecting")) {
                onDisconnecting(session, jsonObject);
            } else {
                throw new RuntimeException("unexpected json: " + jsonObject);
            }
        } catch (Exception e) {
            err("onMessageX ERROR: " + e, e);
        }
    }

    private static void onOhlcv24(Session session, JSONObject jsonObject) {
//        {
//            'e': 'ohlcv24',
//            'pair': 'BTC:USD',
//            'data': [
//                '415.5804',
//                '418.94',
//                '413.0568',
//                '416.8241',
//                239567198169
//            ]
//        }

        JSONArray data = (JSONArray) jsonObject.get("data");
        Object pair = jsonObject.get("pair");
        log(" onOhlcv24[" + pair + "]: data=" + data);
    }

    private static void onHistoryUpdate(Session session, JSONObject jsonObject) {
//        [
//            ['sell', '1457703218519', '41140000', '423.7125', '735480'],
//            ... 0 to n records
//        ]

        // History update - 

        JSONArray data = (JSONArray) jsonObject.get("data");
        log(" onHistoryUpdate: data=" + data);
    }

    private static void onHistory(Session session, JSONObject jsonObject) {
//            [
//              'buy:1457703205200:46860000:423.7125:735479',
//              'sell:1457703191363:35430000:423.7125:735478',
//              ... 201 items
//            ]

        // History snapshot -  trade history of 201 records

        JSONArray data = (JSONArray) jsonObject.get("data");
        log(" onHistory: data=" + data);
    }

    private static void onMdGroupped(Session session, JSONObject jsonObject) {
        JSONObject data = (JSONObject) jsonObject.get("data");
        log(" onMdGroupped: data=" + data);

        // Market Depth
        
        Object buy = data.get("buy");
        Object sell = data.get("sell");
        Object id = data.get("id");
        Object pair = data.get("pair");
        log("  buy=" + buy);
        log("  sell=" + sell);
        log("  pair=" + pair + ";  id=" + id);
    }

    private static void onMd(Session session, JSONObject jsonObject) {
//        {"id":146121117,
//         "buy":[[8497.2083,29000000],[8495.0001,34506329],[8495,85689863],[8489.22,94000000],[8489.2199,37959554],...],
//         "sell":[[8499.9603,2000000],[8499.9604,1000000],[8499.9704,1500004],[8500,3105070],[8502,2000000],[8502.6627,1040000],...],
//         "buy_total":757256114,
//         "sell_total":176748539752,
//         "pair":"BTC:USD"}

        // Order Book snapshot  -  with depth 50

        JSONObject data = (JSONObject) jsonObject.get("data");
        log(" onMd: data=" + data);

        Object buy = data.get("buy");
        Object sell = data.get("sell");
        Object id = data.get("id");
        Object pair = data.get("pair");
        log("  buy=" + buy);
        log("  sell=" + sell);
        log("  pair=" + pair + ";  id=" + id);
    }

    private static void onTx(Session session, JSONObject jsonObject) {
        // {"d":"user:up100734377:a:BTC","c":"order:5067483259:a:BTC","a":"0.01000000","ds":"0.01431350","cs":"0.01000000",
        //  "user":"up100734377","symbol":"BTC","order":5067483259,"amount":"-0.01000000","type":"sell","time":"2017-11-25T00:25:39.812Z",
        //  "balance":"0.01431350","id":"5067483260"}

        // for order [pair=bch_btc, side=BUY, type=LIMIT, amount=0.11000000, price=0.15652204] we have followed
        //
        // {"a":"0.00001378",                    {"a":"0.11000000",                    {"a":"0.00000001",
        //  "symbol":"BTC",                       "symbol":"BCH",                       "symbol":"BTC",
        //  "amount":"-0.01724326",               "amount":"0.11000000",                "amount":"0.00000001",
        //  "c":"user:up100734377:a:BTC",         "c":"user:up100734377:a:BCH",         "c":"user:up100734377:a:BTC",
        //  "d":"order:5456707271:a:BTC",         "d":"order:5456707271:a:BCH",         "d":"order:5456707271:a:BTC",
        //  "type":"buy",                         "buy":5456707271,                     "type":"costsNothing",
        //  "ds":"0.01724326",                    "sell":5456711383,                    "ds":0,
        //  "cs":"0.39942936",                    "type":"buy",                         "cs":"0.39942937",
        //  "balance":"0.39942936",               "ds":0,                               "balance":"0.39942937",
        //  "time":"2018-01-18T01:43:36.661Z",    "symbol2":"BTC",                      "time":"2018-01-18T01:44:03.758Z",
        //  "id":"5456707273",                    "cs":"0.11000000",                    "id":"5456711393",
        //  "user":"up100734377",                 "fee_amount":"0.00002583",            "user":"up100734377",
        //  "order":5456707271}                   "balance":"0.11000000",               "order":5456707271}
        //                                        "price":0.15652204,
        //                                        "time":"2018-01-18T01:44:03.758Z",
        //                                        "id":"5456711392",
        //                                        "user":"up100734377",
        //                                        "order":5456707271}

        // Transaction created (Order successfully completed)

        JSONObject data = (JSONObject) jsonObject.get("data");
        log(" onTx: data=" + data);
    }

    private void onObalance(Session session, final JSONObject jsonObject) {
        // {"symbol":"BTC","balance":"1000000"}
        m_exchange.m_threadPool.submit(new Runnable() {
            @Override public void run() {
                try {
                    JSONObject data = (JSONObject) jsonObject.get("data");
                    log(" onObalance: data=" + data);
                    setAccountValue(data, false);
                } catch (Exception e) {
                    err("onObalance error: " + e, e);
                }
            }
        });
    }

    private void onBalance(Session session, final JSONObject jsonObject) {
        // {"symbol":"BTC","balance":"1431350"}
        m_exchange.m_threadPool.submit(new Runnable() {
            @Override public void run() {
                try {
                    JSONObject data = (JSONObject) jsonObject.get("data");
                    log(" onBalance: data=" + data);
                    setAccountValue(data, true);
                } catch (Exception e) {
                    err("onBalance error: " + e, e);
                }
            }
        });
    }

    private void setAccountValue(JSONObject data, boolean setAvailable) {
        Object symbol = data.get("symbol");
        Object balance = data.get("balance");
        log("  symbol=" + symbol + ";  balance=" + balance); // symbol=BTC;  balance=39942936

        String symbolStr = symbol.toString();
        String symbolStrLow = symbolStr.toLowerCase();
        Currency currency = Currency.getByName(symbolStrLow);
        log("   symbolStr=" + symbolStrLow + ";  currency=" + currency);

        String balanceStr = balance.toString();
        long valueLong = Long.parseLong(balanceStr);
        Long units = m_currencyUnitsMap.get(symbolStr);
        log("    balanceStr=" + balanceStr + ";  valueLong=" + valueLong + "; units=" + units);
        if (units != null) {
            double value = ((double) valueLong) / units;
            log("     " + (setAvailable ? "available" : "allocated") + "=" + value);

            AccountData accountData = m_exchange.m_accountData;
            if (setAvailable) {
                double available = accountData.available(currency);
                double delta = value - available;
                String msg = "account[" + symbolStr + "] " + (delta > 0 ? "+" : "") + Utils.format8(delta);
                console(msg);
                log("      " + msg);
                accountData.setAvailable(currency, value);
            } else {
                double allocated = accountData.allocated(currency);
                double delta = value - allocated;
                log("      account[" + symbolStr + "].allocated " + (delta > 0 ? "+" : "") + Utils.format8(delta));
                accountData.setAllocated(currency, value);
            }
            log("    accountData=" + accountData);
        } else {
            log("!!! ERROR: no units conversion for symbol=" + symbolStr);
        }
    }

    private void onOrder(final JSONObject jsonObject) {
        // {"amount":1000000,"price":"9000.0123","fee":"0.17","remains":"1000000","id":"5067483259","time":"1511569539812",
        //  "type":"sell","pair":{"symbol1":"BTC","symbol2":"USD"}}

        // for order [pair=bch_btc, side=BUY, type=LIMIT, amount=0.11000000, price=0.15652204] we have followed
        // after submit:
        //  {"amount":11000000,"price":"0.15652204","fee":"0.23","remains":"11000000","id":"5456707271","time":"1516239816661","type":"buy","pair":{"symbol1":"BCH","symbol2":"BTC"}}
        // after fill:
        //  {"remains":"0","id":"5456707271","pair":{"symbol1":"BCH","symbol2":"BTC"}}
        // after cancel
        // {"cancel":true,"fremains":"0.12201800","remains":"122018","id":"5475064267","pair":{"symbol1":"ETH","symbol2":"BTC"}}
        m_exchange.m_threadPool.submit(new Runnable() {
            @Override public void run() {
                try {
                    JSONObject data = (JSONObject) jsonObject.get("data");
                    log(" onOrder: data=" + data);
                    JSONObject pair = (JSONObject) data.get("pair");
                    log("  pair=" + pair);
                    String symbol1 = (String) pair.get("symbol1");
                    String symbol2 = (String) pair.get("symbol2");
                    Currency currency1 = Currency.get(symbol1.toLowerCase());
                    Currency currency2 = Currency.get(symbol2.toLowerCase());
                    log("   symbol1=" + symbol1 + "; symbol2=" + symbol2 + "; currency1=" + currency1 + "; currency2=" + currency2);
                    Pair pairObj = Pair.get(currency1, currency2);
                    ExchPairData exchPairData = m_exchange.getPairData(pairObj);
                    log("    pairObj=" + pairObj + "; exchPairData=" + exchPairData);
                    LiveOrdersData liveOrders = exchPairData.getLiveOrders();
                    String id = (String) data.get("id");
                    OrderData od = liveOrders.getOrder(id);
                    log("     order id=" + id + "; OrderData=" + od);

                    if (od == null) {
                        console("got update to unknown order. ignoring. id=" + id);
                        return;
                    }

                    String remainsStr = (String) data.get("remains");
                    double remainsDouble = Double.parseDouble(remainsStr);
                    Long units = m_currencyUnitsMap.get(symbol1);
                    if (units == null) {
                        throw new RuntimeException("no unit for symbol " + symbol1);
                    }
                    double remains = remainsDouble / units;
                    double amount = od.m_amount;
                    double filled = amount - remains;
                    log("       remainsStr=" + remainsStr + "; remainsDouble=" + remainsDouble + "; units=" + units
                            + "; remains=" + remains + "; amount=" + amount + "; filled=" + filled);
                    if (filled >= 0) {
                        Execution.Type execType = null;
                        Object cancel = data.get("cancel");
                        if (cancel != null) {
                            String cancelStr = cancel.toString();
                            if (cancelStr.equals("true")) { // cancelled
                                if (od.m_status != OrderStatus.CANCELLED) {
                                    execType = Execution.Type.cancelled;
                                }
                            } else {
                                log("error: unexpected value in cancel: " + cancel);
                            }
                        } else {
                            String fee = (String) data.get("fee");
                            if (fee != null) { // fee comes with first exec
                                if (od.m_status != OrderStatus.NEW) { // new -> acknowledged
                                    od.m_status = OrderStatus.SUBMITTED;
                                }
                                execType = Execution.Type.acknowledged;
                            } else {
                                od.setFilled(filled); // will set order status inside
                                execType = (remains == 0) ? Execution.Type.fill : Execution.Type.partialFill;
                            }
                        }
                        if (execType != null) {
                            Execution execution = new Execution(execType, data.toString());
                            od.addExecution(execution);
                        }
                        log("  out od=" + od);
                    } else {
                        log("error: invalid filled: " + filled + "; order.amount=" + amount + "; remains=" + remains);
                    }
                    od.notifyListeners();
                } catch (Exception e) {
                    err("onOrder error: " + e, e);
                }
            }
        });
    }

    private static void cancelOrder(Session session, String oid, String orderToCancelId) throws IOException {
        log(" cancelOrder() orderToCancelId=" + orderToCancelId);

//        {
//            "e": "cancel-order",
//            "data": { "order_id": "2477098" },
//            "oid": "1435927928274_12_cancel-order"
//        }

        send(session, "{ \"e\": \"cancel-order\", \"data\": { \"order_id\": \""
                + orderToCancelId + "\" }, \"oid\": \"" + oid + "\" }");
    }

    private static void onOrderBookUnsubscribe(Session session, JSONObject jsonObject) {
        Object oid = jsonObject.get("oid");
        JSONObject data = (JSONObject) jsonObject.get("data");
        log(" onOrderBookUnsubscribe[" + oid + "]: data=" + data);
    }

    private void onMdUpdate(Session session, JSONObject jsonObject) {
//        {
//            "e": "md_update",
//            "data": {
//                "id": 67814,
//                "pair": "BTC:USD",
//                "time": 1435927928879,
//                "bids": [
//                    [241.9477, 0],
//                    ...
//                ],
//                "asks": []
//            }
//        }
        processOrderBook(jsonObject);
    }

    private void onOrderBookSubscribe(Session session, JSONObject jsonObject) {
        // {"timestamp":1511566229,
        //  "bids":[[8228.1220,0.00606695],[8226.6111,0.01433840]],
        //  "asks":[[8231.0941,0.01000000],[8231.1303,0.64500000]],
        //  "pair":"BTC:USD",
        //  "id":145911102,
        //  "sell_total":"1875.41098508",
        //  "buy_total":"6933944.35"}
        //Object oid = jsonObject.get("oid");

        processOrderBook(jsonObject);
    }

    private void processOrderBook(final JSONObject jsonObject) {
        m_exchange.m_threadPool.submit(new Runnable() {
            @Override public void run() {
                try {
                    JSONObject data = (JSONObject) jsonObject.get("data");
                    String pair = (String) data.get("pair"); // "pair":"BTC:USD"
                    JSONArray bids = (JSONArray) data.get("bids");
                    JSONArray asks = (JSONArray) data.get("asks");

                    // "pair":"BTC:USD"
                    // [[8228.1220,0.00606695],[8226.6111,0.01433840]]
                    OrderBook orderBook = m_orderBooks.get(pair);
                    if (orderBook != null) {
                        List<OrderBook.OrderBookEntry> aBids = parseBook(bids);
                        List<OrderBook.OrderBookEntry> aAsks = parseBook(asks);

//        log(" input orderBook[" + orderBook.getPair() + "]: " + orderBook);
                        orderBook.update(aBids, aAsks);
//        log(" updated orderBook[" + orderBook.getPair() + "]: " + orderBook.toString(2));
                    } else {
                        throw new RuntimeException("no orderBook for pair=" + pair);
                    }
                } catch (Exception e) {
                    err("processOrderBook error: " + e, e);
                }
            }
        });
    }

    private List<OrderBook.OrderBookEntry> parseBook(JSONArray jsonArray) { // [[2551.4606,0.00000000]]
        List<OrderBook.OrderBookEntry> aBids = new ArrayList<>();
        for (Object bid : jsonArray) {
            JSONArray aBid = (JSONArray) bid;
            Number price = (Number) aBid.get(0);
            Number size = (Number) aBid.get(1);
            OrderBook.OrderBookEntry entry = new OrderBook.OrderBookEntry(price.doubleValue(), size.doubleValue());
            aBids.add(entry);
//            log("  price=" + price + "; .class=" + price.getClass());
//            log("  size=" + size + "; .class=" + size.getClass());
        }
        return aBids;
    }

    private static void unsubscribeOrderBook(Session session, Pair pair) throws IOException {
//        {
//            "e": "order-book-unsubscribe",
//            "data": {
//                "pair": [ "BTC", "USD" ] },
//            "oid": "1435927928274_4_order-book-unsubscribe"
//        }

        long timeMillis = System.currentTimeMillis();
        send(session, "{ \"e\": \"order-book-unsubscribe\", \"data\": { " + pairToStr(pair) +  " }, \"oid\": \"" + timeMillis + "_order-book-unsubscribe\" }");
    }

    private void onGetBalance(Session session, JSONObject jsonObject) throws Exception {
        // {"balance":{"BTC":"0.02431350","EUR":"0.16","GHS":"0.00000000","BTG":"0.50817888","GBP":"0.00","BCH":"0.00000000",
        //             "USD":"0.04","ETH":"0.00000000","ZEC":"0.00000000","DASH":"0.00000000","RUB":"0.00"},
        //  "obalance":{"BTC":"0.00000000","EUR":"0.00","GHS":"0.00000000","GBP":"0.00","BCH":"0.00000000","USD":"0.00",
        //              "ETH":"0.00000000","ZEC":"0.00000000","DASH":"0.00000000","RUB":"0.00"},
        //  "time":1511566228653}
        Object oid = jsonObject.get("oid");
        JSONObject data = (JSONObject) jsonObject.get("data");
        log(" onGetBalance[" + oid + "]: data=" + data);

        JSONObject balance = (JSONObject) data.get("balance");
        JSONObject obalance = (JSONObject) data.get("obalance");
        log("  balance: " + parseBalance(balance));
        log("  obalance: " + parseBalance(obalance));

        for (Currency currency : m_currencies) {
            String name = currency.m_name.toUpperCase();
            String value = (String) balance.get(name);
            if (value != null) {
                double doubleValue = Double.parseDouble(value);
                if (doubleValue > 0) {
                    m_exchange.m_accountData.setAvailable(currency, doubleValue);
                }
            }
            value = (String) obalance.get(name);
            if (value != null) {
                double doubleValue = Double.parseDouble(value);
                if (doubleValue > 0) {
                    m_exchange.m_accountData.setAllocated(currency, doubleValue);
                }
            }
        }
        m_exchange.notifyAccountListener();
    }

    private String parseBalance(JSONObject balance) {
        // {"BTC":"0.02431350","EUR":"0.16","GHS":"0.00000000","BTG":"0.50817888","GBP":"0.00","BCH":"0.00000000",
        //  "USD":"0.04","ETH":"0.00000000","ZEC":"0.00000000","DASH":"0.00000000","RUB":"0.00"}

        StringBuilder sb = new StringBuilder();

        for (String supportedCurrency : s_supportedCurrencies) {
            appendBalance(sb, balance, supportedCurrency);
        }
        sb.replace(0,2, "[");
        sb.append("]");
        return sb.toString();
    }

    private static void appendBalance(StringBuilder sb, JSONObject balance, String name) {
        String value = (String) balance.get(name);
        if (value != null) {
            if (Double.parseDouble(value) > 0) {
                sb.append("; ").append(name).append("=").append(value);
            }
        }
    }

    private static void onTicker(Session session, JSONObject jsonObject) {
        // {"volume":"998.75079999","high":"8350","last":"8235.3789","low":"8050.5","ask":8249.1156,
        //  "volume30d":"59773.74906418","bid":8248.99,"pair":["BTC","USD"],"timestamp":"1511565345"}
        Object oid = jsonObject.get("oid");
        JSONObject data = (JSONObject) jsonObject.get("data");
        log(" onTicker[" + oid + "]: data=" + data);

        Object bid = data.get("bid");
        Object ask = data.get("ask");
        JSONArray pairArray = (JSONArray) data.get("pair");
        String cur1 = (String) pairArray.get(0);
        String cur2 = (String) pairArray.get(1);
        String pairName = cur1 + "_" + cur2;
        Pair pair = Pair.getByNameInt(pairName.toLowerCase());
        log("  " + pairName + ":  bid=" + bid + "; ask=" + ask + "      pair=" + pair);
    }

    private static void onTick(Session session, JSONObject jsonObject) {
        // {"price":"463.2","symbol1":"ETH","symbol2":"USD"}
        Object data = jsonObject.get("data");
        log(" onTick: data=" + data);
    }

    private static void onDisconnecting(Session session, JSONObject jsonObject) {
        Object reason = jsonObject.get("reason");
        log(" onDisconnecting: " + reason);
    }

    private static void onPing(Session session, JSONObject jsonObject) throws IOException {
        log(" got ping: " + jsonObject);
        //cexioWs.send(JSON.stringify({e: "pong"}));
        send(session, "{\"e\": \"pong\"}");
    }

    private void onAuth(Session session, JSONObject jsonObject) {
        // {"e":"auth","data":{"error":"Invalid API key"},"ok":"error"}
        JSONObject data = (JSONObject) jsonObject.get("data");
        log(" data: " + data);
        String error = (String) data.get("error");
        if (error != null) {
            throw new RuntimeException("auth error: " + error);
        }
//                {
//                    "e": "auth",
//                    "data": {
//                        "ok": "ok"
//                    },
//                    "ok": "ok"
//                }
        String ok = (String) data.get("ok");
        log("  ok: " + ok);
        if (Utils.equals(ok, "ok")) {
            m_exchange.m_live = true; // mark as connected
            m_exchange.notifyAuthenticated();
//            onAuthenticated(session);
        } else {
            throw new RuntimeException("unexpected auth response: " + jsonObject);
        }
    }

    private static void onAuthenticated(Session session) throws Exception {
        log("onAuthenticated");
//        {
//            "e": "subscribe",
//            "rooms": ["pair-BTC-USD"]
//        }

        send(session, "{ \"e\": \"subscribe\", \"rooms\": [\"pair-BTC-USD\"] }");
    }

    private static void __onAuthenticated(Session session) throws Exception {
        log("onAuthenticated");
//        queryTickers(session);

        queryTicket(session, "BTC", "USD");
        Thread.sleep(1000);
        queryTicket(session, "BCH", "USD");
        Thread.sleep(1000);
        queryTicket(session, "BCH", "BTC");

        Thread.sleep(1000);
        queryBalance(session);

        Thread.sleep(1000);
        int depth = 3;
        Pair pair = Pair.get(Currency.BTC, Currency.USD);
        queryOrderBookSnapshoot(session, pair, depth);

        Thread.sleep(1000);
        String oid = System.currentTimeMillis() + "_open-orders";
        queryOpenOrders(session, oid, pair);

        Thread.sleep(1000);
        String orderSize = "0.01";
        String price = "9000.0123";
        String side = "sell";
        oid = System.currentTimeMillis() + "_place-order";
        placeOrder(session, oid, pair, orderSize, price, side);
    }

    private static void queryTickers(Session session) throws IOException {
        //        {
        //            e: "subscribe",
        //            rooms: [ "tickers" ]
        //        }
        send(session, "{\"e\": \"subscribe\", \"rooms\": [ \"tickers\" ]}");
    }

    @Override public void submitOrder(OrderData orderData) throws IOException {
        log("CexIo.submitOrder() orderData=" + orderData);
        String orderSize = orderData.formatSize(orderData.m_amount);
        String orderPrice = orderData.formatPrice(orderData.m_price);
        String orderSide = orderData.m_side.getName();
        String clientOrderId = orderData.m_clientOrderId;
        log(" clientOrderId=" + clientOrderId + "; orderSize=" + orderSize + "; orderPrice=" + orderPrice + "; orderSide=" + orderSide);
        if (clientOrderId == null) {
            throw new RuntimeException("no clientOrderId. orderData=" + orderData);
        }
        String oid = clientOrderId + "_place-order";
        m_submitOrderRequestsMap.put(oid, orderData);
        orderData.m_submitTime = System.currentTimeMillis();
        placeOrder(m_session, oid, orderData.m_pair, orderSize, orderPrice, orderSide);
        orderData.m_status = OrderStatus.SUBMITTED;
        orderData.notifyListeners();
    }

    private static void placeOrder(Session session, String oid, Pair pair, String orderSize, String price, String side) throws IOException {
        //        {
        //            "e": "place-order",
        //            "data": {
        //                "pair": [ "BTC", "USD" ],
        //                "amount": 0.02,
        //                "price": "241.9477",
        //                "type": "buy"
        //            },
        //            "oid": "1435927928274_7_place-order"
        //        }

        send(session, "{ \"e\": \"place-order\", \"data\": { " + pairToStr(pair) + ", \"amount\": " + orderSize
                + ", \"price\": " + price + ", \"type\": \"" + side + "\" }, \"oid\": \"" + oid + "\" }");
    }

    private void onCancelReplaceOrder(final JSONObject jsonObject) {
        //onPlaceOrder(jsonObject);
        m_exchange.m_threadPool.submit(new Runnable() {
            @Override public void run() {
                try {
                    Object oid = jsonObject.get("oid");
                    JSONObject data = (JSONObject) jsonObject.get("data");
                    log(" onCancelReplaceOrder[" + oid + "]: data=" + data);

                    onPlaceOrReplaceOrder(oid, data, true);
                } catch (Exception e) {
                    err("onCancelReplaceOrder error: " + e, e);
                }
            }
        });
    }
    private void onPlaceOrder(final JSONObject jsonObject) {
        // {"error":"There was an error while placing your order: Invalid amount"}
        // {"error":"Error: Place order error: Insufficient funds."}
        // {"amount":"0.01000000","price":"9000.0123","pending":"0.01000000","id":"5067483259","time":1511569539812,"complete":false,"type":"sell"}
        m_exchange.m_threadPool.submit(new Runnable() {
            @Override public void run() {
                try {
                    Object oid = jsonObject.get("oid");
                    JSONObject data = (JSONObject) jsonObject.get("data");
                    log(" onPlaceOrder[" + oid + "]: data=" + data);

                    onPlaceOrReplaceOrder(oid, data, false);
                } catch (Exception e) {
                    err("onPlaceOrder error: " + e, e);
                }
            }
        });
    }

    private void onPlaceOrReplaceOrder(Object oid, JSONObject data, boolean isOrderReplace) {
        String name = isOrderReplace ? "Replace" : "Place";
        OrderData orderData = m_submitOrderRequestsMap.remove(oid);
        log("  orderData=" + orderData);
        if (orderData != null) {
            Object error = data.get("error");
            if (error == null) {
                Object id = data.get("id");
                log("  Order " + name + " OK. id=" + id);
                orderData.m_orderId = id.toString();

                // todo: check - can be partially/filled
                Object complete = data.get("complete"); // "complete":false; complete - boolean - Order completion status
                Object amount = data.get("amount"); // "amount":"0.01000000" : amount - decimal - Order amount
                Object pending = data.get("pending"); // "pending":"0.01000000" : pending - decimal - Order pending amount

                log("   complete=" + complete + "; amount=" + amount + "; pending=" + pending);

                if (!amount.equals(pending)) { // some part of order executed immediately at the time of order placing
                    double pend = Double.parseDouble(pending.toString());
                    double filled = orderData.m_amount - pend;
                    log("    pend=" + pend + "; amount=" + amount + "; filled=" + filled);
                    orderData.setFilled(filled); // will set m_status inside

                    boolean orderFilled = orderData.isFilled();
                    log("     orderFilled=" + orderFilled);

                    Execution.Type execType = orderFilled ? Execution.Type.fill : Execution.Type.partialFill;
                    Execution execution = new Execution(execType, data.toString());
                    orderData.addExecution(execution);
                } else {
                    orderData.m_status = OrderStatus.SUBMITTED;
                }

                if (complete.toString().equals("true")) {
                    OrderStatus status = orderData.m_status;
                    if (status != OrderStatus.FILLED) {
                        log("Error: on" + name + "Order: complete:false but orderStatus=" + status);
                    }
                }

                Pair pair = orderData.m_pair;
                ExchPairData pairData = m_exchange.getPairData(pair);
                LiveOrdersData liveOrders = pairData.getLiveOrders();
                liveOrders.addOrder(orderData);
            } else {
                console("  Error on" + name + "Order[" + oid + "]: " + error);
                orderData.m_status = OrderStatus.ERROR;
                orderData.m_error = error.toString();

                Execution.Type execType = Execution.Type.error;
                Execution execution = new Execution(execType, error.toString());
                orderData.addExecution(execution);
            }
            orderData.notifyListeners();
        } else {
            log("Error in on" + name + "Order, not expected oid=" + oid);
        }
    }

    @Override public void submitOrderReplace(String orderId, OrderData orderData) throws IOException {
        log("CexIo.submitOrderReplace() orderId=" + orderId + "; orderData=" + orderData);
        String orderSize = orderData.formatSize(orderData.m_amount);
        String orderPrice = orderData.formatPrice(orderData.m_price);
        String orderSide = orderData.m_side.getName();
        String clientOrderId = orderData.m_clientOrderId;
        log(" clientOrderId=" + clientOrderId + "; orderSize=" + orderSize + "; orderPrice=" + orderPrice + "; orderSide=" + orderSide);
        if (clientOrderId == null) {
            throw new RuntimeException("no clientOrderId");
        }
        String oid = clientOrderId + "_cancel-replace-order";
        m_submitOrderRequestsMap.put(oid, orderData);
        orderData.m_submitTime = System.currentTimeMillis();
        replaceOrder(m_session, oid, orderId, orderData.m_pair, orderSize, orderPrice, orderSide);
        orderData.m_status = OrderStatus.SUBMITTED;
        orderData.notifyListeners();
    }

    private static void replaceOrder(Session session, String oid, String orderId, Pair pair, String orderSize, String price, String side) throws IOException {
        //        {
        //            "e": "cancel-replace-order",
        //            "data": {
        //                  "order_id": "2477098",
        //                  "pair": ["BTC","USD"],
        //                  "amount": 0.04,
        //                  "price": "243.2500",
        //                  "type": "buy"
        //            },
        //            "oid": "1443464955209_16_cancel-replace-order"
        //        }
        send(session, "{\"e\": \"cancel-replace-order\",\"data\": {\"order_id\": \"" + orderId + "\"," + pairToStr(pair) + ",\"amount\": " + orderSize
                + ",\"price\": \"" + price + "\",\"type\": \"" + side + "\"},\"oid\": \"" + oid + "\"}");
    }

    private static void queryOrderBookSnapshoot(Session session, Pair pair, int depth) throws IOException {
        queryOrderBook(session, pair, depth, false);
    }

    private static void subscribeOrderBook(Session session, Pair pair, int depth) throws IOException {
        queryOrderBook(session, pair, depth, true);
    }

    private static void queryOrderBook(Session session, Pair pair, int depth, boolean streaming) throws IOException {
        //        {
        //            "e": "order-book-subscribe",
        //            "data": {
        //                "pair": [ "BTC", "USD" ],
        //                "subscribe": false,
        //                "depth": 2
        //            },
        //            "oid": "1435927928274_3_order-book-subscribe"
        //        }

        long timeMillis = System.currentTimeMillis();
        String oid = timeMillis + "_order-book-subscribe";
        send(session, "{ \"e\": \"order-book-subscribe\", \"data\": { " + pairToStr(pair) +
                ", \"subscribe\": " + streaming + ", \"depth\": " + depth + " }, \"oid\": \"" + oid + "\" }");
    }

    private static void queryBalance(Session session) throws IOException {
        //        {
        //            "e": "get-balance",
        //            "data": {},
        //            "oid": "1435927928274_2_get-balance"
        //        }

        long timeMillis = System.currentTimeMillis();
        send(session, "{ \"e\": \"get-balance\", \"data\": {}, \"oid\": \"" + timeMillis + "_get-balance\" }");
    }

    private static void queryTicket(Session session, String cur1, String cur2) throws Exception {
        //        {
        //            "e": "ticker",
        //            "data": [ "BTC", "USD" ],
        //            "oid": "1435927928274_1_ticker"
        //        }

        long timeMillis = System.currentTimeMillis();
        send(session, "{ \"e\": \"ticker\", \"data\": [ \"" + cur1 + "\", \"" + cur2 + "\" ], \"oid\": \"" + timeMillis + "_ticker\" }");
    }

    private void onConnected(Session session) throws IOException {
        // {"e":"connected"}
        // can be received in case WebSocket client has reconnected, which means that client needs to send 'authenticate'
        // request and subscribe for notifications, like by first connection

        log("onConnected ");

        m_exchange.notifyConnected();

        long timestamp = System.currentTimeMillis() / 1000;  // Note: java timestamp presented in milliseconds
        String signature = createSignature(timestamp, m_apiSecret, m_apiKey);

        //    {
        //        "e": "auth",
        //        "auth": {
        //            "key": "1WZbtMTbMbo2NsW12vOz9IuPM.",
        //            "signature": "02483c01efc26fac843dd34d0342d269bacf4daa906a32cb71806eb7467dcf58",
        //            "timestamp": 1448034533
        //        }
        //    }
        // signature - Client signature (digest of HMAC-rsa256 with client's API Secret Key, applied to the string, which is
        //             concatenation timestamp and API Key)
        // timestimp - timestimp in seconds, used for signature
        
        send(session, "{ \"e\": \"auth\", \"auth\": { \"key\": \"" + m_apiKey + "\", \"signature\": \""
                + signature + "\", \"timestamp\": " + timestamp + " } }");
    }

    private static void send(Session session, String str) throws IOException {
        log(">> send: " + str);
        m_rateLimiter.enter();
        RemoteEndpoint.Basic basicRemote = session.getBasicRemote();
        basicRemote.sendText(str);
    }

    private static String createSignature(long timestamp, String apiSecret, String apiKey) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(apiSecret.getBytes(),"HmacSHA256");
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(keySpec);
            String line = timestamp + apiKey;
            byte[] hmacBytes = mac.doFinal(line.getBytes());
            return Hex.bytesToHexLowerCase(hmacBytes);
        } catch (Exception e) {
            throw new RuntimeException("createSignature ERROR: " + e, e); // rethrow
        }
    }

    @Override public void connect(Exchange.IExchangeConnectListener listener) throws Exception {
        m_exchange.m_connectListener = listener;

        Endpoint endpoint = new Endpoint() {
            @Override public void onOpen(final Session session, EndpointConfig config) {
                log("onOpen");
                try {
                    m_session = session;
                    session.addMessageHandler(new MessageHandler.Whole<String>() {
                        @Override public void onMessage(String message) {
                            onMessageX(session, message);
                        }
                    });
                } catch (Exception e) {
                    log("onOpen ERROR: " + e);
                    e.printStackTrace();
                }
            }

            @Override public void onClose(Session session, CloseReason closeReason) {
                log("onClose: " + closeReason);

                m_exchange.m_live = false; // mark as disconnected

                m_exchange.m_threadPool.submit(new Runnable() {
                    @Override public void run() {
                        m_exchange.onDisconnected();
                        m_exchange.notifyDisconnected();
                    }
                });
            }

            @Override public void onError(Session session, Throwable thr) {
                log("onError: " + thr);
                thr.printStackTrace();
            }
        };
        log("connectToServer...");
        connectToServer(endpoint);
        log("session isOpen=" + m_session.isOpen());
    }

    @Override public void subscribeOrderBook(OrderBook orderBook, int depth) throws Exception {
        Pair pair = orderBook.m_pair;
        Currency fromCurr = pair.m_from;
        Currency toCurr = pair.m_to;

        log("subscribeOrderBook: " + pair + "; fromCurr: " + fromCurr + "; toCurr: " + toCurr);

        String cur1 = fromCurr.m_name.toUpperCase(); // "BTC"
        String cur2 = toCurr.m_name.toUpperCase(); // "USD"
        String key = cur1 + ":" + cur2;
        m_orderBooks.put(key, orderBook);

        subscribeOrderBook(m_session, pair, depth);
    }

    @Override public void queryOrderBookSnapshot(OrderBook orderBook, int depth) throws Exception {
        Pair pair = orderBook.m_pair;
        Currency fromCurr = pair.m_from;
        Currency toCurr = pair.m_to;

        log("queryOrderBookSnapshot: " + pair + "; fromCurr: " + fromCurr + "; toCurr: " + toCurr);

        String cur1 = fromCurr.m_name.toUpperCase(); // "BTC"
        String cur2 = toCurr.m_name.toUpperCase(); // "USD"
        String key = cur1 + ":" + cur2;
        m_orderBooks.put(key, orderBook);

        queryOrderBookSnapshoot(m_session, pair, depth);
    }

    @Override public void queryAccount() throws Exception {
        queryBalance(m_session);
    }

    @Override public void queryOrders(LiveOrdersData liveOrders) throws Exception {
        Pair pair = liveOrders.m_pair;
        Currency fromCurr = pair.m_from;
        Currency toCurr = pair.m_to;

        log("queryOrders: " + pair + "; fromCurr: " + fromCurr + "; toCurr: " + toCurr);

        String oid = System.currentTimeMillis() + "_open-orders";
        m_liveOrdersRequestsMap.put(oid, liveOrders);
        queryOpenOrders(m_session, oid, pair);
    }

    @Override public void cancelOrder(OrderData orderData) throws IOException {
        String oid = orderData.m_orderId + "_cancel-order";
        m_cancelOrderRequestsMap.put(oid, orderData);
        cancelOrder(m_session, oid, orderData.m_orderId);
        Execution.Type execType = Execution.Type.cancel;
        Execution execution = new Execution(execType, "cancel: " + orderData.toString());
        orderData.addExecution(execution);
        orderData.m_status = OrderStatus.CANCELING;
        orderData.notifyListeners();
    }

    @Override public void rateLimiterActive(boolean active) {
        m_rateLimiter.m_active = true;
    }

    private void onCancelOrder(final JSONObject jsonObject) {
        // {"e":"cancel-order","data":{"error":"There was an error while canceling your order: Invalid Order ID"},"oid":"1511575385_cancel-order","ok":"error"}
        // {"e":"cancel-order","data":{"order_id":"5067483259","fremains":"0.01000000"},"oid":"1511575384_cancel-order","ok":"ok"}
        // {"e":"cancel-order","data":{"error":"Rate limit exceeded","time":1518739156571},"oid":"5673152656_cancel-order"}
        m_exchange.m_threadPool.submit(new Runnable() {
            @Override public void run() {
                try {
                    String ok = (String) jsonObject.get("ok");
                    Object oid = jsonObject.get("oid");
                    JSONObject data = (JSONObject) jsonObject.get("data");
                    String error = (String) data.get("error");
                    log(" onCancelOrder[" + oid + "]: ok=" + ok + "; data=" + data + "; error=" + error);
                    OrderData orderData = m_cancelOrderRequestsMap.remove(oid);
                    log("  orderData=" + orderData);

                    if (Utils.equals(ok, "error") || (error != null)) {
                        console("   Error canceling order: " + error + "; oid=" + oid);
                        if (orderData != null) {
                            double remained = orderData.remained();
                            console("    remained=" + remained);

                            orderData.m_status = OrderStatus.ERROR;

                            Execution.Type execType = Execution.Type.error;
                            Execution execution = new Execution(execType, data.toString());
                            orderData.addExecution(execution);

                            orderData.notifyListeners();
                        }
                    } else if (Utils.equals(ok, "ok")) {
                        log("  order cancelled");

                        if (orderData != null) {
                            double remained = orderData.remained();

                            String fremainsStr = (String) data.get("fremains");
                            double fremains = Double.parseDouble(fremainsStr);
                            log("   remained=" + remained + "; fremainsStr=" + fremainsStr + "; fremains=" + fremains);

                            if (remained != fremains) {
                                log("ERROR: remains mismatch: remained=" + remained + "; fremains=" + fremains);
                            }

                            orderData.m_status = OrderStatus.CANCELLED;

                            Execution.Type execType = Execution.Type.cancelled;
                            Execution execution = new Execution(execType, data.toString());
                            orderData.addExecution(execution);

                            orderData.notifyListeners();
                        } else {
                            log("Error in onCancelOrder, not expected oid=" + oid + "; map.keys=" + new ArrayList<>(m_cancelOrderRequestsMap.keySet()));
                        }
                    } else {
                        throw new RuntimeException("onCancelOrder: unexpected ok=" + ok);
                    }
                } catch (Exception e) {
                    err("onCancelOrder error: " + e, e);
                }
            }
        });
    }

    private static void queryOpenOrders(Session session, String oid, Pair pair) throws IOException {
        //        {
        //            "e": "open-orders",
        //            "data": { "pair": [ "BTC", "USD" ] },
        //            "oid": "1435927928274_6_open-orders"
        //        }

        send(session, "{ \"e\": \"open-orders\", \"data\": { " + pairToStr(pair) + " }, " + "\"oid\": \"" + oid + "\" }");
    }

    private void processOpenOrders(final JSONObject jsonObject) throws Exception {
        m_exchange.m_threadPool.submit(new Runnable() {
            @Override public void run() {
                try {
                    // [{"amount":"0.01000000","price":"9000.0123","pending":"0.01000000","id":"5067483259","time":"1511569539812","type":"sell"}]
                    Object oid = jsonObject.get("oid");
                    JSONArray data = (JSONArray) jsonObject.get("data");
                    log(" processOpenOrders[" + oid + "]: data=" + data);

                    LiveOrdersData liveOrdersData = m_liveOrdersRequestsMap.get(oid);
                    log("  liveOrdersData=" + liveOrdersData);

                    if (liveOrdersData != null) {
                        for (Object order : data) {
                            log("  openOrder: " + order);
                            JSONObject jsonOrder = (JSONObject) order;
                            String orderId = (String) jsonOrder.get("id");
                            String type = (String) jsonOrder.get("type");
                            boolean isBuy = type.equals("buy");
                            OrderSide orderSide = OrderSide.get(isBuy);
                            String priceStr = (String) jsonOrder.get("price");
                            double price = Double.parseDouble(priceStr);

                            String amountStr = (String) jsonOrder.get("amount");

                            double amount = Double.parseDouble(amountStr);

                            String pendingStr = (String) jsonOrder.get("pending");
                            double pending = Double.parseDouble(pendingStr);
                            double filled = amount - pending;
                            log("   orderId: " + orderId
                                    + "; type: " + type // "type":"sell"
                                    + "; orderSide: " + orderSide
                                    + "; priceStr: " + priceStr // "price":"9000.0123"
                                    + "; price: " + price
                                    + "; amountStr: " + amountStr // "amount":"0.01000000"
                                    + "; amount: " + amount
                                    + "; pendingStr: " + pendingStr // "pending":"0.01000000"
                                    + "; pending: " + pending + "; filled=" + filled
                            );

                            OrderData orderData = new OrderData(m_exchange, orderId, liveOrdersData.m_pair, orderSide, OrderType.LIMIT, price, amount);
                            orderData.setFilled(filled);
                            liveOrdersData.addOrder(orderData);
                        }
                        liveOrdersData.notifyListener();
                    }
                } catch (Exception e) {
                    err("processOpenOrders error: " + e, e);
                }
            }
        });
    }

    @NotNull private static String pairToStr(Pair pair) {
        return "\"pair\": [ \"" + pair.m_from.name().toUpperCase() + "\", \"" + pair.m_to.name().toUpperCase() + "\" ]";
    }

    // https://cex.io/rest-api#trade-history
    public static List<TradeTickData> readTicks(long period) {
        // [{"type":"sell","date":"1519040965","amount":"0.02611252","price":"11142.3","tid":"6213558"},
        //  {"type":"buy","date":"1519040926","amount":"0.03700000","price":"11142.3","tid":"6213557"}]
        return null;
    }


    //---------------------------------------------------------------------------------
    private static class ReconnectHandler extends ClientManager.ReconnectHandler {
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


    //---------------------------------------------------------------------------------
    // CEX.IO API is limited to 600 requests per 10 minutes.
    // Server limits WebSocket client to 600 requests per 10 minutes.
    // << received: {"e":"cancel-order","data":{"error":"Rate limit exceeded","time":1518739156571},"oid":"5673152656_cancel-order"}
    public static class RateLimiter {
        public boolean m_active;
        private long m_lastTimestamp = 0;

        public void enter() {
            long millis = System.currentTimeMillis();
            if (m_active) {
                long diff = millis - m_lastTimestamp;
                if (diff < 1000) {
                    long sleep = 1000 - diff;
                    log(" RateLimiter: sleep " + sleep + " ms");
                    try {
                        Thread.sleep(sleep);
                    } catch (InterruptedException e) { /*noop*/ }
                }
            }
            m_lastTimestamp = millis;
        }
    }
}