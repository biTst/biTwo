package bi.two;

import bi.two.algo.BarSplitter;
import bi.two.algo.Watcher;
import bi.two.algo.WeightedAverager;
import bi.two.algo.impl.RegressionAlgo;
import bi.two.calc.RegressionCalc;
import bi.two.chart.ChartData;
import bi.two.chart.TickData;
import bi.two.chart.TickVolumeData;
import bi.two.chart.TimesSeriesData;
import bi.two.exch.*;
import bi.two.ind.RegressionIndicator;
import bi.two.util.MapConfig;
import bi.two.util.Utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Main {

    public static final int PREFILL_TICKS = 2190000; // 190;
    public static final int LAST_BYTES_TO_PROCES = 2190000;

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
        String path = "D:\\data\\.bitstampUSD.csv";
        try {
            File file = new File(path);
            long fileLength = file.length();
            System.out.println("fileLength = " + fileLength);

            FileReader fileReader = new FileReader(file);

            fileReader.skip(fileLength - LAST_BYTES_TO_PROCES);

            ChartData chartData = frame.getChartCanvas().getChartData();

//            readOne();
            readMany(frame, fileReader, chartData);

            System.out.println("DONE");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void readMany(final ChartFrame frame, FileReader fileReader, ChartData chartData) throws IOException {
        final TimesSeriesData<TickData> ticksTs = new TimesSeriesData<TickData>(null) {
            @Override public void addNewestTick(TickData tickData) {
                m_ticks.set(0, tickData);
                notifyListeners(true);
            }
        };
        ticksTs.addTick(new TickData());

        Exchange exchange = Exchange.get("bitstamp");
        Pair pair = Pair.getByName("btc_usd");

        List<Watcher> watchers = new ArrayList<Watcher>();
        for (long period = 10000l; period <= 60000l; period += 10000l) {
            BarSplitter bs = new BarSplitter(ticksTs, 5, period);
            MapConfig config = new MapConfig();
            for (int i = 4; i <= 10; i++) {
                String barsNumStr = Integer.toString(i);
                config.put(RegressionCalc.REGRESSION_BARS_NUM, barsNumStr);
                RegressionAlgo algo = new RegressionAlgo(config, bs);
                Watcher watcher = new Watcher(algo, exchange, pair);
                watchers.add(watcher);
            }
        }

//        chartData.setTicksData("bars", firstBs);

        Runnable callback = new Runnable() {
            private int m_counter = 0;
            private long lastTime = 0;

            @Override public void run() {
                m_counter++;
                if (m_counter == PREFILL_TICKS) {
                    System.out.println("PREFILLED: ticksCount=" + m_counter);
                } else if (m_counter > PREFILL_TICKS) {
                    frame.repaint();

                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
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
        long processedPeriod = watchers.get(watchers.size()-1).getProcessedPeriod();
        System.out.println("   processedPeriod=" + Utils.millisToDHMSStr(processedPeriod)
                + "   spent=" + Utils.millisToDHMSStr(endMillis-startMillis) + " .....................................");

        for (Watcher watcher : watchers) {
            double gain = watcher.totalPriceRatio();

            RegressionIndicator ri = (RegressionIndicator) watcher.m_algo.m_indicators.get(0);
            int barsNum = ri.m_calc.m_barsNum;

            long period = ri.m_bs.m_period;

            System.out.println("GAIN["+barsNum+"]: " + Utils.format8(gain)
                    + "   period=" + Utils.millisToDHMSStr(period) + " .....................................");
        }
    }

    private static void readOne(final ChartFrame frame, FileReader fileReader, ChartData chartData) throws IOException {
        final TimesSeriesData<TickData> ticksTs = new TimesSeriesData<TickData>(null);
        BarSplitter bs = new BarSplitter(ticksTs, 20, 60000l);
        WeightedAverager averager = new WeightedAverager(bs);

        List<Watcher> watchers = new ArrayList<Watcher>();
        Exchange exchange = Exchange.get("bitstamp");
        Pair pair = Pair.getByName("btc_usd");
        MapConfig config = new MapConfig();

        config.put(RegressionCalc.REGRESSION_BARS_NUM, "5");
        RegressionAlgo algo = new RegressionAlgo(config, bs);
        TimesSeriesData<TickData> algoTs = algo.getTS(true);
        TimesSeriesData<TickData> indicatorTs = algo.m_regressionIndicator.getTS(true);
        Watcher watcher0 = new Watcher(algo, exchange, pair);
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
                if (m_counter == PREFILL_TICKS) {
                    List<TickData> ticks = ticksTs.getTicks();
                    long firstTimestamp = ticks.get(ticks.size() - 1).getTimestamp();
                    int size = ticks.size();
                    long lastTimestamp = ticks.get(0).getTimestamp();
                    long timeDiff = lastTimestamp - firstTimestamp;
                    System.out.println("PREFILLED: ticksCount=" + size + "; timeDiff=" + Utils.millisToDHMSStr(timeDiff));
                } else if (m_counter > PREFILL_TICKS) {
                    frame.repaint();

                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
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

    private static void readTicks(FileReader fileReader, BarSplitter bs, Runnable callback, ExchPairData pairData) throws IOException {
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

                bs.addTickDirect(tickData);
                callback.run();
            }
        } finally {
            br.close();
        }
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
