package bi.two.exch.impl;

import bi.two.util.Hex;
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
    public static final String URL = "wss://ws.cex.io/ws/";

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

    public static void main_() {
        try {
            ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();
            ClientManager client = ClientManager.createClient();
            Session session = client.connectToServer(new Endpoint() {
                @Override public void onOpen(final Session session, EndpointConfig config) {
                    System.out.println("onOpen");
                    try {
                        session.addMessageHandler(new MessageHandler.Whole<String>() {
                            @Override public void onMessage(String message) {
                                onMessageX(session, message);


                                //c onst crypto = require('crypto')
                                // const websocket = require('ws')
                                // var apikey = ''
                                // var apisecret = ''
                                // const cexiows = new websocket('wss://ws.cex.io/ws/', {permessagedeflate: false });
                                // function createsignature(timestamp, apikey, apisecret){
                                //      var hmac = crypto.createhmac('sha256', apisecret );
                                //     hmac.update( timestamp + apikey );
                                //     return hmac.digest('hex');
                                // }
                                // function createauthrequest(apikey, apisecret ){
                                //     var timestamp = math.floor(date.now() / 1000);
                                //     var args = { e: 'auth', auth: { key: apikey,
                                //      signature: createsignature(timestamp, apikey, apisecret), timestamp: timestamp } };
                                //     var authmessage = json.stringify( args );
                                //     return authmessage;
                                // }
                                // cexiows.on('message', (mess, error) => {
                                //         console.log("cexio message");
                                //         console.log(mess);
                                //     let jsonmess = json.parse(mess);
                                //     if (jsonmess.e === "connected") {
                                //         cexiows.send(createauthrequest(apikey, apisecret));
                                //         cexiows.send(json.stringify({
                                //            e: "subscribe",
                                //             rooms: [
                                //                 "tickers"
                                //            ]
                                //        }));
                                //     }
                                //    if (jsonmess.e === "ping") {
                                //         console.log("pong message");
                                //         cexiows.send(json.stringify({e: "pong"}));
                                //      } });


//                                m_counter++;
//                                if (m_counter == 4) {
//                                    try {
//                                        session.getBasicRemote().sendText(UNSUBSCRIBE_BTCCNY_TRADES);
//                                    } catch (IOException e) {
//                                        e.printStackTrace();
//                                    }
//                                }
                            }

                        });
    //                        session.getBasicRemote().sendText(SUBSCRIBE_BTCCNY_TICKER);
    //                        session.getBasicRemote().sendText(SUBSCRIBE_BTCCNY_DEPTH);
    //                        session.getBasicRemote().sendText(SUBSCRIBE_BTCCNY_TICKER);
//                        session.getBasicRemote().sendText("{'event':'addChannel','channel':'ok_sub_spotcny_btc_ticker'}");
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
                // {"e":"connected"}
                // can be received in case WebSocket client has reconnected, which means that client needs to send 'authenticate'
                // request and subscribe for notifications, like by first connection
                doAuthenticate(session);
            }
        } catch (Exception e) {
            System.out.println("onMessageX ERROR: " + e);
            e.printStackTrace();
        }
    }

    private static void doAuthenticate(Session session) throws IOException {
        //    {
        //        "e": "auth",
        //            "auth": {
        //        "key": "1WZbtMTbMbo2NsW12vOz9IuPM.",
        //                "signature": "02483c01efc26fac843dd34d0342d269bacf4daa906a32cb71806eb7467dcf58",
        //                "timestamp": 1448034533
        //          }
        //    }
        // signature - Client signature (digest of HMAC-rsa256 with client's API Secret Key, applied to the string, which is
        //             concatenation timestamp and API Key)
        // timestimp - timestimp in seconds, used for signature

        long timestamp = System.currentTimeMillis() / 1000;  // Note: java timestamp presented in milliseconds
        String signature = createSignature(timestamp, s_apiSecret, s_apiKey);
        String jsonStr = "{ e: 'auth', auth: { key: apiKey, signature: " + signature + ", timestamp: " + timestamp + " } }";
System.out.println("jsonStr = " + jsonStr);
        session.getBasicRemote().sendText(jsonStr);
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