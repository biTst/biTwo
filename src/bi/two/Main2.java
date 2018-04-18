package bi.two;

import bi.two.algo.Algo;
import bi.two.algo.BaseAlgo;
import bi.two.chart.ITickData;
import bi.two.chart.TradeData;
import bi.two.exch.*;
import bi.two.ts.BaseTimesSeriesData;
import bi.two.util.ConsoleReader;
import bi.two.util.Log;
import bi.two.util.MapConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class Main2 extends Thread {
    private static final String CONFIG = "cfg\\main2.properties";

    private Exchange m_exchange;
    private Pair m_pair;

    private static void console(String s) { Log.console(s); }
    private static void log(String s) { Log.log(s); }
    private static void err(String s, Throwable t) { Log.err(s, t); }

    public static void main(final String[] args) {
        Log.s_impl = new Log.StdLog();
        MarketConfig.initMarkets(false);

        new Main2().start();
    }

    private Main2() {
        super("MAIN");
        setPriority(Thread.NORM_PRIORITY - 1); // smaller prio
    }

    @Override public void run() {
        try {
            console("Main2 started");
            MapConfig config = new MapConfig();
//            config.loadAndEncrypted(CONFIG);
            config.load(CONFIG);
            console("config loaded");

            String exchangeName = config.getString("exchange");
            m_exchange = Exchange.get(exchangeName);
            m_exchange.m_impl.init(config);

            String pairName = config.getString("pair");
            m_pair = Pair.getByName(pairName);
//            ExchPairData pairData = exchange.getPairData(pair);

            String algoName = config.getPropertyNoComment(BaseAlgo.ALGO_NAME_KEY);
            console("exchange " + exchangeName + "; pair=" + pairName + "; algo=" + algoName);
            if (algoName == null) {
                throw new RuntimeException("no '" + BaseAlgo.ALGO_NAME_KEY + "' param");
            }
            Algo algo = Algo.valueOf(algoName);
            BaseTimesSeriesData tsd = new ExchangeTradesTimesSeriesData(m_exchange, m_pair);
            BaseAlgo algoImpl = algo.createAlgo(config, tsd);
            final long preload = algoImpl.getPreloadPeriod();

            final TradesPreloader preloader = new TradesPreloader(preload);

            m_exchange.connect(new Exchange.IExchangeConnectListener() {
                @Override public void onConnected() { onExchangeConnected(preloader); }
                @Override public void onDisconnected() { onExchangeDisconnected(); }
            });

            new IntConsoleReader().start();

            Thread.sleep(TimeUnit.DAYS.toMillis(365));
            console("done");

        } catch (Exception e) {
            err("main error: " + e, e);
        }
    }

    private void onExchangeConnected(TradesPreloader preloader) {
        console("onExchangeConnected");

//        m_exchange.queryAccount(new Exchange.IAccountListener() {
//            @Override public void onUpdated() throws Exception {
//                onGotAccount();
//            }
//        });
        onGotAccount(preloader);
    }

    private void onGotAccount(final TradesPreloader preloader) {
        try {
            m_exchange.subscribeTrades(m_pair, new ExchPairData.TradesData.ITradeListener() {
                @Override public void onTrade(TradeData td) {
                    console("onTrade td=" + td);
                    preloader.addNewestTick(td);
                }
            });
        } catch (Exception e) {
            err("subscribeTrades error: " + e, e);
        }
    }

    private void onExchangeDisconnected() {
        console("onExchangeDisconnected");
    }

    private boolean onConsoleLine(String line) {
        if (line.equals("t") || line.equals("top")) {
//            logTop();
        } else {
            log("not recognized command: " + line);
        }
        return false; // do not finish ConsoleReader
    }


    //----------------------------------------------------------------------------
    private static class ExchangeTradesTimesSeriesData extends BaseTimesSeriesData {
        public ExchangeTradesTimesSeriesData(Exchange exchange, Pair pair) {

        }

        @Override public ITickData getLatestTick() {
            return null;
        }
    }


    // -----------------------------------------------------------------------------------------------------------
    private class TradesPreloader implements Runnable {
        private boolean m_waitingFirstTrade = true;
        private long m_firstTradeTimestamp;
        private long m_lastTradeTimestamp;
        private List<TradeData> m_liveTicks = new ArrayList<>();

        public TradesPreloader(long preload) {

        }

        public void addNewestTick(TradeData td) {
            m_liveTicks.add(td);
            long timestamp = td.getTimestamp();
            m_lastTradeTimestamp = timestamp;
            if (m_waitingFirstTrade) {
                m_waitingFirstTrade = false;
                m_firstTradeTimestamp = timestamp;
                console("first tick firstTradeTimestamp=" + m_firstTradeTimestamp);

                Thread thread = new Thread(this, "TradesPreloader");
                thread.setPriority(Thread.NORM_PRIORITY - 1); // smaller prio
                thread.start();
            }
        }

        @Override public void run() {
            console("TradesPreloader started");

            try {
                loadCacheInfo();
                loadNewestTrades();
            } catch (Exception e) {
                err("TradesPreloader error: " + e, e);
            }

        }

        private void loadNewestTrades() throws Exception {
            console("sleep 10 sec...");
            TimeUnit.SECONDS.sleep(10);

            console("loadNewestTrades");
            List<? extends ITickData> trades = m_exchange.loadTrades(m_pair, m_lastTradeTimestamp, Direction.backward, 10);
            for (ITickData trade : trades) {
                console(trade.toString());
            }
            if (!trades.isEmpty()) {
                ITickData first = trades.get(0);
                long timestamp = first.getTimestamp();
                long diff = m_lastTradeTimestamp - timestamp;
                console("first trade time diff=" + diff);
            }
        }

        private void loadCacheInfo() {
            console("loadCacheInfo");
            TicksCacheReader ticksCacheReader = m_exchange.getTicksCacheReader();

        }
    }


    // -----------------------------------------------------------------------------------------------------------
    public static class TicksCacheReader {
        private TicksCacheType m_type;

        public TicksCacheReader(TicksCacheType type) {
            m_type = type;
        }

        public enum TicksCacheType {
            one
        }
    }

    // -----------------------------------------------------------------------------------------------------------
    private class IntConsoleReader extends ConsoleReader {
        @Override protected void beforeLine() { System.out.print(">"); }
        @Override protected boolean processLine(String line) throws Exception { return onConsoleLine(line); }
    }
}
