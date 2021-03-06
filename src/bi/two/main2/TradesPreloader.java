package bi.two.main2;

import bi.two.chart.ITickData;
import bi.two.chart.TickData;
import bi.two.chart.TradeData;
import bi.two.exch.Direction;
import bi.two.exch.Exchange;
import bi.two.exch.Pair;
import bi.two.ts.BaseTicksTimesSeriesData;
import bi.two.util.MapConfig;
import bi.two.util.TimeStamp;
import bi.two.util.Utils;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static bi.two.util.Log.*;

// -----------------------------------------------------------------------------------------------------------
public class TradesPreloader implements Runnable {
    private static final int SLEEP_MILLIS = 2000; // do not DDOS
    private static final boolean LOG_PARSING = false;
    public static final int MAX_HISTORY_LOAD_ITERATIONS = 120000; // BitMex:  1000 iterations ~=     18h
                                                                 //         10000            ~=  6d 11h
                                                                 //         20900            ~= 15d
    private final Exchange m_exchange;
    private final Pair m_pair;
    private final TicksCacheReader m_ticksCacheReader;
    private final long m_periodToPreload;
    private final BaseTicksTimesSeriesData<TickData> m_ticksTs;
    private boolean m_waitingFirstTrade = true;
    private long m_firstTradeTimestamp;
    private long m_lastLiveTradeTimestamp;
    private final List<TradeData> m_liveTicks = new ArrayList<>();
    private List<TradesCacheEntry> m_cache = new ArrayList<>();
    private boolean m_preloaded;

    public TradesPreloader(Exchange exchange, Pair pair, long preload, MapConfig config, BaseTicksTimesSeriesData<TickData> ticksTs) {
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

        long newestTimestamp = 0;
        long oldestTimestamp = System.currentTimeMillis();
        for (TradesCacheEntry tradesCacheEntry : m_cache) {
            long oldest = tradesCacheEntry.m_oldestTimestamp;
            long newest = tradesCacheEntry.m_newestTimestamp;
            oldestTimestamp = Math.min(oldestTimestamp, oldest);
            newestTimestamp = Math.max(newestTimestamp, newest);
        }
        long period = newestTimestamp - oldestTimestamp;
        log("playOnlyCache: newestTimestamp=" + newestTimestamp + "; oldestTimestamp=" + oldestTimestamp + "; period=" + Utils.millisToYDHMSStr(period));

        long startTimestamp = (period > m_periodToPreload) ? newestTimestamp - m_periodToPreload : oldestTimestamp;
        playCacheTrades(startTimestamp);
    }

