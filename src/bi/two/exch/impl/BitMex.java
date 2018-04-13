package bi.two.exch.impl;

import bi.two.Main2;
import bi.two.chart.TradeData;
import bi.two.exch.*;
import bi.two.util.Hex;
import bi.two.util.Log;
import bi.two.util.MapConfig;
import bi.two.util.Utils;
import org.glassfish.tyrus.client.ClientManager;
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
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

// based on info from
//  https://testnet.bitmex.com/app/wsAPI
// todo: look at https://github.com/ccxt/ccxt
public class BitMex extends BaseExchImpl {
    private static final String URL = "wss://testnet.bitmex.com/realtime"; // wss://www.bitmex.com/realtime
    private static final String SYMBOL = "XBTUSD";
    private static final String CONFIG_FILE = "cfg\\bitmex.properties";
    private static final String API_KEY_KEY = "bitmex_apiKey";
    private static final String API_SECRET_KEY = "bitmex_apiSecret";
    private static final SimpleDateFormat TIMESTAMP_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    static {
        TIMESTAMP_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT"));
    }

    private Exchange m_exchange; // todo: move to parent
    private String m_apiKey;
    private String m_apiSecret;

    private Exchange.IExchangeConnectListener m_exchangeConnectListener; // todo: move to parent ?
    private Session m_session; // todo: create parent BaseWebServiceExch and move there ?
    private boolean m_waitingFirstAccountUpdate;

    public BitMex(Exchange exchange) {
        m_exchange = exchange;
    }

    public void init(MapConfig config) {
        m_apiKey = config.getString(API_KEY_KEY);
        m_apiSecret = config.getString(API_SECRET_KEY);
    }

    private static String pairToSymbol(Pair pair) {
console("pairToSymbol pair=" + pair + "  => " + SYMBOL);
        return SYMBOL; // todo
    }

