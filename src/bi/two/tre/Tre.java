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

public class Tre implements OrderBook.IOrderBookListener {
    public static final boolean LOG_ROUND_CALC = false;
    public static final boolean LOG_MKT_DISTRIBUTION = false;
    public static final boolean LOG_RATES = true;
    private static final boolean LOOK_ALL_ROUNDS = false;

    private static final boolean CANCEL_ALL_ORDERS_AT_START = true;
    private static final boolean DO_MIN_BALANCE = true;
    private static final boolean PLACE_MIN_BALANCE_ORDERS = true;
    private static final boolean SEND_REPLACE = true;

    public static final int BEST_PLANS_COUNT = 40;

    private static final String CONFIG = "cfg/tre.properties";
    private static final int SUBSCRIBE_DEPTH = 7;
    private static final boolean SNAPSHOT_ONLY = false;
    private static Currency[][] TRE_CURRENCIES = {
            {Currency.BTC, Currency.USD, Currency.ETH},
//            {Currency.BTC, Currency.USD, Currency.BCH},
//            {Currency.BTC, Currency.USD, Currency.BTG},
//            {Currency.BTC, Currency.USD, Currency.DASH},
//            {Currency.BTC, Currency.EUR, Currency.BCH},
//            {Currency.BTC, Currency.EUR, Currency.ETH},
    };

    public static boolean s_analyzeRounds = false;

    private Exchange m_exchange;
    private List<RoundData> m_roundDatas = new ArrayList<>();
    private ArrayList<PairData> m_pairDatas = new ArrayList<>();
    private ExecutorService m_threadPool;
    private Timer m_timer;
    private TimerTask m_secTimerTask;
    private boolean m_initialized;
    private Runnable m_secRunnable = new Runnable() { @Override public void run() { onSecTimer(); } };
    private String m_lastLogStr = "";
    private Map<Pair,Pair> m_waitingBooks;
    private List<OrderWatcher> m_orderWatchers = new ArrayList<>();

    private static void console(String s) { Log.console(s); }
    private static void log(String s) { Log.log(s); }
    private static void err(String s, Throwable t) { Log.err(s, t); }

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

            m_exchange = Exchange.get("cex");
            m_exchange.m_impl = new CexIo(config, m_exchange);

            if (LOOK_ALL_ROUNDS) {
                iterateRounds();
            }