    private void playCacheTrades(long oldestTradeTime) throws IOException {
        log("playCacheTrades: oldestTradeTime=" + oldestTradeTime);
        long currentTimestamp = oldestTradeTime;
        int cacheEntriesProcessed = 0;
        long newestTimestamp = 0;
        TimeStamp timeStamp = new TimeStamp();

        int lastMatchedIndex = -1;
        while (true) {
            long startTimestamp = currentTimestamp;
            log(" next iteration: currentTimestamp=" + currentTimestamp + ". date=" + new Date(currentTimestamp));
            int skippedTicksNum = 0;
            int addedTicksNum = 0;
            int cacheSize = m_cache.size();
            List<TradesCacheEntry> matchedList = new ArrayList<>();
            List<Integer> matchedIndexes = new ArrayList<>();
            for (int i = 0; i < cacheSize; i++) {
                TradesCacheEntry cacheEntry = m_cache.get(i);
                boolean matched = cacheEntry.matched(currentTimestamp);
                if (matched) {
                    matchedList.add(cacheEntry);
                    matchedIndexes.add(i);
                }
            }
            boolean matched = false;
            int matchedNum = matchedList.size();
            if (matchedNum > 0) {
                log(" got matched " + matchedNum + " cacheEntries");
                for (int i = 0, matchedListSize = matchedList.size(); i < matchedListSize; i++) {
                    TradesCacheEntry cacheEntry = matchedList.get(i);
                    Integer index = matchedIndexes.get(i);
                    log("  matched cacheEntry[" + i + "]: index=" + index + "; " + cacheEntry);
                    long newest = cacheEntry.m_newestTimestamp;
                    List<TickData> historyTicks = cacheEntry.loadTrades(m_ticksCacheReader);
//console("playTicks : " + cacheEntry + "; size=" + historyTicks.size());
                    for (TickData tick : historyTicks) {
                        long tickTime = tick.getTimestamp();
                        if (tickTime <= newest) {
                            if (currentTimestamp <= tickTime) {
                                if (currentTimestamp < tickTime) {
                                    currentTimestamp = tickTime;
                                }
                                playTick(tick);
                                addedTicksNum++;
                            } else {
                                skippedTicksNum++;
                            }
                        } else { // else tickTime > newest
                            if (LOG_PARSING) {
                                console("ERR: tick time " + tickTime + " is bigger than for TradesCacheEntry=" + cacheEntry);
                            }
                            skippedTicksNum++;
                        }
                    }
                    if (addedTicksNum > 0) {
                        lastMatchedIndex = index;
                        currentTimestamp++;
                        cacheEntriesProcessed++;
                        newestTimestamp = Math.max(newestTimestamp, newest);
                        log("   added " + addedTicksNum + " ticks, skipped " + skippedTicksNum + " ticks");
                        break;
                    } else {
                        log("   no ticks added from " + cacheEntry);
                    }
                }
            }
            if (!matched) {
                if (LOG_PARSING) {
                    long period = newestTimestamp - oldestTradeTime;
                    console("NO MORE matched cacheEntries. cacheEntriesProcessed=" + cacheEntriesProcessed
                            + "; period=" + Utils.millisToYDHMSStr(period) + "; newestTimestamp=" + newestTimestamp);
                }

                if ((lastMatchedIndex != -1) && (lastMatchedIndex < (cacheSize - 1))) {
                    if (LOG_PARSING) {
                        int nextIndex = lastMatchedIndex + 1;
                        TradesCacheEntry cacheEntryAfter = m_cache.get(nextIndex);
                        matched = cacheEntryAfter.matched(currentTimestamp);
                        console("   next after last matched cacheEntry[" + nextIndex + "]: " + cacheEntryAfter + "; currentTimestamp=" + currentTimestamp + "; matched=" + matched);
                    }
                }

                // search cache entry after
                long min = Long.MAX_VALUE;
                for (int i = 0; i < cacheSize; i++) {
                    TradesCacheEntry cacheEntry = m_cache.get(i);
                    long oldest = cacheEntry.m_oldestTimestamp;
                    if (newestTimestamp < oldest) {
                        min = Math.min(min, oldest);
                        if (LOG_PARSING) {
                            long jump = min - newestTimestamp;
                            console("  got after jump: cacheEntry[" + i + "]=" + cacheEntry + "; min=" + min + ";  jump=" + jump);
                        }
                        currentTimestamp = oldest;
                        break;
                    }
                }
                if (min == Long.MAX_VALUE) {
                    break;
                }
            }
            if (timeStamp.getPassedMillis() > 10000) {
                timeStamp.restart();
                console("playCacheTrades : " + cacheEntriesProcessed + " of " + cacheSize);
            }
            if (startTimestamp == currentTimestamp) {
                throw new RuntimeException("error startTimestamp is not changed: " + startTimestamp);
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
        int maxIterations = MAX_HISTORY_LOAD_ITERATIONS; // 1000 iteration ~= =18h
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
                if (cacheEntry.m_newestTimestamp == ret) { // cache entry full of the same timestamp trades, step back
                    ret--;
                }
                return ret;
            }
        }
        return 0;
    }

