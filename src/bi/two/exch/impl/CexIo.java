package bi.two.exch.impl;

import bi.two.util.Hex;
import bi.two.util.MapConfig;
import bi.two.util.Utils;
import org.glassfish.tyrus.client.ClientManager;
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
            MapConfig config = new MapConfig();
            config.loadAndEncrypted(CONFIG);

            s_apiSecret = config.getString("cex_apiSecret");
System.out.println("s_apiSecret = " + s_apiSecret);
            s_apiKey = config.getString("cex_apiKey");
System.out.println("s_apiKey = " + s_apiKey);

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
            } else if (Utils.equals(e, "order")) {
                onOrder(session, jsonObject);
            } else if (Utils.equals(e, "balance")) {
                onBalance(session, jsonObject);
            } else if (Utils.equals(e, "obalance")) {
                onObalance(session, jsonObject);
            } else if (Utils.equals(e, "tx")) {
                onTx(session, jsonObject);
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

        Object symbol = jsonObject.get("symbol");
        Object balance = jsonObject.get("balance");
        System.out.println("  symbol=" + symbol + ";  balance=" + balance);
    }

    private static void onBalance(Session session, JSONObject jsonObject) {
        // {"symbol":"BTC","balance":"1431350"}
        JSONObject data = (JSONObject) jsonObject.get("data");
        System.out.println(" onBalance: data=" + data);

        Object symbol = jsonObject.get("symbol");
        Object balance = jsonObject.get("balance");
        System.out.println("  symbol=" + symbol + ";  balance=" + balance);
    }

    private static void onOrder(Session session, JSONObject jsonObject) {
        // {"amount":1000000,"price":"9000.0123","fee":"0.17","remains":"1000000","id":"5067483259","time":"1511569539812",
        //  "type":"sell","pair":{"symbol1":"BTC","symbol2":"USD"}}
        JSONObject data = (JSONObject) jsonObject.get("data");
        System.out.println(" onOrder: data=" + data);
    }

    private static void onPlaceOrder(Session session, JSONObject jsonObject) {
        // {"error":"There was an error while placing your order: Invalid amount"}
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

    private static void onOpenOrders(Session session, JSONObject jsonObject) {
        // []
        Object oid = jsonObject.get("oid");
        Object data = jsonObject.get("data");
        System.out.println(" onOpenOrders[" + oid + "]: data=" + data);
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
        System.out.println("  bid=" + bid + "; ask=" + ask);
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

    private static void onAuthenticated(Session session) throws IOException, InterruptedException {
System.out.println("onAuthenticated");
//        cexioWs.send(JSON.stringify({
//                e: "subscribe",
//                rooms: [
//                  "tickers"
//                 ]
//        }));
//        send(session, "{\"e\": \"subscribe\", \"rooms\": [ \"tickers\" ]}");

//        {
//            "e": "ticker",
//            "data": [ "BTC", "USD" ],
//            "oid": "1435927928274_1_ticker"
//        }

        long timeMillis = System.currentTimeMillis() / 1000;
        send(session, "{ \"e\": \"ticker\", \"data\": [ \"BTC\", \"USD\" ], \"oid\": \"" + timeMillis + "_ticker\" }");

//        {
//            "e": "get-balance",
//            "data": {},
//            "oid": "1435927928274_2_get-balance"
//        }

        Thread.sleep(1000);
        timeMillis = System.currentTimeMillis() / 1000;
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