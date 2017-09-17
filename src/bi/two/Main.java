package bi.two;

import bi.two.algo.BaseAlgo;
import bi.two.algo.Watcher;
import bi.two.algo.impl.RegressionAlgo;
import bi.two.chart.*;
import bi.two.exch.ExchPairData;
import bi.two.exch.Exchange;
import bi.two.exch.MarketConfig;
import bi.two.exch.Pair;
import bi.two.util.MapConfig;
import bi.two.util.Utils;

import java.awt.*;
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
                                System.out.println("lines was read: " + m_counter);
                                lastTime = time;
                            }
                        }
                    }
                }
            };

            ExchPairData pairData = exchange.getPairData(pair);
            long startMillis = System.currentTimeMillis();
            tickReader.readTicks(config, ticksTs, callback, pairData);

            ticksTs.waitAllFinished();

            long endMillis = System.currentTimeMillis();

            logResults(watchers, startMillis, endMillis);

            frame.repaint();

            System.out.println("DONE");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static List<Watcher> setup(TimesSeriesData<TickData> ticksTs0, MapConfig config,
                                       ChartCanvas chartCanvas, Exchange exchange, Pair pair) throws Exception {
        int parallel = config.getInt("parallel");
        BaseTimesSeriesData ticksTs = new ParallelTimesSeriesData(ticksTs0, parallel);

        final boolean collectTicks = config.getBoolean("collect.ticks");
        if (!collectTicks) { // add initial tick to update
            ticksTs0.addOlderTick(new TickData());
        }

        boolean collectValues = config.getBoolean("collect.values");

        MapConfig algoConfig = new MapConfig();
        algoConfig.put(RegressionAlgo.COLLECT_VALUES_KEY, Boolean.toString(collectValues));

        List<Vary.VaryItem> varies = new ArrayList<>();
        for (Vary vary : Vary.values()) {
            String name = vary.name();
            String prop = config.getProperty(name);
            Vary.VaryItem varyItem;
            if (prop == null) {
                String from = config.getString(name + ".from");
                String to = config.getString(name + ".to");
                String step = config.getString(name + ".step");
                varyItem = new Vary.VaryItem(vary, from, to, step);
            } else {
                varyItem = Vary.VaryItem.parseVary(prop, vary);
            }
            varies.add(varyItem);
        }

        List<Watcher> watchers = new ArrayList<>();
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
                addChart(chartData, ticksTs0, topLayers, "price", Colors.alpha(Color.RED, 70), TickPainter.TICK);
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

    private static void doVary(final List<Vary.VaryItem> varies, int index, final MapConfig algoConfig, final ITimesSeriesData<TickData> ticksTs,
                               final Exchange exchange, final Pair pair, final List<Watcher> watchers) {
        final int nextIndex = index + 1;
        final Vary.VaryItem varyItem = varies.get(index);
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
                    ITimesSeriesData<TickData> activeTicksTs = ticksTs.getActive();
                    Watcher watcher = new Watcher(algoConfig, exchange, pair, activeTicksTs) {
                        @Override protected BaseAlgo createAlgo(ITimesSeriesData parent) {
                            return new RegressionAlgo(algoConfig, parent);
                        }
                    };
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
