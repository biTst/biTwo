package bi.two;

import bi.two.algo.Algo;
import bi.two.algo.BaseAlgo;
import bi.two.chart.ITickData;
import bi.two.chart.TickData;
import bi.two.chart.TradeData;
import bi.two.exch.*;
import bi.two.ts.BaseTimesSeriesData;
import bi.two.util.ConsoleReader;
import bi.two.util.Log;
import bi.two.util.MapConfig;
import bi.two.util.Utils;

import java.io.*;
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
        Log.s_impl = new Log.FileLog(); //StdLog();
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
            final long preloadPeriod = algoImpl.getPreloadPeriod();

            final TradesPreloader preloader = new TradesPreloader(m_exchange, m_pair, preloadPeriod, config);

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
        private final TicksCacheReader m_ticksCacheReader;
        private final long m_periodToPreload;
        private boolean m_waitingFirstTrade = true;
        private long m_firstTradeTimestamp;
        private long m_lastLiveTradeTimestamp;
        private List<TradeData> m_liveTicks = new ArrayList<>();
        private List<TradesCacheEntry> m_cache = new ArrayList<>();

        public TradesPreloader(Exchange exchange, Pair pair, long preload, MapConfig config) {
            m_exchange = exchange;
            m_pair = pair;
            m_periodToPreload = preload;

            m_ticksCacheReader = m_exchange.getTicksCacheReader(config); // todo: remove ?
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
            console("TradesPreloader thread started");

            try {
                loadCacheInfo();
                long oldestTradeTime = loadHistoryTrades();
                playCacheTrades(oldestTradeTime);
            } catch (Exception e) {
                err("TradesPreloader error: " + e, e);
            }
        }

        private void playCacheTrades(long oldestTradeTime) throws IOException {
            console("playCacheTrades: oldestTradeTime=" + oldestTradeTime);
            long timestamp = oldestTradeTime;

            while (true) {
                console(" next iteration: timestamp=" + timestamp);
                boolean matched = false;
                int skippedTicksNum = 0;
                for (TradesCacheEntry cacheEntry : m_cache) {
                    matched = (cacheEntry.m_oldestPartialTimestamp < timestamp) && (timestamp <= cacheEntry.m_newestTimestamp);
                    if (matched) {
                        console(" got matched cacheEntry: " + cacheEntry);
                        List<TickData> historyTicks = cacheEntry.loadTrades(m_ticksCacheReader);
                        for (TickData tick : historyTicks) {
                            long tickTime = tick.getTimestamp();
                            if (timestamp <= tickTime) {
                                if (timestamp < tickTime) {
                                    timestamp = tickTime;
                                }
                                if (skippedTicksNum > 0) {
                                    console("  skipped " + skippedTicksNum + " ticks");
                                    skippedTicksNum = 0;
                                }
                                console("  tick: " + tick);
                            } else {
                                log("  skip tick: " + tick);
                                skippedTicksNum++;
                            }
                        }
                        timestamp++;
                        break;
                    }
                }
                if (!matched) {
                    console(" no more matched cacheEntries");
                    break;
                }
            }
        }

        private long loadHistoryTrades() throws Exception {
            console("loadHistoryTrades for period: " + Utils.millisToYDHMSStr(m_periodToPreload));
            int ticksNumInBlockToLoad = 500; // todo: make this exch dependent. bitmex: Maximum result count is 500
            int maxIterations = 1000;
            long timestamp = m_lastLiveTradeTimestamp;
            for (int i = 0; i < maxIterations; i++) {
                console("---- next iteration[" + i + "]: timestamp=" + timestamp);

                long cacheTimestamp = probeCache(timestamp);
                if (cacheTimestamp == 0) {
                    timestamp = loadHistoryTrades(timestamp, ticksNumInBlockToLoad);
                    TimeUnit.SECONDS.sleep(1); // do not DDOS
                } else {
                    timestamp = cacheTimestamp; // got cached trades block containing requested timestamp; update timestamp to oldest block trade time
                }

                long allPeriod = m_lastLiveTradeTimestamp - timestamp; // note: m_lastLiveTradeTimestamp updates on live trades
                console(" history ticks blocks num = " + m_cache.size() + "; period=" + Utils.millisToYDHMSStr(allPeriod));

                if (allPeriod > m_periodToPreload) {
                    console("requested period loaded: " + Utils.millisToYDHMSStr(m_periodToPreload));
                    break;
                }
            }
            return m_lastLiveTradeTimestamp - m_periodToPreload;
        }

        private long probeCache(long timestamp) {
            for (TradesCacheEntry cacheEntry : m_cache) {
                boolean matched = cacheEntry.probe(timestamp);
                if (matched) {
                    console(" got matched cacheEntry: " + cacheEntry);
                    long ret = cacheEntry.m_oldestPartialTimestamp;
                    return ret;
                }
            }
            return 0;
        }

        private long loadHistoryTrades(long timestamp, int ticksNumInBlockToLoad) throws Exception {
            console("loadHistoryTrades() timestamp=" + timestamp + "; ticksNumInBlockToLoad=" + ticksNumInBlockToLoad + " ...");

            // todo: make this exch dependent: bitmex loads trades with timestamp LESS than passed, so add 1ms to load from incoming
            List<? extends ITickData> trades = m_exchange.loadTrades(m_pair, timestamp + 1, Direction.backward, ticksNumInBlockToLoad);

            int tradesNum = trades.size();
            int numToLogAtEachSide = 7;
            for (int i = 0; i < tradesNum; i++) {
                ITickData trade = trades.get(i);
                log("[" + i + "] " + trade.toString());
                if (i == numToLogAtEachSide - 1) {
                    i = tradesNum - (numToLogAtEachSide + 1);
                    log("...");
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
                    log("first live_trade - history_trade time diff=" + diff);

                    long oldestTimestamp = oldestPartialTimestamp;
                    int cutIndex = lastIndex;
                    while (cutIndex >= 0) {
                        int checkIndex = cutIndex - 1;
                        ITickData cut = trades.get(checkIndex);
                        long cutTimestamp = cut.getTimestamp();
                        log("cutTimestamp[" + checkIndex + "]=" + cutTimestamp);
                        if (oldestPartialTimestamp != cutTimestamp) {
                            oldestTimestamp = cutTimestamp;
                            break;
                        }
                        cutIndex--;
                    }

                    TradesCacheEntry tradesCacheEntry = addTradesCacheEntry(oldestPartialTimestamp, oldestTimestamp, newestTimestamp);
                    writeToCache(trades, tradesCacheEntry);

                    return oldestPartialTimestamp;
                } else {
                    console("!!! ALL block ticks with the same timestamp - re-requesting with the bigger block");
                    TimeUnit.SECONDS.sleep(1); // do not DDOS
                    return loadHistoryTrades(timestamp, ticksNumInBlockToLoad * 2);
                }
            }
            return -1; // error
        }

        private void writeToCache(List<? extends ITickData> trades, TradesCacheEntry tradesCacheEntry) {
            String fileName = tradesCacheEntry.getFileName();
            console("writeToCache() fileName=" + fileName);

            File cacheDir = m_ticksCacheReader.m_cacheDir;
            File file = new File(cacheDir, fileName);
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

                log(" writeToCache ok; fileLen=" + file.length());
            } catch (Exception e) {
                err("writeToCache error: " + e, e);
                throw new RuntimeException("writeToCache error: " + e, e);
            }
        }

        private void loadCacheInfo() {
            console("loadCacheInfo");

            File cacheDir = m_ticksCacheReader.m_cacheDir;
            String[] list = cacheDir.list();
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
                    addTradesCacheEntry(t1, t2, t3);
                }
                console("loaded " + m_cache.size() + " cache entries");
            } else {
                throw new RuntimeException("loadCacheInfo error: list=null");
            }
        }

        private TradesCacheEntry addTradesCacheEntry(long t1, long t2, long t3) {
            TradesCacheEntry tradesCacheEntry = new TradesCacheEntry(t1, t2, t3);
            m_cache.add(tradesCacheEntry);
            return tradesCacheEntry;
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

            public boolean probe(long timestamp) {
                return (m_oldestTimestamp <= timestamp) && (timestamp <= m_newestTimestamp);
            }

            @Override public String toString() {
                return "TradesCacheEntry{" +
                        "partial=" + m_oldestPartialTimestamp +
                        ", oldest=" + m_oldestTimestamp +
                        ", newest=" + m_newestTimestamp +
                        '}';
            }

            public List<TickData> loadTrades(TicksCacheReader ticksCacheReader) throws IOException {
                String fileName = getFileName();
                return ticksCacheReader.loadTrades(fileName);
            }

            public String getFileName() {
                return m_oldestPartialTimestamp + "-" + m_oldestTimestamp + "-" + m_newestTimestamp + ".trades";
            }
        }
    }


    // -----------------------------------------------------------------------------------------------------------
    public static class TicksCacheReader {
        public final DataFileType m_dataFileType;
        public final File m_cacheDir;

        public TicksCacheReader(DataFileType dataFileType, String cacheDir) {
            this(dataFileType, new File(cacheDir));
        }

        public TicksCacheReader(DataFileType dataFileType, File cacheDir) {
            m_dataFileType = dataFileType;
            m_cacheDir = cacheDir;
        }

        public List<TickData> loadTrades(String fileName) throws IOException {
            File cacheFile = new File(m_cacheDir, fileName);
            BufferedReader br = new BufferedReader(new FileReader(cacheFile));
            try {
                List<TickData> ret = new ArrayList<>();
                String line;
                while((line = br.readLine())!=null) {
                    TickData tickData = m_dataFileType.parseLine(line);
                    ret.add(tickData);
                }
                return ret;
            } finally {
                br.close();
            }
        }
    }

    // -----------------------------------------------------------------------------------------------------------
    private class IntConsoleReader extends ConsoleReader {
        @Override protected void beforeLine() { System.out.print(">"); }
        @Override protected boolean processLine(String line) throws Exception { return onConsoleLine(line); }
    }
}
