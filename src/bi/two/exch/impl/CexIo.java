package bi.two.exch.impl;

import bi.two.exch.MarketConfig;
import bi.two.exch.Pair;
import bi.two.util.Hex;
import bi.two.util.MapConfig;
import bi.two.util.Utils;
import org.glassfish.tyrus.client.ClientManager;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.websocket.*;
import java.io.IOException;
import java.net.URI;

// based on info from https://cex.io/websocket-api
public class CexIo {
    private static final String URL = "wss://ws.cex.io/ws/";
    private static final String CONFIG = "cfg/cex.io.properties";

    private static String s_apiSecret;
    private static String s_apiKey;


    public static void main(String[] args) {
//        test();
        main_();
    }

    private static void test() {
        String apiSecret = "1IuUeW4IEWatK87zBTENHj1T17s";
        String apiKey = "1WZbtMTbMbo2NsW12vOz9IuPM";
        long timestamp = 1448034533; // 1448034533 means Fri Nov 20 2015 17:48:53 GMT+0200 (EET)
        String signature = "7d581adb01ad22f1ed38e1159a7f08ac5d83906ae1a42fe17e7d977786fe9694"; // expected signature

        String sign = createSignature(timestamp, apiSecret, apiKey);
        System.out.println("signature=" + sign);
        System.out.println(" expected=" + signature);
        System.out.println("   equals=" + signature.equals(sign));
    }

    private static void main_() {
        try {
            MarketConfig.initMarkets();

            MapConfig config = new MapConfig();
            config.loadAndEncrypted(CONFIG);

            s_apiSecret = config.getString("cex_apiSecret");
            s_apiKey = config.getString("cex_apiKey");

            ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();
            ClientManager client = ClientManager.createClient();
            Session session = client.connectToServer(new Endpoint() {
                @Override public void onOpen(final Session session, EndpointConfig config) {
                    System.out.println("onOpen");
                    try {
                        session.addMessageHandler(new MessageHandler.Whole<String>() {
                            @Override public void onMessage(String message) {
                                onMessageX(session, message);
                            }
                        });
                    } catch (Exception e) {
                        System.out.println("onOpen ERROR: " + e);
                        e.printStackTrace();
                    }
                }

                @Override public void onClose(Session session, CloseReason closeReason) {
                    System.out.println("onClose: " + closeReason);
                }

                @Override public void onError(Session session, Throwable thr) {
                    System.out.println("onError: " + thr);
                    thr.printStackTrace();
                }
            }, cec, new URI(URL));
            System.out.println("session isOpen=" + session.isOpen() + "; session=" + session);
            Thread.sleep(125000);
            System.out.println("done");
        } catch (Exception e) {
            System.out.println("ERROR: " + e);
            e.printStackTrace();
        }
    }

    private static void onMessageX(Session session, String message) {
        System.out.println("Received message: " + message);
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
                onOpenOrders(session, jsonObject);
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
            System.out.println("onMessageX ERROR: " + e);
            e.printStackTrace();
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
        System.out.println(" onOhlcv24[" + pair + "]: data=" + data);
    }

    private static void onHistoryUpdate(Session session, JSONObject jsonObject) {
//        [
//            ['sell', '1457703218519', '41140000', '423.7125', '735480'],
//            ... 0 to n records
//        ]

        // History update - 

        JSONArray data = (JSONArray) jsonObject.get("data");
        System.out.println(" onHistoryUpdate: data=" + data);
    }

    private static void onHistory(Session session, JSONObject jsonObject) {
//            [
//              'buy:1457703205200:46860000:423.7125:735479',
//              'sell:1457703191363:35430000:423.7125:735478',
//              ... 201 items
//            ]

        // History snapshot -  trade history of 201 records

        JSONArray data = (JSONArray) jsonObject.get("data");
        System.out.println(" onHistory: data=" + data);
    }

