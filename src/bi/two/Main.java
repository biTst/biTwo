package bi.two;

import bi.two.algo.BaseAlgo;
import bi.two.algo.Watcher;
import bi.two.algo.impl.RegressionAlgo;
import bi.two.chart.*;
import bi.two.exch.ExchPairData;
import bi.two.exch.Exchange;
import bi.two.exch.MarketConfig;
import bi.two.exch.Pair;
import bi.two.opt.Vary;
import bi.two.opt.WatchersProducer;
import bi.two.ts.TimesSeriesData;
import bi.two.util.MapConfig;
import bi.two.util.Utils;

import java.awt.*;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class Main {

    public static void main(String[] args) {
        MarketConfig.initMarkets();

        final ChartFrame frame = new ChartFrame();
        frame.setVisible(true);

        new Thread("MAIN") {
            @Override public void run() {
                loadData(frame);
            }
        }.start();
    }

    private static void loadData(final ChartFrame frame) {
        MapConfig config = new MapConfig();
        try {
            Exchange exchange = Exchange.get("bitstamp");
            Pair pair = Pair.getByName("btc_usd");
            ExchPairData pairData = exchange.getPairData(pair);

            config.load("vary.properties");

            String tickReaderName = config.getString("tick.reader");
            final boolean collectTicks = config.getBoolean("collect.ticks");
            boolean collectValues = config.getBoolean(BaseAlgo.COLLECT_VALUES_KEY);
            int prefillTicks = config.getInt("prefill.ticks");

            MapConfig defAlgoConfig = getDefaultConfig(config);
            defAlgoConfig.put(BaseAlgo.COLLECT_VALUES_KEY, Boolean.toString(collectValues));

            WatchersProducer producer = new WatchersProducer(config, defAlgoConfig);

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

                if (collectTicks) {
                    ChartCanvas chartCanvas = frame.getChartCanvas();
                    setupChart(collectValues, chartCanvas, ticksTs, watchers);
                }

                long startMillis = System.currentTimeMillis();

                Runnable callback = collectTicks ? new ReadProgressCallback(frame, prefillTicks) : null;
                TickReader tickReader = TickReader.get(tickReaderName);
                tickReader.readTicks(config, ticksTs, callback, pairData);
                ticksTs.waitAllFinished();

                logResults(watchers, startMillis);

                frame.repaint();
            }

            producer.logResults();

            System.out.println("all DONE");

            try {
                Thread.sleep(TimeUnit.MINUTES.toMillis(5));
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

    private static void setupChart(boolean collectValues, ChartCanvas chartCanvas, TimesSeriesData<TickData> ticksTs0, List<Watcher> watchers) {
        Watcher first = watchers.get(0);
        RegressionAlgo algo = (RegressionAlgo) first.m_algo;

        ChartData chartData = chartCanvas.getChartData();
        ChartSetting chartSetting = chartCanvas.getChartSetting();

        // layout
        ChartAreaSettings top = chartSetting.addChartAreaSettings("top", 0, 0, 1, 0.4f, Color.RED);
        List<ChartAreaLayerSettings> topLayers = top.getLayers();
        {
            addChart(chartData, ticksTs0, topLayers, "price", Colors.alpha(Color.RED, 70), TickPainter.TICK);
            addChart(chartData, algo.m_regressor.m_splitter, topLayers, "price.buff", Colors.alpha(Color.BLUE, 100), TickPainter.BAR); // regressor price buffer
            addChart(chartData, algo.m_regressor.getJoinNonChangedTs(), topLayers, "regressor", Color.PINK, TickPainter.LINE); // Linear Regression Curve
            addChart(chartData, algo.m_regressorBars, topLayers, "regressor.bars", Color.ORANGE, TickPainter.BAR);
        }

        ChartAreaSettings bottom = chartSetting.addChartAreaSettings("indicator", 0, 0.4f, 1, 0.2f, Color.GREEN);
        List<ChartAreaLayerSettings> bottomLayers = bottom.getLayers();
        {
            ////addChart(chartData, algo.m_differ.getJoinNonChangedTs(), bottomLayers, "diff", Colors.alpha(Color.GREEN, 100), TickPainter.LINE); // diff = Linear Regression Slope
            //addChart(chartData, algo.m_scaler.getJoinNonChangedTs(), bottomLayers, "slope", Colors.alpha(Colors.LIME, 60), TickPainter.LINE /*RIGHT_CIRCLE*/); // diff (Linear Regression Slope) scaled by price
            ////addChart(chartData, algo.m_averager.m_splitter, bottomLayers, "slope.buf", Colors.alpha(Color.YELLOW, 100), TickPainter.BAR));
            //addChart(chartData, algo.m_averager.getJoinNonChangedTs(), bottomLayers, "slope.avg", Colors.alpha(Color.RED, 60), TickPainter.LINE);
            ////addChart(chartData, algo.m_averager.m_splitteralgo.m_signaler.m_splitter, bottomLayers, "sig.buf", Colors.alpha(Color.DARK_GRAY, 100), TickPainter.BAR));
            //addChart(chartData, algo.m_signaler.getJoinNonChangedTs(), bottomLayers, "signal.avg", Colors.alpha(Color.GRAY,100), TickPainter.LINE);
            addChart(chartData, algo.m_powerer.getJoinNonChangedTs(), bottomLayers, "power", Color.CYAN, TickPainter.LINE);
            addChart(chartData, algo.m_smoother.getJoinNonChangedTs(), bottomLayers, "zlema", Color.ORANGE, TickPainter.LINE);
            addChart(chartData, algo.m_adjuster.getMinTs(), bottomLayers, "min", Color.MAGENTA, TickPainter.LINE);
            addChart(chartData, algo.m_adjuster.getMaxTs(), bottomLayers, "max", Color.MAGENTA, TickPainter.LINE);
            addChart(chartData, algo.m_adjuster.getZeroTs(), bottomLayers, "zero", Colors.alpha(Color.green, 100), TickPainter.LINE);
        }

        ChartAreaSettings value = chartSetting.addChartAreaSettings("value", 0, 0.6f, 1, 0.2f, Color.LIGHT_GRAY);
        List<ChartAreaLayerSettings> valueLayers = value.getLayers();
        {
            addChart(chartData, algo.m_adjuster.getJoinNonChangedTs(), valueLayers, "value", Color.blue, TickPainter.LINE);
        }

        if (collectValues) {
            ChartAreaSettings gain = chartSetting.addChartAreaSettings("gain", 0, 0.8f, 1, 0.2f, Color.ORANGE);
            gain.setHorizontalLineValue(1);

            Watcher watcher = watchers.get(0);
            addChart(chartData, watcher, topLayers, "trades", Color.WHITE, TickPainter.TRADE);

            List<ChartAreaLayerSettings> gainLayers = gain.getLayers();
            addChart(chartData, watcher.getGainTs(), gainLayers, "gain", Color.blue, TickPainter.LINE);
        }
    }

    private static void addChart(ChartData chartData, ITicksData ticksData, List<ChartAreaLayerSettings> layers, String name, Color color, TickPainter tickPainter) {
        chartData.setTicksData(name, ticksData);
        layers.add(new ChartAreaLayerSettings(name, color, tickPainter));
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

                RegressionAlgo ralgo = (RegressionAlgo) watcher.m_algo;
                String key = ralgo.key(false);
                System.out.println("GAIN[" + key + "]: " + Utils.format8(gain)
                        + "   trades=" + watcher.m_tradesNum + " .....................................");
            }

            Watcher lastWatcher = watchers.get(watchersNum - 1);
            long processedPeriod = lastWatcher.getProcessedPeriod();
            long endMillis = System.currentTimeMillis();
            System.out.println("   processedPeriod=" + Utils.millisToDHMSStr(processedPeriod)
                    + "   spent=" + Utils.millisToDHMSStr(endMillis - startMillis) + " .....................................");

            double gain = maxWatcher.totalPriceRatio(true);
            RegressionAlgo ralgo = (RegressionAlgo) maxWatcher.m_algo;
            String key = ralgo.key(true);
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
                    if (time - lastTime > 5000) {
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
