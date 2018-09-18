package bi.two;

import bi.two.algo.Algo;
import bi.two.algo.BaseAlgo;
import bi.two.algo.Watcher;
import bi.two.chart.TickData;
import bi.two.exch.Exchange;
import bi.two.exch.MarketConfig;
import bi.two.exch.Pair;
import bi.two.opt.BaseProducer;
import bi.two.opt.Vary;
import bi.two.opt.WatchersProducer;
import bi.two.ts.*;
import bi.two.util.Log;
import bi.two.util.MapConfig;
import bi.two.util.Utils;

import java.io.File;
import java.text.NumberFormat;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static bi.two.util.Log.console;
import static bi.two.util.Log.err;

public class Main {
    private static NumberFormat INTEGER_FORMAT = NumberFormat.getIntegerInstance();

    static {
        INTEGER_FORMAT.setGroupingUsed(true);
    }

    public static void main(final String[] args) {
        Log.s_impl = new Log.FileLog();
        MarketConfig.initMarkets(false);

        new Thread("MAIN") {
            @Override public void run() {
                setPriority(Thread.NORM_PRIORITY - 1); // smaller prio
                loadData(args);
            }
        }.start();
    }

    private static void loadData(String[] args) {
        ChartFrame frame = null;

        try {
            String file = "cfg" + File.separator + "vary.properties";
            if (args.length > 0) {
                file = args[0];
            }
            MapConfig config = new MapConfig();
//            config.loadAndEncrypted(file);
            config.load(file);

            String exchangeName = config.getString("exchange");
            Exchange exchange = Exchange.get(exchangeName);
            String pairName = config.getString("pair");
            Pair pair = Pair.getByName(pairName);
//            ExchPairData pairData = exchange.getPairData(pair);

            String tickReaderName = config.getString("tick.reader");
            final boolean collectTicks = config.getBoolean("collect.ticks");
            if (collectTicks) {
                frame = new ChartFrame();
                frame.setVisible(true);
            }
            boolean collectValues = config.getBoolean(BaseAlgo.COLLECT_VALUES_KEY);
            boolean joinTicksInReader = config.getBoolean(BaseAlgo.JOIN_TICKS_IN_READER_KEY);
            int prefillTicks = config.getInt("prefill.ticks");

            MapConfig defAlgoConfig = getDefaultConfig(config);
            // todo: copy all keys from config to defAlgoConfig ?
            defAlgoConfig.put(BaseAlgo.COLLECT_VALUES_KEY, Boolean.toString(collectValues));
            defAlgoConfig.put(BaseAlgo.JOIN_TICKS_IN_READER_KEY, Boolean.toString(joinTicksInReader));
            defAlgoConfig.put(BaseAlgo.ALGO_NAME_KEY, config.getString(BaseAlgo.ALGO_NAME_KEY));

            WatchersProducer producer = new WatchersProducer(config, defAlgoConfig);
            long allStartMillis = System.currentTimeMillis();

            boolean chartNotLoaded = true;
            for (int i = 1; producer.isActive(); i++) {
                cleanMemory();

                BaseTicksTimesSeriesData<TickData> ticksTs = collectTicks
                        ? new TicksTimesSeriesData<TickData>(null)
                        : new NoTicksTimesSeriesData<TickData>(null);
                if (!collectTicks) { // add initial tick to update
                    ticksTs.addOlderTick(new TickData());
                }

                BaseTicksTimesSeriesData<TickData> joinedTicksTs;
                if (joinTicksInReader) {
                    long joinTicks = config.getNumber(Vary.joinTicks).longValue();
                    joinedTicksTs = new TickJoiner(ticksTs, joinTicks);
                } else {
                    joinedTicksTs = ticksTs;
                }

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

                long startMillis = System.currentTimeMillis();

                Runnable callback = collectTicks ? new ReadProgressCallback(frame, prefillTicks) : null;
                TradesReader tradesReader = TradesReader.get(tickReaderName);

                String tickWriterName = config.getString("tick.writer");
                TradesWriter tradesWriter = (tickWriterName != null) ? TradesWriter.get(tickWriterName) : null;

                tradesReader.readTicks(config, ticksTs, callback);
                ticksTs.waitAllFinished();

                logResults(watchers, startMillis);

                if (frame != null) {
                    frame.repaint();
                }
            }
            cleanMemory();

            BaseProducer bestProducer = producer.logResults();
            if (bestProducer != null) {
                bestProducer.logResultsEx();
            }

            long allEndMillis = System.currentTimeMillis();
            console("all DONE in " + Utils.millisToYDHMSStr(allEndMillis - allStartMillis));
        } catch (Exception e) {
            err("load data error: " + e, e);
        }

        try {
            Runtime.getRuntime().gc();
            TimeUnit.DAYS.sleep(3);
        } catch (InterruptedException e) { /*noop*/ }
    }

