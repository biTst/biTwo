package bi.two.tre;

import bi.two.exch.*;
import bi.two.exch.impl.CexIo;
import bi.two.util.MapConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class Tre {
    private static final String CONFIG = "cfg/tre.properties";
    private static final int SUBSCRIBE_DEPTH = 3;

    private Exchange m_exchange;
    private int m_connectedPairsCounter = 0;
    private List<OrderBook> m_books = new ArrayList<>();

    public static void main(String[] args) {
        new Tre().main();
    }

    private void main() {
        try {
            MarketConfig.initMarkets(false);

            MapConfig config = new MapConfig();
            config.loadAndEncrypted(CONFIG);

            m_exchange = Exchange.get("cex");
            m_exchange.m_impl = new CexIo(config);

            m_exchange.connect(new Exchange.IExchangeConnectListener() {
                @Override public void onConnected() {
                    try {
                        System.out.println("onConnected()");
                        start();
                    } catch (Exception e) {
                        System.out.println("onConnected error: " + e);
                        e.printStackTrace();
                    }
                }
            });

            Thread.sleep(TimeUnit.DAYS.toMillis(365));
            System.out.println("done");
        } catch (Exception e) {
            System.out.println("ERROR: " + e);
            e.printStackTrace();
        }
    }

    private void start() throws Exception {
        Currency[] cur = new Currency[]{Currency.BTC, Currency.USD, Currency.BCH};
        int length = cur.length;
        for (int i = 0; i < length; i++) {
            Currency cur1 = cur[i];
            Currency cur2 = cur[(i + 1) % length];
            subscribePairBook(cur1, cur2);
        }
    }

    private void subscribePairBook(Currency cur1, Currency cur2) throws Exception {
        Pair pair = m_exchange.getPair(cur1, cur2);
        if (pair == null) {
            pair = m_exchange.getPair(cur2, cur1);
            if (pair == null) {
                throw new RuntimeException("no pair for currencies found: " + cur1 + "; " + cur2);
            }
        }
        OrderBook orderBook = m_exchange.getOrderBook(pair);
        m_books.add(orderBook);
        orderBook.subscribe(new Exchange.IOrderBookListener() {
            private boolean m_firstUpdate = true;

            @Override public void onUpdated() {
                System.out.println("orderBook.onUpdated: " + orderBook.toString(1));
                if (m_firstUpdate) {
                    m_firstUpdate = false;
                    m_connectedPairsCounter++;
                    System.out.println("first update for pair " + orderBook.getPair());
                }
                if (m_connectedPairsCounter == 3) {
                    onBooksUpdated();
                }
            }
        }, SUBSCRIBE_DEPTH);
    }

    private void onBooksUpdated() {
        StringBuilder sb = new StringBuilder("--== onBooksUpdated ");
        for (OrderBook book : m_books) {
            sb.append("  ");
            sb.append(book.getPair());
            sb.append(book.toString(1));
        }
        System.out.println(sb.toString());
    }
}
