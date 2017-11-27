package bi.two.tre;

import bi.two.exch.Currency;
import bi.two.exch.*;
import bi.two.exch.impl.CexIo;
import bi.two.util.MapConfig;
import bi.two.util.TimeStamp;
import bi.two.util.Utils;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class Tre {
    private static final String CONFIG = "cfg/tre.properties";
    private static final int SUBSCRIBE_DEPTH = 3;
    private static final double MAKER_PROFIT_RATE = 0.001; // 0.1%
    private static final BestRoundDataComparator BEST_ROUND_DATA_COMPARATOR = new BestRoundDataComparator();

    private Exchange m_exchange;
    private int m_connectedPairsCounter = 0;
    private List<OrderBook> m_books = new ArrayList<>();
    private Currency[] m_currencies;
    private RoundData m_bestRound;
    private TimeStamp m_bestRoundTimeStamp;
    private double m_bestTakerRate = 0;
    private double m_bestRate = 0;

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
        Pair pair = findPair(cur1, cur2);
        OrderBook orderBook = m_exchange.getOrderBook(pair);
        m_books.add(orderBook);
        // subscribe snapshot
        orderBook.subscribe(new Exchange.IOrderBookListener() {
            private boolean m_firstUpdate = true;

            @Override public void onUpdated() {
//                System.out.println("orderBook.onUpdated: " + orderBook.toString(1));
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

    private Pair findPair(Currency cur1, Currency cur2) {
        Pair pair = m_exchange.findPair(cur1, cur2);
        if (pair == null) {
            throw new RuntimeException("no pair for currencies found: " + cur1 + "; " + cur2);
        }
        return pair;
    }

    private void onBooksUpdated() {
//        StringBuilder sb = new StringBuilder("--== onBooksUpdated ");
//        for (OrderBook book : m_books) {
//            sb.append("  ");
//            sb.append(book.m_pair);
//            sb.append(book.toString(1));
//        }
//        System.out.println(sb.toString());

        Map<Currency, Map<Currency, PairDirectionData>> pairDirectionMap = new HashMap<>();
        final int length = m_currencies.length;
        for (int i = 0; i < length; i++) {
            Currency cur1 = m_currencies[i];
            Currency cur2 = m_currencies[(i + 1) % length];
            preparePairDirectionData(cur1, cur2, pairDirectionMap);
            preparePairDirectionData(cur2, cur1, pairDirectionMap);
        }

        List<RoundData> takerRounds = new ArrayList<>();
        List<RoundData> rounds = new ArrayList<>();
        for (int j = 0; j < 2; j++) {
            RoundData roundData = roundTaker((j == 0), pairDirectionMap);
            rounds.add(roundData);
            takerRounds.add(roundData);
        }

        for (int i = 0; i < length; i++) {
            for (int j = 0; j < 2; j++) {
                RoundData roundData = roundMaker(i, (j == 0), pairDirectionMap);
                rounds.add(roundData);
            }
        }
        Collections.sort(rounds, BEST_ROUND_DATA_COMPARATOR);
        Collections.sort(takerRounds, BEST_ROUND_DATA_COMPARATOR);
//        for (RoundData round : rounds) {
//            System.out.println(" round: " + round);
//        }
        RoundData bestTakerRound = takerRounds.get(0);
        double takerRate = bestTakerRound.m_rate;
        m_bestTakerRate = Math.max(m_bestTakerRate, takerRate);

        RoundData bestRound = rounds.get(0);
        double rate = bestRound.m_rate;
        m_bestRate = Math.max(m_bestRate, rate);

        String best = "        " + Utils.format8(m_bestTakerRate) + "; " + Utils.format8(m_bestRate);
        if (Utils.equals(m_bestRound, bestRound)) {
            System.out.println(" best round:     " + bestRound + "  live: " + m_bestRoundTimeStamp.getPassed() + best);
        } else {
            m_bestRound = bestRound;
            m_bestRoundTimeStamp = new TimeStamp();
            System.out.println(" new best round: " + bestRound + best);
        }
    }

    private RoundData roundTaker(boolean forward, Map<Currency, Map<Currency, PairDirectionData>> pairDirectionMap) {
        String roundName = buildRoundName(0, forward);
//        System.out.println("roundTaker(" + roundName + ") forward=" + forward);
//        StringBuilder takerSb = new StringBuilder();
        double takerValue = 1.0;
        int length = m_currencies.length;
        int index = 0;
        for (int i = 0; i < length; i++) {
            int nextIndex = (index + (forward ? 1 : -1) + length) % length;
            Currency cur1 = m_currencies[index];
            Currency cur2 = m_currencies[nextIndex];
            PairDirectionData pairDirectionData = pairDirectionMap.get(cur1).get(cur2);
//            double mktPrice = pairDirectionData.m_mktPrice;
//            PairDirection pairDirection = pairDirectionData.m_pairDirection;
//            Pair pair = pairDirection.m_pair;
//            boolean direct = pairDirection.m_forward;
//            OrderBook orderBook = m_exchange.getOrderBook(pair);

//            System.out.println(" [" + index + "] " + cur1.m_name + "->" + cur2.m_name + "; pair:" + pair + "; book:" + orderBook.toString(1)
//                    + "; direct=" + direct + "; mktPrice=" + mktPrice);

//            if (i == 0) {
//                takerSb.append(Utils.format8(takerValue)).append(cur1.m_name);
//            }
//            double translated = takerValue * pairDirectionData.m_takerRate;
            double commissioned = takerValue * pairDirectionData.m_fullTakerRate;
//            takerSb.append(" -> ").append(Utils.format8(translated))
//                    .append(" => ").append(Utils.format8(commissioned)).append(cur2.m_name);
            takerValue = commissioned;
            index = nextIndex;
        }
//        System.out.println("  taker: " + takerSb.toString());
        RoundData ret = new RoundData(false, roundName, takerValue);
        return ret;
    }

    private RoundData roundMaker(int startIndex, boolean forward, Map<Currency, Map<Currency, PairDirectionData>> pairDirectionMap) {
        String roundName = buildRoundName(startIndex, forward);
//        StringBuilder makerSb = new StringBuilder();
        double makerValue = 1.0;
        boolean firstDirect = false;
//        double firstMktPrice = 0;
        double oppositePrice = 0;
        int length = m_currencies.length;
        int index = startIndex;
        for (int i = 0; i < length; i++) {
            int nextIndex = (index + (forward ? 1 : -1) + length) % length;
            Currency cur1 = m_currencies[index];
            Currency cur2 = m_currencies[nextIndex];
            PairDirectionData pairDirectionData = pairDirectionMap.get(cur1).get(cur2);
            PairDirection pairDirection = pairDirectionData.m_pairDirection;
//            double mktPrice = pairDirectionData.m_mktPrice;
            Pair pair = pairDirection.m_pair;
            boolean direct = pairDirection.m_forward;
            OrderBook orderBook = m_exchange.getOrderBook(pair);

            if (i > 0) {
//                double makerTranslated = makerValue * pairDirectionData.m_takerRate;
                double makerCommissioned = makerValue * pairDirectionData.m_fullTakerRate;
//                if (i == 1) {
//                    makerSb.append(Utils.format8(makerValue)).append(cur1.m_name);
//                }
//                makerSb.append(" -> ").append(Utils.format8(makerTranslated))
//                        .append(" => ").append(Utils.format8(makerCommissioned))
//                        .append(cur2.m_name);
                makerValue = makerCommissioned;
            } else {
                firstDirect = direct;
//                firstMktPrice = mktPrice;
                OrderBook.OrderBookEntry oppositeEntry = (direct ? orderBook.m_asks : orderBook.m_bids).get(0);
                oppositePrice = oppositeEntry.m_price;
            }
            index = nextIndex;
        }
//        System.out.println("roundMaker(" + roundName + ") startIndex=" + startIndex + "; forward=" + forward + ";  " + makerSb.toString());
        double makerPrice = firstDirect ? (1 / makerValue) : makerValue;
        double makerRate = firstDirect ? oppositePrice / makerPrice : makerPrice / oppositePrice;
//        double profitPrice = firstDirect ? makerPrice * (1 + MAKER_PROFIT_RATE) : makerPrice / (1 + MAKER_PROFIT_RATE);
//        String makerRateStr = Utils.format8(makerRate);
//        System.out.println("    maker: firstDirect=" + firstDirect + "; [" + Utils.format8(firstMktPrice) + " - " + Utils.format8(oppositePrice)
//                + "]  " + Utils.format8(makerPrice) + ";  " + Utils.format8(profitPrice) + "; rate=" + makerRateStr);
//        if ((firstMktPrice < oppositePrice) && ((firstMktPrice < makerPrice) && (makerPrice < oppositePrice)) ||
//            (firstMktPrice > oppositePrice) && ((firstMktPrice > makerPrice) && (makerPrice > oppositePrice))) {
//            System.out.println("           @@@@@@@@@@@@    in BETWEEN");
//        }
        RoundData ret = new RoundData(true, roundName, makerRate);
        return ret;
    }

    private String buildRoundName(int startIndex, boolean forward) {
        int index = startIndex;
        int length = m_currencies.length;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            Currency cur = m_currencies[index];
            if (i > 0) {
                sb.append("->");
            }
            sb.append(cur.m_name);
            index = (index + (forward ? 1 : -1) + length) % length;
        }
        return sb.toString();
    }

    private void preparePairDirectionData(Currency cur1, Currency cur2, Map<Currency, Map<Currency, PairDirectionData>> pairDirectionMap) {
        PairDirection pairDirection = m_exchange.getPairDirection(cur1, cur2);
        Pair pair = pairDirection.m_pair;
        boolean direct = pairDirection.m_forward;

        OrderBook orderBook = m_exchange.getOrderBook(pair);
        OrderBook.OrderBookEntry entry = (direct ? orderBook.m_bids : orderBook.m_asks).get(0);
        double mktPrice = entry.m_price;

        ExchPairData pairData = m_exchange.getPairData(pair);
        double commission = pairData.m_commission;
        double takerRate = direct ? (1 * mktPrice) : (1 / mktPrice);
        double fullTakerRate = takerRate * (1 - commission);

//        System.out.println(" " + cur1.m_name + "->" + cur2.m_name
//                + "; pair:" + pair
//                + "; direct=" + direct
//                + "; book:" + orderBook.toString(1)
//                + "; mktPrice=" + mktPrice
//                + "; taker=" + Utils.format8(fullTakerRate)
//        );

        PairDirectionData pairDirectionData = new PairDirectionData(pairDirection, entry, mktPrice, takerRate, fullTakerRate);
        Map<Currency, PairDirectionData> map = pairDirectionMap.get(cur1);
        if (map == null) {
            map = new HashMap<>();
            pairDirectionMap.put(cur1, map);
        }
        map.put(cur2, pairDirectionData);
    }


    // -----------------------------------------------------------------------------------------------------------
    private static class PairDirectionData {
        private final PairDirection m_pairDirection;
        private final OrderBook.OrderBookEntry m_entry;
        private final double m_mktPrice;
        private final double m_takerRate;
        private final double m_fullTakerRate;

        public PairDirectionData(PairDirection pairDirection, OrderBook.OrderBookEntry entry, double mktPrice, double takerRate, double fullTakerRate) {
            m_pairDirection = pairDirection;
            m_entry = entry;
            m_mktPrice = mktPrice;
            m_takerRate = takerRate;
            m_fullTakerRate = fullTakerRate;
        }
    }

    // -----------------------------------------------------------------------------------------------------------
    private class RoundData {
        private final boolean m_maker;
        private final String m_roundName;
        private final double m_rate;

        public RoundData(boolean maker, String roundName, double rate) {
            m_maker = maker;
            m_roundName = roundName;
            m_rate = rate;
        }

        @Override public String toString() {
            return "RD{" + m_roundName +
                    " maker=" + m_maker +
                    ", rate=" + Utils.format8(m_rate) +
                    '}';
        }

        @Override public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            RoundData roundData = (RoundData) o;

            if (m_maker != roundData.m_maker) {
                return false;
            }
            if (Double.compare(roundData.m_rate, m_rate) != 0) {
                return false;
            }
            return m_roundName.equals(roundData.m_roundName);
        }

        @Override public int hashCode() {
            int result = (m_maker ? 1 : 0);
            result = 31 * result + m_roundName.hashCode();
            long temp = Double.doubleToLongBits(m_rate);
            result = 31 * result + (int) (temp ^ (temp >>> 32));
            return result;
        }
    }

    // -----------------------------------------------------------------------------------------------------------
    private static class BestRoundDataComparator implements Comparator<RoundData> {
        @Override public int compare(RoundData o1, RoundData o2) {
            if(!o1.m_maker && (o1.m_rate > 1)) {
                if(!o2.m_maker && (o2.m_rate > 1)) {
                    return Double.compare(o2.m_rate, o1.m_rate); // both takers - decreasing order
                } else {
                    return -1;
                }
            } else {
                if(!o2.m_maker && (o2.m_rate > 1)) {
                    return 1;
                } else {
                    return Double.compare(o2.m_rate, o1.m_rate); // decreasing order
                }
            }
        }
    }
}
