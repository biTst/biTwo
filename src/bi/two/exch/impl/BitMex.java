package bi.two.exch.impl;

import bi.two.util.Hex;
import bi.two.util.Log;
import bi.two.util.MapConfig;
import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.container.jdk.client.JdkClientContainer;
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
import java.util.concurrent.TimeUnit;

// based on info from
//  https://testnet.bitmex.com/app/wsAPI
public class BitMex {
    private static final String URL = "wss://testnet.bitmex.com/realtime"; // wss://www.bitmex.com/realtime

    private static String s_apiKey;
    private static String s_apiSecret;

    private static void console(String s) { Log.console(s); }
    private static void log(String s) { Log.log(s); }
    private static void err(String s, Throwable t) { Log.err(s, t); }

    public static void main(String[] args) {
        log("main()");

        try {
            String file = "cfg\\bitmex.properties";
            MapConfig config = new MapConfig();
            config.load(file);
            //config.loadAndEncrypted(file);

            s_apiKey = config.getString("bitmex_apiKey");
            s_apiSecret = config.getString("bitmex_apiSecret");

            Endpoint endpoint = new Endpoint() {
                @Override public void onOpen(final Session session, EndpointConfig config) {
                    log("onOpen() session=" + session + "; config=" + config);

                    session.addMessageHandler(new MessageHandler.Whole<String>() {
                        private boolean waitForFirstMessage = true;

                        @Override public void onMessage(String message) {
                            log("onMessage() message=" + message);
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
                                    authenticate(session);

                                } catch (Exception e) {
                                    err("send error: " + e, e);
                                }
                            } else {
                                onMessageX(session, message);
                            }
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

    private static void onMessageX(Session session, String message) {
        try {
            JSONParser parser = new JSONParser();
            JSONObject jsonObject = (JSONObject) parser.parse(message);
            log(" jsonObject=" + jsonObject);
            JSONObject request = (JSONObject) jsonObject.get("request");
            log(" request=" + request);
            if (request != null) {
                Boolean success = (Boolean) jsonObject.get("success");
                log(" success=" + success);
                onSubscribed(session, success, request);
            } else {

            }

//            String e = (String) jsonObject.get("e");
//            if (Utils.equals(e, "connected")) {
//                onConnected(session);
//            } else if (Utils.equals(e, "auth")) {
//                onAuth(session, jsonObject);
//            } else if (Utils.equals(e, "ping")) {
//                onPing(session, jsonObject);
//            } else if (Utils.equals(e, "tick")) {
//                onTick(session, jsonObject);
//            } else if (Utils.equals(e, "ticker")) {
//                onTicker(session, jsonObject);
//            } else if (Utils.equals(e, "get-balance")) {
//                onGetBalance(session, jsonObject);
//            } else if (Utils.equals(e, "order-book-subscribe")) {
//                onOrderBookSubscribe(session, jsonObject);
//            } else if (Utils.equals(e, "order-book-unsubscribe")) {
//                onOrderBookUnsubscribe(session, jsonObject);
//            } else if (Utils.equals(e, "open-orders")) {
//                processOpenOrders(jsonObject);
//            } else if (Utils.equals(e, "place-order")) {
//                onPlaceOrder(jsonObject);
//            } else if (Utils.equals(e, "cancel-replace-order")) {
//                onCancelReplaceOrder(jsonObject);
//            } else if (Utils.equals(e, "cancel-order")) {
//                onCancelOrder(jsonObject);
//            } else if (Utils.equals(e, "order")) {
//                onOrder(jsonObject);
//            } else if (Utils.equals(e, "balance")) {
//                onBalance(session, jsonObject);
//            } else if (Utils.equals(e, "obalance")) {
//                onObalance(session, jsonObject);
//            } else if (Utils.equals(e, "tx")) {
//                onTx(session, jsonObject);
//            } else if (Utils.equals(e, "md")) {
//                onMd(session, jsonObject);
//            } else if (Utils.equals(e, "md_groupped")) {
//                onMdGroupped(session, jsonObject);
//            } else if (Utils.equals(e, "md_update")) {
//                onMdUpdate(session, jsonObject);
//            } else if (Utils.equals(e, "history")) {
//                onHistory(session, jsonObject);
//            } else if (Utils.equals(e, "history-update")) {
//                onHistoryUpdate(session, jsonObject);
//            } else if (Utils.equals(e, "ohlcv24")) {
//                onOhlcv24(session, jsonObject);
//            } else if (Utils.equals(e, "disconnecting")) {
//                onDisconnecting(session, jsonObject);
//            } else {
//                throw new RuntimeException("unexpected json: " + jsonObject);
//            }
        } catch (Exception e) {
            err("onMessageX ERROR: " + e, e);
        }
    }

    private static void onSubscribed(Session session, Boolean success, JSONObject request) throws IOException {
        String op = (String) request.get("op");
        log("  op=" + op);
        if (op.equals("authKey")) {
            onAuthenticated(session, success);
        }
    }

    private static void onAuthenticated(Session session, Boolean success) throws IOException {
        // {"success":true,
        //  "request":{"op":"authKey","args":["XXXXXXXXXXXXXXXXX",1521077672912,"YYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYY"]}}
        log("onAuthenticated success=" + success);
        if (success) {
            subscribeAccount(session);
        }
    }

    private static void subscribeAccount(Session session) throws IOException {
        // -------------------------------------------------------------------------------------------------------------------------------------
        // Updates on your current account balance and margin requirements
        send(session, "{\"op\": \"subscribe\", \"args\": [\"margin\"]}");

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

        // {"success":true,
        //  "subscribe":"margin",
        //  "request":{"op":"subscribe","args":["margin"]}}
    }

    private static void authenticate(Session session) throws NoSuchAlgorithmException, InvalidKeyException, IOException {
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

        SecretKeySpec keySpec = new SecretKeySpec(s_apiSecret.getBytes(), "HmacSHA256");
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(keySpec);
        String line = "GET/realtime" + nounce;
        byte[] hmacBytes = mac.doFinal(line.getBytes());
        String signature = Hex.bytesToHexLowerCase(hmacBytes);

        send(session, "{\"op\": \"authKey\", \"args\": [\"" + s_apiKey + "\", " + nounce + ", \"" + signature + "\"]}");

        // {"success":true,
        //  "request":{"op":"authKey","args":["XXXXXXXXXXXXXXXXX",1521077672912,"YYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYY"]}}
    }

    private static void requestQuote(Session session) throws IOException {
        // -------------------------------------------------------------------------------------------------------------------------------------
        // Top level of the book
        send(session, "{\"op\": \"subscribe\", \"args\": [\"quote:XBTUSD\"]}");

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
        send(session, "{\"op\": \"subscribe\", \"args\": [\"orderBook10:XBTUSD\"]}");

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
        send(session, "{\"op\": \"subscribe\", \"args\": [\"instrument:XBTUSD\"]}");

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

    private static void requestLiveTrades(Session session) throws IOException {
        // -------------------------------------------------------------------------------------------------------------------------------------
        // Live trades
        send(session, "{\"op\": \"subscribe\", \"args\": [\"trade:XBTUSD\"]}");

        // {"table":"trade",
        //  "keys":[],
        //  "types":{"timestamp":"timestamp","symbol":"symbol","side":"symbol","size":"long","price":"float","tickDirection":"symbol","trdMatchID":"guid","grossValue":"long","homeNotional":"float","foreignNotional":"float"},
        //  "foreignKeys":{"symbol":"instrument","side":"side"},
        //  "attributes":{"timestamp":"sorted","symbol":"grouped"},
        //  "action":"partial",
        //  "data":[
        //   {"timestamp":"2018-03-15T00:47:51.389Z",
        //    "symbol":"XBTUSD",
        //    "side":"Buy",
        //    "size":3000,
        //    "price":8121,
        //    "tickDirection":"MinusTick",
        //    "trdMatchID":"92658e51-e4b5-3378-254b-ec2a85cb9a9e",
        //    "grossValue":36942000,
        //    "homeNotional":0.36942,
        //    "foreignNotional":3000}
        //   ],
        //  "filter":{"symbol":"XBTUSD"}
        // }

        // {"success":true,
        //  "subscribe":"trade:XBTUSD",
        //  "request":{"op":"subscribe","args":["trade:XBTUSD"]}
        // }

        // {"table":"trade",
        //  "action":"insert",
        //  "data":[
        //   {"timestamp":"2018-03-15T00:47:57.027Z",
        //    "symbol":"XBTUSD",
        //    "side":"Buy",
        //    "size":229,
        //    "price":8120,
        //    "tickDirection":"MinusTick",
        //    "trdMatchID":"f47a0a0f-0067-db07-c551-4f71da87d5ed",
        //    "grossValue":2820135,
        //    "homeNotional":0.02820135,
        //    "foreignNotional":229
        //   }
        //  ]
        // }
    }

    private static void requestFullOrderBook(Session session) throws IOException {
        // -------------------------------------------------------------------------------------------------------------------------------------
        // Full level 2 orderBook
        send(session, "{\"op\": \"subscribe\", \"args\": [\"orderBookL2:XBTUSD\"]}");

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
        log(">> send: " + str);
//        m_rateLimiter.enter();
        RemoteEndpoint.Basic basicRemote = session.getBasicRemote();
        basicRemote.sendText(str);
    }
}
