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
import java.util.concurrent.atomic.AtomicInteger;

public class Tre implements OrderBook.IOrderBookListener {
    public static final boolean LOG_ROUND_CALC = false;
    public static final boolean LOG_MKT_DISTRIBUTION = false;
    public static final boolean LOG_RATES = true;
    public static final boolean CANCEL_ALL_ORDERS_AT_START = true;
    public static final int BEST_PLANS_COUNT = 40;

    private static final String CONFIG = "cfg/tre.properties";
    private static final int SUBSCRIBE_DEPTH = 7;
    private static final boolean SNAPSHOT_ONLY = true;
    private static final Currency[][] TRE_CURRENCIES = {
            {Currency.BTC, Currency.USD, Currency.BCH},
//            {Currency.BTC, Currency.USD, Currency.ETH},
//            {Currency.BTC, Currency.USD, Currency.DASH},
//            {Currency.BTC, Currency.USD, Currency.BTG},
//            {Currency.BTC, Currency.EUR, Currency.BCH},
//            {Currency.BTC, Currency.EUR, Currency.ETH},
    };

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

    private static void log(String s) { Log.log(s); }
    private static void err(String s, Throwable t) { Log.err(s, t); }

    public static void main(String[] args) {
        new Tre().main();
    }

    private void main() {
        try {
//            Log.s_impl = new Log.FileLog();
            Log.s_impl = new Log.StdLog();

            m_timer = new Timer();

            MarketConfig.initMarkets(false);

            MapConfig config = new MapConfig();
            config.loadAndEncrypted(CONFIG);

            m_exchange = Exchange.get("cex");
            m_exchange.m_impl = new CexIo(config, m_exchange);

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

    // can be called on reconnect
    private void onExchangeConnected() {
        try {
            log("onConnected() " + m_exchange);

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
        log("Exchange.onAccount() " + m_exchange.m_accountData);
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
        List<Pair> allPairs = Pair.getAllPairs();
        log(" queryOrders() allPairs=" + allPairs + "...");
        final List<Pair> waitingLiveOrders = new ArrayList<>();
        final List<String> orderIdsToCancel = new ArrayList<>();
        final AtomicInteger orderToCancelCounter = new AtomicInteger();
        for (final Pair pair : allPairs) {
            waitingLiveOrders.add(pair);
            m_exchange.queryOrders(pair, new Exchange.IOrdersListener() {
                @Override public void onUpdated(Map<String, OrderData> orders) {
                    log("queryOrders.onOrders(" + pair + ") orders=" + orders);
                    try {
                        for (OrderData orderData : orders.values()) {
                            String orderId = orderData.m_orderId;
                            orderIdsToCancel.add(orderId);
                            orderToCancelCounter.incrementAndGet();
                            orderData.addOrderListener(new OrderData.IOrderListener() {
                                @Override public void onUpdated(OrderData orderData) {
                                    log("IOrderListener.onUpdated() " + orderData);
                                    try {
                                        OrderStatus status = orderData.m_status;
                                        if (status == OrderStatus.CANCELLED) {
                                            String cancelledOrderId = orderData.m_orderId;
                                            log("order cancelled: cancelledOrderId=" + cancelledOrderId);
                                            orderIdsToCancel.remove(cancelledOrderId);
                                            if (orderIdsToCancel.isEmpty()) {
                                                int ordersToCancelNum = orderToCancelCounter.get();
                                                log("all orders cancelled. ordersToCancelNum=" + ordersToCancelNum);
                                                onAllOrdersCancelled();
                                            }
                                        }
                                    } catch (Exception e) {
                                        err("IOrderListener.onUpdated error: " + e, e);
                                    }
                                }
                            });
                            log(" cancelOrder() " + orderData);
                            m_exchange.cancelOrder(orderData);
                        }
                        waitingLiveOrders.remove(pair);
                        if (waitingLiveOrders.isEmpty()) {
                            log("all pairs LiveOrders received");
                            int ordersToCancelNum = orderToCancelCounter.get();
                            if (ordersToCancelNum == 0) {
                                log(" no orders cancelled - finish");
                                onAllOrdersCancelled();
                            }
                        }
                    } catch (Exception e) {
                        err("queryOrders.onOrders error: " + e, e);
                    }
                }
            });
        }
    }

    private void onAllOrdersCancelled() throws Exception {
        log("onAllOrdersCancelled()");
        continueInit();
    }

    private void continueInit() throws Exception {
        log("continueInit()");
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
        log("subscribeBooks()");
        m_waitingBooks = new HashMap<>();
        for (PairData pairData : m_pairDatas) {
            subscribePairBook(pairData);
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
System.out.println(System.currentTimeMillis() + ": " + line);
        }

        if (m_waitingBooks != null) {
            m_waitingBooks.remove(pair);
            if (m_waitingBooks.isEmpty()) {
                System.out.println("all books are LIVE");
                m_waitingBooks = null;
                minBalance();
//                evaluate();
            }
        }
    }

    private void minBalance() {
        System.out.println("minBalance");
        Map<Pair, CurrencyValue> totalMinPassThruOrdersSize = new HashMap<>();
        for (RoundData roundData : m_roundDatas) {
            System.out.println(" roundData: " + roundData);
            Map<Pair, CurrencyValue> map = roundData.m_minPassThruOrdersSize;
            System.out.println("  minPassThruOrdersSize: " + map);
            for (Map.Entry<Pair, CurrencyValue> entry : map.entrySet()) {
                System.out.println("   entry: " + entry);
                Pair pair = entry.getKey();
                CurrencyValue minPassThruOrdersSize = entry.getValue();

                CurrencyValue currentValue = totalMinPassThruOrdersSize.get(pair);
                if (currentValue != null) {
                    Currency currentCurrency = currentValue.m_currency;
                    Currency currency = minPassThruOrdersSize.m_currency;
                    System.out.println("    current: " + currentValue + "; new=" + minPassThruOrdersSize);
                    if (currentCurrency != currency) {
                        throw new RuntimeException("currency mismatch: " + currentCurrency + " != " + currency);
                    }
                    double current = currentValue.m_value;
                    double value = minPassThruOrdersSize.m_value;
                    if (value <= current) {
                        continue;
                    }
                }
                System.out.println("     put into totalMinPassThruOrdersSize: " + pair + " -> " + minPassThruOrdersSize);
                totalMinPassThruOrdersSize.put(pair, minPassThruOrdersSize);
            }
        }
        Map<Currency,Double> minBalanceMap = new HashMap<>();
        System.out.println(" totalMinPassThruOrdersSize=" + totalMinPassThruOrdersSize);
        for (Map.Entry<Pair, CurrencyValue> entry : totalMinPassThruOrdersSize.entrySet()) {
            System.out.println("  entry=" + entry);
            Pair pair = entry.getKey();
            CurrencyValue value = entry.getValue();
            updateMinBalance(minBalanceMap, pair.m_from, value);
            updateMinBalance(minBalanceMap, pair.m_to, value);
        }
        System.out.println(" minBalanceMap=" + minBalanceMap);

        Map<Double, Currency> availableRateMap = new TreeMap<>(new Comparator<Double>() {
            @Override public int compare(Double d1, Double d2) { return Double.compare(d2, d1); } // decreasing
        });
        AccountData m_accountData = m_exchange.m_accountData;
        for (Map.Entry<Currency, Double> entry : minBalanceMap.entrySet()) {
            System.out.println("  entry=" + entry);
            Currency currency = entry.getKey();
            Double min = entry.getValue();
            double available = m_accountData.available(currency);
            System.out.println("   min=" + min + "; available=" + available);
            if (available > 0) {
                double rate = available / min;
                System.out.println("    rate=" + rate);
                availableRateMap.put(rate, currency);
            }
        }
        System.out.println(" availableRateMap=" + availableRateMap);
        Double bestRate = availableRateMap.keySet().iterator().next();
        System.out.println("  bestRate=" + bestRate);
        Currency bestBalanceCurrency = availableRateMap.get(bestRate);
        System.out.println("  bestBalanceCurrency=" + bestBalanceCurrency);

        for (Map.Entry<Currency, Double> entry : minBalanceMap.entrySet()) {
            System.out.println("  entry=" + entry);
            Currency currency = entry.getKey();
            Double min = entry.getValue();
            double available = m_accountData.available(currency);
            System.out.println("   min=" + min + "; available=" + available);

            double need = min - available;
            if (need > 0) {
                System.out.println("    not enough balance, need " + need + " " + currency);
                PairDirection pairDirection = PairDirection.get(bestBalanceCurrency, currency);
                Pair pair = pairDirection.m_pair;
                boolean supportPair = m_exchange.supportPair(pair);
                System.out.println("    pairDirection=" + pairDirection + "; pair=" + pair + "; supportPair=" + supportPair);
                if (supportPair) {
                    ExchPairData exchPairData = m_exchange.getPairData(pair);
                    CurrencyValue minOrderToCreate = exchPairData.m_minOrderToCreate;
                    System.out.println("    exchPairData=" + exchPairData + "; minOrderToCreate=" + minOrderToCreate);

                    double minOrder = minOrderToCreate.m_value;
                    Currency minOrderCurrency = minOrderToCreate.m_currency;
                    if (minOrderCurrency != currency) {
                        double minOrderConverted = m_exchange.m_accountData.convert(minOrderCurrency, currency, minOrder);
                        System.out.println("     minOrderConverted=" + minOrderConverted + " " + currency);
                        minOrder = minOrderConverted;
                    }

                    if (need < minOrder) {
                        System.out.println("    need=" + need + " is less than minOrder=" + minOrder + "; need increased to " + minOrder);
                        need = minOrder;
                    }

                    Currency sourceCurrency = pairDirection.getSourceCurrency();
                    Currency fromCurrency = pair.m_from;
                    OrderSide orderSide = OrderSide.get(sourceCurrency != fromCurrency);
                    System.out.println("    sourceCurrency=" + sourceCurrency + "; fromCurrency=" + fromCurrency + "  => orderSide=" + orderSide + " " + fromCurrency.m_name);

                    PairData pairData = PairData.get(pair);
                    OrderBook orderBook = exchPairData.getOrderBook();
                    System.out.println("     pairData=" + pairData + "; orderBook=" + orderBook);

                    ArrayList<RoundNodePlan.RoundStep> steps = new ArrayList<>();
                    CurrencyValue needValue = new CurrencyValue(need, currency);
                    double rate = RoundNodeType.LMT.distribute(pairData, null, orderSide, orderBook, needValue, steps);
                    System.out.println("         distribute() rate=" + rate);

                    for (RoundNodePlan.RoundStep step : steps) {
                        StringBuilder sb = new StringBuilder();
                        sb.append("          ");
                        step.log(sb);
                        System.out.println(sb.toString());
                    }

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
                System.out.println("    enough balance");
            }
        }
    }

    private void addOrderWatcher(OrderWatcher orderWatcher) {
        m_orderWatchers.add(orderWatcher);
    }

    private void updateMinBalance(Map<Currency, Double> minBalanceMap, Currency currency, CurrencyValue value) {
        System.out.println("   updateMinBalance for " + currency + "; value=" + value);
        double newValue = value.m_value;

        Currency valueCurrency = value.m_currency;
        if (currency != valueCurrency) {
            double convertedValue = m_exchange.m_accountData.convert(valueCurrency, currency, newValue);
            System.out.println("    convert " + valueCurrency + "->" + currency + ": " + convertedValue);
            newValue = convertedValue;
        }

        Double minBalance = minBalanceMap.get(currency);
        System.out.println("    minBalance=" + minBalance);
        if (minBalance != null) {
            if (newValue <= minBalance) {
                return;
            }
        }
        System.out.println("    put: " + currency + " -> " + newValue);
        minBalanceMap.put(currency, newValue);
    }

    private void evaluate() {
        AccountData accountData = m_exchange.m_accountData;
        for (RoundPlan plan : RoundData.s_allPlans) {
            System.out.println("evaluate " + plan);
            boolean allEnough = true;
            for (RoundNodePlan nodePlan : plan.m_roundNodePlans) {
                System.out.println(" " + nodePlan);
                CurrencyValue startValue = nodePlan.m_startValue;
                System.out.println("  startValue=" + startValue);
                Currency startCurrency = startValue.m_currency;
                double startCurrencyAvailable = accountData.available(startCurrency);
                System.out.println("   acct.available" + startCurrency + "=" + startCurrencyAvailable);
                if (startCurrencyAvailable > startValue.m_value * 2) {
                    System.out.println("    enough startCurrency " + startCurrency);
                    CurrencyValue outValue = nodePlan.m_outValue;
                    System.out.println("     outValue " + outValue);
                    Currency outCurrency = outValue.m_currency;
                    double outCurrencyAvailable = accountData.available(outCurrency);
                    System.out.println("      acct.available" + outCurrency + "=" + outCurrencyAvailable);
                    double outValueValue = outValue.m_value;
                    double outNeedMore = outValueValue - outCurrencyAvailable;
                    if (outNeedMore > 0) {
                        System.out.println("       outNeedMore=" + outNeedMore + outCurrency);

                        PairDirectionData pdd = nodePlan.m_pdd;
                        System.out.println("        pdd=" + pdd);
                        PairData pairData = pdd.m_pairData;
                        System.out.println("        pairData=" + pairData);
                        PairDirection pairDirection = pdd.m_pairDirection;
                        System.out.println("        pairDirection=" + pairDirection);
                        boolean forward = pairDirection.m_forward;
                        System.out.println("        forward=" + forward);
                        OrderSide orderSide = OrderSide.get(!forward);
                        System.out.println("        orderSide=" + orderSide);
                        ExchPairData exchPairData = pairData.m_exchPairData;
                        System.out.println("        exchPairData=" + exchPairData);
                        OrderBook orderBook = exchPairData.getOrderBook();
                        System.out.println("        orderBook=" + orderBook);

                        ArrayList<RoundNodePlan.RoundStep> steps = new ArrayList<>();
                        double rate = RoundNodeType.LMT.distribute(pairData, null, orderSide, orderBook, startValue, steps);
                        System.out.println("         distribute() rate=" + rate);

                        for (RoundNodePlan.RoundStep step : steps) {
                            StringBuilder sb = new StringBuilder();
                            sb.append("          ");
                            step.log(sb);
                            System.out.println(sb.toString());
                        }
                        return;
                    } else {
                        System.out.println("       enough outCurrency " + outCurrencyAvailable + outCurrency);
                    }
                } else {
                    System.out.println("    not enough startCurrency " + startCurrency);
                    allEnough = false;
                    break;
                }
            }
            if (allEnough) {
                System.out.println(" allEnough");
            }
        }
    }

    private void onSecTimer() {
        log("secRunnable.run()");
    }

    private void logTop() {
System.out.println("best plans: ");
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
System.out.println(sb.toString());
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
        public final Exchange m_exchange;
        public final RoundNodePlan.RoundStep m_roundStep;
        private final Pair m_pair;
        public State m_state = State.none;
        public final ExchPairData m_exchPairData;

        public OrderWatcher(Exchange exchange, RoundNodePlan.RoundStep roundStep) {
            m_exchange = exchange;
            m_roundStep = roundStep;

            m_pair = roundStep.m_pair;
            PairData pairData = PairData.get(m_pair);
            pairData.addOrderBookListener(new OrderBook.IOrderBookListener() {
                @Override public void onOrderBookUpdated(OrderBook orderBook) {
                    System.out.println("OrderWatcher.onOrderBookUpdated() orderBook=" + orderBook);
                    OrderBook.Spread topSpread = orderBook.getTopSpread();
                    System.out.println(" topSpread=" + topSpread);
                }
            });
            m_exchPairData = exchange.getPairData(m_pair);
        }

        public void start() {
            System.out.println("OrderWatcher.start()");
            OrderData orderData = new OrderData(m_exchange, null, m_pair, m_roundStep.m_orderSide, OrderType.LIMIT, m_roundStep.m_rate, m_roundStep.m_orderSize);
            System.out.println(" submitOrder: " + orderData);
//            m_exchange.submitOrder(orderData);
//            m_state = State.submitted;
        }

        private enum State {
            none,
            submitted,
        }
    }
}
