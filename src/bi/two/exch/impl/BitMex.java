package bi.two.exch.impl;

import bi.two.util.Log;
import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.container.jdk.client.JdkClientContainer;

import javax.websocket.*;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;

// based on info from
//  https://testnet.bitmex.com/app/wsAPI
public class BitMex {
    private static final String URL = "wss://testnet.bitmex.com/realtime";

    private static void console(String s) { Log.console(s); }
    private static void log(String s) { Log.log(s); }
    private static void err(String s, Throwable t) { Log.err(s, t); }

    public static void main(String[] args) {
        log("main()");
        try {
            Endpoint endpoint = new Endpoint() {
                @Override public void onOpen(final Session session, EndpointConfig config) {
                    log("onOpen() session=" + session + "; config=" + config);

                    session.addMessageHandler(new MessageHandler.Whole<String>() {
                        private boolean waitForFirstMessage = true;

                        @Override public void onMessage(String message) {
                            log("onMessage() message=" + message);
                            if (waitForFirstMessage) {
                                waitForFirstMessage = false;
                                // {"op": "subscribe", "args": [<SubscriptionTopic>]}
                                try {
                                    send(session, "{\"op\": \"subscribe\", \"args\": [\"orderBookL2:XBTUSD\"]}");

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

                                } catch (IOException e) {
                                    err("send error: " + e, e);
                                }
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
