package bi.two;

import bi.two.algo.BarSplitter;
import bi.two.algo.Watcher;
import bi.two.algo.impl.RegressionAlgo;
import bi.two.calc.BarsWeightedAverager;
import bi.two.chart.*;
import bi.two.exch.*;
import bi.two.util.MapConfig;
import bi.two.util.Utils;

import java.awt.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class Main {

    public static int s_prefillTicks = Integer.MAX_VALUE;

    public static void main(String[] args) {
        MarketConfig.initMarkets();

        final ChartFrame frame = new ChartFrame();
        frame.setVisible(true);

        new Thread() {
            @Override public void run() {
                loadData(frame);
            }
        }.start();
    }

    private static void loadData(final ChartFrame frame) {
        MapConfig config = new MapConfig();
        try {
            config.load("vary.properties");

            String path = config.getProperty("dataFile");
            File file = new File(path);
            long fileLength = file.length();
            System.out.println("fileLength = " + fileLength);

            FileReader fileReader = new FileReader(file);

            long lastBytesToProces = config.getLong("process.bytes");
            s_prefillTicks = config.getInt("prefill.ticks");
            fileReader.skip(fileLength - lastBytesToProces);

            ChartCanvas chartCanvas = frame.getChartCanvas();
            readMany(frame, config, fileReader, chartCanvas);
//            readOne(frame, fileReader, chartCanvas);

            System.out.println("DONE");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void readMany(final ChartFrame frame, MapConfig config, FileReader fileReader, ChartCanvas chartCanvas) throws IOException {
        final boolean collectTicks = config.getBoolean("collect.ticks");
        final TimesSeriesData<TickData> ticksTs = new TimesSeriesData<TickData>(null) {
            @Override public void addNewestTick(TickData tickData) {
                if (collectTicks) {
                    super.addNewestTick(tickData);
                } else {
                    m_ticks.set(0, tickData); // always update last tick
                    notifyListeners(true);
                }
            }
        };
        if (!collectTicks) { // add initial tick to update
            ticksTs.addOlderTick(new TickData());
        }

        Exchange exchange = Exchange.get("bitstamp");
        Pair pair = Pair.getByName("btc_usd");

        long periodFrom = config.getLong("period.from");
        long periodTo = config.getLong("period.to");
        long periodStep = config.getLong("period.step");
        int barsFrom = config.getInt("bars.from");
        int barsTo = config.getInt("bars.to");
        int barsStep = config.getInt("bars.step");
        float thresholdFrom = config.getFloat("threshold.from");
        float thresholdTo = config.getFloat("threshold.to");
        float thresholdStep = config.getFloat("threshold.step");
        boolean collectValues = config.getBoolean("collect.values");

        MapConfig algoConfig = new MapConfig();
        algoConfig.put(RegressionAlgo.COLLECT_LAVUES_KEY, Boolean.toString(collectValues));

        RegressionAlgo algo = null;
        List<Watcher> watchers = new ArrayList<Watcher>();
        for (long period = periodFrom; period <= periodTo; period += periodStep) {
            for (int barsNum = barsFrom; barsNum <= barsTo; barsNum += barsStep) {
                String barsNumStr = Integer.toString(barsNum);
                algoConfig.put(RegressionAlgo.REGRESSION_BARS_NUM_KEY, barsNumStr);

                for (float threshold = thresholdFrom; threshold <= thresholdTo; threshold += thresholdStep) {
                    algoConfig.put(RegressionAlgo.THRESHOLD_KEY, Float.toString(threshold));
                    RegressionAlgo nextAlgo = new RegressionAlgo(algoConfig, ticksTs);
                    if (algo == null) {
                        algo = nextAlgo;
                    }
                    Watcher watcher = new Watcher(config, nextAlgo, exchange, pair);
                    watchers.add(watcher);
                }
            }
        }

        ChartData chartData = chartCanvas.getChartData();
        if (collectTicks) {
            chartData.setTicksData("price", ticksTs);
            chartData.setTicksData("price.buff", algo.m_regressor.m_splitter); // regressor price buffer
            chartData.setTicksData("regressor", algo.m_regressor.getJoinNonChangedTs()); // Linear Regression Curve
            chartData.setTicksData("regressor.bars", algo.m_regressorBars);
//            chartData.setTicksData("bars2.avg", algo.m_regressorBarsAvg);
//            chartData.setTicksData("diff", algo.m_differ.getJoinNonChangedTs()); // diff = Linear Regression Slope
            chartData.setTicksData("slope", algo.m_scaler.getJoinNonChangedTs()); // diff (Linear Regression Slope) scaled by price
//            chartData.setTicksData("slope.buf", algo.m_averager.m_splitter);
            chartData.setTicksData("slope.avg", algo.m_averager.getJoinNonChangedTs());
//            chartData.setTicksData("sig.buf", algo.m_signaler.m_splitter);
            chartData.setTicksData("signal.avg", algo.m_signaler.getJoinNonChangedTs());
            chartData.setTicksData("power", algo.m_powerer.getJoinNonChangedTs());
            chartData.setTicksData("value", algo.m_adjuster.getJoinNonChangedTs());

            // layout
            ChartAreaSettings top = new ChartAreaSettings("top", 0, 0, 1, 0.5f, Color.RED);
            List<ChartAreaLayerSettings> topLayers = top.getLayers();
            {
                topLayers.add(new ChartAreaLayerSettings("price", Colors.alpha(Color.RED, 70), TickPainter.TICK));
                topLayers.add(new ChartAreaLayerSettings("price.buff", Colors.alpha(Color.BLUE, 100), TickPainter.BAR));
//            topLayers.add(new ChartAreaLayerSettings("avg", Color.ORANGE, TickPainter.LINE));
//            topLayers.add(new ChartAreaLayerSettings("trades", Color.YELLOW, TickPainter.TRADE));
                topLayers.add(new ChartAreaLayerSettings("regressor", Color.PINK, TickPainter.LINE));
                topLayers.add(new ChartAreaLayerSettings("regressor.bars", Color.ORANGE, TickPainter.BAR));
//                topLayers.add(new ChartAreaLayerSettings("bars2.avg", Colors.LIME, TickPainter.RIGHT_CIRCLE));
            }

            ChartAreaSettings bottom = new ChartAreaSettings("indicator", 0, 0.5f, 1, 0.25f, Color.GREEN);
            List<ChartAreaLayerSettings> bottomLayers = bottom.getLayers();
            {
//            bottomLayers.add(new ChartAreaLayerSettings("indicator", Color.GREEN, TickPainter.LINE));
//                bottomLayers.add(new ChartAreaLayerSettings("diff", Colors.alpha(Color.GREEN, 100), TickPainter.LINE));
                bottomLayers.add(new ChartAreaLayerSettings("slope", Colors.alpha(Colors.LIME, 60), TickPainter.LINE /*RIGHT_CIRCLE*/));
//                bottomLayers.add(new ChartAreaLayerSettings("slope.buf", Colors.alpha(Color.YELLOW, 100), TickPainter.BAR));
                bottomLayers.add(new ChartAreaLayerSettings("slope.avg", Color.RED, TickPainter.LINE));
//                bottomLayers.add(new ChartAreaLayerSettings("sig.buf", Colors.alpha(Color.DARK_GRAY, 100), TickPainter.BAR));
                bottomLayers.add(new ChartAreaLayerSettings("signal.avg", Color.GRAY, TickPainter.LINE));
                bottomLayers.add(new ChartAreaLayerSettings("power", Color.CYAN, TickPainter.LINE));
            }

            ChartAreaSettings value = new ChartAreaSettings("value", 0, 0.75f, 1, 0.25f, Color.LIGHT_GRAY);
            List<ChartAreaLayerSettings> valueLayers = value.getLayers();
            {
                valueLayers.add(new ChartAreaLayerSettings("value", Color.blue, TickPainter.LINE));
            }

            if (collectValues) {
                Watcher watcher = watchers.get(0);
                chartData.setTicksData("trades", watcher);

                topLayers.add(new ChartAreaLayerSettings("trades", Color.WHITE, TickPainter.TRADE));
            }

            ChartSetting chartSetting = chartCanvas.getChartSetting();
            chartSetting.addChartAreaSettings(top);
            chartSetting.addChartAreaSettings(bottom);
            chartSetting.addChartAreaSettings(value);
        }

        Runnable callback = new Runnable() {
            private int m_counter = 0;
            private long lastTime = 0;

            @Override public void run() {
                m_counter++;
                if (m_counter == s_prefillTicks) {
                    System.out.println("PREFILLED: ticksCount=" + m_counter);
                } else if (m_counter > s_prefillTicks) {
                    frame.repaint();

                    if (m_counter % 5 == 0) {
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                } else {
                    if (m_counter % 5000 == 0) {
                        long time = System.currentTimeMillis();
                        if (time - lastTime > 5000) {
                            System.out.println("m_counter = " + m_counter);
                            lastTime = time;
                        }
                    }
                }
            }
        };

        ExchPairData pairData = exchange.getPairData(pair);
        long startMillis = System.currentTimeMillis();
        readTicks(fileReader, ticksTs, callback, pairData);
        long endMillis = System.currentTimeMillis();

        frame.repaint();

        logResults(watchers, startMillis, endMillis);
    }

    private static void logResults(List<Watcher> watchers, long startMillis, long endMillis) {
        double maxGain = 0;
        Watcher maxWatcher = null;
        for (Watcher watcher : watchers) {
            double gain = watcher.totalPriceRatio();
            if (gain > maxGain) {
                maxGain = gain;
                maxWatcher = watcher;
            }

            RegressionAlgo ralgo = (RegressionAlgo) watcher.m_algo;
            float threshold = ralgo.m_threshold;
            int curveLength = ralgo.m_curveLength;

            System.out.println("GAIN[" + curveLength + "," + threshold /*+ ", " + Utils.millisToDHMSStr(period)*/ + "]: " + Utils.format8(gain)
                    + "   trades=" + watcher.m_tradesNum + " .....................................");
        }

        long processedPeriod = watchers.get(watchers.size()-1).getProcessedPeriod();
        System.out.println("   processedPeriod=" + Utils.millisToDHMSStr(processedPeriod)
                + "   spent=" + Utils.millisToDHMSStr(endMillis-startMillis) + " .....................................");

        double gain = maxWatcher.totalPriceRatio();
        RegressionAlgo ralgo = (RegressionAlgo) maxWatcher.m_algo;
        float threshold = ralgo.m_threshold;
        int curveLength = ralgo.m_curveLength;

        System.out.println("MAX GAIN[" + curveLength + ", " + threshold /*+ ", " + Utils.millisToDHMSStr(period)*/ + "]: " + Utils.format8(gain)
                + "   trades=" + maxWatcher.m_tradesNum + " .....................................");

        try {
            Thread.sleep(TimeUnit.MINUTES.toMillis(5));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static void readOne(final ChartFrame frame, FileReader fileReader, ChartCanvas chartCanvas) throws IOException {
        ChartData chartData = chartCanvas.getChartData();
        final TimesSeriesData<TickData> ticksTs = new TimesSeriesData<TickData>(null);
        BarSplitter bs = new BarSplitter(ticksTs, 20, 60000l);
        BarsWeightedAverager averager = new BarsWeightedAverager(bs);

        List<Watcher> watchers = new ArrayList<Watcher>();
        Exchange exchange = Exchange.get("bitstamp");
        Pair pair = Pair.getByName("btc_usd");
        MapConfig config = new MapConfig();

        config.put(RegressionAlgo.REGRESSION_BARS_NUM_KEY, "5");
        RegressionAlgo algo = new RegressionAlgo(config, bs);
        TimesSeriesData<TickData> algoTs = algo.getTS(true);
        TimesSeriesData<TickData> indicatorTs = algo.m_regressionIndicator.getJoinNonChangedTs();
        Watcher watcher0 = new Watcher(config, algo, exchange, pair);
        watchers.add(watcher0);

        chartData.setTicksData("price", ticksTs);
        chartData.setTicksData("bars", bs);
        chartData.setTicksData("avg", averager);
        chartData.setTicksData("regressor", indicatorTs);
        chartData.setTicksData("adjusted", algoTs);

        Runnable callback = new Runnable() {
            private int m_counter = 0;
            private long lastTime = 0;

            @Override public void run() {
                m_counter++;
                if (m_counter == s_prefillTicks) {
                    List<TickData> ticks = ticksTs.getTicks();
                    long firstTimestamp = ticks.get(ticks.size() - 1).getTimestamp();
                    int size = ticks.size();
                    long lastTimestamp = ticks.get(0).getTimestamp();
                    long timeDiff = lastTimestamp - firstTimestamp;
                    System.out.println("PREFILLED: ticksCount=" + size + "; timeDiff=" + Utils.millisToDHMSStr(timeDiff));
                } else if (m_counter > s_prefillTicks) {
                    frame.repaint(100);
                } else {
                    if (m_counter % 5000 == 0) {
                        long time = System.currentTimeMillis();
                        if (time - lastTime > 5000) {
                            System.out.println("m_counter = " + m_counter);
                            lastTime = time;
                        }
                    }
                }
            }
        };

        ExchPairData pairData = exchange.getPairData(pair);
        long startMillis = System.currentTimeMillis();
        readTicks(fileReader, ticksTs, callback, pairData);
        long endMillis = System.currentTimeMillis();

        frame.repaint();

        logResults(watchers, startMillis, endMillis);
    }

    private static void readTicks(FileReader fileReader, TimesSeriesData<TickData> ticksTs, Runnable callback, ExchPairData pairData) throws IOException {
        TopData topData = pairData.m_topData;
        BufferedReader br = new BufferedReader(fileReader, 1024 * 1024);
        try {
            br.readLine(); // skip to the end of line

            String line;
            while ((line = br.readLine()) != null) {
                // System.out.println("line = " + line);
                TickVolumeData tickData = parseLine(line);

                float price = tickData.getPrice();
                topData.init(price, price, price);
                pairData.m_newestTick = tickData;

                ticksTs.addNewestTick(tickData);
                callback.run();
            }
        } finally {
            br.close();
        }
    }

    private static TickVolumeData parseLine(String line) {
        int indx1 = line.indexOf(",");
        if (indx1 > 0) {
            int priceIndex = indx1 + 1;
            int indx2 = line.indexOf(",", priceIndex);
            if (indx2 > 0) {
                String timestampStr = line.substring(0, indx1);
                String priceStr = line.substring(priceIndex, indx2);
                String volumeStr = line.substring(indx2 + 1);

                //System.out.println("timestampStr = " + timestampStr +"; priceStr = " + priceStr +"; volumeStr = " + volumeStr );

                long timestampSeconds = Long.parseLong(timestampStr);
                float price = Float.parseFloat(priceStr);
                float volume = Float.parseFloat(volumeStr);

                long timestampMs= timestampSeconds * 1000;
                TickVolumeData tickData = new TickVolumeData(timestampMs, price, volume);
                return tickData;
            }
        }
        return null;
    }
}
