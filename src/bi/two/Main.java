package bi.two;

import bi.two.algo.Algo;
import bi.two.algo.BaseAlgo;
import bi.two.algo.Watcher;
import bi.two.chart.ITickData;
import bi.two.chart.TickData;
import bi.two.exch.Exchange;
import bi.two.exch.MarketConfig;
import bi.two.exch.Pair;
import bi.two.opt.BaseProducer;
import bi.two.opt.Vary;
import bi.two.opt.WatchersProducer;
import bi.two.telegram.TheBot;
import bi.two.ts.*;
import bi.two.ts.join.TickJoiner;
import bi.two.util.Log;
import bi.two.util.MapConfig;
import bi.two.util.TimeStamp;
import bi.two.util.Utils;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static bi.two.util.Log.console;
import static bi.two.util.Log.err;

public class Main {
    private static final String DEF_CONFIG_FILE = "cfg" + File.separator + "vary.properties";

    private static long s_maxUsedMemory = 0;

    public static void main(final String[] args) {
        // cfg\vary.properties logs\log.log

        String logFileLocation = (args.length) > 1 ? args[1] : Log.FileLog.DEF_LOG_FILE_LOCATION;
        System.out.println("Started: " + (new Date()) + "; logFileLocation = " + logFileLocation);
        Log.s_impl = new Log.FileLog(logFileLocation);

        MarketConfig.initMarkets(false);

        Thread thread = new Thread("MAIN") { @Override public void run() { loadData(args); } };
        thread.setPriority(Thread.NORM_PRIORITY - 1); // smaller prio - do not bother host
        thread.start();
    }

    private static void loadData(String[] args) {
        ChartFrame frame = null;

        try {
            MapConfig config = initConfig(args);

            String botKey = config.getStringOrDefault("botKey", "@");
            TheBot theBot = TheBot.create(config);

            MapConfig defAlgoConfig = new MapConfig();
            parseParams(config, defAlgoConfig);

            Exchange exchange = Exchange.get(config);
            String pairName = config.getString("pair");
            Pair pair = Pair.getByName(pairName);
//            ExchPairData pairData = exchange.getPairData(pair);

            Integer joinTicksInReader = config.getIntOrDefault("joinTicksInReader", 0); // no join by def
            TickJoiner joiner = (joinTicksInReader > 0) ? TickJoiner.get(config) : null;

            String tickReaderName = config.getString("tick.reader");
            final boolean collectTicks = config.getBoolean("collect.ticks");
            if (collectTicks) {
                frame = new ChartFrame();
                frame.setVisible(true);
            }
            boolean collectValues = config.getBoolean(BaseAlgo.COLLECT_VALUES_KEY);

            initDefaultConfig(config, defAlgoConfig);
            // todo: copy all keys from config to defAlgoConfig ?
            defAlgoConfig.put(BaseAlgo.COLLECT_VALUES_KEY, Boolean.toString(collectValues));
            defAlgoConfig.put(BaseAlgo.ALGO_NAME_KEY, config.getString(BaseAlgo.ALGO_NAME_KEY));

            WatchersProducer producer = new WatchersProducer(config, defAlgoConfig);
            TimeStamp allStart = new TimeStamp();
            boolean stopOnLowMemory = config.getBooleanOrDefault("stopOnLowMemory", false);

            boolean chartNotLoaded = true;
            for (int i = 1; producer.isActive(); i++) {
                cleanMemory(stopOnLowMemory); // clean in every iteration

                BaseTicksTimesSeriesData<TickData> ticksTs = collectTicks
                        ? new TicksTimesSeriesData<TickData>(null)
                        : new NoTicksTimesSeriesData<TickData>(null);
                if (!collectTicks) { // add initial tick to update
                    ticksTs.addOlderTick(new TickData());
                }

                BaseTicksTimesSeriesData<? extends ITickData> joinedTicksTs = TickJoiner.wrapIfNeeded(joiner, ticksTs, joinTicksInReader, collectTicks);

                List<Watcher> watchers = producer.getWatchers(defAlgoConfig, joinedTicksTs, config, exchange, pair);
                console("## iteration " + i + "  watchers.num=" + watchers.size());

                if (watchers.isEmpty()) {
                    continue;
                }

                if (collectTicks && chartNotLoaded) {
                    ChartCanvas chartCanvas = frame.getChartCanvas();
                    setupChart(collectValues, chartCanvas, joinedTicksTs, watchers);
                    chartNotLoaded = false;
                }

                BaseTicksTimesSeriesData<TickData> writerTicksTs = (i == 1)
                        ? TradesWriterTicksTs.wrapIfNeeded(ticksTs, config) // write on first iteration only
                        : ticksTs;

                TradesReader tradesReader = TradesReader.get(tickReaderName);
                TimeStamp iterationStart = new TimeStamp();
                tradesReader.readTicks(config, writerTicksTs, exchange);
                ticksTs.waitWhenAllFinish();
                notifyFinish(watchers);

                logIterationResults(watchers, iterationStart, theBot, botKey, i);

                if (frame != null) {
                    frame.repaint();
                }
            }
            cleanMemory(stopOnLowMemory); // clean at the end

            BaseProducer bestProducer = producer.logResults();
            if (bestProducer != null) {
                bestProducer.logResultsEx(theBot, botKey);
            }

            console("all DONE in " + allStart.getPassed());
            Log.s_impl.flush();
        } catch (Exception e) {
            err("load data error: " + e, e);
        }

        try {
            Runtime.getRuntime().gc();
            TimeUnit.DAYS.sleep(3);
        } catch (InterruptedException e) { /*noop*/ }
    }

