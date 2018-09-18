package bi.two.tre;

import bi.two.exch.*;
import bi.two.exch.Currency;
import bi.two.exch.impl.CexIo;
import bi.two.util.ConsoleReader;
import bi.two.util.Log;
import bi.two.util.MapConfig;
import bi.two.util.Utils;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static bi.two.util.Log.*;

public class Tre implements OrderBook.IOrderBookListener {
    public static final boolean LOG_ROUND_CALC = false;
    public static final boolean LOG_MKT_DISTRIBUTION = false;
    public static final boolean LOG_RATES = false; // false - for smaller log file
    private static final boolean LOOK_ALL_ROUNDS = true; // search for all possible rings

    private static final boolean CANCEL_ALL_ORDERS_AT_START = true;
    private static final boolean DO_MIN_BALANCE = true;
    private static final boolean PLACE_MIN_BALANCE_ORDERS = true;
    static final boolean SEND_REPLACE = true;

    public static final int BEST_PLANS_COUNT = 40;

    private static final String CONFIG = "cfg/tre.properties";
    private static final int SUBSCRIBE_DEPTH = 7;
    private static final boolean SNAPSHOT_ONLY = false;
    private static final long TIMER_DELAY = 500; // 500 ms = 1/2 sec
    private static final int THREAD_POOL_SHUTDOWN_WAIT = 5; // 5 sec

    private static Currency[][] TRE_CURRENCIES = {
            // BTC ETH BCH XRP BTG
            {Currency.BTC, Currency.USD, Currency.ETH},
            {Currency.BTC, Currency.USD, Currency.BCH},
            {Currency.BTC, Currency.USD, Currency.XRP},
            {Currency.BTC, Currency.USD, Currency.BTG},
            {Currency.BTC, Currency.USD, Currency.DASH},
//            {Currency.BTC, Currency.EUR, Currency.ETH},
//            {Currency.BTC, Currency.GBP, Currency.BCH},
    };

    public static boolean s_analyzeRounds = false;

    private Exchange m_exchange;
    private List<RoundData> m_roundDatas = new ArrayList<>();
    private ArrayList<PairData> m_pairDatas = new ArrayList<>();
    private ExecutorService m_threadPool;
    private Timer m_timer;
    private boolean m_initialized;
    private TimerTask m_timerTask;
    private Runnable m_timerRunnable = new Runnable() { @Override public void run() { onTimer(); } };
    private String m_lastLogStr = "";
    private Map<Pair,Pair> m_waitingBooks;
    private List<IWatcher> m_watchers = new ArrayList<>();
    private long m_firstBookUpdateTime;
    private int m_bookUpdatesNum;
    public static boolean m_pauseBookRoundNotify;

    public static void main(String[] args) {
        new Tre().main();
    }

    private void main() {
        try {
            Log.s_impl = new Log.FileLog();

            m_timer = new Timer();

            MarketConfig.initMarkets(false);

            MapConfig config = new MapConfig();
            config.loadAndEncrypted(CONFIG);

            CexIo.s_reconnectTimeout = THREAD_POOL_SHUTDOWN_WAIT + 2; // thread pool waits 5 sec to finish tasks, start reconnect after that

            m_exchange = Exchange.get("cex");
            m_exchange.m_impl = new CexIo(config, m_exchange);

            m_exchange.rateLimiterActive(false); // initially allow frequent requests

            if (LOOK_ALL_ROUNDS) {
                iterateRounds();
            }

            m_exchange.connect(new Exchange.IExchangeConnectListener() {
                @Override public void onConnected() { }
                @Override public void onAuthenticated() { onExchangeConnected(); }
                @Override public void onDisconnected() { onExchangeDisconnected(); }
            });

            new IntConsoleReader().start();

            Thread.sleep(TimeUnit.DAYS.toMillis(365));
            log("done");
        } catch (Exception e) {
            err("ERROR: " + e, e);
        }
    }

