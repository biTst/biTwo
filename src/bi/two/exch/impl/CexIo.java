package bi.two.exch.impl;

import bi.two.exch.*;
import bi.two.util.Hex;
import bi.two.util.Log;
import bi.two.util.MapConfig;
import bi.two.util.Utils;
import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.client.ClientProperties;
import org.glassfish.tyrus.container.jdk.client.JdkClientContainer;
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

// based on info from https://cex.io/websocket-api
//  to check: https://github.com/zackurben/cex.io-api-java  https://github.com/joshho/cex.io-api-java
public class CexIo extends BaseExchImpl {
    private static final String URL = "wss://ws.cex.io/ws/";

    private final Exchange m_exchange;
    private final String m_apiKey;
    private final String m_apiSecret;
    private Session m_session;
    private Exchange.IExchangeConnectListener m_exchangeConnectListener;
    private Map<String,OrderBook> m_orderBooks = new HashMap<>();
    private List<Currency> m_currencies = new ArrayList<>();
    private String[] m_supportedCurrencies = new String[]{"BTC", "USD", "EUR", "GHS", "BTG", "GBP", "BCH",};
    private Map<String,LiveOrdersData> m_liveOrdersMap = new HashMap<>();

    private static void log(String s) { Log.log(s); }
    private static void err(String s, Throwable t) { Log.err(s, t); }

