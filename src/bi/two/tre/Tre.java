package bi.two.tre;

import bi.two.exch.*;
import bi.two.exch.impl.CexIo;
import bi.two.util.MapConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class Tre {
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

            m_exchange.connect(new Exchange.IExchangeConnectListener() {
                @Override public void onConnected() {
                    try {
                        System.out.println("onConnected() " + m_exchange);
                        start();
                    } catch (Exception e) {
                        System.out.println("onConnected error: " + e);
                        e.printStackTrace();
                    }
                }

                @Override public void onDisconnected() {
                    for (RoundData roundData : m_roundDatas) {
                        roundData.onDisconnected();
                    }
                    for (PairData pairData : m_pairDatas) {
                        pairData.onDisconnected();
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


    // can be called on reconnect ?
    private void start() throws Exception {
        System.out.println("start()");

        initIfNeeded();

        System.out.println("queryAccount()...");
        m_exchange.queryAccount(new Exchange.IAccountListener() {
            @Override public void onUpdated() throws Exception {
                System.out.println("Account.onOrderBookUpdated() " + m_exchange.m_accountData);
                subscribeBooks();
            }
        });
    }

    private void initIfNeeded() {
        if (m_roundDatas.isEmpty()) {
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
            }
            System.out.println("pairDatas: " + m_pairDatas);
        }
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
        pairData.subscribeOrderBook(orderBook, SNAPSHOT_ONLY, SUBSCRIBE_DEPTH);
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