    private void iterateRounds() {
        ArrayList<Currency> curr = new ArrayList<>();
        ArrayList<Pair> pairs = new ArrayList<>(m_exchange.m_pairsMap.keySet()); // supported
        console("onConnected() pairs=" + pairs);
        for (Pair pair : pairs) {
            Currency c1 = pair.m_from;
            if (!curr.contains(c1)) {
                curr.add(c1);
            }
            Currency c2 = pair.m_to;
            if (!curr.contains(c2)) {
                curr.add(c2);
            }
        }
        console(" curr=" + curr);
        Currency[] array = new Currency[3];
        ArrayList<Currency[]> out = new ArrayList<>();
        iterateRounds(array, 0, curr, 0, m_exchange.m_pairsMap, out);
        Currency[][] currencies = out.toArray(new Currency[out.size()][]);
        TRE_CURRENCIES = currencies;
    }

    private void iterateRounds(Currency[] array, int i, ArrayList<Currency> curr, int j, Map<Pair, ExchPairData> map, List<Currency[]> out) {
        for (int k = j; k < curr.size(); k++) {
            Currency currency = curr.get(k);
            if (i > 0) {
                Currency currencyFrom = array[i - 1];
                Pair pair = Pair.get(currencyFrom, currency);
                boolean contain = map.containsKey(pair);
                if (!contain) {
                    pair = Pair.get(currency, currencyFrom);
                    contain = map.containsKey(pair);
                }
                if (contain) {
                    array[i] = currency;
                    if (i == 2) { // got round
                        Currency currencyTo = array[0];
                        pair = Pair.get(currency, currencyTo);
                        contain = map.containsKey(pair);
                        if (!contain) {
                            pair = Pair.get(currencyTo, currency);
                            contain = map.containsKey(pair);
                        }
                        if (contain) {
                            console("got round: " + array[0].m_name + "-" + array[1].m_name + "-" + array[2].m_name);
                            Currency[] currencies = {array[0], array[1], array[2]};
                            out.add(currencies);
                        }
                    } else {
                        iterateRounds(array, i + 1, curr, j + 1, map, out);
                    }
                }
            } else {
                array[i] = currency;
                iterateRounds(array, i + 1, curr, j + 1, map, out);
            }
        }
    }

    // can be called on reconnect
    private void onExchangeConnected() {
        try {
            console("onConnected() " + m_exchange);

            if (m_roundDatas.isEmpty()) { // initIfNeeded
                mainInit();
            }

            s_analyzeRounds = true; // feed pairs->rounds
            m_pauseBookRoundNotify = false;

            log(" queryAccount()...");
            m_exchange.queryAccount(new Exchange.IAccountListener() {
                @Override public void onAccountUpdated() throws Exception {
                    onGotAccount();
                }
            });
        } catch (Exception e) {
            err("onExchangeConnected error: " + e, e);
        }
    }

    private void onGotAccount() throws Exception {
        console("Exchange.onAccount() " + m_exchange.m_accountData);
        if (!m_initialized) {
            m_initialized = true;
            initThreadPool();

            if (CANCEL_ALL_ORDERS_AT_START && m_exchange.m_accountData.hasAllocated()) {
                cancelAllOrders();
            } else {
                continueInit();
            }
        }
    }

    private void cancelAllOrders() throws Exception {
        console(" queryOrders() pairDatas=" + m_pairDatas + "...");
        Iterator<PairData> iterator = m_pairDatas.iterator();
        cancelAllOrders(iterator);
    }

    private void cancelAllOrders(final Iterator<PairData> pairsIterator) throws Exception {
        if (pairsIterator.hasNext()) {
            PairData nextPair = pairsIterator.next();
            console("cancelAllOrders() nextPair=" + nextPair);

            TimeUnit.MILLISECONDS.sleep(500); // small delay

            final Pair pair = nextPair.m_pair;
            m_exchange.queryOrders(pair, new Exchange.IOrdersListener() {
                @Override public void onUpdated(Map<String, OrderData> orders) {
                    console("queryOrders.onOrders(" + pair + ") orders=" + orders);
                    try {
                        if (orders.isEmpty()) {
                            console(" no live orders for pair: " + pair);
                            cancelAllOrders(pairsIterator);
                        } else {
                            Iterator<OrderData> ordersIterator = orders.values().iterator();
                            cancelAllOrders(pairsIterator, ordersIterator);
                        }
                    } catch (Exception e) {
                        String msg = "queryOrders.onOrders error: " + e;
                        console(msg);
                        err(msg, e);
                    }
                }
            });
        } else {
            console("cancelAllOrders() no more pairs - all cancelled");
            onAllOrdersCancelled();
        }
    }

