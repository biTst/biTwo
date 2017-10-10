package bi.two.exch.impl;

import bi.two.exch.BaseExchImpl;
import com.pusher.client.Pusher;
import com.pusher.client.PusherOptions;
import com.pusher.client.channel.Channel;
import com.pusher.client.channel.ChannelEventListener;
import com.pusher.client.channel.SubscriptionEventListener;
import com.pusher.client.connection.ConnectionEventListener;
import com.pusher.client.connection.ConnectionState;
import com.pusher.client.connection.ConnectionStateChange;

import java.util.concurrent.TimeUnit;

public class Bitstamp extends BaseExchImpl {
    public static void main(String[] args) {
        new Listener().start();
    }

    private static class Listener extends Thread {
        @Override public void run() {
            System.out.println("TickerListener thread started");

            setPriority(Thread.NORM_PRIORITY - 1); // smaller prio

            PusherOptions options = new PusherOptions(); //.setCluster("eu");

            // https://www.bitstamp.net/websocket/
            Pusher pusher = new Pusher( "de504dc5763aeef9ff52", options);

            Channel tradesChannel = pusher.subscribe("live_trades",  // live_trades_btceur
                    new ChannelEventListener() {
                        @Override public void onSubscriptionSucceeded(String channelName) {
                            System.out.println("Subscribed to tradesChannel: " + channelName);
                        }
                        @Override public void onEvent(String s, String s1, String s2) {
                            System.out.println("s=" + s + "; s1=" + s1 + "; s2=" + s2);
                        }
                    } ); // , "trade"

            tradesChannel.bind("trade", new SubscriptionEventListener() {
                @Override public void onEvent(String channelName, String eventName, final String data) {
                    System.out.println("channelName=" + channelName + "; eventName=" + eventName + "; data=" + data);
                }
            });

            Channel orderBookChannel = pusher.subscribe("diff_order_book",  // diff_order_book_btceur
                    new ChannelEventListener() {
                        @Override public void onSubscriptionSucceeded(String channelName) {
                            System.out.println("Subscribed to tradesChannel: " + channelName);
                        }
                        @Override public void onEvent(String s, String s1, String s2) {
                            System.out.println("s=" + s + "; s1=" + s1 + "; s2=" + s2);
                        }
                    } ); // , "trade"

            orderBookChannel.bind("data", new SubscriptionEventListener() {
                @Override public void onEvent(String channelName, String eventName, final String data) {
                    System.out.println("channelName=" + channelName + "; eventName=" + eventName + "; data=" + data);
                }
            });

            pusher.connect(new ConnectionEventListener() {
                @Override public void onConnectionStateChange(ConnectionStateChange change) {
                    System.out.println("State changed to " + change.getCurrentState() + " from " + change.getPreviousState());
                }

                @Override public void onError(String message, String code, Exception e) {
                    System.out.println("There was a problem connecting! code=" + code + "; message=" + message + "; e=" + e);
                }
            }, ConnectionState.ALL); // ConnectionState.DISCONNECTED


            try {
                Thread.sleep(TimeUnit.MINUTES.toMillis(5));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            System.out.println("TickerListener thread finished");
        }
    }
}