    private static void onMdGroupped(Session session, JSONObject jsonObject) {
        JSONObject data = (JSONObject) jsonObject.get("data");
        System.out.println(" onMdGroupped: data=" + data);

        // Market Depth
        
        Object buy = data.get("buy");
        Object sell = data.get("sell");
        Object id = data.get("id");
        Object pair = data.get("pair");
        System.out.println("  buy=" + buy);
        System.out.println("  sell=" + sell);
        System.out.println("  pair=" + pair + ";  id=" + id);
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
        System.out.println(" onMd: data=" + data);

        Object buy = data.get("buy");
        Object sell = data.get("sell");
        Object id = data.get("id");
        Object pair = data.get("pair");
        System.out.println("  buy=" + buy);
        System.out.println("  sell=" + sell);
        System.out.println("  pair=" + pair + ";  id=" + id);
    }

    private static void onTx(Session session, JSONObject jsonObject) {
        // {"d":"user:up100734377:a:BTC","c":"order:5067483259:a:BTC","a":"0.01000000","ds":"0.01431350","cs":"0.01000000",
        //  "user":"up100734377","symbol":"BTC","order":5067483259,"amount":"-0.01000000","type":"sell","time":"2017-11-25T00:25:39.812Z",
        //  "balance":"0.01431350","id":"5067483260"}
        JSONObject data = (JSONObject) jsonObject.get("data");
        System.out.println(" onTx: data=" + data);
    }

    private static void onObalance(Session session, JSONObject jsonObject) {
        // {"symbol":"BTC","balance":"1000000"}
        JSONObject data = (JSONObject) jsonObject.get("data");
        System.out.println(" onObalance: data=" + data);

        Object symbol = data.get("symbol");
        Object balance = data.get("balance");
        System.out.println("  symbol=" + symbol + ";  balance=" + balance);
    }

    private static void onBalance(Session session, JSONObject jsonObject) {
        // {"symbol":"BTC","balance":"1431350"}
        JSONObject data = (JSONObject) jsonObject.get("data");
        System.out.println(" onBalance: data=" + data);

        Object symbol = data.get("symbol");
        Object balance = data.get("balance");
        System.out.println("  symbol=" + symbol + ";  balance=" + balance);
    }

    private static void onOrder(Session session, JSONObject jsonObject) {
        // {"amount":1000000,"price":"9000.0123","fee":"0.17","remains":"1000000","id":"5067483259","time":"1511569539812",
        //  "type":"sell","pair":{"symbol1":"BTC","symbol2":"USD"}}
        JSONObject data = (JSONObject) jsonObject.get("data");
        System.out.println(" onOrder: data=" + data);
    }