    // [timestamp ... older)
    private long downloadHistoryTrades(long timestamp, int ticksNumInBlockToLoad) throws Exception {
        console("downloadHistoryTrades() timestamp=" + timestamp + "; ticksNumInBlockToLoad=" + ticksNumInBlockToLoad + " ...");

        long actualTimestamp = timestamp + 1; // todo: make this exch dependent: bitmex loads trades with timestamp LESS than passed, so add 1ms to load from incoming
        List<? extends ITickData> trades = m_exchange.loadTrades(m_pair, actualTimestamp, Direction.backward, ticksNumInBlockToLoad);

        int tradesNum = trades.size();
        ITickData firstTrade = trades.get(0);
        long newestTimestamp = firstTrade.getTimestamp();
        int lastIndex = tradesNum - 1;
        ITickData lastTrade = trades.get(lastIndex);
        long oldestPartialTimestamp = lastTrade.getTimestamp();

        // verify timestamps in bounds
        ListIterator<? extends ITickData> listIterator = trades.listIterator();
        while (listIterator.hasNext()) {
            ITickData trade = listIterator.next();
            long tradeTimestamp = trade.getTimestamp();
            long diff = 0;
            if (tradeTimestamp < oldestPartialTimestamp) {
                diff = tradeTimestamp - oldestPartialTimestamp;
            } else if (newestTimestamp < tradeTimestamp) {
                diff = tradeTimestamp - newestTimestamp;
            }
            if (diff != 0) {
                console("trade timestamp=" + tradeTimestamp + " out of block bounds. diff=" + diff
                        + "; [" + oldestPartialTimestamp + "..." + newestTimestamp + "] removing");
                listIterator.remove();
                tradesNum--;
                lastIndex--;
            }
        }

        int numToLogAtEachSide = 5;
        for (int i = 0; i < tradesNum; i++) {
            ITickData trade = trades.get(i);
            log("[" + i + "] " + trade.toString());
            if (i == numToLogAtEachSide - 1) {
                i = tradesNum - (numToLogAtEachSide + 1);
                log("...");
            }
        }
        if (!trades.isEmpty()) {
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
                                                                                                                 // V  use timestamp here - no breaks in time
                TradesCacheEntry tradesCacheEntry = addTradesCacheEntry(oldestPartialTimestamp, oldestTimestamp, timestamp);
                writeToCache(trades, tradesCacheEntry);

                return oldestPartialTimestamp;
            } else {
                int increasedTicksNumInBlockToLoad = ticksNumInBlockToLoad * 2;
                int maxTicksNumInBlockToLoad = m_exchange.getMaxTradeHistoryLoadCount(); // bitmex: Maximum result count is 500
                console("!!! ALL block ticks with the same timestamp=" + newestTimestamp + ". increasedTicksNumInBlockToLoad=" + increasedTicksNumInBlockToLoad + "; maxTicksNumInBlockToLoad=" + maxTicksNumInBlockToLoad);
                if (increasedTicksNumInBlockToLoad <= maxTicksNumInBlockToLoad) {
                    console(" - re-requesting with the bigger block " + increasedTicksNumInBlockToLoad);
                    Thread.sleep(SLEEP_MILLIS); // do not DDOS
                    return downloadHistoryTrades(timestamp, increasedTicksNumInBlockToLoad);
                } else {
                                                                                                                           // V  use timestamp here - no breaks in time
                    TradesCacheEntry tradesCacheEntry = addTradesCacheEntry(oldestPartialTimestamp, oldestPartialTimestamp, timestamp);
                    writeToCache(trades, tradesCacheEntry);

                    long oldestPartialTimestampMinusOne = Math.min(timestamp, oldestPartialTimestamp) - 1;
                    console("- unable to re-request with the bigger block, max block reached. request from oldestPartialTimestampMinusOne="+oldestPartialTimestampMinusOne);
                    return oldestPartialTimestampMinusOne;
                }
            }
        }
        return -1; // error
    }

    private void writeToCache(List<? extends ITickData> trades, TradesCacheEntry tradesCacheEntry) {
        String fileName = tradesCacheEntry.getFileName();
        console("writeToCache() fileName=" + fileName);

        m_ticksCacheReader.writeToCache(trades, fileName);
    }

    private void loadCacheInfo() {
        log("loadCacheInfo");

        File cacheDir = m_ticksCacheReader.m_cacheDir;
        String[] list = cacheDir.list();
        if (list != null) {
            Arrays.sort(list); // sort files by name
            for (String name : list) {
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
//console("name: " + name + " : " + s1 + " : " + s2 + " : " + s3);
                            long t1 = Long.parseLong(s1);
                            long t2 = Long.parseLong(s2);
                            long t3 = Long.parseLong(s3);
                            addTradesCacheEntry(t1, t2, t3);
                            continue;
                        }
                    }
                }
                log("ignored name: " + name);
            }
            int size = m_cache.size();
            log("loaded " + size + " cache entries");
            if (size > 0) {
                TradesCacheEntry firstEntry = m_cache.get(0);
                TradesCacheEntry lastEntry = m_cache.get(size - 1);
                log(" firstEntry=" + firstEntry + "; lastEntry=" + lastEntry);
            }
        } else {
            throw new RuntimeException("loadCacheInfo error: list=null");
        }
    }

    private TradesCacheEntry addTradesCacheEntry(long oldestPartialTimestamp, long oldestTimestamp, long newestTimestamp) {
        TradesCacheEntry tradesCacheEntry = new TradesCacheEntry(oldestPartialTimestamp, oldestTimestamp, newestTimestamp);
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

        public boolean matched(long currentTimestamp) {
            return ((m_oldestPartialTimestamp == m_oldestTimestamp)
                        ? (m_oldestTimestamp <= currentTimestamp)
                        : (m_oldestPartialTimestamp < currentTimestamp))
                    && (currentTimestamp <= m_newestTimestamp);
        }
    }
}