            m_exchange.connect(new Exchange.IExchangeConnectListener() {
                @Override public void onConnected() { onExchangeConnected(); }
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
        ArrayList<Currency> curr = new ArrayList<Currency>();
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

            log(" queryAccount()...");
            m_exchange.queryAccount(new Exchange.IAccountListener() {
                @Override public void onUpdated() throws Exception {
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

            if (CANCEL_ALL_ORDERS_AT_START) {
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
                @Override public void onUpdated(OrderData orderData) {
                    console("IOrderListener.onUpdated() " + orderData);
                    try {
                        OrderStatus status = orderData.m_status;
                        if (status == OrderStatus.CANCELLED) {
                            String cancelledOrderId = orderData.m_orderId;
                            console("order cancelled: cancelledOrderId=" + cancelledOrderId);
                            cancelAllOrders(pairsIterator, ordersIterator);
                        }
                    } catch (Exception e) {
                        String msg = "IOrderListener.onUpdated error: " + e;
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
        startSecTimer();
    }

    private void onExchangeDisconnected() {
        try {
            log("onExchangeDisconnected");
            m_initialized = false;
            stopSecTimer();
            shutdownThreadPool();
            for (RoundData roundData : m_roundDatas) {
                roundData.onDisconnected();
            }
            for (PairData pairData : m_pairDatas) {
                pairData.onDisconnected();
            }
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
            m_threadPool.awaitTermination(5, TimeUnit.SECONDS);
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

    private void startSecTimer() {
        m_secTimerTask = new TimerTask() {
            @Override public void run() {
                if (m_threadPool != null) {
                    m_threadPool.submit(m_secRunnable);
                }
            }
        };
        m_timer.scheduleAtFixedRate(m_secTimerTask, 1000, 1000);
    }

    private void stopSecTimer() {
        m_secTimerTask.cancel();
        m_timer.purge();
    }

    private void subscribeBooks() throws Exception {
        console("subscribeBooks()");
        m_waitingBooks = new HashMap<>();
        for (PairData pairData : m_pairDatas) {
            subscribePairBook(pairData);
            TimeUnit.MILLISECONDS.sleep(500); // small delay
        }
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

        StringBuilder sb = new StringBuilder();
        List<RoundPlan> roundPlans = Utils.firstItems(RoundData.s_allPlans, 8);
        for (RoundPlan roundPlan : roundPlans) {
            roundPlan.minLog(sb);
            sb.append("; ");
        }
        String line = sb.toString();
        if (!line.equals(m_lastLogStr)) { // do not log the same twice
            m_lastLogStr = line;
            console(line);
        }

        if (m_waitingBooks != null) {
            m_waitingBooks.remove(pair);
            if (m_waitingBooks.isEmpty()) {
                console("all books are LIVE");
                m_waitingBooks = null;
                if (DO_MIN_BALANCE) {
                    minBalance();
                }
//                evaluate();
            }
        }

        if (m_orderWatchers.isEmpty()) { // feed pairs->rounds if not min_balance orders
            s_analyzeRounds = true;
        }
    }

    private void minBalance() {
        console("minBalance");
        Map<Pair, CurrencyValue> totalMinPassThruOrdersSize = new HashMap<>();
        for (RoundData roundData : m_roundDatas) {
            log(" roundData: " + roundData);
            Map<Pair, CurrencyValue> map = roundData.m_minPassThruOrdersSize;
            console("  minPassThruOrdersSize[" + roundData + "]: " + map);
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
        console(" totalMinPassThruOrdersSize=" + totalMinPassThruOrdersSize);
        for (Map.Entry<Pair, CurrencyValue> entry : totalMinPassThruOrdersSize.entrySet()) {
            log("  entry=" + entry);
            Pair pair = entry.getKey();
            CurrencyValue value = entry.getValue();
            updateMinBalance(minBalanceMap, pair.m_from, value);
            updateMinBalance(minBalanceMap, pair.m_to, value);
        }
        console(" minBalanceMap=" + minBalanceMap);

        Map<Double, Currency> availableRateMap = new TreeMap<>(new Comparator<Double>() {
            @Override public int compare(Double d1, Double d2) { return Double.compare(d2, d1); } // decreasing
        });
        AccountData m_accountData = m_exchange.m_accountData;
        for (Map.Entry<Currency, Double> entry : minBalanceMap.entrySet()) {
            log("  entry=" + entry);
            Currency currency = entry.getKey();
            Double min = entry.getValue();
            double available = m_accountData.available(currency);
            log("   min=" + min + "; available=" + available);
            if (available > 0) {
                double rate = available / min;
                log("    rate=" + rate);
                availableRateMap.put(rate, currency);
            }
        }
        console(" availableRateMap=" + availableRateMap);
        Double bestRate = availableRateMap.keySet().iterator().next();
        console("  bestRate=" + bestRate);
        Currency bestBalanceCurrency = availableRateMap.get(bestRate);
        console("  bestBalanceCurrency=" + bestBalanceCurrency);

        for (Map.Entry<Currency, Double> entry : minBalanceMap.entrySet()) {
            console("  entry=" + entry);
            Currency currency = entry.getKey();
            Double min = entry.getValue();
            double available = m_accountData.available(currency);
            double need = min - available;
            double needRate = need / min;
            console("   min=" + min + "; available=" + available + "; need=" + need + "; needRate=" + needRate);

            if (needRate > 0.1) {
                console("    not enough balance, need " + need + " " + currency);
                PairDirection pairDirection = PairDirection.get(bestBalanceCurrency, currency);
                Pair pair = pairDirection.m_pair;
                boolean supportPair = m_exchange.supportPair(pair);
                console("    pairDirection=" + pairDirection + "; pair=" + pair + "; supportPair=" + supportPair);
                if (supportPair) {
                    ExchPairData exchPairData = m_exchange.getPairData(pair);
                    CurrencyValue minOrderToCreate = exchPairData.m_minOrderToCreate;
                    console("    exchPairData=" + exchPairData + "; minOrderToCreate=" + minOrderToCreate);

                    double minOrder = minOrderToCreate.m_value;
                    Currency minOrderCurrency = minOrderToCreate.m_currency;
                    if (minOrderCurrency != currency) {
                        double minOrderConverted = m_exchange.m_accountData.convert(minOrderCurrency, currency, minOrder);
                        console("     minOrderConverted=" + minOrderConverted + " " + currency);
                        minOrder = minOrderConverted;
                    }

                    if (need < minOrder) {
                        console("    need=" + need + " is less than minOrder=" + minOrder + "; need increased to " + minOrder);
                        need = minOrder;
                    }

                    Currency sourceCurrency = pairDirection.getSourceCurrency();
                    Currency fromCurrency = pair.m_from;
                    OrderSide orderSide = OrderSide.get(sourceCurrency != fromCurrency);
                    console("    sourceCurrency=" + sourceCurrency + "; fromCurrency=" + fromCurrency + "  => orderSide=" + orderSide + " " + fromCurrency.m_name);

                    PairData pairData = PairData.get(pair);
                    OrderBook orderBook = exchPairData.getOrderBook();
                    console("     pairData=" + pairData + "; orderBook=" + orderBook);

                    OrderBook.Spread topSpread = orderBook.getTopSpread();
                    console("      topSpread=" + topSpread);

                    ArrayList<RoundNodePlan.RoundStep> steps = new ArrayList<>();
                    CurrencyValue needValue = new CurrencyValue(need, currency);
                    double rate = RoundNodeType.LMT.distribute(pairData, null, orderSide, orderBook, needValue, steps);
                    console("         distribute() rate=" + rate);

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
                }
            } else {
                console("    enough balance");
            }
        }
    }

    private void addOrderWatcher(OrderWatcher orderWatcher) {
        m_orderWatchers.add(orderWatcher);
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

    private void evaluate() {
        AccountData accountData = m_exchange.m_accountData;
        for (RoundPlan plan : RoundData.s_allPlans) {
            console("evaluate " + plan);
            boolean allEnough = true;
            for (RoundNodePlan nodePlan : plan.m_roundNodePlans) {
                console(" " + nodePlan);
                CurrencyValue startValue = nodePlan.m_startValue;
                console("  startValue=" + startValue);
                Currency startCurrency = startValue.m_currency;
                double startCurrencyAvailable = accountData.available(startCurrency);
                console("   acct.available" + startCurrency + "=" + startCurrencyAvailable);
                if (startCurrencyAvailable > startValue.m_value * 2) {
                    console("    enough startCurrency " + startCurrency);
                    CurrencyValue outValue = nodePlan.m_outValue;
                    console("     outValue " + outValue);
                    Currency outCurrency = outValue.m_currency;
                    double outCurrencyAvailable = accountData.available(outCurrency);
                    console("      acct.available" + outCurrency + "=" + outCurrencyAvailable);
                    double outValueValue = outValue.m_value;
                    double outNeedMore = outValueValue - outCurrencyAvailable;
                    if (outNeedMore > 0) {
                        console("       outNeedMore=" + outNeedMore + outCurrency);

                        PairDirectionData pdd = nodePlan.m_pdd;
                        console("        pdd=" + pdd);
                        PairData pairData = pdd.m_pairData;
                        console("        pairData=" + pairData);
                        PairDirection pairDirection = pdd.m_pairDirection;
                        console("        pairDirection=" + pairDirection);
                        boolean forward = pairDirection.m_forward;
                        console("        forward=" + forward);
                        OrderSide orderSide = OrderSide.get(!forward);
                        console("        orderSide=" + orderSide);
                        ExchPairData exchPairData = pairData.m_exchPairData;
                        console("        exchPairData=" + exchPairData);
                        OrderBook orderBook = exchPairData.getOrderBook();
                        console("        orderBook=" + orderBook);

                        ArrayList<RoundNodePlan.RoundStep> steps = new ArrayList<>();
                        double rate = RoundNodeType.LMT.distribute(pairData, null, orderSide, orderBook, startValue, steps);
                        console("         distribute() rate=" + rate);

                        for (RoundNodePlan.RoundStep step : steps) {
                            StringBuilder sb = new StringBuilder();
                            sb.append("          ");
                            step.log(sb);
                            console(sb.toString());
                        }
                        return;
                    } else {
                        console("       enough outCurrency " + outCurrencyAvailable + outCurrency);
                    }
                } else {
                    console("    not enough startCurrency " + startCurrency);
                    allEnough = false;
                    break;
                }
            }
            if (allEnough) {
                console(" allEnough");
            }
        }
    }

    private void onSecTimer() {
        log("secRunnable.run()");

        ListIterator<OrderWatcher> iterator = m_orderWatchers.listIterator();
        while (iterator.hasNext()) {
            OrderWatcher orderWatcher = iterator.next();
            boolean done = orderWatcher.onSecTimer();
            if (done) {
                log("orderWatcher id DONE: " + orderWatcher);
                iterator.remove();
            }
        }
    }

    private void logTop() {
console("best plans: ");
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
    private static class OrderWatcher {
        public static final long MAX_LIVE_OUT_OF_SPREAD = TimeUnit.SECONDS.toMillis(5);

        public final Exchange m_exchange;
        public final RoundNodePlan.RoundStep m_roundStep;
        private final Pair m_pair;
        private final PairData m_pairData;
        public State m_state = State.none;
        public final ExchPairData m_exchPairData;
        private OrderData m_orderData;
        public long m_outOfTopSpreadTime; // time when order becomes ot of spread
        private OrderBook.Spread m_lastTopSpread;
        private OrderData.IOrderListener m_orderListener = new OrderData.IOrderListener() {
            @Override public void onUpdated(OrderData orderData) {
                onOrderUpdated(orderData);
            }
        };
        private final OrderBook.IOrderBookListener m_orderBookListener = new OrderBook.IOrderBookListener() {
            @Override public void onOrderBookUpdated(OrderBook orderBook) {
                onBookUpdated(orderBook);
            }
        };

        public OrderWatcher(Exchange exchange, RoundNodePlan.RoundStep roundStep) {
            m_exchange = exchange;
            m_roundStep = roundStep;
            m_pair = roundStep.m_pair;
            m_orderData = new OrderData(m_exchange, null, m_pair, m_roundStep.m_orderSide, OrderType.LIMIT, m_roundStep.m_rate, m_roundStep.m_orderSize);

            m_pairData = PairData.get(m_pair);
            m_pairData.addOrderBookListener(m_orderBookListener);
            m_exchPairData = exchange.getPairData(m_pair);
        }

        @Override public String toString() {
            return "OrderWatcher: " + m_orderData;
        }

        public void start() {
            try {
                console("OrderWatcher.start()");
                m_orderData.addOrderListener(m_orderListener);
                console(" submitOrder: " + m_orderData);
                m_exchange.submitOrder(m_orderData);
                m_state = State.submitted;
            } catch (Exception e) {
                String msg = "OrderWatcher.start() error: " + e;
                console(msg);
                err(msg, e);
            }
        }

        boolean onSecTimer() {
            if (m_state == State.done) {
                return true;
            }
            OrderStatus orderStatus = m_orderData.m_status;
            if (orderStatus == OrderStatus.NEW) {
                console("onSecTimer: order not yet submitted");
                return false;
            }
            if (orderStatus == OrderStatus.FILLED) {
                console("onSecTimer: order is already filled");
                m_state = State.done;
                return true;
            }
            try {
                if (m_outOfTopSpreadTime > 0) { // order is out of spread
                    long outOfSpreadOld = System.currentTimeMillis() - m_outOfTopSpreadTime;
                    if (outOfSpreadOld > MAX_LIVE_OUT_OF_SPREAD) {
                        console("onSecTimer: order out of top spread already " + outOfSpreadOld + " ms. moving...");

                        OrderBook orderBook = m_exchPairData.getOrderBook();
                        OrderBook.Spread topSpread = orderBook.getTopSpread();
                        double orderPrice = m_orderData.m_price;
                        double minPriceStep = m_exchPairData.m_minPriceStep;
                        console(" orderBook.topSpread=" + topSpread + "; orderPrice=" + orderPrice + "; minPriceStep=" + Utils.format8(minPriceStep));
                        OrderSide side = m_orderData.m_side;
                        boolean isBuy = side.isBuy();

                        double askPrice = topSpread.m_askEntry.m_price;
                        double bidPrice = topSpread.m_bidEntry.m_price;
                        double topSpreadDiff = askPrice - bidPrice;
                        double priceStep = topSpreadDiff / 10;
                        console(" topSpreadDiff=" + Utils.format8(topSpreadDiff) + "; priceStep=" + Utils.format8(priceStep));
                        if (priceStep < minPriceStep) {
                            priceStep = minPriceStep;
                            console("  priceStep fixed to minPriceStep=" + minPriceStep);
                        }

                        double price = isBuy ? bidPrice + priceStep : askPrice - priceStep;
                        console("   price=" + price + ";  spread=" + topSpread);

                        String replaceOrderId = m_orderData.m_orderId;
                        OrderData orderData = m_orderData.copyForReplace();
                        // todo: check for minOrderSize
                        orderData.m_price = price;
                        console("    submitOrderReplace: " + orderData);
                        if (SEND_REPLACE) {
                            orderData.addOrderListener(m_orderListener);
                            m_orderData = orderData;
                            m_exchange.submitOrderReplace(replaceOrderId, orderData);
                            m_outOfTopSpreadTime = 0; // reset
                        }
                    }
                }
            } catch (Exception e) {
                String msg = "OrderWatcher.onSecTimer() error: " + e;
                console(msg);
                err(msg, e);
            }
            return (m_state == State.done);
        }

        private void onBookUpdated(OrderBook orderBook) {
            console("OrderWatcher.onOrderBookUpdated() orderBook=" + orderBook);

            OrderStatus orderStatus = m_orderData.m_status;
            if (orderStatus == OrderStatus.NEW) {
                console(" order not yet submitted");
                return;
            }
            if (orderStatus == OrderStatus.FILLED) {
                console(" order is already filled");
                m_state = State.done;
                return;
            }

            OrderBook.Spread topSpread = orderBook.getTopSpread();
            if ((m_lastTopSpread == null) || !topSpread.equals(m_lastTopSpread)) {
                double orderPrice = m_orderData.m_price;
                console(" changed topSpread=" + topSpread + "; orderPrice: " + Utils.format8(orderPrice));
                m_lastTopSpread = topSpread;

                OrderSide side = m_orderData.m_side;
                boolean isBuy = side.isBuy();
                OrderBook.OrderBookEntry entry = isBuy ? topSpread.m_bidEntry : topSpread.m_askEntry;
                double sidePrice = entry.m_price;
                double delta = sidePrice - orderPrice;
                double minPriceStep = m_exchPairData.m_minPriceStep;
                console("  side price " + (isBuy ? "buy" : "ask") + "Price=" + Utils.format8(sidePrice)
                        + "; delta=" + Utils.format12(delta) + "; minPriceStep=" + Utils.format8(minPriceStep));

                if (Math.abs(delta) < (minPriceStep / 2)) {
                    console("  order is on spread side");
                    double entrySize = entry.m_size;
                    double orderRemained = m_orderData.remained();
                    console("   entrySize=" + entrySize + "; orderRemained=" + orderRemained);
                    if (entrySize > orderRemained) {
                        console("    !!! some other order on the same price");
                    } else {
                        console("    all fine - order on spread side alone");
                    }
                    m_outOfTopSpreadTime = 0; // order is on spread
                } else { // need order price update
                    console("  !!! need order price update.");
                    double topSpreadDiff = topSpread.m_askEntry.m_price - topSpread.m_bidEntry.m_price;
                    console("       topSpreadDiff=" + Utils.format8(topSpreadDiff));
                    m_outOfTopSpreadTime = System.currentTimeMillis();
                }
            }
        }

        private void onOrderUpdated(OrderData orderData) {
            console("Order.onUpdated() orderData=" + orderData);
            boolean isFilled = orderData.isFilled();
            if (isFilled) {
                console(" order is FILLED => DONE");
                m_pairData.removeOrderBookListener(m_orderBookListener);
                m_state = State.done;
            }
        }

        private enum State {
            none,
            submitted,
            done,
        }
    }
}
