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
import bi.two.util.Utils;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
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

            final TradesPreloader preloader = new TradesPreloader(m_exchange, m_pair, preload, config);

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
    private static class TradesPreloader implements Runnable {
        private final Exchange m_exchange;
        private final Pair m_pair;
        private final File m_cacheDir;
        private boolean m_waitingFirstTrade = true;
        private long m_firstTradeTimestamp;
        private long m_lastLiveTradeTimestamp;
        private List<TradeData> m_liveTicks = new ArrayList<>();
        private List<List<? extends ITickData>> m_historyTicks = new ArrayList<>();
        private List<TradesCacheEntry> m_cache = new ArrayList<>();

        public TradesPreloader(Exchange exchange, Pair pair, long preload, MapConfig config) {
            m_exchange = exchange;
            m_pair = pair;

            String cacheDir = config.getPropertyNoComment("cache.dir");
            console("TradesPreloader<> cacheDir=" + cacheDir);
            if (cacheDir != null) {
                File dir = new File(cacheDir);
                if (dir.isDirectory()) {
                    m_cacheDir = dir;
                } else {
                    throw new RuntimeException("cache.dir "
                            + (dir.exists() ? "is not exist" : "is not a dir")
                            + ": " + cacheDir);
                }
            } else {
                throw new RuntimeException("cache.dir is not defined");
            }
        }

        public void addNewestTick(TradeData td) {
            m_liveTicks.add(td);
            long timestamp = td.getTimestamp();
            m_lastLiveTradeTimestamp = timestamp;
            if (m_waitingFirstTrade) {
                m_waitingFirstTrade = false;
                m_firstTradeTimestamp = timestamp;
                console("got first tick firstTradeTimestamp=" + m_firstTradeTimestamp);

                Thread thread = new Thread(this, "TradesPreloader");
                thread.setPriority(Thread.NORM_PRIORITY - 1); // smaller prio
                thread.start();
            }
        }

        @Override public void run() {
            console("TradesPreloader started");

            try {
                loadCacheInfo();
                loadHistoryTrades();
            } catch (Exception e) {
                err("TradesPreloader error: " + e, e);
            }
        }

        private void loadHistoryTrades() throws Exception {
            int ticksNumInBlockToLoad = 50;
            long timestamp = m_lastLiveTradeTimestamp;
            while(true) {
                timestamp = loadHistoryTrades(timestamp, ticksNumInBlockToLoad);
                TimeUnit.SECONDS.sleep(1); // do not DDOS
            }
        }

        private long loadHistoryTrades(long timestamp, int ticksNumInBlockToLoad) throws Exception {
            console("loadHistoryTrades() timestamp=" + timestamp + "; ticksNumInBlockToLoad=" + ticksNumInBlockToLoad + " ...");

            // bitmex loads trades with timestamp LESS than passes, so add 1ms to load from incoming
            List<? extends ITickData> trades = m_exchange.loadTrades(m_pair, timestamp + 1, Direction.backward, ticksNumInBlockToLoad);

            int tradesNum = trades.size();
            int numToLogAtEachSide = 7;
            for (int i = 0; i < tradesNum; i++) {
                ITickData trade = trades.get(i);
                console("[" + i + "] " + trade.toString());
                if (i == numToLogAtEachSide - 1) {
                    i = tradesNum - (numToLogAtEachSide + 1);
                    console("...");
                }
            }
            if (!trades.isEmpty()) {
                ITickData first = trades.get(0);
                long newestTimestamp = first.getTimestamp();
                int lastIndex = tradesNum - 1;
                ITickData last = trades.get(lastIndex);
                long oldestPartialTimestamp = last.getTimestamp();
                long diff = timestamp - newestTimestamp;
                long period = newestTimestamp - oldestPartialTimestamp;
                console(tradesNum + " trades loaded: newestTimestamp=" + newestTimestamp + "; oldestPartialTimestamp[" + lastIndex + "]=" + oldestPartialTimestamp + "; period=" + Utils.millisToYDHMSStr(period));
                if (period > 0) {
                    console("first live_trade - history_trade time diff=" + diff);

                    long oldestTimestamp = oldestPartialTimestamp;
                    int cutIndex = lastIndex;
                    while (cutIndex >= 0) {
                        int checkIndex = cutIndex - 1;
                        ITickData cut = trades.get(checkIndex);
                        long cutTimestamp = cut.getTimestamp();
                        console("cutTimestamp[" + checkIndex + "]=" + cutTimestamp);
                        if (oldestPartialTimestamp != cutTimestamp) {
                            oldestTimestamp = cutTimestamp;
                            break;
                        }
                        cutIndex--;
                    }

                    writeToCache(trades, oldestPartialTimestamp, oldestTimestamp, newestTimestamp);

                    trades = trades.subList(0, cutIndex);
                    int cutTradesNum = trades.size();
                    console("cutIndex=" + cutIndex + " -> removing " + (tradesNum - cutTradesNum) + " tail ticks");
                    int cutLastIndex = cutTradesNum - 1;
                    ITickData cutLast = trades.get(cutLastIndex);
                    long cutLastTimestamp = cutLast.getTimestamp();
                    console(cutTradesNum + " cut trades: cutLastTimestamp[" + cutLastIndex + "]=" + cutLastTimestamp);

                    m_historyTicks.add(trades);
                    long allPeriod = m_lastLiveTradeTimestamp - oldestPartialTimestamp;
                    console("-------- history ticks blocks num = " + m_historyTicks.size() + "; period=" + Utils.millisToYDHMSStr(allPeriod));

                    return oldestPartialTimestamp;
                } else {
                    console("!!! ALL block ticks with the same timestamp - re-requesting with the bigger block");
                    TimeUnit.SECONDS.sleep(1); // do not DDOS
                    return loadHistoryTrades(timestamp, ticksNumInBlockToLoad * 2);
                }
            }
            return -1; // error
        }

        private void writeToCache(List<? extends ITickData> trades, long oldestPartialTimestamp, long oldestTimestamp, long newestTimestamp) {
            String fileName = oldestPartialTimestamp + "-" + oldestTimestamp + "-" + newestTimestamp + ".trades";
            console("writeToCache() fileName=" + fileName);

            File file = new File(m_cacheDir, fileName);
            try {
                FileOutputStream fos = new FileOutputStream(file);
                try {
                    BufferedOutputStream bos = new BufferedOutputStream(fos);
                    try {
                        for (int i = trades.size() - 1; i >= 0; i--) {
                            ITickData trade = trades.get(i);
                            long timestamp = trade.getTimestamp();
                            float price = trade.getClosePrice();
                            bos.write(Long.toString(timestamp).getBytes());
                            bos.write(';');
                            bos.write(Float.toString(price).getBytes());
                            bos.write('\n');
                        }
                    } finally {
                        bos.flush();
                        bos.close();
                    }
                } finally {
                    fos.close();
                }

                console("writeToCache ok; fileLen=" + file.length());
            } catch (Exception e) {
                err("writeToCache error: " + e, e);
                throw new RuntimeException("writeToCache error: " + e, e);
            }
        }

        private void loadCacheInfo() {
            console("loadCacheInfo");
            TicksCacheReader ticksCacheReader = m_exchange.getTicksCacheReader(); // todo: remove ?

            String[] list = m_cacheDir.list();
            if (list != null) {
                for (String name : list) {
                    console("name: " + name);
                    int indx1 = name.indexOf('-');
                    int indx2 = name.indexOf('-', indx1 + 1);
                    int indx3 = name.indexOf('.', indx2 + 1);
                    String s1 = name.substring(0, indx1);
                    String s2 = name.substring(indx1 + 1, indx2);
                    String s3 = name.substring(indx2 + 1, indx3);
                    console(" : " + s1 + " : " + s2 + " : " + s3);
                    long t1 = Long.parseLong(s1);
                    long t2 = Long.parseLong(s2);
                    long t3 = Long.parseLong(s3);
                    TradesCacheEntry tradesCacheEntry = new TradesCacheEntry(t1, t2, t3);
                    m_cache.add(tradesCacheEntry);
                }
                console("loaded " + m_cache.size() + " cache entries");
            } else {
                throw new RuntimeException("loadCacheInfo error: list=null");
            }
        }

        // -----------------------------------------------------------------------------------------------------------
        public static class TradesCacheEntry {
            public final long m_oldestPartialTimestamp;
            public final long m_oldestTimestamp;
            public final long m_newestTimestamp;

            public TradesCacheEntry(long oldestPartialTimestamp, long oldestTimestamp, long newestTimestamp) {
                m_oldestPartialTimestamp = oldestPartialTimestamp;
                m_oldestTimestamp = oldestTimestamp;
                m_newestTimestamp = newestTimestamp;
            }
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
