package bi.two.tre;

import bi.two.exch.*;
import bi.two.exch.impl.CexIo;
import bi.two.util.Log;
import bi.two.util.MapConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Tre implements OrderBook.IOrderBookListener {
    public static final boolean LOG_ROUND_CALC = false;
    public static final boolean LOG_MKT_DISTRIBUTION = false;

    private static final String CONFIG = "cfg/tre.properties";
    private static final int SUBSCRIBE_DEPTH = 7;
    private static final boolean SNAPSHOT_ONLY = false;
    private static final Currency[][] TRE_CURRENCIES = {
            {Currency.BTC, Currency.USD, Currency.BCH},
            {Currency.BTC, Currency.USD, Currency.ETH},
            {Currency.BTC, Currency.USD, Currency.DASH},
            {Currency.BTC, Currency.USD, Currency.BTG},
            {Currency.BTC, Currency.EUR, Currency.BCH},
            {Currency.BTC, Currency.EUR, Currency.ETH},
    };

    private Exchange m_exchange;
    private List<RoundData> m_roundDatas = new ArrayList<>();
    private ArrayList<PairData> m_pairDatas = new ArrayList<>();
    private State m_state = State.watching;
    private ExecutorService m_threadPool;
    private Timer m_timer;
    private TimerTask m_secTimerTask;
    private boolean m_initialized;
    private Runnable m_secRunnable = new Runnable() { @Override public void run() { onSecTimer(); } };
    private String m_lastLogStr = "";

    private static void log(String s) { Log.log(s); }
    private static void err(String s, Throwable t) { Log.err(s, t); }

    public static void main(String[] args) {
        new Tre().main();
    }

    private void main() {
        try {
//            Log.s_impl = new Log.TimestampLog();
            Log.s_impl = new Log.NoLog();

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
                    log("Account.onAccount() " + m_exchange.m_accountData);
                    if (!m_initialized) {
                        initThreadPool();
                        subscribeBooks();
                        startSecTimer();
                        m_initialized = true;
                    }
                }
            });
        } catch (Exception e) {
            err("onExchangeConnected error: " + e, e);
        }
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
                System.err.println("cancel non-finished tasks");
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
            roundData.getPairDatas(m_pairDatas);
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
    }

    @Override public void onOrderBookUpdated(OrderBook orderBook) {
        log("onOrderBookUpdated: " + orderBook);
        Pair pair = orderBook.m_pair;
        PairData pairData = PairData.get(pair);
        pairData.onOrderBookUpdated(orderBook);

        StringBuilder sb = new StringBuilder();
        List<RoundPlan> roundPlans = RoundData.s_allPlans.subList(0, 6);
        for (RoundPlan roundPlan : roundPlans) {
            roundPlan.minLog(sb);
            sb.append("; ");
        }
        String line = sb.toString();
        if (!line.equals(m_lastLogStr)) { // do not log the same twice
            m_lastLogStr = line;
            System.out.println(line);
        }
    }

    private void onSecTimer() {
        log("secRunnable.run()");

//        List<RoundPlan> roundPlans = RoundData.s_allPlans.subList(0, 6);
//        for (RoundPlan roundPlan : roundPlans) {
//            System.out.println(roundPlan.log());
//        }
    }


    // -----------------------------------------------------------------------------------------------------------
    private enum State {
        watching,
        catching;

        public State onBooksUpdated() {
            return null; // no state change
        }
    }
}