    private static void cleanMemory() {
        long freeMemory1 = Runtime.getRuntime().freeMemory();
        long totalMemory1 = Runtime.getRuntime().totalMemory();
        long maxMemory1 = Runtime.getRuntime().maxMemory();
        long usedMemory1 = totalMemory1 - freeMemory1;
        Algo.resetIterationCaches();
        Runtime.getRuntime().gc();
        long freeMemory2 = Runtime.getRuntime().freeMemory();
        long totalMemory2 = Runtime.getRuntime().totalMemory();
        long maxMemory2 = Runtime.getRuntime().maxMemory();
        long usedMemory2 = totalMemory2 - freeMemory2;


        console("memory(free/used/total/max): "
                + format(freeMemory1) + "/" + format(usedMemory1) + "/" + format(totalMemory1) + "/" + format(maxMemory1)
                + "  =>  "
                + format(freeMemory2) + "/" + format(usedMemory2) + "/" + format(totalMemory2) + "/" + format(maxMemory2)
        );
    }

    private static String format(long memory) {
        return INTEGER_FORMAT.format(memory);
    }

    private static MapConfig getDefaultConfig(MapConfig config) {
        MapConfig algoConfig = new MapConfig();

        String params = config.getString("params");
        if (params != null) {
            String[] split = params.split(";");
            for (String s : split) {
                String[] nv = s.trim().split("=");
                String name = nv[0];
                String value = nv[1];
                algoConfig.put(name, value);
                config.put(name, value);
            }
        }

        // read default config
        for (Vary vary : Vary.values()) {
            String name = vary.name();
            Number number = config.getNumber(vary);
            if (number != null) {
                algoConfig.put(name, number);
            }
        }
        return algoConfig;
    }

    private static void setupChart(boolean collectValues, ChartCanvas chartCanvas, BaseTicksTimesSeriesData<TickData> ticksTs, List<Watcher> watchers) {
        Watcher firstWatcher = watchers.get(0);
        firstWatcher.m_algo.setupChart(collectValues, chartCanvas, ticksTs, firstWatcher);
    }

    private static void logResults(List<Watcher> watchers, long startMillis) {
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
                    + "   spent=" + Utils.millisToYDHMSStr(endMillis - startMillis) + " .....................................");

            double gain = 0;
            if (maxWatcher != null) {
                gain = maxWatcher.totalPriceRatio(true);
                console(maxWatcher.getGainLogStr("MAX ", gain));
            }

            double processedDays = ((double) processedPeriod) / TimeUnit.DAYS.toMillis(1);
            console(" processedDays=" + processedDays
                    + "; perDay=" + Utils.format8(Math.pow(gain, 1 / processedDays))
                    + "; inYear=" + Utils.format8(Math.pow(gain, 365 / processedDays))
            );
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

    //=============================================================================================
    private static class ReadProgressCallback implements Runnable {
        private final ChartFrame m_frame;
        private final int m_prefillTicks;
        private int m_counter = 0;
        private long lastTime = 0;

        public ReadProgressCallback(ChartFrame frame, int prefillTicks) {
            m_frame = frame;
            m_prefillTicks = prefillTicks;
        }

        @Override public void run() {
            m_counter++;
            if (m_counter == m_prefillTicks) {
                console("PREFILLED: ticksCount=" + m_counter);
            } else if (m_counter > m_prefillTicks) {
                // them simulate slow trades
                if (m_counter % 40 == 0) {
                    if (m_frame != null) {
                        m_frame.repaint();
                    }
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) { /*noop*/ }
                }
            } else {
                if (m_counter % 10000 == 0) {
                    long time = System.currentTimeMillis();
                    if (time - lastTime > 10000) {
                        console("lines was read: " + m_counter);
                        lastTime = time;
                    }
                }
            }
        }
    }
}
