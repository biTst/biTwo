package bi.two.main2;

import bi.two.chart.ITickData;
import bi.two.chart.TickData;
import bi.two.chart.TradeData;
import bi.two.exch.Direction;
import bi.two.exch.Exchange;
import bi.two.exch.Pair;
import bi.two.ts.TicksTimesSeriesData;
import bi.two.util.Log;
import bi.two.util.MapConfig;
import bi.two.util.Utils;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

// -----------------------------------------------------------------------------------------------------------
public class TradesPreloader implements Runnable {
    private static final int SLEEP_MILLIS = 2000; // do not DDOS

    private final Exchange m_exchange;
    private final Pair m_pair;
    private final TicksCacheReader m_ticksCacheReader;
    private final long m_periodToPreload;
    private final TicksTimesSeriesData<TickData> m_ticksTs;
    private boolean m_waitingFirstTrade = true;
    private long m_firstTradeTimestamp;
    private long m_lastLiveTradeTimestamp;
    private final List<TradeData> m_liveTicks = new ArrayList<>();
    private List<TradesCacheEntry> m_cache = new ArrayList<>();
    private boolean m_preloaded;

    private static void console(String s) { Log.console(s); }
    private static void log(String s) { Log.log(s); }
    private static void err(String s, Throwable t) { Log.err(s, t); }

    public TradesPreloader(Exchange exchange, Pair pair, long preload, MapConfig config, TicksTimesSeriesData<TickData> ticksTs) {
        m_exchange = exchange;
        m_pair = pair;
        m_periodToPreload = preload;
        m_ticksTs = ticksTs;

        m_ticksCacheReader = m_exchange.getTicksCacheReader(config); // todo: remove ?
    }

    protected void onTicksPreloaded() {}
    protected void onLiveTick() {}

