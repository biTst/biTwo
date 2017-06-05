package bi.two.algo;

import bi.two.chart.ITickData;
import bi.two.chart.ITimesSeriesData;
import bi.two.chart.TickData;
import bi.two.chart.TimesSeriesData;
import org.apache.commons.math3.stat.regression.SimpleRegression;

import java.util.List;

public class Regressor extends TimesSeriesData<TickData> {
    private final int m_barsNum;
    private final SimpleRegression m_simpleRegression = new SimpleRegression(true);

    public Regressor(int barsNum, final ITimesSeriesData tsData) {
        m_barsNum = barsNum;

        tsData.addListener(new ITimesSeriesData.ITimesSeriesListener() {
            @Override public void onChanged() {
                m_simpleRegression.clear();

                int validPoints = 0;
                List<? extends ITickData> ticks = tsData.getTicks();
                int size = ticks.size();
                for (int i = 0; i < size; i++) {
                    ITickData tick = ticks.get(i);
                    ITickData nextTick = getValidTick(tick);
                    if (nextTick != null) {
                        float price = nextTick.getMaxPrice();
                        if (nextTick.isValid()) {
                            m_simpleRegression.addData(m_barsNum - i, price);
                            validPoints++;
                        }
                    }
                }

                if (validPoints > 3) {
                    ITickData latestTick = ticks.get(0);
                    long latestTickTime = latestTick.getTimestamp();
                    double slope = m_simpleRegression.getSlope();
                    TickData tick = new TickData(latestTickTime, (float) slope);
                    add(tick);
                }

                //                barSplitter.iterateBars(new BarSplitter.IBarProcessor() {
//                    int counter = 0;
//
//                    @Override public boolean processBar(BarSplitter.BarHolder barHolder) {
//                        BarSplitter.TickNode latestTick = getLatestTick(barHolder);
//                        if (latestTick != null) {
//                            float price = latestTick.m_param.getMaxPrice();
//                            m_simpleRegression.addData(m_barsNum - counter, price);
//                            counter++;
//                            return true; // continue processing
//                        }
//                        counter = 0;
//                        return false; // stop processing
//                    }
//
//                    private BarSplitter.TickNode getLatestTick(BarSplitter.BarHolder barHolder) {
//                        BarSplitter.TickNode latestTick = barHolder.getLatestTick();
//                        if (latestTick == null) { // no ticks in current bar
//                            BarSplitter.BarHolder olderBar = barHolder.getOlderBar();
//                            if (olderBar != null) {
//                                return getLatestTick(olderBar);
//                            }
//                        }
//                        return latestTick;
//                    }
//
////                    @Override public boolean processBarNode(BarSplitter.BarNode barNode) {
////                        BarSplitter.TickNode latestTick = getLatestTick(barNode);
////                        if (latestTick != null) {
////                            float price = latestTick.m_param.getMaxPrice();
////                            m_simpleRegression.addData(m_barsNum - counter, price);
////                            counter++;
////                            return true; // continue processing
////                        }
////                        counter = 0;
////                        return false; // stop processing
////                    }
//
////                    private BarSplitter.TickNode getLatestTick(BarSplitter.BarNode barNode) {
////                        if(barNode == null) {
////                            return null;
////                        }
////                        BarSplitter.TickNode latestTick = barNode.m_param.getLatestTick();
////                        if(latestTick == null) { // no ticks in current bar
////                            return  getLatestTick((BarSplitter.BarNode) barNode.m_prev);
////                        }
////                        return latestTick;
////                    }
//
//                    @Override public void done() {
//                        if (counter == m_barsNum) {
//                            long lastTickTime = barSplitter.getLastTickTime();
//                            double slope = m_simpleRegression.getSlope();
//                            m_ticks.add(new TickData(lastTickTime, (float) slope));
//                        }
//                    }
//                });
            }

            private ITickData getValidTick(ITickData tick) {
                if (tick.isValid()) {
                    return tick;
                }
                ITickData olderTick = tick.getOlderTick();
                return olderTick == null ? null : getValidTick(olderTick);
            }
        });
    }

//    public List<? extends ITickData> getTicks() {
//        return new ArrayList<TickData>(m_ticks);
//    }
}