    private static Pair symbolToPair(String symbol) {
        Pair ret = Pair.getByName("btc_usd");
console("symbolToPair symbol=" + symbol + "  => " + ret);
        return ret; // todo
    }

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
//                                    requestOrderBook(session);
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
            JSONParser parser = new JSONParser();
            JSONObject json = (JSONObject) parser.parse(message);
            console(" json=" + json);
            JSONObject request = (JSONObject) json.get("request");
            console(" request=" + request);
            if (request != null) {
                // {"success":true,                     "request":{"op":"authKey","args":["QGCD0OqdauJZ9LHbvBuq0tHE",1521898505226,"caa8ec1a5cecd030019c22f73a42a0774775653a9a21dd90395ffbafb9e87b98"]}}
                // {"success":true,"subscribe":"margin","request":{"op":"subscribe","args":["margin"]}}
                Boolean success = (Boolean) json.get("success");
                if (success != null) {
                    String subscribe = (String) json.get("subscribe");
                    console(" success=" + success + "; subscribe=" + subscribe);
                    if (subscribe != null) {
                        onSubscribed(session, subscribe, success, request);
                    } else {
                        String op = (String) request.get("op");
                        console("  op=" + op);
                        if (op.equals("authKey")) {
                            onAuthenticated(session, success);
                        } else {
                            console("ERROR: not supported message (op='" + op + "'): " + json);
                        }
                    }
                } else {
                    console("ERROR: not supported message (no success): " + json);
                }
            } else {
                String table = (String) json.get("table");
                if (table != null) {
                    if (table.equals("margin")) {
                        onMargin(json);
                    } else if (table.equals("trade")) {
                        onTrade(json);
                    } else {
                        console("ERROR: not supported table='" + table + "' message: " + json);
                    }
                } else {
                    String info = (String) json.get("info");
                    String version = (String) json.get("version");
                    console(" info=" + info + "; version=" + version);
                    if ((info != null) && (version != null)) {
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

    private static void onAuthenticated(Session session, Boolean success) throws IOException {
        // {"success":true,
        //  "request":{"op":"authKey","args":["XXXXXXXXXXXXXXXXX",1521077672912,"YYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYY"]}}
        console("onAuthenticated success=" + success);
        if (success) {
            subscribeAccount(session);
        }
    }

    private static void subscribeAccount(Session session) throws IOException {
        // -------------------------------------------------------------------------------------------------------------------------------------
        // Updates on your current account balance and margin requirements
        send(session, "{\"op\": \"subscribe\", \"args\": [\"margin\"]}");

        // {"success":true,
        //  "subscribe":"margin",
        //  "request":{"op":"subscribe","args":["margin"]}}
    }

    private void onMargin(JSONObject json) {
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
        //  "data":[{"account":47464,
        //           "currency":"XBt",
        //           "riskLimit":1000000000000,
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

        if (m_waitingFirstAccountUpdate) {
            m_waitingFirstAccountUpdate = false;
            // connected + authenticated + gotAccountData
            if (m_exchangeConnectListener != null) {
                m_exchangeConnectListener.onConnected();
            }
        }
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

        SecretKeySpec keySpec = new SecretKeySpec(m_apiSecret.getBytes(), "HmacSHA256");
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(keySpec);
        String line = "GET/realtime" + nounce;
        byte[] hmacBytes = mac.doFinal(line.getBytes());
        String signature = Hex.bytesToHexLowerCase(hmacBytes);

        send(session, "{\"op\": \"authKey\", \"args\": [\"" + m_apiKey + "\", " + nounce + ", \"" + signature + "\"]}");

        // {"success":true,
        //  "request":{"op":"authKey","args":["XXXXXXXXXXXXXXXXX",1521077672912,"YYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYY"]}}
    }

    private static void requestQuote(Session session) throws IOException {
        // -------------------------------------------------------------------------------------------------------------------------------------
        // Top level of the book
        send(session, "{\"op\": \"subscribe\", \"args\": [\"quote:" + SYMBOL + "\"]}");

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

    private static void requestOrderBook(Session session) throws IOException {
        // -------------------------------------------------------------------------------------------------------------------------------------
        // Top 10 levels using traditional full book push
        send(session, "{\"op\": \"subscribe\", \"args\": [\"orderBook10:" + SYMBOL + "\"]}");

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

    private static void requestInstrument(Session session) throws IOException {
        // -------------------------------------------------------------------------------------------------------------------------------------
        // Instrument updates including turnover and bid/ask
        send(session, "{\"op\": \"subscribe\", \"args\": [\"instrument:" + SYMBOL + "\"]}");

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
        //    "isInverse":true,"initMargin":0.01,"maintMargin":0.005,"riskLimit":20000000000,"riskStep":10000000000,"limit":null,"capped":false,"taxed":true,"deleverage":true,"makerFee":-0.00025,"takerFee":0.00075,
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

    private static void requestLiveTrades(Session session, Pair pair) throws IOException {
        String symbol = pairToSymbol(pair);
        // -------------------------------------------------------------------------------------------------------------------------------------
        // Live trades
        send(session, "{\"op\": \"subscribe\", \"args\": [\"trade:" + symbol + "\"]}");

        // {"success":true,
        //  "subscribe":"trade:XBTUSD",
        //  "request":{"op":"subscribe","args":["trade:XBTUSD"]}
        // }
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
            console("   [" + i + "]:" + obj);
            TradeData td = parseTrade(obj);
            String s = (String) obj.get("symbol");
            if (!Utils.equals(s, symbol)) {
                symbol = (String) obj.get("symbol");
                Pair pair = symbolToPair(symbol);
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
        console("    side=" + side + "; size=" + size + "; price=" + price + "; timestamp=" + timestampStr);

        boolean isBuy = side.equals("Buy");
        OrderSide orderSide = OrderSide.get(isBuy);
        Date date = TIMESTAMP_FORMAT.parse(timestampStr);
        long timestamp = date.getTime();
        console("     isBuy=" + isBuy + "; orderSide=" + orderSide + "; date=" + date + "; timestamp=" + timestamp);

        return new TradeData(timestamp, price.floatValue(), size.floatValue(), orderSide);
    }

    private static void requestFullOrderBook(Session session) throws IOException {
        // -------------------------------------------------------------------------------------------------------------------------------------
        // Full level 2 orderBook
        send(session, "{\"op\": \"subscribe\", \"args\": [\"orderBookL2:" + SYMBOL + "\"]}");

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

//        client.getProperties().put(ClientProperties.RECONNECT_HANDLER, new CexIo.ReconnectHandler());

        client.connectToServer(endpoint, cec, new URI(URL));
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

    @Override public void connect(Exchange.IExchangeConnectListener iExchangeConnectListener) throws Exception {
        m_exchangeConnectListener = iExchangeConnectListener;

        Endpoint endpoint = new Endpoint() {
            @Override public void onOpen(final Session session, EndpointConfig config) {
                console("onOpen");
                m_waitingFirstAccountUpdate = true;
                try {
                    m_session = session;
                    session.addMessageHandler(new MessageHandler.Whole<String>() {
                        @Override public void onMessage(String message) {
                            onMessageX(session, message);
                        }
                    });
                } catch (Exception e) {
                    err("onOpen ERROR: " + e, e);
                }
            }

            @Override public void onClose(Session session, CloseReason closeReason) {
                console("onClose: " + closeReason);

                m_exchange.m_live = false; // mark as disconnected

//                m_exchange.m_threadPool.submit(new Runnable() {
//                    @Override public void run() {
                        m_exchange.onDisconnected();
                        if (m_exchangeConnectListener != null) {
                            m_exchangeConnectListener.onDisconnected();
                        }
//                    }
//                });
            }

            @Override public void onError(Session session, Throwable thr) {
                err("onError: " + thr, thr);
            }
        };
        console("connectToServer...");
        connectToServer(endpoint);
        console("session isOpen=" + m_session.isOpen());
    }

    @Override public Main2.TicksCacheReader getTicksCacheReader() {
        return new Main2.TicksCacheReader(Main2.TicksCacheReader.TicksCacheType.one);
    }
}