    private void cancelAllOrders(final Iterator<PairData> pairsIterator, final Iterator<OrderData> ordersIterator) throws Exception {
        if (ordersIterator.hasNext()) {
            final OrderData nextOrder = ordersIterator.next();
            console("cancelAllOrders() nextOrder=" + nextOrder);
            nextOrder.addOrderListener(new OrderData.IOrderListener() {
                private boolean m_done;

                @Override public void onOrderUpdated(OrderData orderData) {
                    console("IOrderListener.onOrderUpdated() " + orderData);
                    try {
                        OrderStatus status = orderData.m_status;
                        if ((status == OrderStatus.CANCELLED) && !m_done) {
                            m_done = true; // CANCELLED can come twice; process only once
                            String cancelledOrderId = orderData.m_orderId;
                            console("order cancelled: cancelledOrderId=" + cancelledOrderId);
                            cancelAllOrders(pairsIterator, ordersIterator);
                        }
                    } catch (Exception e) {
                        String msg = "IOrderListener.onOrderUpdated error: " + e;
                        console(msg);
                        err(msg, e);
                    }
                }
            });
            console(" cancelOrder() " + nextOrder);
            m_exchange.cancelOrder(nextOrder);
        } else {
            console("cancelAllOrders() no more live orders - all cancelled");
            cancelAllOrders(pairsIterator);
        }
    }

    private void onAllOrdersCancelled() throws Exception {
        console("onAllOrdersCancelled()");
        continueInit();
    }

    private void continueInit() throws Exception {
        console("continueInit()");
        subscribeBooks();
        startTimer();
        m_exchange.rateLimiterActive(true);
    }

    private void onExchangeDisconnected() {
        try {
            log("onExchangeDisconnected");
            m_initialized = false;
            m_exchange.rateLimiterActive(false);
            stopSecTimer();
            shutdownThreadPool();
            for (RoundData roundData : m_roundDatas) {
                roundData.onDisconnected();
            }
            for (PairData pairData : m_pairDatas) {
                pairData.onDisconnected();
            }
            m_watchers.clear();
        } catch (Exception e) {
            err("onExchangeDisconnected error: " + e, e);
        }
    }

    private void initThreadPool() {
        m_threadPool = Executors.newSingleThreadExecutor();
        m_exchange.setThreadPool(m_threadPool);
    }