    private static void onCancelOrder(Session session, JSONObject jsonObject) {
        // {"e":"cancel-order","data":{"error":"There was an error while canceling your order: Invalid Order ID"},"oid":"1511575385_cancel-order","ok":"error"}
        // {"e":"cancel-order","data":{"order_id":"5067483259","fremains":"0.01000000"},"oid":"1511575384_cancel-order","ok":"ok"}
        String ok = (String) jsonObject.get("ok");
        Object oid = jsonObject.get("oid");
        JSONObject data = (JSONObject) jsonObject.get("data");
        System.out.println(" onCancelOrder[" + oid + "]: ok=" + ok + "; data=" + data);

        if (Utils.equals(ok, "error")) {
            String error = (String) data.get("error");
            System.out.println("  Error canceling order: " + error);
        } else if (Utils.equals(ok, "ok")) {
            System.out.println("  order cancelled");
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
        System.out.println(" onPlaceOrder[" + oid + "]: data=" + data);

        Object error = data.get("error");
        if (error != null) {
            System.out.println("  Error placing order[" + oid + "]: " + error);
        } else {

        }
    }

    private static void onOpenOrders(Session session, JSONObject jsonObject) throws Exception {
        // [{"amount":"0.01000000","price":"9000.0123","pending":"0.01000000","id":"5067483259","time":"1511569539812","type":"sell"}]
        Object oid = jsonObject.get("oid");
        JSONArray data = (JSONArray) jsonObject.get("data");
        System.out.println(" onOpenOrders[" + oid + "]: data=" + data);
        String orderToCancelId = null;
        for (Object order : data) {
            System.out.println("  openOrder: " + order);
            JSONObject jsonOrder = (JSONObject) order;
            String orderId = (String) jsonOrder.get("id");
            System.out.println("   orderId: " + orderId);
            orderToCancelId = orderId; // will cancel last order
        }

        if (orderToCancelId != null) {
            cancelOrder(session, orderToCancelId);

            // cancel with invalid order id
//            Thread.sleep(1000);
//            cancelOrder(session, orderToCancelId + "_x");
        }
    }

    private static void cancelOrder(Session session, String orderToCancelId) throws IOException {
        System.out.println(" cancelOrder() orderToCancelId=" + orderToCancelId);

//        {
//            "e": "cancel-order",
//            "data": { "order_id": "2477098" },
//            "oid": "1435927928274_12_cancel-order"
//        }

        long timeMillis = System.currentTimeMillis() / 1000;
        send(session, "{ \"e\": \"cancel-order\", \"data\": { \"order_id\": \""
                + orderToCancelId + "\" }, \"oid\": \"" + timeMillis + "_cancel-order\" }");
    }

    private static void onOrderBookUnsubscribe(Session session, JSONObject jsonObject) {
        Object oid = jsonObject.get("oid");
        JSONObject data = (JSONObject) jsonObject.get("data");
        System.out.println(" onOrderBookUnsubscribe[" + oid + "]: data=" + data);
    }

    private static void onOrderBookSubscribe(Session session, JSONObject jsonObject) throws IOException {
        // {"timestamp":1511566229,
        //  "bids":[[8228.1220,0.00606695],[8226.6111,0.01433840]],
        //  "asks":[[8231.0941,0.01000000],[8231.1303,0.64500000]],
        //  "pair":"BTC:USD",
        //  "id":145911102,
        //  "sell_total":"1875.41098508",
        //  "buy_total":"6933944.35"}
        Object oid = jsonObject.get("oid");
        JSONObject data = (JSONObject) jsonObject.get("data");
        System.out.println(" onOrderBookSubscribe[" + oid + "]: data=" + data);

        Object bids = data.get("bids");
        Object asks = data.get("asks");
        System.out.println("  bids=" + bids);
        System.out.println("  asks=" + asks);

        unsubscribeOrderBook(session);
    }

    private static void unsubscribeOrderBook(Session session) throws IOException {
//        {
//            "e": "order-book-unsubscribe",
//            "data": {
//                "pair": [ "BTC", "USD" ] },
//            "oid": "1435927928274_4_order-book-unsubscribe"
//        }

        long timeMillis = System.currentTimeMillis() / 1000;
        send(session, "{ \"e\": \"order-book-unsubscribe\", \"data\": { \"pair\": [ \"BTC\", \"USD\" ] }, \"oid\": \"" + timeMillis + "_order-book-unsubscribe\" }");
    }

    private static void onGetBalance(Session session, JSONObject jsonObject) {
        // {"balance":{"BTC":"0.02431350","EUR":"0.16","GHS":"0.00000000","BTG":"0.50817888","GBP":"0.00","BCH":"0.00000000",
        //             "USD":"0.04","ETH":"0.00000000","ZEC":"0.00000000","DASH":"0.00000000","RUB":"0.00"},
        //  "obalance":{"BTC":"0.00000000","EUR":"0.00","GHS":"0.00000000","GBP":"0.00","BCH":"0.00000000","USD":"0.00",
        //              "ETH":"0.00000000","ZEC":"0.00000000","DASH":"0.00000000","RUB":"0.00"},
        //  "time":1511566228653}
        Object oid = jsonObject.get("oid");
        JSONObject data = (JSONObject) jsonObject.get("data");
        System.out.println(" onGetBalance[" + oid + "]: data=" + data);

        JSONObject balance = (JSONObject) data.get("balance");
        JSONObject obalance = (JSONObject) data.get("obalance");
        System.out.println("  balance: " + parseBalance(balance));
        System.out.println("  obalance: " + parseBalance(obalance));
    }

    private static String parseBalance(JSONObject balance) {
        // {"BTC":"0.02431350","EUR":"0.16","GHS":"0.00000000","BTG":"0.50817888","GBP":"0.00","BCH":"0.00000000",
        //  "USD":"0.04","ETH":"0.00000000","ZEC":"0.00000000","DASH":"0.00000000","RUB":"0.00"}

        StringBuilder sb = new StringBuilder();
        append(sb, balance, "BTC");
        append(sb, balance, "EUR");
        append(sb, balance, "GHS");
        append(sb, balance, "BTG");
        append(sb, balance, "GBP");
        append(sb, balance, "BCH");
        if (sb.length() > 0) {
            sb.replace(0,2, "[");
        }
        sb.append("]");
        return sb.toString();
    }

    private static void append(StringBuilder sb, JSONObject balance, String name) {
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
        System.out.println(" onTicker[" + oid + "]: data=" + data);

        Object bid = data.get("bid");
        Object ask = data.get("ask");
        JSONArray pairArray = (JSONArray) data.get("pair");
        String cur1 = (String) pairArray.get(0);
        String cur2 = (String) pairArray.get(1);
        String pairName = cur1 + "_" + cur2;
        Pair pair = Pair.getByNameInt(pairName.toLowerCase());
        System.out.println("  " + pairName + ":  bid=" + bid + "; ask=" + ask + "      pair=" + pair);
    }

    private static void onTick(Session session, JSONObject jsonObject) {
        // {"price":"463.2","symbol1":"ETH","symbol2":"USD"}
        Object data = jsonObject.get("data");
        System.out.println(" onTick: data=" + data);
    }

    private static void onDisconnecting(Session session, JSONObject jsonObject) {
        Object reason = jsonObject.get("reason");
        System.out.println(" onDisconnecting: " + reason);
    }

    private static void onPing(Session session, JSONObject jsonObject) throws IOException {
        System.out.println(" got ping: " + jsonObject);
        //cexioWs.send(JSON.stringify({e: "pong"}));
        send(session, "{\"e\": \"pong\"}");
    }

    private static void onAuth(Session session, JSONObject jsonObject) throws Exception {
        // {"e":"auth","data":{"error":"Invalid API key"},"ok":"error"}
        JSONObject data = (JSONObject) jsonObject.get("data");
        System.out.println(" data: " + data);
        String error = (String) data.get("error");
        System.out.println("  error: " + error);
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
        System.out.println("  ok: " + ok);
        if (Utils.equals(ok, "ok")) {
            onAuthenticated(session);
        } else {
            throw new RuntimeException("unexpected auth response: " + jsonObject);
        }
    }

    private static void onAuthenticated(Session session) throws Exception {
System.out.println("onAuthenticated");
//        {
//            "e": "subscribe",
//            "rooms": ["pair-BTC-USD"]
//        }

        send(session, "{ \"e\": \"subscribe\", \"rooms\": [\"pair-BTC-USD\"] }");
    }

    private static void __onAuthenticated(Session session) throws Exception {
System.out.println("onAuthenticated");
//        cexioWs.send(JSON.stringify({
//                e: "subscribe",
//                rooms: [
//                  "tickers"
//                 ]
//        }));
//        send(session, "{\"e\": \"subscribe\", \"rooms\": [ \"tickers\" ]}");

        queryTicket(session, "BTC", "USD");
        queryTicket(session, "BCH", "USD");
        queryTicket(session, "BCH", "BTC");

        //        {
//            "e": "get-balance",
//            "data": {},
//            "oid": "1435927928274_2_get-balance"
//        }

        Thread.sleep(1000);
        long timeMillis = System.currentTimeMillis() / 1000;
        send(session, "{ \"e\": \"get-balance\", \"data\": {}, \"oid\": \"" + timeMillis + "_get-balance\" }");

//        {
//            "e": "order-book-subscribe",
//            "data": {
//                "pair": [ "BTC", "USD" ],
//                "subscribe": false,
//                "depth": 2
//            },
//            "oid": "1435927928274_3_order-book-subscribe"
//        }

        Thread.sleep(1000);
        timeMillis = System.currentTimeMillis() / 1000;
        String depth = "3";
        send(session, "{ \"e\": \"order-book-subscribe\", \"data\": { \"pair\": [ \"BTC\", \"USD\" ], \"subscribe\": false, \"depth\": "
                + depth + " }, \"oid\": \"" + timeMillis + "_order-book-subscribe\" }");

//        {
//            "e": "open-orders",
//            "data": { "pair": [ "BTC", "USD" ] },
//            "oid": "1435927928274_6_open-orders"
//        }

        Thread.sleep(1000);
        timeMillis = System.currentTimeMillis() / 1000;
        send(session, "{ \"e\": \"open-orders\", \"data\": { \"pair\": [ \"BTC\", \"USD\" ] }, \"oid\": \"" + timeMillis + "_open-orders\" }");

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

        Thread.sleep(1000);
        timeMillis = System.currentTimeMillis() / 1000;
        String orderSize = "0.01";
        String price = "9000.0123";
        String side = "sell";
        send(session, "{ \"e\": \"place-order\", \"data\": { \"pair\": [ \"BTC\", \"USD\" ], \"amount\": " + orderSize
                + ", \"price\": " + price + ", \"type\": \"" + side + "\" }, \"oid\": \"" + timeMillis + "_place-order\" }");
    }

    private static void queryTicket(Session session, String cur1, String cur2) throws Exception {
        //        {
        //            "e": "ticker",
        //            "data": [ "BTC", "USD" ],
        //            "oid": "1435927928274_1_ticker"
        //        }

        Thread.sleep(1000);
        long timeMillis = System.currentTimeMillis() / 1000;
        send(session, "{ \"e\": \"ticker\", \"data\": [ \""+cur1+"\", \""+cur2+"\" ], \"oid\": \"" + timeMillis + "_ticker\" }");
    }

    private static void onConnected(Session session) throws IOException {
        // {"e":"connected"}
        // can be received in case WebSocket client has reconnected, which means that client needs to send 'authenticate'
        // request and subscribe for notifications, like by first connection

        long timestamp = System.currentTimeMillis() / 1000;  // Note: java timestamp presented in milliseconds
        String signature = createSignature(timestamp, s_apiSecret, s_apiKey);

        //    {
        //        "e": "auth",
        //            "auth": {
        //                "key": "1WZbtMTbMbo2NsW12vOz9IuPM.",
        //                "signature": "02483c01efc26fac843dd34d0342d269bacf4daa906a32cb71806eb7467dcf58",
        //                "timestamp": 1448034533
        //          }
        //    }
        // signature - Client signature (digest of HMAC-rsa256 with client's API Secret Key, applied to the string, which is
        //             concatenation timestamp and API Key)
        // timestimp - timestimp in seconds, used for signature
        String jsonStr = "{ \"e\": \"auth\", \"auth\": { \"key\": \"" + s_apiKey + "\", \"signature\": \"" + signature + "\", \"timestamp\": " + timestamp + " } }";
System.out.println("jsonStr = " + jsonStr);
        send(session, jsonStr);
    }

    private static void send(Session session, String str) throws IOException {
        System.out.println(">> send: " + str);
        session.getBasicRemote().sendText(str);
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
}