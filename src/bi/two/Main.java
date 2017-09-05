package bi.two;

import bi.two.algo.Watcher;
import bi.two.algo.impl.RegressionAlgo;
import bi.two.chart.*;
import bi.two.exch.*;
import bi.two.exch.impl.Bitfinex;
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

    private static int s_prefillTicks = Integer.MAX_VALUE;

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
            s_prefillTicks = config.getInt("prefill.ticks");

            String name = config.getString("tick.reader");
            TickReader tickReader = TickReader.get(name);

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

Exchange exchange = Exchange.get("bitstamp");
            Pair pair = Pair.getByName("btc_usd");

            ChartCanvas chartCanvas = frame.getChartCanvas();
            List<Watcher> watchers = setup(ticksTs, config, chartCanvas, exchange, pair);

            Runnable callback = new Runnable() {
                private int m_counter = 0;
                private long lastTime = 0;

                @Override public void run() {
                    m_counter++;
                    if (m_counter == s_prefillTicks) {
                        System.out.println("PREFILLED: ticksCount=" + m_counter);
                    } else if (m_counter > s_prefillTicks) {
                        if (m_counter % 20 == 0) {
                            frame.repaint();
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
            tickReader.readTicks(config, ticksTs, callback, pairData);

            long endMillis = System.currentTimeMillis();

            logResults(watchers, startMillis, endMillis);

            frame.repaint();

            System.out.println("DONE");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static List<Watcher> setup(TimesSeriesData<TickData> ticksTs, MapConfig config,
                                       ChartCanvas chartCanvas, Exchange exchange, Pair pair) throws Exception {
        final boolean collectTicks = config.getBoolean("collect.ticks");
        if (!collectTicks) { // add initial tick to update
            ticksTs.addOlderTick(new TickData());
        }

        long periodFrom = config.getPeriodInMillis("period.from");
        long periodTo = config.getPeriodInMillis("period.to");
        long periodStep = config.getPeriodInMillis("period.step");
        int barsFrom = config.getInt("bars.from");
        int barsTo = config.getInt("bars.to");
        int barsStep = config.getInt("bars.step");
        float thresholdFrom = config.getFloat("threshold.from");
        float thresholdTo = config.getFloat("threshold.to");
        float thresholdStep = config.getFloat("threshold.step");
        boolean collectValues = config.getBoolean("collect.values");

        int slopeLen = config.getInt("slope.len");
        String slopeLenStr = Integer.toString(slopeLen);
        int signalLen = config.getInt("signal.len");
        String signalLenStr = Integer.toString(signalLen);

        MapConfig algoConfig = new MapConfig();
        algoConfig.put(RegressionAlgo.COLLECT_LAVUES_KEY, Boolean.toString(collectValues));
        algoConfig.put(RegressionAlgo.SLOPE_LEN_KEY, slopeLenStr);
        algoConfig.put(RegressionAlgo.SIGNAL_LEN_KEY, signalLenStr);

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
            ChartAreaSettings top = new ChartAreaSettings("top", 0, 0, 1, 0.4f, Color.RED);
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

            ChartAreaSettings bottom = new ChartAreaSettings("indicator", 0, 0.4f, 1, 0.2f, Color.GREEN);
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

            ChartAreaSettings value = new ChartAreaSettings("value", 0, 0.6f, 1, 0.2f, Color.LIGHT_GRAY);
            List<ChartAreaLayerSettings> valueLayers = value.getLayers();
            {
                valueLayers.add(new ChartAreaLayerSettings("value", Color.blue, TickPainter.LINE));
            }

            ChartAreaSettings gain = new ChartAreaSettings("gain", 0, 0.8f, 1, 0.2f, Color.ORANGE);
            gain.setHorizontalLineValue(1);
            List<ChartAreaLayerSettings> gainLayers = gain.getLayers();
            {
                gainLayers.add(new ChartAreaLayerSettings("gain", Color.blue, TickPainter.LINE));
            }

            if (collectValues) {
                Watcher watcher = watchers.get(0);

                chartData.setTicksData("trades", watcher);
                topLayers.add(new ChartAreaLayerSettings("trades", Color.WHITE, TickPainter.TRADE));

                chartData.setTicksData("gain", watcher.getGainTs());
            }

            ChartSetting chartSetting = chartCanvas.getChartSetting();
            chartSetting.addChartAreaSettings(top);
            chartSetting.addChartAreaSettings(bottom);
            chartSetting.addChartAreaSettings(value);
            chartSetting.addChartAreaSettings(gain);
        }

        return watchers;
    }

    private static void logResults(List<Watcher> watchers, long startMillis, long endMillis) {
        double maxGain = 0;
        Watcher maxWatcher = null;
        for (Watcher watcher : watchers) {
            double gain = watcher.totalPriceRatio(true);
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

        long processedPeriod = watchers.get(watchers.size() - 1).getProcessedPeriod();
        System.out.println("   processedPeriod=" + Utils.millisToDHMSStr(processedPeriod)
                + "   spent=" + Utils.millisToDHMSStr(endMillis - startMillis) + " .....................................");

        double gain = maxWatcher.totalPriceRatio(true);
        RegressionAlgo ralgo = (RegressionAlgo) maxWatcher.m_algo;
        float threshold = ralgo.m_threshold;
        int curveLength = ralgo.m_curveLength;

        System.out.println("MAX GAIN[" + curveLength + ", " + threshold /*+ ", " + Utils.millisToDHMSStr(period)*/ + "]: " + Utils.format8(gain)
                + "   trades=" + maxWatcher.m_tradesNum + " .....................................");

        double processedDays = ((double) processedPeriod) / TimeUnit.DAYS.toMillis(1);
        System.out.println(" processedDays=" + processedDays
                + "; perDay=" + Utils.format8(Math.pow(gain, 1 / processedDays))
                + "; inYear=" + Utils.format8(Math.pow(gain, 365 / processedDays))
        );

        System.out.println(maxWatcher.log());
        System.out.println(ralgo.log());

        try {
            Thread.sleep(TimeUnit.MINUTES.toMillis(5));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static void readFileTicks(FileReader fileReader, TimesSeriesData<TickData> ticksTs, Runnable callback,
                                      ExchPairData pairData, String dataFileType) throws IOException {
        TopData topData = pairData.m_topData;
        BufferedReader br = new BufferedReader(fileReader, 1024 * 1024);
        try {
            br.readLine(); // skip to the end of line

            DataFileType type = DataFileType.get(dataFileType);

            String line;
            while ((line = br.readLine()) != null) {
                // System.out.println("line = " + line);
                TickData tickData = type.parseLine(line);

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

    //=============================================================================================
    private enum TickReader {
        FILE("file") {
            @Override public void readTicks(MapConfig config, TimesSeriesData<TickData> ticksTs, Runnable callback, ExchPairData pairData) throws Exception {
                String path = config.getProperty("dataFile");
                File file = new File(path);
                long fileLength = file.length();
                System.out.println("fileLength = " + fileLength);

                FileReader fileReader = new FileReader(file);

                long lastBytesToProces = config.getLong("process.bytes");
                if (lastBytesToProces > 0) {
                    fileReader.skip(fileLength - lastBytesToProces);
                }

                String dataFileType = config.getProperty("dataFile.type");

                readFileTicks(fileReader, ticksTs, callback, pairData, dataFileType);
            }
        },
        BITFINEX("bitfinex") {
            @Override public void readTicks(MapConfig config, TimesSeriesData<TickData> ticksTs, Runnable callback, ExchPairData pairData) throws Exception {
                TopData topData = pairData.m_topData;
                List<TradeTickData> ticks = Bitfinex.readTicks(TimeUnit.MINUTES.toMillis(5 * 100));
                for (int i = ticks.size() - 1; i >= 0; i--) {
                    TradeTickData tick = ticks.get(i);
                    float price = tick.getPrice();
                    topData.init(price, price, price);
                    pairData.m_newestTick = tick;

                    ticksTs.addNewestTick(tick);
                    callback.run();
                }
            }
        };

        private final String m_name;

        TickReader(String name) {
            m_name = name;
        }

        public static TickReader get(String name) {
            for (TickReader tickReader : values()) {
                if (tickReader.m_name.equals(name)) {
                    return tickReader;
                }
            }
            throw new RuntimeException("Unknown TickReader '" + name + "'");
        }

        public void readTicks(MapConfig config, TimesSeriesData<TickData> ticksTs, Runnable callback, ExchPairData pairData) throws Exception {
            throw new RuntimeException("must be overridden");
        }
    }


    //=============================================================================================
    public enum DataFileType {
        CSV("csv") {
            @Override public TickData parseLine(String line) {
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
        },
        TABBED("tabbed") {
            private final long STEP = TimeUnit.MINUTES.toMillis(5);
            
            private long m_time = 0;

            @Override public TickData parseLine(String line) {
                String[] extra = line.split("\t");
                float price = Float.parseFloat(extra[0]);
                m_time += STEP;
                TickExtraData tickData = new TickExtraData(m_time, price, extra);
                return tickData;
            }
        };

        private final String m_type;

        DataFileType(String type) {
            m_type = type;
        }

        public static DataFileType get(String type) {
            for (DataFileType dataFileType : values()) {
                if (dataFileType.m_type.equals(type)) {
                    return dataFileType;
                }
            }
            throw new RuntimeException("Unknown DataFileType '" + type + "'");
        }

        public TickData parseLine(String line) { throw new RuntimeException("must be overridden"); }
    }

    
    public static class TickExtraData extends TickData {
        public final String[] m_extra;

        public TickExtraData(long time, float price, String[] extra) {
            super(time, price);
            m_extra = extra;
        }
    }
}