    public void addNewestTick(TradeData td) {
        synchronized (m_liveTicks) {
            if (m_preloaded) {
                playTick(td);
                onLiveTick();
            } else {
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
        }
    }

    @Override public void run() {
        console("TradesPreloader thread started");

        try {
            loadCacheInfo();
            long oldestTradeTime = downloadHistoryTrades();
            playCacheTrades(oldestTradeTime);
            playLiveTicks();
        } catch (Exception e) {
            err("TradesPreloader error: " + e, e);
        }
    }

    public void playOnlyCache() throws IOException {
        loadCacheInfo();

        long stamp = System.currentTimeMillis();
        for (TradesCacheEntry tradesCacheEntry : m_cache) {
            long oldest = tradesCacheEntry.m_oldestTimestamp;
            stamp = Math.min(stamp, oldest);
        }

        playCacheTrades(stamp);
    }

    private void playCacheTrades(long oldestTradeTime) throws IOException {
        console("playCacheTrades: oldestTradeTime=" + oldestTradeTime);
        long timestamp = oldestTradeTime;

        while (true) {
            console(" next iteration: timestamp=" + timestamp + ". date=" + new Date(timestamp));
            boolean matched = false;
            int skippedTicksNum = 0;
            int addedTicksNum = 0;
            for (TradesCacheEntry cacheEntry : m_cache) {
                matched = (cacheEntry.m_oldestPartialTimestamp < timestamp) && (timestamp <= cacheEntry.m_newestTimestamp);
                if (matched) {
                    log(" got matched cacheEntry: " + cacheEntry);
                    List<TickData> historyTicks = cacheEntry.loadTrades(m_ticksCacheReader);
                    for (TickData tick : historyTicks) {
                        long tickTime = tick.getTimestamp();
                        if (timestamp <= tickTime) {
                            if (timestamp < tickTime) {
                                timestamp = tickTime;
                            }
                            playTick(tick);
                            addedTicksNum++;
                        } else {
                            skippedTicksNum++;
                        }
                    }
                    timestamp++;
                    log("  added " + addedTicksNum + " ticks, skipped " + skippedTicksNum + " ticks");
                    break;
                }
            }
            if (!matched) {
                console(" no more matched cacheEntries");
                break;
            }
        }
    }

    private void playLiveTicks() {
        synchronized (m_liveTicks) {
            console("playLiveTicks: size=" + m_liveTicks.size());
            for (TradeData liveTick : m_liveTicks) {
//                console("  tick: " + liveTick);
                playTick(liveTick);
            }
            m_liveTicks.clear();
            m_preloaded = true;
            onTicksPreloaded();
        }
    }

    private void playTick(TickData tick) {
        m_ticksTs.addNewestTick(tick);
    }

    private long downloadHistoryTrades() throws Exception {
        console("downloadHistoryTrades for period: " + Utils.millisToYDHMSStr(m_periodToPreload));
        int ticksNumInBlockToLoad = m_exchange.getMaxTradeHistoryLoadCount(); // bitmex: Maximum result count is 500
        int maxIterations = 10000; // 1000 iteration ~= =18h
        long timestamp = m_lastLiveTradeTimestamp;
        int iteration = 1;
        while (iteration < maxIterations) {
            console("---- next iteration[" + iteration + "]: timestamp=" + timestamp);

            long cacheTimestamp = probeCache(timestamp);
            if (cacheTimestamp == 0) {
                timestamp = downloadHistoryTrades(timestamp, ticksNumInBlockToLoad);
                Thread.sleep(SLEEP_MILLIS); // do not DDOS
            } else {
                timestamp = cacheTimestamp; // got cached trades block containing requested timestamp; update timestamp to oldest block trade time
            }

            long allPeriod = m_lastLiveTradeTimestamp - timestamp; // note: m_lastLiveTradeTimestamp updates on live trades
            console(" history ticks blocks num = " + m_cache.size() + "; period=" + Utils.millisToYDHMSStr(allPeriod));

            if (allPeriod > m_periodToPreload) {
                console("requested period loaded: " + Utils.millisToYDHMSStr(m_periodToPreload));
                break;
            }
            iteration++;
            if (iteration == maxIterations) {
                console("maxIterations=" + maxIterations + " reached. stopping downloadHistoryTrades");
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

    private long downloadHistoryTrades(long timestamp, int ticksNumInBlockToLoad) throws Exception {
        console("downloadHistoryTrades() timestamp=" + timestamp + "; ticksNumInBlockToLoad=" + ticksNumInBlockToLoad + " ...");

        long actualTimestamp = timestamp + 1; // todo: make this exch dependent: bitmex loads trades with timestamp LESS than passed, so add 1ms to load from incoming
        List<? extends ITickData> trades = m_exchange.loadTrades(m_pair, actualTimestamp, Direction.backward, ticksNumInBlockToLoad);

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
                int increasedTicksNumInBlockToLoad = ticksNumInBlockToLoad * 2;
                int maxTicksNumInBlockToLoad = m_exchange.getMaxTradeHistoryLoadCount(); // bitmex: Maximum result count is 500
                console("!!! ALL block ticks with the same timestamp. increasedTicksNumInBlockToLoad=" + increasedTicksNumInBlockToLoad + "; maxTicksNumInBlockToLoad=" + maxTicksNumInBlockToLoad);
                if (increasedTicksNumInBlockToLoad <= maxTicksNumInBlockToLoad) {
                    console(" - re-requesting with the bigger block " + increasedTicksNumInBlockToLoad);
                    Thread.sleep(SLEEP_MILLIS); // do not DDOS
                    return downloadHistoryTrades(timestamp, increasedTicksNumInBlockToLoad);
                } else {
                    console("- unable to re-request with the bigger block, max block reached");

                    TradesCacheEntry tradesCacheEntry = addTradesCacheEntry(oldestPartialTimestamp, oldestPartialTimestamp, oldestPartialTimestamp);
                    writeToCache(trades, tradesCacheEntry);

                    return oldestPartialTimestamp - 1;
                }
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
                // todo: use regexp
                int indx1 = name.indexOf('-');
                if (indx1 != -1) {
                    int indx2 = name.indexOf('-', indx1 + 1);
                    if (indx2 != -1) {
                        int indx3 = name.indexOf('.', indx2 + 1);
                        if (indx3 != -1) {
                            String s1 = name.substring(0, indx1);
                            String s2 = name.substring(indx1 + 1, indx2);
                            String s3 = name.substring(indx2 + 1, indx3);
                            console(" : " + s1 + " : " + s2 + " : " + s3);
                            long t1 = Long.parseLong(s1);
                            long t2 = Long.parseLong(s2);
                            long t3 = Long.parseLong(s3);
                            addTradesCacheEntry(t1, t2, t3);
                        }
                    }
                }
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
