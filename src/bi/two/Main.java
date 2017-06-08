package bi.two;

import bi.two.algo.BarSplitter;
import bi.two.algo.WeightedAverager;
import bi.two.algo.impl.RegressionAlgo;
import bi.two.chart.ChartData;
import bi.two.chart.TickData;
import bi.two.chart.TickVolumeData;
import bi.two.chart.TimesSeriesData;
import bi.two.exch.MarketConfig;
import bi.two.util.Utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;

public class Main {

    public static final int PREFILL_TICKS = 190; // 190;
    public static final int LAST_LINES_TO_PROCES = 1190000;

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

            fileReader.skip(fileLength - LAST_LINES_TO_PROCES);

            ChartData chartData = frame.getChartCanvas().getChartData();

            final TimesSeriesData<TickData> ticksTs = new TimesSeriesData<TickData>(null);
            BarSplitter bs = new BarSplitter(ticksTs);
            WeightedAverager averager = new WeightedAverager(bs);

            RegressionAlgo algo = new RegressionAlgo(bs);
            TimesSeriesData<TickData> algoTs = algo.getTS(true);

            chartData.setTicksData("price", ticksTs);
            chartData.setTicksData("bars", bs);
            chartData.setTicksData("avg", averager);
            chartData.setTicksData("regressor", algoTs);

            Runnable callback = new Runnable() {
                private int m_counter = 0;

                @Override public void run() {
                    if (m_counter == PREFILL_TICKS) {
                        List<TickData> ticks = ticksTs.getTicks();
                        long firstTimestamp = ticks.get(ticks.size() - 1).getTimestamp();
                        int size = ticks.size();
                        long lastTimestamp = ticks.get(0).getTimestamp();
                        long timeDiff = lastTimestamp - firstTimestamp;
                        System.out.println("ticksCount=" + size + "; timeDiff=" + Utils.millisToDHMSStr(timeDiff));
                    } else if (m_counter > PREFILL_TICKS) {
                        frame.repaint();

                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    m_counter++;
                }
            };

            readTicks(fileReader, ticksTs, callback);

            frame.repaint();

            System.out.println("DONE");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void readTicks(FileReader fileReader, TimesSeriesData<TickData> ticksTs, Runnable callback) throws IOException {
        BufferedReader br = new BufferedReader(fileReader);
        try {
            br.readLine(); // skip to the end of line

            String line;
            while ((line = br.readLine()) != null) {
                // System.out.println("line = " + line);
                TickVolumeData tickData = parseLine(line);
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
