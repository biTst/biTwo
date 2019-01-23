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
import bi.two.util.Utils;

import java.io.File;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static bi.two.util.Log.console;
import static bi.two.util.Log.err;

public class Main {
    private static final String DEF_CONFIG_FILE = "cfg" + File.separator + "vary.properties";

    private static long s_maxUsedMemory = 0;

    private static final NumberFormat INTEGER_FORMAT = NumberFormat.getIntegerInstance();
    static {
        INTEGER_FORMAT.setGroupingUsed(true);
    }

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

            String botToken = config.getPropertyNoComment("telegram");
            String admin = config.getPropertyNoComment("admin");
            String botKey = config.getStringOrDefault("botKey", "@");
            TheBot theBot = TheBot.create(botToken, admin);

            MapConfig defAlgoConfig = new MapConfig();
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

            String exchangeName = config.getString("exchange");
            Exchange exchange = Exchange.get(exchangeName);
            String pairName = config.getString("pair");
            Pair pair = Pair.getByName(pairName);
//            ExchPairData pairData = exchange.getPairData(pair);

            Integer joinTicksInReader = config.getInt("joinTicksInReader");
            boolean joinTicks = (joinTicksInReader > 0);
            TickJoiner joiner;
            if (joinTicks) {
                String joinerName = config.getString("joiner");
                joiner = TickJoiner.get(joinerName);
            } else {
                joiner = null;
            }

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
            long allStartMillis = System.currentTimeMillis();
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

                BaseTicksTimesSeriesData<? extends ITickData> joinedTicksTs = (joiner != null)
                        ? joiner.createBaseTickJoiner(ticksTs, joinTicksInReader, collectTicks)
                        : ticksTs;

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

                String tickWriterName = config.getPropertyNoComment("tick.writer");
                TradesWriter tradesWriter = ((tickWriterName != null) && (i == 1)) // write on first iteration only
                                                ? TradesWriter.get(tickWriterName)
                                                : null;

                BaseTicksTimesSeriesData<TickData> writerTicksTs = (tickWriterName != null) ? new TradesWriterTicksTs(ticksTs, tradesWriter, config) : ticksTs;

                TradesReader tradesReader = TradesReader.get(tickReaderName);
                long startMillis = System.currentTimeMillis();
                tradesReader.readTicks(config, writerTicksTs, exchange);
                ticksTs.waitWhenAllFinish();
                notifyFinish(watchers);

                logResults(watchers, startMillis, theBot, botKey, i);

                if (frame != null) {
                    frame.repaint();
                }
            }
            cleanMemory(stopOnLowMemory); // clean at the end

            BaseProducer bestProducer = producer.logResults();
            if (bestProducer != null) {
                bestProducer.logResultsEx(theBot, botKey);
            }

            long allEndMillis = System.currentTimeMillis();
            console("all DONE in " + Utils.millisToYDHMSStr(allEndMillis - allStartMillis));
            Log.s_impl.flush();
        } catch (Exception e) {
            err("load data error: " + e, e);
        }

        try {
            Runtime.getRuntime().gc();
            TimeUnit.DAYS.sleep(3);
        } catch (InterruptedException e) { /*noop*/ }
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
        long maxMemory1 = Runtime.getRuntime().maxMemory();
        long usedMemory1 = totalMemory1 - freeMemory1;
        s_maxUsedMemory = Math.max(s_maxUsedMemory, usedMemory1);
        Algo.resetIterationCaches();
        Runtime.getRuntime().gc();
        long freeMemory2 = Runtime.getRuntime().freeMemory();
        long totalMemory2 = Runtime.getRuntime().totalMemory();
        long maxMemory2 = Runtime.getRuntime().maxMemory();
        long usedMemory2 = totalMemory2 - freeMemory2;

        console("memory(free/used/total/max): "
                + formatMemory(freeMemory1) + "/" + formatMemory(usedMemory1) + "/" + formatMemory(totalMemory1) + "/" + formatMemory(maxMemory1)
                + "  =>  "
                + formatMemory(freeMemory2) + "/" + formatMemory(usedMemory2) + "/" + formatMemory(totalMemory2) + "/" + formatMemory(maxMemory2)
                + "; maxUsed=" + formatMemory(s_maxUsedMemory)
        );

        if(stopOnLowMemory) {
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

    public static String formatMemory(long memory) {
        return INTEGER_FORMAT.format(memory);
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

    private static void logResults(List<Watcher> watchers, long startMillis, TheBot theBot, String botKey, int iteration) {
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

            Watcher lastWatcher = watchers.get(watchersNum - 1);
            long processedPeriod = lastWatcher.getProcessedPeriod();
            long endMillis = System.currentTimeMillis();
            console("   processedPeriod=" + Utils.millisToYDHMSStr(processedPeriod)
                    + "   spent=" + Utils.millisToYDHMSStr(endMillis - startMillis));

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
                    + "; processedDays=" + processedDays
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
