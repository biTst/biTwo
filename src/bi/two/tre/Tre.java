package bi.two.tre;

import bi.two.exch.*;
import bi.two.exch.impl.CexIo;
import bi.two.util.MapConfig;
import bi.two.util.Utils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class Tre {
    private static final String CONFIG = "cfg/tre.properties";
    private static final int SUBSCRIBE_DEPTH = 3;

    private Exchange m_exchange;
    private int m_connectedPairsCounter = 0;
    private List<OrderBook> m_books = new ArrayList<>();
    private Currency[] m_currencies;

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
        m_currencies = new Currency[]{Currency.BTC, Currency.USD, Currency.BCH};
        int length = m_currencies.length;
        for (int i = 0; i < length; i++) {
            Currency cur1 = m_currencies[i];
            Currency cur2 = m_currencies[(i + 1) % length];
            subscribePairBook(cur1, cur2);
        }
    }

    private void subscribePairBook(Currency cur1, Currency cur2) throws Exception {
        Pair pair = getPair(cur1, cur2);
        OrderBook orderBook = m_exchange.getOrderBook(pair);
        m_books.add(orderBook);
        orderBook.snapshot(new Exchange.IOrderBookListener() {
            private boolean m_firstUpdate = true;

            @Override public void onUpdated() {
                System.out.println("orderBook.onUpdated: " + orderBook.toString(1));
                if (m_firstUpdate) {
                    m_firstUpdate = false;
                    m_connectedPairsCounter++;
                    System.out.println("first update for pair " + orderBook.m_pair);
                }
                if (m_connectedPairsCounter == 3) {
                    onBooksUpdated();
                }
            }
        }, SUBSCRIBE_DEPTH);
    }

    private Pair getPair(Currency cur1, Currency cur2) {
        Pair pair = m_exchange.getPair(cur1, cur2);
        if (pair == null) {
            pair = m_exchange.getPair(cur2, cur1);
            if (pair == null) {
                throw new RuntimeException("no pair for currencies found: " + cur1 + "; " + cur2);
            }
        }
        return pair;
    }

    private void onBooksUpdated() {
        StringBuilder sb = new StringBuilder("--== onBooksUpdated ");
        for (OrderBook book : m_books) {
            sb.append("  ");
            sb.append(book.m_pair);
            sb.append(book.toString(1));
        }
        System.out.println(sb.toString());

        round(0, true);
        round(0, false);

        round(1, true);
        round(1, false);

        round(2, true);
        round(2, false);
    }

    private void round(int startIndex, boolean forward) {
        int index = startIndex;
        int length = m_currencies.length;
        StringBuilder sb = new StringBuilder("");
        for (int i = 0; i < length; i++) {
            Currency cur = m_currencies[index];
            if (i > 0) {
                sb.append("->");
            }
            sb.append(cur.m_name);
            index = (index + (forward ? 1 : -1) + length) % length;
        }

        System.out.println("round(" + sb.toString() + ") startIndex=" + startIndex + "; forward=" + forward);
        index = startIndex;
        StringBuilder takerSb = new StringBuilder("");
        StringBuilder makerSb = new StringBuilder("");
        double takerValue = 1.0;
        double makerValue = 1.0;
        boolean firstDirect = false;
        double firstMktPrice = 0;
        double oppositePrice = 0;
        for (int i = 0; i < length; i++) {
            int nextIndex = (index + (forward ? 1 : -1) + length) % length;
            Currency cur1 = m_currencies[index];
            Currency cur2 = m_currencies[nextIndex];
            Pair pair = getPair(cur1, cur2);
            Currency curFrom = pair.m_from;
            boolean direct = (cur1 == curFrom);
            OrderBook orderBook = m_exchange.getOrderBook(pair);
            OrderBook.OrderBookEntry entry = (direct ? orderBook.m_bids : orderBook.m_asks).get(0);
            double mktPrice = entry.m_price;

            ExchPairData pairData = m_exchange.getPairData(pair);
            double commission = pairData.m_commission;

            System.out.println(" [" + index + "] " + cur1.m_name + "->" + cur2.m_name + "; pair:" + pair + "; book:" + orderBook.toString(1)
                    + "; direct=" + direct + "; mktPrice=" + mktPrice);
            double translated = direct ? (takerValue * mktPrice) : (takerValue / mktPrice);
            double commissioned = translated * (1 - commission);

            if (i == 0) {
                takerSb.append(Utils.format8(takerValue)).append(cur1.m_name);
            }
            takerSb.append(" -> ").append(Utils.format8(translated))
                    .append(" => ").append(Utils.format8(commissioned)).append(cur2.m_name);
            takerValue = commissioned;

            if (i > 0) {
                double makerTranslated = direct ? makerValue * mktPrice : makerValue / mktPrice;
                double makerCommissioned = makerTranslated * (1 - commission);
                if (i == 1) {
                    makerSb.append(Utils.format8(makerValue)).append(cur1.m_name);
                }
                makerSb.append(" -> ").append(Utils.format8(makerTranslated))
                        .append(" => ").append(Utils.format8(makerCommissioned)).append(cur2.m_name);
                makerValue = makerCommissioned;
            } else {
                firstDirect = direct;
                firstMktPrice = mktPrice;
                OrderBook.OrderBookEntry oppositeEntry = (direct ? orderBook.m_asks : orderBook.m_bids).get(0);
                oppositePrice = oppositeEntry.m_price;
            }

            index = nextIndex;
        }
        System.out.println("  taker: " + takerSb.toString());
        System.out.println("  maker: " + makerSb.toString());
        double makerRate = firstDirect ? (1 / makerValue) : makerValue;
        System.out.println("    makerRate: " + Utils.format8(makerRate) + "  [" + Utils.format8(firstMktPrice) + " - " + Utils.format8(oppositePrice) + "]");
    }
}