    private void shutdownThreadPool() {
        try {
            log("attempt to shutdown ThreadPool");
            m_threadPool.shutdown();
            m_threadPool.awaitTermination(THREAD_POOL_SHUTDOWN_WAIT, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            log("tasks interrupted");
        } finally {
            if (!m_threadPool.isTerminated()) {
                log("cancel non-finished tasks");
            }
            m_threadPool.shutdownNow();
            log("shutdown ThreadPool finished");
            m_threadPool = null;
        }
    }

    private void mainInit() { // executed only once
        for (Currency[] currencies : TRE_CURRENCIES) {
            RoundData roundData = new RoundData(currencies, m_exchange);
            m_roundDatas.add(roundData);
            roundData.fillPairDatas(m_pairDatas);
        }
        log("roundDatas: " + m_roundDatas);

        for (PairData pairData : m_pairDatas) {
            Pair pair = pairData.m_pair;
            boolean supportPair = m_exchange.supportPair(pair);
            if (!supportPair) {
                throw new RuntimeException("exchange " + m_exchange + " does not support pair " + pair);
            }

            OrderBook orderBook = m_exchange.getOrderBook(pair);
            ExchPairData exchPairData = m_exchange.getPairData(pair);
            pairData.init(exchPairData, orderBook);
        }
        log("pairDatas: " + m_pairDatas);
    }

    private void startTimer() {
        m_timerTask = new TimerTask() {
            @Override public void run() {
                if (m_threadPool != null) {
                    if (!m_threadPool.isTerminated() && !m_threadPool.isShutdown()) {
                        m_threadPool.submit(m_timerRunnable);
                    } else {
                        log("timerRunnable skipped - threadPool is Terminated or Shutdown");
                    }
                }
            }
        };
        m_timer.scheduleAtFixedRate(m_timerTask, TIMER_DELAY, TIMER_DELAY);
    }

    private void stopSecTimer() {
        m_timerTask.cancel();
        m_timer.purge();
    }

    private void subscribeBooks() throws Exception {
        console("subscribeBooks()");
        m_waitingBooks = new HashMap<>();
        for (PairData pairData : m_pairDatas) {
            subscribePairBook(pairData);
            TimeUnit.MILLISECONDS.sleep(500); // small delay
        }
        console(" all books subscribed");
    }

    private void subscribePairBook(PairData pairData) throws Exception {
        Pair pair = pairData.m_pair;
        log(" subscribePairBook: " + pair);

        OrderBook orderBook = m_exchange.getOrderBook(pair);
        if (SNAPSHOT_ONLY) {
            orderBook.snapshot(this, SUBSCRIBE_DEPTH);
        } else {
            orderBook.subscribe(this, SUBSCRIBE_DEPTH);
        }
        m_waitingBooks.put(pair, pair);
    }

    @Override public void onOrderBookUpdated(OrderBook orderBook) {
        log("onOrderBookUpdated: " + orderBook);

        Pair pair = orderBook.m_pair;
        PairData pairData = PairData.get(pair);
        pairData.onOrderBookUpdated(orderBook);

        if (m_pauseBookRoundNotify) {
            return;
        }

        StringBuilder sb = new StringBuilder();

        List<RoundPlan> filteredPlans = new ArrayList<>();
        Map<RoundDirectedData, RoundPlan> lmmMap = new HashMap<>();
        Map<RoundDirectedData, RoundPlan> llmMap = new HashMap<>();
        Map<RoundDirectedData, RoundPlan> lllMap = new HashMap<>();
        for (RoundPlan roundPlan : RoundData.s_allPlans) {
            if (roundPlan.m_roundPlanType == RoundPlanType.LMT_LMT_LMT) {
                lllMap.put(roundPlan.m_rdd, roundPlan);
            } else if (roundPlan.m_roundPlanType == RoundPlanType.LMT_LMT_MKT) {
                llmMap.put(roundPlan.m_rdd, roundPlan);
            } else if (roundPlan.m_roundPlanType == RoundPlanType.LMT_MKT_MKT) {
                lmmMap.put(roundPlan.m_rdd, roundPlan);
            } else {
                filteredPlans.add(roundPlan);
            }
        }
        TreeMap<Double, RoundPlan> matchedRoundsMap = null;
        filteredPlans = Utils.firstItems(filteredPlans, 5);
        for (RoundPlan roundPlan : filteredPlans) {
            RoundDirectedData rdd = roundPlan.m_rdd;
            roundPlan.minLog(sb);
            sb.append("-");
            RoundPlan lmmPlan = lmmMap.get(rdd);
            double lmmRate = lmmPlan.m_roundRate;
            sb.append(Utils.format6(lmmRate));
            sb.append("-");
            RoundPlan llmPlan = llmMap.get(rdd);
            double llmRate = llmPlan.m_roundRate;
            sb.append(Utils.format6(llmRate));
            sb.append("-");
            RoundPlan lllPlan = lllMap.get(rdd);
            double lllRate = lllPlan.m_roundRate;
            sb.append(Utils.format6(lllRate));
            sb.append("; ");

            double roundRate = roundPlan.m_roundRate;
            if ((roundRate > 1)
                    || ((roundRate > 0.999) && (lmmRate > 1.004) && (llmRate > 1.005) && (lllRate > 1.006))
                    || ((roundRate > 0.998) && (lmmRate > 1.006) && (llmRate > 1.008) && (lllRate > 1.010))
                    || ((roundRate > 0.997) && (lmmRate > 1.008) && (llmRate > 1.011) && (lllRate > 1.014))
                    || ((roundRate > 0.996) && (lmmRate > 1.010) && (llmRate > 1.014) && (lllRate > 1.018))
                    || ((roundRate > 0.995) && (lmmRate > 1.012) && (llmRate > 1.017) && (lllRate > 1.022))
                    || ((roundRate > 0.994) && (lmmRate > 1.014) && (llmRate > 1.020) && (lllRate > 1.026))
//                    || ((roundRate > 0.9948) && (lmmRate > 1.002) && (llmRate > 1.003) && (lllRate > 1.004))
                    ) {
                double rateSum = roundRate + lmmRate + llmRate + lllRate;
                if (matchedRoundsMap == null) {
                    matchedRoundsMap = new TreeMap<>(new Comparator<Double>() {
                        @Override public int compare(Double d1, Double d2) {
                            return Double.compare(d2, d1);
                        }
                    });
                }
                matchedRoundsMap.put(rateSum, roundPlan);
            }
        }
        String line = sb.toString();
        if (!line.equals(m_lastLogStr)) { // do not log the same twice
            m_lastLogStr = line;
            console(line);

            if (matchedRoundsMap != null) {
                console("  matched : " + matchedRoundsMap);
                RoundPlan best = matchedRoundsMap.entrySet().iterator().next().getValue();
                RoundDirectedData rdd = best.m_rdd;

                console("best: " + best.logFull());

                RoundPlan lmmPlan = lmmMap.get(rdd);
                console("lmm : " + lmmPlan.logFull());

                RoundPlan llmPlan = llmMap.get(rdd);
                console("llm : " + llmPlan.logFull());

                RoundPlan lllPlan = lllMap.get(rdd);
                console("lll : " + lllPlan.logFull());

                RoundWatcher roundWatcher = new RoundWatcher(m_exchange, best, lllPlan);
                addOrderWatcher(roundWatcher);
                roundWatcher.start();

                m_pauseBookRoundNotify = true;
            }
        }

        long millis = System.currentTimeMillis();
        if (m_firstBookUpdateTime == 0) {
            m_firstBookUpdateTime = millis;
        }
        m_bookUpdatesNum++;
        if ((millis > m_firstBookUpdateTime) && (m_bookUpdatesNum % 50 == 0)) {
            double speed = ((double) m_bookUpdatesNum) / (millis - m_firstBookUpdateTime) * 1000; // upd/sec
            console("  speed: " + speed + " upd/sec");
        }

        if (m_waitingBooks != null) {
            m_waitingBooks.remove(pair);
            if (m_waitingBooks.isEmpty()) {
                m_waitingBooks = null;
                onAllBooksLive();
            }
        }
    }

    private void onAllBooksLive() {
        console("all books are LIVE");
        if (DO_MIN_BALANCE) {
            minBalance();
        }
        if (m_watchers.isEmpty()) {
            s_analyzeRounds = true; // feed pairs->rounds if no min_balance orders
            log("orderWatcher empty - can start analyzeRounds");
        }
    }

    private void minBalance() {
        console("minBalance");
        Map<Pair, CurrencyValue> totalMinPassThruOrdersSize = new HashMap<>();
        for (RoundData roundData : m_roundDatas) {
            log(" roundData: " + roundData);
            Map<Pair, CurrencyValue> map = roundData.m_minPassThruOrdersSize;
            console("  minPassThruOrdersSize[" + roundData + "]: " + toString1(map));
            for (Map.Entry<Pair, CurrencyValue> entry : map.entrySet()) {
                log("   entry: " + entry);
                Pair pair = entry.getKey();
                CurrencyValue minPassThruOrdersSize = entry.getValue();

                CurrencyValue currentValue = totalMinPassThruOrdersSize.get(pair);
                if (currentValue != null) {
                    Currency currentCurrency = currentValue.m_currency;
                    Currency currency = minPassThruOrdersSize.m_currency;
                    log("    current: " + currentValue + "; new=" + minPassThruOrdersSize);
                    if (currentCurrency != currency) {
                        throw new RuntimeException("currency mismatch: " + currentCurrency + " != " + currency);
                    }
                    double current = currentValue.m_value;
                    double value = minPassThruOrdersSize.m_value;
                    if (value <= current) {
                        continue;
                    }
                }
                log("     put into totalMinPassThruOrdersSize: " + pair + " -> " + minPassThruOrdersSize);
                totalMinPassThruOrdersSize.put(pair, minPassThruOrdersSize);
            }
        }
        Map<Currency,Double> minBalanceMap = new HashMap<>();
        console(" totalMinPassThruOrdersSize=" + toString1(totalMinPassThruOrdersSize));
        for (Map.Entry<Pair, CurrencyValue> entry : totalMinPassThruOrdersSize.entrySet()) {
            log("  entry=" + entry);
            Pair pair = entry.getKey();
            CurrencyValue value = entry.getValue();
            updateMinBalance(minBalanceMap, pair.m_from, value);
            updateMinBalance(minBalanceMap, pair.m_to, value);
        }
        console(" minBalanceMap=" + toString2(minBalanceMap));

        Map<Double, Currency> availableRateMap = new TreeMap<>(new Comparator<Double>() {
            @Override public int compare(Double d1, Double d2) { return Double.compare(d2, d1); } // decreasing
        });
        AccountData m_accountData = m_exchange.m_accountData;
        for (Map.Entry<Currency, Double> entry : minBalanceMap.entrySet()) {
            log("  entry=" + entry);
            Currency currency = entry.getKey();
            Double min = entry.getValue();
            double available = m_accountData.getAvailable(currency);
            log("   min=" + min + "; available=" + available);
            if (available > 0) {
                double rate = available / min;
                log("    rate=" + rate);
                availableRateMap.put(rate, currency);
            }
        }
        console(" availableRateMap=" + availableRateMap);
        Double bestRate = availableRateMap.keySet().iterator().next();
        Currency bestBalanceCurrency = availableRateMap.get(bestRate);
        console("  bestRate=" + bestRate + "  =>  bestBalanceCurrency=" + bestBalanceCurrency);

        for (Map.Entry<Currency, Double> entry : minBalanceMap.entrySet()) {
            Currency currency = entry.getKey();
            Double min = entry.getValue();
            double available = m_accountData.getAvailable(currency);
            double need = min - available;
            double needRate = need / min;
            String entryLog = "  entry=" + entry + ":  min=" + Utils.format8(min) + "; available=" + available + "; need=" + Utils.format8(need) + "; needRate=" + Utils.format8(needRate);

            if (needRate > 0.1) {
                console(entryLog);
                PairDirection pairDirection = PairDirection.get(bestBalanceCurrency, currency);
                Pair pair = pairDirection.m_pair;
                boolean supportPair = m_exchange.supportPair(pair);
                console("    not enough balance, need " + Utils.format8(need) + " " + currency + "; pairDirection=" + pairDirection + "; pair=" + pair + "; supportPair=" + supportPair);
                if (supportPair) {
                    ExchPairData exchPairData = m_exchange.getPairData(pair);
                    CurrencyValue minOrderToCreate = exchPairData.m_minOrderToCreate;

                    Currency sourceCurrency = pairDirection.getSourceCurrency();
                    Currency fromCurrency = pair.m_from;
                    OrderSide orderSide = OrderSide.get(sourceCurrency != fromCurrency);
                    console("    exchPairData=" + exchPairData + "; minOrderToCreate=" + minOrderToCreate + ";  sourceCurrency=" + sourceCurrency + "; fromCurrency=" + fromCurrency + "  => orderSide=" + orderSide + " " + fromCurrency.m_name);

                    PairData pairData = PairData.get(pair);
                    OrderBook orderBook = exchPairData.getOrderBook();
                    log("     pairData=" + pairData + "; orderBook=" + orderBook);

                    OrderBook.Spread topSpread = orderBook.getTopSpread();
                    console("      topSpread=" + topSpread);

                    double minOrder = minOrderToCreate.m_value;
                    Currency minOrderCurrency = minOrderToCreate.m_currency;
                    if (minOrderCurrency != currency) {
                        double minPriceStep = exchPairData.m_minPriceStep;
                        double rate = orderSide.isBuy()
                                ? topSpread.m_bidEntry.m_price + minPriceStep
                                : topSpread.m_askEntry.m_price - minPriceStep;

                        double minOrderConverted = minOrder * rate;
                        console("     rate=" + rate + "; minOrderConverted=" + minOrderConverted + " " + currency);
                        minOrder = minOrderConverted;
                    }

                    if (need < minOrder) {
                        console("    need=" + need + " is less than minOrder=" + minOrder + "; need increased to " + minOrder);
                        need = minOrder;
                    }

                    ArrayList<RoundNodePlan.RoundStep> steps = new ArrayList<>();
                    CurrencyValue needValue = new CurrencyValue(need, currency);
                    double rate = RoundNodeType.LMT.distribute(pairData, null, orderSide, orderBook, needValue, steps, null);
                    log("         distribute() rate=" + Utils.format8(rate));

                    for (RoundNodePlan.RoundStep step : steps) {
                        StringBuilder sb = new StringBuilder();
                        sb.append("          ");
                        step.log(sb);
                        console(sb.toString());
                    }

                    if (PLACE_MIN_BALANCE_ORDERS) {
                        if (rate > 0) { // plan found
                            if (steps.size() == 1) { // run one-order nodes for now
                                OrderWatcher orderWatcher = new OrderWatcher(m_exchange, steps.get(0));
                                addOrderWatcher(orderWatcher);
                                orderWatcher.start();
                                break; // run only one order for now
                            }
                        }
                    }
                } else {
                    console("    not supported pair: " + pair);
                }
            } else {
                console(entryLog + ":  enough balance");
            }
        }
    }

    private <T extends Object> String toString1(Map<T, CurrencyValue> map) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        Iterator<Map.Entry<T, CurrencyValue>> iterator = map.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<T, CurrencyValue> entry = iterator.next();
            T key = entry.getKey();
            CurrencyValue value = entry.getValue();
            sb.append(key.toString());
            sb.append("=");
            sb.append(value.format8());
            if (iterator.hasNext()) {
                sb.append("; ");
            }
        }
        sb.append("}");
        return sb.toString();
    }

    private <T extends Object> String toString2(Map<T, Double> map) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        Iterator<Map.Entry<T, Double>> iterator = map.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<T, Double> entry = iterator.next();
            T key = entry.getKey();
            Double value = entry.getValue();
            sb.append(key.toString());
            sb.append("=");
            sb.append(Utils.format8(value));
            if (iterator.hasNext()) {
                sb.append("; ");
            }
        }
        sb.append("}");
        return sb.toString();
    }

    private void addOrderWatcher(IWatcher watcher) {
        m_watchers.add(watcher);
    }

    private void updateMinBalance(Map<Currency, Double> minBalanceMap, Currency currency, CurrencyValue value) {
        log("   updateMinBalance for " + currency + "; value=" + value);
        double newValue = value.m_value;

        Currency valueCurrency = value.m_currency;
        if (currency != valueCurrency) {
            double convertedValue = m_exchange.m_accountData.convert(valueCurrency, currency, newValue);
            log("    convert " + valueCurrency + "->" + currency + ": " + convertedValue);
            newValue = convertedValue;
        }

        Double minBalance = minBalanceMap.get(currency);
        log("    minBalance=" + minBalance);
        if (minBalance != null) {
            if (newValue <= minBalance) {
                return;
            }
        }
        log("    put: " + currency + " -> " + newValue);
        minBalanceMap.put(currency, newValue);
    }

    private void onTimer() {
        log("onTimer()");

        if (!m_watchers.isEmpty()) {
            ListIterator<IWatcher> iterator = m_watchers.listIterator();
            while (iterator.hasNext()) {
                IWatcher watcher = iterator.next();
                boolean done = watcher.onTimer();
                if (done) {
                    log("Watcher is DONE, removing: " + watcher);
                    iterator.remove();
                }
            }

            if (m_watchers.isEmpty()) {
                s_analyzeRounds = true; // feed pairs->rounds
                m_pauseBookRoundNotify = false;
                log("orderWatcher empty - can start analyzeRounds");
            }
        }
    }

    private void logTop() {
        console("best plans----------------------------------------------------------------------------------------");
        int num = Math.min(RoundData.s_bestPlans.size(), BEST_PLANS_COUNT);
        for (int j = 0; j < num; j++) {
            StringBuilder sb = new StringBuilder();
            long timestamp = 0;
            RoundPlan roundPlan = RoundData.s_bestPlans.get(j);
            for (int i = 0; i < 8; i++) {
                long nextTimestamp = roundPlan.m_timestamp;
                sb.append((nextTimestamp - timestamp));
                sb.append(": ");
                roundPlan.minLog(sb);
                sb.append("; ");
                roundPlan = roundPlan.m_nextPlan;
                if (roundPlan == null) {
                    break;
                }
                timestamp = nextTimestamp;
            }
            console(sb.toString());
        }
        console("--------------------------------------------------------------------------------------------------");
    }

    private boolean onConsoleLine(String line) {
        if (line.equals("t") || line.equals("top")) {
            logTop();
        } else {
            log("not recognized command: " + line);
        }
        return false; // do not finish ConsoleReader
    }


    // -----------------------------------------------------------------------------------------------------------
    private class IntConsoleReader extends ConsoleReader {
        @Override protected void beforeLine() { System.out.print(">"); }
        @Override protected boolean processLine(String line) throws Exception { return onConsoleLine(line); }
    }


    // -----------------------------------------------------------------------------------------------------------
    public interface IWatcher {
        /** @return true if watcher is Done. */
        boolean onTimer();
    }
}
