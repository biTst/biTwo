package bi.two;

import bi.two.algo.BarSplitter;
import bi.two.algo.Regressor;
import bi.two.algo.WeightedAverager;
import bi.two.chart.*;
import bi.two.util.Utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Main {

    public static final int PREFILL_TICKS = 190; // 190;
    public static final int LAST_LINES_TO_PROCES = 1190000;

    public static void main(String[] args) {
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

            final ChartData chartData = frame.getChartCanvas().getChartData();

            TimesSeriesData<TickData> ticksTs = new TimesSeriesData<TickData>();
            final BarSplitter bs0 = new BarSplitter(ticksTs);
            final WeightedAverager averager0 = new WeightedAverager(bs0);
            final Regressor regressor0 = new Regressor(BarSplitter.BARS_NUM, bs0);

            final List<TickData> ticks = new ArrayList<TickData>();
            final BarSplitter bs = new BarSplitter(null);
            final WeightedAverager averager = new WeightedAverager(bs);
            final Regressor regressor = new Regressor(BarSplitter.BARS_NUM, bs);

            final BaseTicksData ticksData = new BaseTicksData() {
                @Override public List<? extends ITickData> obtainTicks() {
                    return new ArrayList<TickData>(ticks);
                }
            };
            chartData.setTicksData("price", ticksData);

            final BaseTicksData barsData = new BaseTicksData() {
                @Override public List<? extends ITickData> obtainTicks() {
                    return bs.getBarDatas();
                }
            };
            chartData.setTicksData("bars", barsData);

            final BaseTicksData averagerData = new BaseTicksData() {
                @Override public List<? extends ITickData> obtainTicks() {
                    return averager.getTicks();
                }
            };
            chartData.setTicksData("avg", averagerData);

            final BaseTicksData regressorData = new BaseTicksData() {
                @Override public List<? extends ITickData> obtainTicks() {
                    return regressor.getTicks();
                }
            };
            chartData.setTicksData("regressor", regressorData);
            
            chartData.setTicksData("price2", ticksTs);
            chartData.setTicksData("bars2", bs0);
            chartData.setTicksData("avg2", averager0);
            chartData.setTicksData("regressor2", regressor0);

            Runnable callback = new Runnable() {
                private int m_counter = 0;

                @Override public void run() {
                    if (m_counter == PREFILL_TICKS) {
                        long firstTimestamp = ticks.get(ticks.size() - 1).getTimestamp();
                        int size = ticks.size();
                        long lastTimestamp = ticks.get(0).getTimestamp();
                        long timeDiff = lastTimestamp - firstTimestamp;
                        System.out.println("ticksCount=" + size + "; timeDiff=" + Utils.millisToDHMSStr(timeDiff));
                    } else if (m_counter > PREFILL_TICKS) {
                        resetRepaint(ticksData, barsData, averagerData, regressorData, frame);

                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    m_counter++;
                }
            };

            readTicks(fileReader, ticksTs, ticks, bs, callback);

            resetRepaint(ticksData, barsData, averagerData, regressorData, frame);

            System.out.println("DONE");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void resetRepaint(BaseTicksData ticksData, BaseTicksData barsData, BaseTicksData averagerData, BaseTicksData regressorData, ChartFrame frame) {
        ticksData.reset();
        barsData.reset();
        averagerData.reset();
        regressorData.reset();
        frame.repaint();
    }

    private static void readTicks(FileReader fileReader, TimesSeriesData<TickData> ticksTs, List<TickData> ticks, BarSplitter bs, Runnable callback) throws IOException {
        BufferedReader br = new BufferedReader(fileReader);
        try {
            br.readLine(); // skip to the end of line

            String line;
            while ((line = br.readLine()) != null) {
                // System.out.println("line = " + line);
                TickVolumeData tickData = parseLine(line);
                ticksTs.add(tickData);

                ticks.add(0, tickData);
                bs.onTick(tickData);

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

    // ---------------------------------------------------------------
    private static abstract class BaseTicksData implements ITicksData {
        private List<? extends ITickData> m_copy;

        public abstract List<? extends ITickData> obtainTicks();

        public BaseTicksData() {
        }

        public List<? extends ITickData> getTicks() {
            if(m_copy == null) {
                m_copy = obtainTicks();
            }
            return m_copy;
        }

        public void reset() {
            m_copy = null;
        }
    }
}
