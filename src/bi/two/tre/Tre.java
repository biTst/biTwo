package bi.two.tre;

import bi.two.exch.*;
import bi.two.exch.impl.CexIo;
import bi.two.util.MapConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Tre {
    public static final boolean LOG_ROUND_CALC = false;
    public static final boolean LOG_MKT_DISTRIBUTION = false;

    private static final String CONFIG = "cfg/tre.properties";
    private static final int SUBSCRIBE_DEPTH = 7;
    private static final boolean SNAPSHOT_ONLY = true;
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
    private OrderBook.IOrderBookListener m_bookListener = new OrderBook.IOrderBookListener() {
        @Override public void onOrderBookUpdated(OrderBook orderBook) {
            System.out.println("onOrderBookUpdated: " + orderBook);
            Pair pair = orderBook.m_pair;
            PairData pairData = PairData.get(pair);
            pairData.onOrderBookUpdated(orderBook);
        }
    };
    private Timer m_timer;
    private TimerTask m_secTimerTask;
    private boolean m_initialized;
    private Runnable m_secRunnable = new Runnable() {
        @Override public void run() {
            System.out.println("secRunnable.run()");
        }
    };


    public static void main(String[] args) {
        new Tre().main();
    }

    private void main() {
        try {
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
            System.out.println("done");
        } catch (Exception e) {
            System.out.println("ERROR: " + e);
            e.printStackTrace();
        }
    }

    private void onExchangeDisconnected() {
        try {
            System.out.println("onExchangeDisconnected");
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
            System.out.println("onExchangeDisconnected error: " + e);
            e.printStackTrace();
        }
    }

    // can be called on reconnect ?
    private void onExchangeConnected() {
        try {
            System.out.println("onConnected() " + m_exchange);

            initIfNeeded();

            System.out.println(" queryAccount()...");
            m_exchange.queryAccount(new Exchange.IAccountListener() {
                @Override public void onUpdated() throws Exception {
                    System.out.println("Account.onAccount() " + m_exchange.m_accountData);
                    if (!m_initialized) {
                        initThreadPool();
                        subscribeBooks();
                        startSecTimer();
                        m_initialized = true;
                    }
                }
            });
        } catch (Exception e) {
            System.out.println("onExchangeConnected error: " + e);
            e.printStackTrace();
        }
    }

    private void initThreadPool() {
        m_threadPool = Executors.newSingleThreadExecutor();
        m_exchange.setThreadPool(m_threadPool);
    }

    private void shutdownThreadPool() {
        try {
            System.out.println("attempt to shutdown ThreadPool");
            m_threadPool.shutdown();
            m_threadPool.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            System.err.println("tasks interrupted");
        } finally {
            if (!m_threadPool.isTerminated()) {
                System.err.println("cancel non-finished tasks");
            }
            m_threadPool.shutdownNow();
            System.out.println("shutdown ThreadPool finished");
            m_threadPool = null;
        }
    }

    private void initIfNeeded() {
        if (m_roundDatas.isEmpty()) {
            mainInit();
        }
    }

    private void mainInit() { // executed only once
        for (Currency[] currencies : TRE_CURRENCIES) {
            RoundData roundData = new RoundData(currencies, m_exchange);
            m_roundDatas.add(roundData);
            roundData.getPairDatas(m_pairDatas);
        }
        System.out.println("roundDatas: " + m_roundDatas);

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
        System.out.println("pairDatas: " + m_pairDatas);
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
        System.out.println("subscribeBooks()");
        for (PairData pairData : m_pairDatas) {
            subscribePairBook(pairData);
        }
    }

    private void subscribePairBook(PairData pairData) throws Exception {
        Pair pair = pairData.m_pair;
        System.out.println(" subscribePairBook: " + pair);

        OrderBook orderBook = m_exchange.getOrderBook(pair);
        if (SNAPSHOT_ONLY) {
            orderBook.snapshot(m_bookListener, SUBSCRIBE_DEPTH);
        } else {
            orderBook.subscribe(m_bookListener, SUBSCRIBE_DEPTH);
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
}
