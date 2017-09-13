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
                        m_ticks.set(0, tickData); // always update only last tick
                        notifyListeners(true);
                    }
                }
            };

Exchange exchange = Exchange.get("bitstamp");
            Pair pair = Pair.getByName("btc_usd");

            ChartCanvas chartCanvas = frame.getChartCanvas();
            List<Watcher> watchers = setup(ticksTs, config, chartCanvas, exchange, pair);
            System.out.println("watchers.num=" + watchers.size());

            Runnable callback = new Runnable() {
                private int m_counter = 0;
                private long lastTime = 0;

                @Override public void run() {
                    m_counter++;
                    if (m_counter == s_prefillTicks) {
                        System.out.println("PREFILLED: ticksCount=" + m_counter);
                    } else if (m_counter > s_prefillTicks) {
                        if (m_counter % 40 == 0) {
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

        boolean collectValues = config.getBoolean("collect.values");

        MapConfig algoConfig = new MapConfig();
        algoConfig.put(RegressionAlgo.COLLECT_VALUES_KEY, Boolean.toString(collectValues));

        List<VaryItem> varies = new ArrayList<>();
        for (Vary vary : Vary.values()) {
            String name = vary.name();
            String from = config.getString(name + ".from");
            String to = config.getString(name + ".to");
            String step = config.getString(name + ".step");
            varies.add(new VaryItem(vary, from, to, step));
        }

        List<Watcher> watchers = new ArrayList<Watcher>();
        doVary(varies, 0, algoConfig, ticksTs, exchange, pair, watchers);
        Watcher first = watchers.get(0);
        RegressionAlgo algo = (RegressionAlgo) first.m_algo;

        if (collectTicks) {
            ChartData chartData = chartCanvas.getChartData();
            ChartSetting chartSetting = chartCanvas.getChartSetting();

            // layout
            ChartAreaSettings top = chartSetting.addChartAreaSettings("top", 0, 0, 1, 0.4f, Color.RED);
            List<ChartAreaLayerSettings> topLayers = top.getLayers();
            {
                addChart(chartData, ticksTs, topLayers, "price", Colors.alpha(Color.RED, 70), TickPainter.TICK);
                addChart(chartData, algo.m_regressor.m_splitter, topLayers, "price.buff", Colors.alpha(Color.BLUE, 100), TickPainter.BAR); // regressor price buffer
                addChart(chartData, algo.m_regressor.getJoinNonChangedTs(), topLayers, "regressor", Color.PINK, TickPainter.LINE); // Linear Regression Curve
addChart(chartData, algo.m_regressorDivided.getJoinNonChangedTs(), topLayers, "regressor.divided", Color.ORANGE, TickPainter.LINE);
addChart(chartData, algo.m_regressorDivided2.getJoinNonChangedTs(), topLayers, "regressor.divided2", Colors.LIGHT_BLUE, TickPainter.LINE);
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
                addChart(chartData, algo.m_adjuster.getZeroTs(), bottomLayers, "zero", Colors.alpha(Color.green,100), TickPainter.LINE);
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

        return watchers;
    }

    private static void addChart(ChartData chartData, ITicksData ticksData, List<ChartAreaLayerSettings> layers, String name, Color color, TickPainter tickPainter) {
        chartData.setTicksData(name, ticksData);
        layers.add(new ChartAreaLayerSettings(name, color, tickPainter));
    }

    private static void doVary(final List<VaryItem> varies, int index, final MapConfig algoConfig, final TimesSeriesData<TickData> ticksTs,
                               final Exchange exchange, final Pair pair, final List<Watcher> watchers) {
        final int nextIndex = index + 1;
        final VaryItem varyItem = varies.get(index);
        String from = varyItem.m_from;
        String to = varyItem.m_to;
        String step = varyItem.m_step;

        Vary.VaryType varyType = varyItem.m_vary.m_varyType;
        varyType.iterate(from, to, step, new IParamIterator<String>() {
            @Override public void doIteration(String value) {
                algoConfig.put(varyItem.m_vary.m_key, value);
                if (nextIndex < varies.size()) {
                    doVary(varies, nextIndex, algoConfig, ticksTs, exchange, pair, watchers);
                } else {
                    RegressionAlgo nextAlgo = new RegressionAlgo(algoConfig, ticksTs);
                    Watcher watcher = new Watcher(algoConfig, nextAlgo, exchange, pair);
                    watchers.add(watcher);
                }
            }
        });
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
            String key = ralgo.key(false);
            System.out.println("GAIN[" + key + "]: " + Utils.format8(gain)
                    + "   trades=" + watcher.m_tradesNum + " .....................................");
        }

        long processedPeriod = watchers.get(watchers.size() - 1).getProcessedPeriod();
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

        System.out.println(maxWatcher.log());
//        System.out.println(ralgo.log());

        try {
            Thread.sleep(TimeUnit.MINUTES.toMillis(5));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static void readFileTicks(FileReader fileReader, TimesSeriesData<TickData> ticksTs, Runnable callback,
                                      ExchPairData pairData, String dataFileType, boolean skipBytes) throws IOException {
        TopData topData = pairData.m_topData;
        BufferedReader br = new BufferedReader(fileReader, 1024 * 1024);
        try {
            if (skipBytes) { // after bytes skipping we may point to the middle of line
                br.readLine(); // skip to the end of line
            }

            DataFileType type = DataFileType.get(dataFileType);

            String line;
            while ((line = br.readLine()) != null) {
                // System.out.println("line = " + line);
                TickData tickData = type.parseLine(line);

                float price = tickData.getClosePrice();
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
                boolean skipBytes = (lastBytesToProces > 0);
                if (skipBytes) {
                    fileReader.skip(fileLength - lastBytesToProces);
                }

                String dataFileType = config.getProperty("dataFile.type");

                readFileTicks(fileReader, ticksTs, callback, pairData, dataFileType, skipBytes);
            }
        },
        BITFINEX("bitfinex") {
            @Override public void readTicks(MapConfig config, TimesSeriesData<TickData> ticksTs, Runnable callback, ExchPairData pairData) throws Exception {
                TopData topData = pairData.m_topData;
                List<TradeTickData> ticks = Bitfinex.readTicks(TimeUnit.MINUTES.toMillis(5 * 100));
                for (int i = ticks.size() - 1; i >= 0; i--) {
                    TradeTickData tick = ticks.get(i);
                    float price = tick.getClosePrice();
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


    //=============================================================================================
    public static class TickExtraData extends TickData {
        public final String[] m_extra;

        public TickExtraData(long time, float price, String[] extra) {
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
    static class VaryItem {
        private final Vary m_vary;
        public final String m_from;
        public final String m_to;
        public final String m_step;

        public VaryItem(Vary vary, String from, String to, String step) {
            m_vary = vary;
            m_from = from;
            m_to = to;
            m_step = step;
        }

        @Override public String toString() {
            return "VaryItem{" +
                    "vary=" + m_vary +
                    ", from='" + m_from + '\'' +
                    ", to='" + m_to + '\'' +
                    ", step='" + m_step + '\'' +
                    '}';
        }
    }

}