    public CexIo(MapConfig config, Exchange exchange) {
        m_exchange = exchange;
        m_apiKey = config.getString("cex_apiKey");
        m_apiSecret = config.getString("cex_apiSecret");

        for (String name : m_supportedCurrencies) {
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
                processOpenOrders(session, jsonObject);
            } else if (Utils.equals(e, "place-order")) {
                onPlaceOrder(session, jsonObject);
            } else if (Utils.equals(e, "cancel-order")) {
                onCancelOrder(session, jsonObject);
            } else if (Utils.equals(e, "order")) {
                onOrder(session, jsonObject);
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
        JSONObject data = (JSONObject) jsonObject.get("data");
        log(" onTx: data=" + data);
    }

    private static void onObalance(Session session, JSONObject jsonObject) {
        // {"symbol":"BTC","balance":"1000000"}
        JSONObject data = (JSONObject) jsonObject.get("data");
        log(" onObalance: data=" + data);

        Object symbol = data.get("symbol");
        Object balance = data.get("balance");
        log("  symbol=" + symbol + ";  balance=" + balance);
    }

    private static void onBalance(Session session, JSONObject jsonObject) {
        // {"symbol":"BTC","balance":"1431350"}
        JSONObject data = (JSONObject) jsonObject.get("data");
        log(" onBalance: data=" + data);

        Object symbol = data.get("symbol");
        Object balance = data.get("balance");
        log("  symbol=" + symbol + ";  balance=" + balance);
    }

    private static void onOrder(Session session, JSONObject jsonObject) {
        // {"amount":1000000,"price":"9000.0123","fee":"0.17","remains":"1000000","id":"5067483259","time":"1511569539812",
        //  "type":"sell","pair":{"symbol1":"BTC","symbol2":"USD"}}
        JSONObject data = (JSONObject) jsonObject.get("data");
        log(" onOrder: data=" + data);
    }

    private static void onCancelOrder(Session session, JSONObject jsonObject) {
        // {"e":"cancel-order","data":{"error":"There was an error while canceling your order: Invalid Order ID"},"oid":"1511575385_cancel-order","ok":"error"}
        // {"e":"cancel-order","data":{"order_id":"5067483259","fremains":"0.01000000"},"oid":"1511575384_cancel-order","ok":"ok"}
        String ok = (String) jsonObject.get("ok");
        Object oid = jsonObject.get("oid");
        JSONObject data = (JSONObject) jsonObject.get("data");
        log(" onCancelOrder[" + oid + "]: ok=" + ok + "; data=" + data);

        if (Utils.equals(ok, "error")) {
            String error = (String) data.get("error");
            log("  Error canceling order: " + error);
        } else if (Utils.equals(ok, "ok")) {
            log("  order cancelled");
        } else {
            throw new RuntimeException("onCancelOrder: unexpected ok=" + ok);
        }
    }

    private static void onPlaceOrder(Session session, JSONObject jsonObject) {
        // {"error":"There was an error while placing your order: Invalid amount"}
        // {"error":"Error: Place order error: Insufficient funds."}
        // {"amount":"0.01000000","price":"9000.0123","pending":"0.01000000","id":"5067483259","time":1511569539812,"complete":false,"type":"sell"}
        Object oid = jsonObject.get("oid");
        JSONObject data = (JSONObject) jsonObject.get("data");
        log(" onPlaceOrder[" + oid + "]: data=" + data);

        Object error = data.get("error");
        if (error != null) {
            log("  Error placing order[" + oid + "]: " + error);
        } else {
            Object id = data.get("id");
            log("  Order place OK. id=" + id);
        }
    }

    private static void cancelOrder(Session session, String orderToCancelId) throws IOException {
        log(" cancelOrder() orderToCancelId=" + orderToCancelId);

//        {
//            "e": "cancel-order",
//            "data": { "order_id": "2477098" },
//            "oid": "1435927928274_12_cancel-order"
//        }

        long timeMillis = System.currentTimeMillis();
        send(session, "{ \"e\": \"cancel-order\", \"data\": { \"order_id\": \""
                + orderToCancelId + "\" }, \"oid\": \"" + timeMillis + "_cancel-order\" }");
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
                    if (orderBook == null) {
                        throw new RuntimeException("no orderBook for pair=" + pair);
                    }

                    List<OrderBook.OrderBookEntry> aBids = parseBook(bids);
                    List<OrderBook.OrderBookEntry> aAsks = parseBook(asks);

//        log(" input orderBook[" + orderBook.getPair() + "]: " + orderBook);
                    orderBook.update(aBids, aAsks);
//        log(" updated orderBook[" + orderBook.getPair() + "]: " + orderBook.toString(2));
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

    private static void unsubscribeOrderBook(Session session) throws IOException {
//        {
//            "e": "order-book-unsubscribe",
//            "data": {
//                "pair": [ "BTC", "USD" ] },
//            "oid": "1435927928274_4_order-book-unsubscribe"
//        }

        long timeMillis = System.currentTimeMillis();
        send(session, "{ \"e\": \"order-book-unsubscribe\", \"data\": { \"pair\": [ \"BTC\", \"USD\" ] }, \"oid\": \"" + timeMillis + "_order-book-unsubscribe\" }");
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

        m_exchange.m_accountListener.onUpdated();
    }

    private String parseBalance(JSONObject balance) {
        // {"BTC":"0.02431350","EUR":"0.16","GHS":"0.00000000","BTG":"0.50817888","GBP":"0.00","BCH":"0.00000000",
        //  "USD":"0.04","ETH":"0.00000000","ZEC":"0.00000000","DASH":"0.00000000","RUB":"0.00"}

        StringBuilder sb = new StringBuilder();

        for (String supportedCurrency : m_supportedCurrencies) {
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
        log("  error: " + error);
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
            if (m_exchangeConnectListener != null) {
                m_exchangeConnectListener.onConnected();
            }
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
        String cur1 = "BTC";
        String cur2 = "USD";
        queryOrderBookSnapshoot(session, cur1, cur2, depth);

        Thread.sleep(1000);
        String oid = System.currentTimeMillis() + "_open-orders";
        queryOpenOrders(session, oid, "BTC", "USD");

        Thread.sleep(1000);
        String orderSize = "0.01";
        String price = "9000.0123";
        String side = "sell";
        placeOrder(session, Pair.get(Currency.BTC, Currency.USD), orderSize, price, side);
    }

    private static void queryTickers(Session session) throws IOException {
        //        {
        //            e: "subscribe",
        //            rooms: [ "tickers" ]
        //        }
        send(session, "{\"e\": \"subscribe\", \"rooms\": [ \"tickers\" ]}");
    }

    private static void placeOrder(Session session, Pair pair, String orderSize, String price, String side) throws IOException {
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

        long timeMillis = System.currentTimeMillis();
        send(session, "{ \"e\": \"place-order\", \"data\": { \"pair\": [ \"" + pair.m_from + "\", \"" + pair.m_to + "\" ], \"amount\": " + orderSize
                + ", \"price\": " + price + ", \"type\": \"" + side + "\" }, \"oid\": \"" + timeMillis + "_place-order\" }");
    }

    private static void replaceOrder(Session session, String orderId, Pair pair, String orderSize, String price, String side) throws IOException {
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

        long timeMillis = System.currentTimeMillis();
        send(session, "{\"e\": \"cancel-replace-order\",\"data\": {\"order_id\": \"" + orderId + "\",\"pair\": [\"" + pair.m_from + "\",\"" + pair.m_to + "\"],\"amount\": " + orderSize + ",\"price\": \"" + price + "\",\"type\": \"" + side + "\"},\"oid\": \"" + timeMillis + "_cancel-replace-order\"}");
    }

    private static void queryOrderBookSnapshoot(Session session, String cur1, String cur2, int depth) throws IOException {
        queryOrderBook(session, cur1, cur2, depth, false);
    }

    private static void subscribeOrderBook(Session session, String cur1, String cur2, int depth) throws IOException {
        queryOrderBook(session, cur1, cur2, depth, true);
    }

    private static void queryOrderBook(Session session, String cur1, String cur2, int depth, boolean streaming) throws IOException {
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
        send(session, "{ \"e\": \"order-book-subscribe\", \"data\": { \"pair\": [ \"" + cur1 + "\", \""
                + cur2 + "\" ], \"subscribe\": " + streaming + ", \"depth\": " + depth + " }, \"oid\": \"" + oid + "\" }");
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

    @Override public void connect(Exchange.IExchangeConnectListener iExchangeConnectListener) throws Exception {
        m_exchangeConnectListener = iExchangeConnectListener;

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
                m_exchange.onDisconnected();
                if (m_exchangeConnectListener != null) {
                    m_exchangeConnectListener.onDisconnected();
                }
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
        subscribeOrderBook(m_session, cur1, cur2, depth);
        String key = cur1 + ":" + cur2;
        m_orderBooks.put(key, orderBook);
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

        queryOrderBookSnapshoot(m_session, cur1, cur2, depth);
    }

    @Override public void queryAccount() throws Exception {
        queryBalance(m_session);
    }

    @Override public void queryOrders(LiveOrdersData liveOrders) throws Exception {
        Pair pair = liveOrders.m_pair;
        Currency fromCurr = pair.m_from;
        Currency toCurr = pair.m_to;

        log("queryOrders: " + pair + "; fromCurr: " + fromCurr + "; toCurr: " + toCurr);

        String cur1 = fromCurr.m_name.toUpperCase(); // "BTC"
        String cur2 = toCurr.m_name.toUpperCase(); // "USD"

        String oid = System.currentTimeMillis() + "_open-orders";
        m_liveOrdersMap.put(oid, liveOrders);
        queryOpenOrders(m_session, oid, cur1, cur2);
    }

    @Override public void submitOrder(OrderData orderData) throws IOException {
        String orderSize = orderData.formatSize(orderData.m_amount);
        String orderPrice = orderData.formatPrice(orderData.m_price);
        String orderSide = orderData.m_side.getName();
        placeOrder(m_session, orderData.m_pair, orderSize, orderPrice, orderSide);
    }

    @Override public void cancelOrder(OrderData orderData) throws IOException {
        cancelOrder(m_session, orderData.m_orderId);
    }

    private static void queryOpenOrders(Session session, String oid, String cur1, String cur2) throws IOException {
        //        {
        //            "e": "open-orders",
        //            "data": { "pair": [ "BTC", "USD" ] },
        //            "oid": "1435927928274_6_open-orders"
        //        }

        send(session, "{ \"e\": \"open-orders\", \"data\": { \"pair\": [ \"" + cur1 + "\", \"" + cur2 + "\" ] }, \"oid\": \"" + oid + "\" }");
    }

    private void processOpenOrders(Session session, final JSONObject jsonObject) throws Exception {
        m_exchange.m_threadPool.submit(new Runnable() {
            @Override public void run() {
                try {
                    // [{"amount":"0.01000000","price":"9000.0123","pending":"0.01000000","id":"5067483259","time":"1511569539812","type":"sell"}]
                    Object oid = jsonObject.get("oid");
                    JSONArray data = (JSONArray) jsonObject.get("data");
                    log(" processOpenOrders[" + oid + "]: data=" + data);

                    LiveOrdersData liveOrdersData = m_liveOrdersMap.get(oid);
                    log("  liveOrdersData=" + liveOrdersData);

                    if (liveOrdersData != null) {
                        for (Object order : data) {
                            log("  openOrder: " + order);
                            JSONObject jsonOrder = (JSONObject) order;
                            String orderId = (String) jsonOrder.get("id");
                            log("   orderId: " + orderId);
                            String type = (String) jsonOrder.get("type");
                            log("   type: " + type); // "type":"sell"
                            boolean isBuy = type.equals("buy");
                            OrderSide orderSide = OrderSide.get(isBuy);
                            String priceStr = (String) jsonOrder.get("price");
                            log("   priceStr: " + priceStr); // "price":"9000.0123"
                            double price = Double.parseDouble(priceStr);
                            log("    price: " + price);
                            String amountStr = (String) jsonOrder.get("amount");
                            log("   amountStr: " + amountStr); // "amount":"0.01000000"
                            double amount = Double.parseDouble(amountStr);
                            log("    amount: " + amount);
                            String pendingStr = (String) jsonOrder.get("pending");
                            log("   pendingStr: " + pendingStr); // "pending":"0.01000000"
                            double pending = Double.parseDouble(pendingStr);
                            log("    pending: " + pending);

                            OrderData orderData = new OrderData(m_exchange, orderId, liveOrdersData.m_pair, orderSide, OrderType.LIMIT, price, amount);
                            orderData.setFilled(amount - pending);
                            liveOrdersData.addOrder(orderData);
                        }
                        liveOrdersData.notifyListener();
                    }


//        if (orderToCancelId != null) {
//            cancelOrder(session, orderToCancelId);
//
//            // cancel with invalid order id
////            Thread.sleep(1000);
////            cancelOrder(session, orderToCancelId + "_x");
//        }
                } catch (Exception e) {
                    err("processOpenOrders error: " + e, e);
                }
            }
        });
    }

    //---------------------------------------------------------------------------------
    private static class ReconnectHandler extends ClientManager.ReconnectHandler {
        private int m_counter;
        private int m_connectFailure;

        @Override public boolean onDisconnect(CloseReason closeReason) {
            m_counter++;
            m_connectFailure = 0;
            log("onDisconnect() closeReason=" + closeReason +
                   "\n Reconnecting... (reconnect count: " + m_counter + ")");
            return true;
        }

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

        @Override public long getDelay() {
            return 1;
        }
    }
}