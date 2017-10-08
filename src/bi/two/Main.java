package bi.two;

import bi.two.algo.BaseAlgo;
import bi.two.algo.Watcher;
import bi.two.chart.TickData;
import bi.two.exch.ExchPairData;
import bi.two.exch.Exchange;
import bi.two.exch.MarketConfig;
import bi.two.exch.Pair;
import bi.two.opt.BaseProducer;
import bi.two.opt.Vary;
import bi.two.opt.WatchersProducer;
import bi.two.ts.TimesSeriesData;
import bi.two.util.MapConfig;
import bi.two.util.Utils;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class Main {

    public static void main(final String[] args) {
        MarketConfig.initMarkets();

        final ChartFrame frame = new ChartFrame();
        frame.setVisible(true);

        new Thread("MAIN") {
            @Override public void run() {
                loadData(frame, args);
            }
        }.start();
    }

    private static void loadData(final ChartFrame frame, String[] args) {
        MapConfig config = new MapConfig();
        try {
            Exchange exchange = Exchange.get("bitstamp");
            Pair pair = Pair.getByName("btc_usd");
            ExchPairData pairData = exchange.getPairData(pair);

            String file = "vary.properties";
            if (args.length > 0) {
                file = args[0];
            }
            config.load(file);

            String tickReaderName = config.getString("tick.reader");
            final boolean collectTicks = config.getBoolean("collect.ticks");
            boolean collectValues = config.getBoolean(BaseAlgo.COLLECT_VALUES_KEY);
            int prefillTicks = config.getInt("prefill.ticks");

            MapConfig defAlgoConfig = getDefaultConfig(config);
            defAlgoConfig.put(BaseAlgo.COLLECT_VALUES_KEY, Boolean.toString(collectValues));
            defAlgoConfig.put(BaseAlgo.ALGO_NAME_KEY, config.getString(BaseAlgo.ALGO_NAME_KEY));

            WatchersProducer producer = new WatchersProducer(config, defAlgoConfig);
            long allStartMillis = System.currentTimeMillis();

            boolean chartNotLoaded = true;
            for (int i = 1; producer.isActive(); i++) {
                System.out.println("## iteration " + i);

                TimesSeriesData<TickData> ticksTs = new TicksTimesSeriesData(collectTicks);
                if (!collectTicks) { // add initial tick to update
                    ticksTs.addOlderTick(new TickData());
                }

                List<Watcher> watchers = producer.getWatchers(defAlgoConfig, ticksTs, config, exchange, pair);
                System.out.println(" watchers.num=" + watchers.size());

                if (watchers.isEmpty()) {
                    continue;
                }

                if (collectTicks && chartNotLoaded) {
                    ChartCanvas chartCanvas = frame.getChartCanvas();
                    setupChart(collectValues, chartCanvas, ticksTs, watchers);
                    chartNotLoaded = false;
                }

                long startMillis = System.currentTimeMillis();

                Runnable callback = collectTicks ? new ReadProgressCallback(frame, prefillTicks) : null;
                TickReader tickReader = TickReader.get(tickReaderName);
                tickReader.readTicks(config, ticksTs, callback, pairData);
                ticksTs.waitAllFinished();

                logResults(watchers, startMillis);

                frame.repaint();
            }

            BaseProducer bestProducer = producer.logResults();
            bestProducer.logResultsEx();

            long allEndMillis = System.currentTimeMillis();
            System.out.println("all DONE in " + Utils.millisToYDHMSStr(allEndMillis - allStartMillis));

            try {
                Thread.sleep(TimeUnit.HOURS.toMillis(5));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static MapConfig getDefaultConfig(MapConfig config) {
        MapConfig algoConfig = new MapConfig();

        // read default config
        for (Vary vary : Vary.values()) {
            String name = vary.name();
            Number number = config.getNumber(vary);
            if (number == null) {
                throw new RuntimeException("def config not specified for " + vary);
            }
            algoConfig.put(name, number);
        }
        return algoConfig;
    }

    private static void setupChart(boolean collectValues, ChartCanvas chartCanvas, TimesSeriesData<TickData> ticksTs, List<Watcher> watchers) {
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

                BaseAlgo algo = watcher.m_algo;
                String key = algo.key(false);
                System.out.println("GAIN[" + key + "]: " + Utils.format8(gain)
                        + "   trades=" + watcher.m_tradesNum + " .....................................");
            }

            Watcher lastWatcher = watchers.get(watchersNum - 1);
            long processedPeriod = lastWatcher.getProcessedPeriod();
            long endMillis = System.currentTimeMillis();
            System.out.println("   processedPeriod=" + Utils.millisToYDHMSStr(processedPeriod)
                    + "   spent=" + Utils.millisToYDHMSStr(endMillis - startMillis) + " .....................................");

            double gain = maxWatcher.totalPriceRatio(true);
            BaseAlgo algo = maxWatcher.m_algo;
            String key = algo.key(true);
            System.out.println("MAX GAIN[" + key + "]: " + Utils.format8(gain)
                    + "   trades=" + maxWatcher.m_tradesNum + " .....................................");

            double processedDays = ((double) processedPeriod) / TimeUnit.DAYS.toMillis(1);
            System.out.println(" processedDays=" + processedDays
                    + "; perDay=" + Utils.format8(Math.pow(gain, 1 / processedDays))
                    + "; inYear=" + Utils.format8(Math.pow(gain, 365 / processedDays))
            );
        }

//        System.out.println(maxWatcher.log());
//        System.out.println(ralgo.log());
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
                System.out.println("PREFILLED: ticksCount=" + m_counter);
            } else if (m_counter > m_prefillTicks) {
                if (m_counter % 40 == 0) {
                    m_frame.repaint();
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            } else {
                if (m_counter % 10000 == 0) {
                    long time = System.currentTimeMillis();
                    if (time - lastTime > 10000) {
                        System.out.println("lines was read: " + m_counter);
                        lastTime = time;
                    }
                }
            }
        }
    }

    //=============================================================================================
    private static class TicksTimesSeriesData extends TimesSeriesData<TickData> {
        private final boolean m_collectTicks;

        public TicksTimesSeriesData(boolean collectTicks) {
            super(null);
            m_collectTicks = collectTicks;
        }

        @Override public void addNewestTick(TickData tickData) {
            if (m_collectTicks) {
                super.addNewestTick(tickData);
            } else {
                m_ticks.set(0, tickData); // always update only last tick
                notifyListeners(true);
            }
        }
    }

}
