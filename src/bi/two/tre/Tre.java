package bi.two.tre;

import bi.two.exch.Currency;
import bi.two.exch.*;
import bi.two.exch.impl.CexIo;
import bi.two.util.MapConfig;
import bi.two.util.Utils;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class Tre {
    private static final String CONFIG = "cfg/tre.properties";
    private static final int SUBSCRIBE_DEPTH = 3;
    private static final double MAKER_PROFIT_RATE = 0.001; // 0.1%
    private static final BestRoundDataComparator BEST_ROUND_DATA_COMPARATOR = new BestRoundDataComparator();
    private static final boolean SNAPSHOT_ONLY = false;
    private static final Currency[][] TRE_CURRENCIES = {
            {Currency.BTC, Currency.USD, Currency.BCH},
            {Currency.BTC, Currency.USD, Currency.ETH},
//            {Currency.BTC, Currency.EUR, Currency.ETH},
//            {Currency.BTC, Currency.USD, Currency.DASH},
//            {Currency.BTC, Currency.USD, Currency.BTG},
    };

    private Exchange m_exchange;
    private int m_connectedPairsCounter = 0;
    private List<RoundData> m_roundDatas = new ArrayList<>();
    private ArrayList<PairData> m_pairDatas = new ArrayList<>();
    private List<OrderBook> m_books = new ArrayList<>();
    private Currency[] m_currencies;
    private RoundDataOld m_bestRound;
    private double m_bestTakerRate = 0;
    private double m_bestRate = 0;
    private State m_state = State.watching;

    public static void main(String[] args) {
        new Tre().main();
    }

    private void main() {
        try {
            MarketConfig.initMarkets(false);

            MapConfig config = new MapConfig();
            config.loadAndEncrypted(CONFIG);

            m_exchange = Exchange.get("cex");
            m_exchange.m_impl = new CexIo(config, m_exchange);

start();

//            m_exchange.connect(new Exchange.IExchangeConnectListener() {
//                @Override public void onConnected() {
//                    try {
//                        System.out.println("onConnected()");
//                        start();
//                    } catch (Exception e) {
//                        System.out.println("onConnected error: " + e);
//                        e.printStackTrace();
//                    }
//                }
//            });

            Thread.sleep(TimeUnit.DAYS.toMillis(365));
            System.out.println("done");
        } catch (Exception e) {
            System.out.println("ERROR: " + e);
            e.printStackTrace();
        }
    }


    // can be called on reconnect ?
    private void start() throws Exception {
        System.out.println("start()");

        initIfNeeded();

//        System.out.println("queryAccount()...");
//        m_exchange.queryAccount(new Exchange.IAccountListener() {
//            @Override public void onUpdated() throws Exception {
//                System.out.println("Account.onUpdated() " + m_exchange.m_accountData);
//                m_currencies = TRE_CURRENCIES;
//                int length = m_currencies.length;
//                for (int i = 0; i < length; i++) {
//                    Currency cur1 = m_currencies[i];
//                    Currency cur2 = m_currencies[(i + 1) % length];
//                    subscribePairBook(cur1, cur2);
//                }
//            }
//        });
    }

    private void initIfNeeded() {
        if (m_roundDatas.isEmpty()) {
            for (Currency[] currencies : TRE_CURRENCIES) {
                RoundData roundData = new RoundData(currencies);
                m_roundDatas.add(roundData);
                roundData.getPairDatas(m_pairDatas);
            }
            System.out.println("roundDatas: " + m_roundDatas);
            System.out.println("pairDatas: " + m_pairDatas);
        }
    }

    private void subscribePairBook(Currency cur1, Currency cur2) throws Exception {
        Pair pair = Pair.get(cur1, cur2);
        final OrderBook orderBook = m_exchange.getOrderBook(pair);
        m_books.add(orderBook);
        // subscribe snapshot
        Exchange.IOrderBookListener listener = new Exchange.IOrderBookListener() {
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
        };
        if (SNAPSHOT_ONLY) {
            orderBook.snapshot(listener, SUBSCRIBE_DEPTH);
        } else {
            orderBook.subscribe(listener, SUBSCRIBE_DEPTH);
        }
    }

//    private Pair findPair(Currency cur1, Currency cur2) {
//        Pair pair = m_exchange.findPair(cur1, cur2);
//        if (pair == null) {
//            throw new RuntimeException("no pair for currencies found: " + cur1 + "; " + cur2);
//        }
//        return pair;
//    }

    private void onBooksUpdated() {
//        StringBuilder sb = new StringBuilder("--== onBooksUpdated ");
//        for (OrderBook book : m_books) {
//            sb.append("  ");
//            sb.append(book.m_pair);
//            sb.append(book.toString(1));
//        }
//        System.out.println(sb.toString());

        Map<Currency, Map<Currency, PairDirectionDataOld>> pairDirectionMap = new HashMap<>();
        final int length = m_currencies.length;
        for (int i = 0; i < length; i++) {
            Currency cur1 = m_currencies[i];
            Currency cur2 = m_currencies[(i + 1) % length];
            preparePairDirectionData(cur1, cur2, pairDirectionMap);
            preparePairDirectionData(cur2, cur1, pairDirectionMap);
        }

        List<RoundDataOld> takerRounds = new ArrayList<>();
        List<RoundDataOld> rounds = new ArrayList<>();
        for (int j = 0; j < 2; j++) {
            RoundDataOld roundData = roundTaker((j == 0), pairDirectionMap);
            rounds.add(roundData);
            takerRounds.add(roundData);
        }

        for (int i = 0; i < length; i++) {
            for (int j = 0; j < 2; j++) {
                RoundDataOld roundData = roundMaker(i, (j == 0), pairDirectionMap);
                rounds.add(roundData);
            }
        }
        Collections.sort(rounds, BEST_ROUND_DATA_COMPARATOR);
        Collections.sort(takerRounds, BEST_ROUND_DATA_COMPARATOR);
//        for (RoundData round : rounds) {
//            System.out.println(" round: " + round);
//        }
        RoundDataOld bestTakerRound = takerRounds.get(0);
        double takerRate = bestTakerRound.m_rate;
        m_bestTakerRate = Math.max(m_bestTakerRate, takerRate);

        RoundDataOld bestRound1 = rounds.get(0);
        RoundDataOld bestRound2 = rounds.get(1);
        RoundDataOld bestRound3 = rounds.get(2);
        double rate = bestRound1.m_rate;
        m_bestRate = Math.max(m_bestRate, rate);

        if (!Utils.equals(m_bestRound, bestRound1)) {
            m_bestRound = bestRound1;
        }
        System.out.println(" new best round: " + bestRound1 + "  "  + bestRound2 + "  "  + bestRound3
                + "        " + Utils.format8(bestTakerRound.m_rate) + "/" + Utils.format8(m_bestTakerRate) + "; " + Utils.format8(m_bestRate));

//        analyzeRound(bestRound1);

        setState(m_state.onBooksUpdated());
    }

    private void analyzeRound(RoundDataOld round) {
        System.out.println("analyzeRound() round=" + round);

        CurrencyValue value = null;
        List<PairDirectionDataOld> roundSides = round.m_sides;
        for (PairDirectionDataOld roundSide : roundSides) {
            PairDirection pairDirection = roundSide.m_pairDirection;
            Pair pair = pairDirection.m_pair;
            boolean forward = pairDirection.m_forward;
            OrderBook.OrderBookEntry bookEntry = roundSide.m_entry;
            OrderBook.Spread spread = roundSide.m_spread;
            Currency pairCurrency = pair.m_from;
            String pairCurrencyName = pairCurrency.m_name;
            System.out.println(" pair=" + pair + "; forward=" + forward + "; pairCurrency=" + pairCurrencyName
                    + "; bookEntry=" + bookEntry + "; spread=" + spread);

            Currency srcCur = pairDirection.getSourceCurrency();
            Currency dstCur = pairDirection.getDestinationCurrency();
            double bookEntrySize = bookEntry.m_size;

            boolean isSellOrder = (srcCur == pairCurrency);
            OrderSide orderSide = isSellOrder ? OrderSide.SELL : OrderSide.BUY;
            double availableSrc = m_exchange.m_accountData.available(srcCur);

            ExchPairData pairData = m_exchange.getPairData(pair);
            CurrencyValue minOrder = pairData.m_minOrderToCreate;
            if (minOrder == null) {
                throw new RuntimeException("no minOrderToCreate defined for " + pair);
            }

            CurrencyValue bookEntryValue = new CurrencyValue(bookEntrySize, pairCurrency);

            String srcCurName = srcCur.m_name;
            System.out.println("  " + srcCurName + " => " + dstCur.m_name
                    + ";  available in book: " + bookEntryValue
                    + ";  available in acct: " + availableSrc + srcCurName
                    + "; minOrder=" + minOrder
                    + "; orderSide=" + orderSide
            );

            if (value == null) {
                value = new CurrencyValue(availableSrc, srcCur); // start with account value
                System.out.println("  init value=" + value);
            }
            double bookMarketPrice = bookEntry.m_price;
            double minOrderSize = minOrder.m_value;
            if (isSellOrder) {
//                pair=Pair[bch_usd]; forward=true; bookEntry=[1560.0;0.77941000]; spread=Spread{bid=[1560.0;0.77941000], ask=[1561.9839;0.32895006]}
//                bch => usd;  available in book: 0.77941bch;  available in acct: 1.3bch; minOrder=<0.01bch>; orderSide=SELL
                double orderSize1 = value.m_value;
                double orderSize2 = (bookEntrySize < orderSize1) ? bookEntrySize : orderSize1;
                CurrencyValue value2 = new CurrencyValue(orderSize2, srcCur);
                double orderSize3 = (orderSize2 < minOrderSize) ? 0 : orderSize2;
                CurrencyValue value3 = new CurrencyValue(orderSize3, srcCur);

                double orderSize4 = (value3.m_value * bookMarketPrice) * (1 - pairData.m_commission);
                CurrencyValue value4 = new CurrencyValue(orderSize4, dstCur);

                System.out.println("    start value: " + value
                        + ";  available in book: " + bookEntryValue
                        + " =>  " + value2
                        + "; minOrder=" + minOrder
                        + " =>  " + value3
                        + "  " + orderSide + " " + value3 + " @ " + bookMarketPrice + " ==> " + value4);
                value = value4;
            } else { // buy order
//                pair=Pair[btc_usd]; forward=false; bookEntry=[10226.9967;0.19406382]; spread=Spread{bid=[10214.6356;0.01000000], ask=[10226.9967;0.19406382]}
//                usd => btc;  available in book: 0.19406382btc;  available in acct: 2126.04usd; minOrder=<0.01btc>; orderSide=BUY
                double orderSize1 = value.m_value; // 2126.04usd
                //                   2126.04usd   10226.9967usd/btc                               ==> 0.2075316727148254579958943371909btc
                double orderSize2 = (orderSize1 / bookMarketPrice) * (1 - pairData.m_commission);
                CurrencyValue value2 = new CurrencyValue(orderSize2, dstCur);
                double orderSize3 = (bookEntrySize < orderSize2) ? bookEntrySize : orderSize2;
                CurrencyValue value3 = new CurrencyValue(orderSize3, dstCur);
                double orderSize4 = (orderSize3 < minOrderSize) ? 0 : orderSize3;
                CurrencyValue value4 = new CurrencyValue(orderSize4, srcCur);

                System.out.println("    start value: " + value
                        + " @ " + bookMarketPrice
                        + ";  can buy: " + value2
                        + ";  available in book: " + bookEntryValue
                        + " =>  " + value3
                        + "; minOrder=" + minOrder
                        + " =>  " + value4
                );
                if (orderSize4 > 0) {
                    System.out.println("     " + orderSide + " " + value4 + " @ " + bookMarketPrice);
                } else {
                    System.out.println("     can not trade - too low size ");
                }
                value = value4;
            }
        }
    }

    private void setState(State state) {
        if (state != null) {
            System.out.println(":: state changed to: " + state);
            m_state = state;
        }
    }

    private RoundDataOld roundTaker(boolean forward, Map<Currency, Map<Currency, PairDirectionDataOld>> pairDirectionMap) {
        String roundName = buildRoundName(0, forward);
//        System.out.println("roundTaker(" + roundName + ") forward=" + forward);
//        StringBuilder takerSb = new StringBuilder();
        double takerValue = 1.0;
        int length = m_currencies.length;
        int index = 0;
        List<PairDirectionDataOld> sides = new ArrayList<>();
        for (int i = 0; i < length; i++) {
            int nextIndex = (index + (forward ? 1 : -1) + length) % length;
            Currency cur1 = m_currencies[index];
            Currency cur2 = m_currencies[nextIndex];
            PairDirectionDataOld pairDirectionData = pairDirectionMap.get(cur1).get(cur2);
            sides.add(pairDirectionData);
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
        RoundDataOld ret = new RoundDataOld(false, roundName, takerValue, sides);
        return ret;
    }

    private RoundDataOld roundMaker(int startIndex, boolean forward, Map<Currency, Map<Currency, PairDirectionDataOld>> pairDirectionMap) {
        String roundName = buildRoundName(startIndex, forward);
//        StringBuilder makerSb = new StringBuilder();
        double makerValue = 1.0;
        boolean firstDirect = false;
//        double firstMktPrice = 0;
        double oppositePrice = 0;
        int length = m_currencies.length;
        int index = startIndex;
        List<PairDirectionDataOld> sides = new ArrayList<>();
        for (int i = 0; i < length; i++) {
            int nextIndex = (index + (forward ? 1 : -1) + length) % length;
            Currency cur1 = m_currencies[index];
            Currency cur2 = m_currencies[nextIndex];
            PairDirectionDataOld pairDirectionData = pairDirectionMap.get(cur1).get(cur2);
            sides.add(pairDirectionData);
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
        RoundDataOld ret = new RoundDataOld(true, roundName, makerRate, sides);
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

    private void preparePairDirectionData(Currency cur1, Currency cur2, Map<Currency, Map<Currency, PairDirectionDataOld>> pairDirectionMap) {
        PairDirection pairDirection = PairDirection.get(cur1, cur2);
        Pair pair = pairDirection.m_pair;
        boolean direct = pairDirection.m_forward;

        OrderBook orderBook = m_exchange.getOrderBook(pair);
        OrderBook.OrderBookEntry entry = (direct ? orderBook.m_bids : orderBook.m_asks).get(0);
        OrderBook.Spread spread = orderBook.getTopSpread();
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

        PairDirectionDataOld pairDirectionData = new PairDirectionDataOld(pairDirection, entry, mktPrice, takerRate, fullTakerRate, spread);
        Map<Currency, PairDirectionDataOld> map = pairDirectionMap.get(cur1);
        if (map == null) {
            map = new HashMap<>();
            pairDirectionMap.put(cur1, map);
        }
        map.put(cur2, pairDirectionData);
    }


    // -----------------------------------------------------------------------------------------------------------
    private static class PairDirectionDataOld {
        private final PairDirection m_pairDirection;
        private final OrderBook.OrderBookEntry m_entry;
        private final double m_mktPrice;
        private final double m_takerRate;
        private final double m_fullTakerRate;
        private final OrderBook.Spread m_spread;

        public PairDirectionDataOld(PairDirection pairDirection, OrderBook.OrderBookEntry entry, double mktPrice, double takerRate,
                                    double fullTakerRate, OrderBook.Spread spread) {
            m_pairDirection = pairDirection;
            m_entry = entry;
            m_mktPrice = mktPrice;
            m_takerRate = takerRate;
            m_fullTakerRate = fullTakerRate;
            m_spread = spread;
        }
    }


    // -----------------------------------------------------------------------------------------------------------
    private static class RoundDataOld {
        private final boolean m_maker;
        private final String m_roundName;
        private final double m_rate;
        private final List<PairDirectionDataOld> m_sides;

        public RoundDataOld(boolean maker, String roundName, double rate, List<PairDirectionDataOld> sides) {
            m_maker = maker;
            m_roundName = roundName;
            m_rate = rate;
            m_sides = sides;
        }

        @Override public String toString() {
            return "RD{" + m_roundName +
                    " " + (m_maker ? "maker" : "taker") +
                    " rate=" + Utils.format8(m_rate) +
                    '}';
        }

        @Override public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            RoundDataOld roundData = (RoundDataOld) o;

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
    private static class BestRoundDataComparator implements Comparator<RoundDataOld> {
        @Override public int compare(RoundDataOld o1, RoundDataOld o2) {
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


    // -----------------------------------------------------------------------------------------------------------
    private enum State {
        watching,
        catching;

        public State onBooksUpdated() {
            return null; // no state change
        }
    }

    // -----------------------------------------------------------------------------------------------------------
    private static class RoundData {
        public final Round m_round;
        private final List<RoundDirectedData> m_directedRounds = new ArrayList<>();

        public RoundData(Currency[] currencies) {
            m_round = Round.get(currencies[0], currencies[1], currencies[2]);

            int length = currencies.length;
            for(int i = 0; i < length; i++) {
                add(i, true);
                add(i, false);
            }
        }

        @Override public String toString() {
            return m_round.toString();
        }

        private void add(int startIndex, boolean forward) {
            Currency[] roundCurrencies = m_round.m_currencies;
            int length = roundCurrencies.length;
            Currency[] currencies = new Currency[length];
            int index = startIndex;
            for (int i = 0; i < length; i++) {
                Currency currency = roundCurrencies[index];
                currencies[i] = currency;
                index = (index + (forward ? 1 : -1) + length) % length;
            }

            RoundDirectedData roundDirectedData = new RoundDirectedData(currencies);
            m_directedRounds.add(roundDirectedData);
        }

        public void getPairDatas(List<PairData> pds) {
            for (RoundDirectedData directedRound : m_directedRounds) {
                directedRound.getPairDatas(pds);
            }
        }
    }

    // -----------------------------------------------------------------------------------------------------------
    private static class RoundDirectedData {
        public final Currency[] m_currencies;
        public final Round m_round;
        public final String m_name;
        private final List<PairDirectionData> m_pdds = new ArrayList<>();

        public RoundDirectedData(Currency[] c) {
            m_currencies = c;
            Currency c0 = c[0];
            Currency c1 = c[1];
            Currency c2 = c[2];
            m_round = Round.get(c0, c1, c2);

            m_name = c0.m_name + "->" + c1.m_name + "->" + c2.m_name;

            int len = c.length;
            for (int i = 0; i < len; i++) {
                Currency from = c[i];
                Currency to = c[(i + 1) % len];
                PairDirectionData pairDirectionData = PairDirectionData.get(from, to);
                m_pdds.add(pairDirectionData);
            }
        }

        @Override public String toString() {
            return m_name;
        }

        @Override public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            RoundDirectedData that = (RoundDirectedData) o;
            return m_round.equals(that.m_round);
        }

        @Override public int hashCode() {
            return m_round.hashCode();
        }

        public void getPairDatas(List<PairData> pds) {
            for (PairDirectionData pdd : m_pdds) {
                PairData pairData = pdd.m_pairData;
                if (!pds.contains(pairData)) {
                    pds.add(pairData);
                }
            }
        }
    }

    // -----------------------------------------------------------------------------------------------------------
    private static class PairDirectionData {
        private static Map<PairDirection, PairDirectionData> s_map = new HashMap<>();

        public final PairDirection m_pairDirection;
        public final PairData m_pairData;

        public static PairDirectionData get(Currency from, Currency to) {
            PairDirection pairDirection = PairDirection.get(from, to);
            PairDirectionData pdd = s_map.get(pairDirection);
            if (pdd == null) {
                pdd = new PairDirectionData(pairDirection);
            }
            return pdd;
        }

        public PairDirectionData(PairDirection pairDirection) {
            m_pairDirection = pairDirection;
            m_pairData = PairData.get(pairDirection.m_pair);
        }

        @Override public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            PairDirectionData that = (PairDirectionData) o;
            return m_pairDirection.equals(that.m_pairDirection);
        }

        @Override public int hashCode() {
            return m_pairDirection.hashCode();
        }
    }

    // -----------------------------------------------------------------------------------------------------------
    private static class PairData {
        private static Map<Pair, PairData> s_map = new HashMap<>();

        public final Pair m_pair;

        public static PairData get(Pair pair) {
            PairData pairData = s_map.get(pair);
            if (pairData == null) {
                pairData = new PairData(pair);
            }
            return pairData;
        }

        public PairData(Pair pair) {
            m_pair = pair;
        }

        @Override public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            PairData pairData = (PairData) o;
            return m_pair.equals(pairData.m_pair);
        }

        @Override public int hashCode() {
            return m_pair.hashCode();
        }

        @Override public String toString() {
            return m_pair.toString();
        }
    }
}