    private static void parseParams(MapConfig config, MapConfig defAlgoConfig) {
        String params = config.getString("params");
        if (params != null) {
            String[] split = params.split(";");
            for (String s : split) {
                String[] nv = s.trim().split("=");
                String name = nv[0];
                String value = nv[1];
                config.put(name, value);
                defAlgoConfig.put(name, value);
            }
        }
    }

    private static void notifyFinish(List<Watcher> watchers) {
        for (Watcher watcher : watchers) {
            watcher.notifyFinish();
        }
    }

    private static MapConfig initConfig(String[] args) throws IOException {
        String file = (args.length > 0) ? args[0] : DEF_CONFIG_FILE;
        MapConfig config = new MapConfig();
//            config.loadAndEncrypted(file);
        console("use config file: " + file);
        config.load(file);
        return config;
    }

    private static void cleanMemory(boolean stopOnLowMemory) throws InterruptedException {
        long freeMemory1 = Runtime.getRuntime().freeMemory();
        long totalMemory1 = Runtime.getRuntime().totalMemory();
        long usedMemory1 = totalMemory1 - freeMemory1;
        String memStat1 = Utils.memStat();
        s_maxUsedMemory = Math.max(s_maxUsedMemory, usedMemory1);
        Algo.resetIterationCaches();
        Runtime.getRuntime().gc();
        String memStat2 = Utils.memStat();

        console("memory(free/used/total/max): "
                + memStat1 + "  =>  " + memStat2
                + "; maxUsed=" + Utils.formatMemory(s_maxUsedMemory)
        );

        if(stopOnLowMemory) {
            long freeMemory2 = Runtime.getRuntime().freeMemory();
            long maxMemory2 = Runtime.getRuntime().maxMemory();
            double freeRate = ((double)freeMemory2) / maxMemory2;
            if (freeRate < 0.2) {
                console("*****************************************************************************");
                console("*****************************************************************************");
                console("**********         MEMORY STOP freeRate=" + freeRate);
                console("*****************************************************************************");
                console("*****************************************************************************");
                TimeUnit.DAYS.sleep(3);
            }
        }
    }

    private static void initDefaultConfig(MapConfig config, MapConfig algoConfig) {
        // read default config
        for (Vary vary : Vary.values()) {
            String name = vary.name();
            Number number = config.getNumberOrNull(vary);
            if (number != null) {
                algoConfig.put(name, number);
            }
        }
    }

    private static void setupChart(boolean collectValues, ChartCanvas chartCanvas, BaseTicksTimesSeriesData<? extends ITickData> ticksTs, List<Watcher> watchers) {
        Watcher firstWatcher = watchers.get(0);
        firstWatcher.m_algo.setupChart(collectValues, chartCanvas, ticksTs, firstWatcher);
    }

    private static void logIterationResults(List<Watcher> watchers, TimeStamp iterationStart, TheBot theBot, String botKey, int iteration) {
        int watchersNum = watchers.size();
        if (watchersNum > 0) {
            double maxGain = 0;
            Watcher maxWatcher = null;
            for (Watcher watcher : watchers) {
                double gain = watcher.totalPriceRatio(true);
                if (gain > maxGain) {
                    maxGain = gain;
                    maxWatcher = watcher;
                }
                console(watcher.getGainLogStr("", gain));
            }

            // search working watcher - non empty
            Watcher testWatcher = null;
            for (Watcher watcher : watchers) {
                if (watcher.m_tradesNum > 0) {
                    testWatcher = watcher;
                    break;
                }
            }
            if (testWatcher == null) {
                testWatcher = watchers.get(watchersNum - 1);
            }
            long processedPeriod = testWatcher.getProcessedPeriod();
            console("   processedPeriod=" + Utils.millisToYDHMSStr(processedPeriod) + "   spent=" + iterationStart.getPassed());

            double gain = 0;
            if (maxWatcher != null) {
                gain = maxWatcher.totalPriceRatio(true);
                console(maxWatcher.getGainLogStr("MAX ", gain));
            }

            double processedDays = ((double) processedPeriod) / TimeUnit.DAYS.toMillis(1);
            double perDay = Math.pow(gain, 1 / processedDays);
            double inYear = Math.pow(gain, 365 / processedDays);
            console(" perDay=" + Utils.format8(perDay)
                    + ";   *** inYear=" + Utils.format8(inYear)
                    + "; processedDays=" + Utils.format2(processedDays)
                    + " *** ....................................."
            );

            if (theBot != null) {
                theBot.sendMsg(botKey + ": i:" + iteration + " d:" + Utils.format6(perDay) + " y:" + Utils.format5(inYear), true);
            }
        }

//        console(maxWatcher.log());
//        console(ralgo.log());
    }

    //=============================================================================================
    public static class TickExtraData extends TickData {
        public final String[] m_extra;

        TickExtraData(long time, float price, String[] extra) {
            super(time, price);
            m_extra = extra;
        }

        @Override protected String getName() {
            return "TickExtraData";
        }
    }


    //=============================================================================================
    public interface IParamIterator<P> {
        void doIteration(P param);
    }
}
