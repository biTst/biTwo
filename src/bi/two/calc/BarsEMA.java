package bi.two.calc;

import bi.two.algo.BarSplitter;
import bi.two.chart.ITickData;
import bi.two.chart.TickData;
import bi.two.ts.BaseTimesSeriesData;
import bi.two.ts.ITimesSeriesData;
import bi.two.ts.TicksTimesSeriesData;

import java.util.ArrayList;
import java.util.List;

// EMA - with fading bars sizes
// todo - switch to 'extends BarsBasedCalculator'
public class BarsEMA extends BaseTimesSeriesData<ITickData> {
    private static final double DEF_THRESHOLD = 0.995;
    private static final int MIN_LEN = 3;

    private final BarSplitter m_barSplitter;
    private final BarsProcessor m_barsProcessor = new BarsProcessor();
    private final double[] m_fadingRate;
    private boolean m_dirty;
    private boolean m_filled;
    private boolean m_initialized;
    private TickData m_tickData;

    public BarsEMA(ITimesSeriesData<? extends ITickData> tsd, float length, long barSize) {
        this(tsd, length, barSize, DEF_THRESHOLD);
    }

    public BarsEMA(ITimesSeriesData<? extends ITickData> tsd, float length, long barSize, double threshold) {
        super();
        if (length < MIN_LEN) {
            throw new RuntimeException("EMA.length passed " + length + "; should be " + MIN_LEN + " or bigger");
        }
        int barsNum = 0;
        double alpha = 2.0 / (length + 1); // 0.33
        double rate = 1 - alpha; // 0.66
        double sum = 0;
        List<Double> multipliers = new ArrayList<>();
        while (sum < threshold) {
            double multiplier = alpha * Math.pow(rate, barsNum);
            multipliers.add(multiplier);
            barsNum++;
            sum += multiplier;
        }
        double rest = 1.0d - sum;
        int size = multipliers.size();
        m_fadingRate = new double[size];
        for (int i = 0; i < size; i++) {
            Double multiplier = multipliers.get(i);
            m_fadingRate[i] = multiplier + rest * multiplier;
        }
        m_barSplitter = new BarSplitter(tsd, barsNum, barSize);
        setParent(m_barSplitter);
    }

    @Override public void onChanged(ITimesSeriesData ts, boolean changed) {
        boolean iAmChanged = false;
        if (changed) {
            if (!m_initialized) {
                BarSplitter.BarHolder newestBar = m_barSplitter.m_newestBar;
                if(newestBar != null) {
                    newestBar.addBarHolderListener(new BarSplitter.BarHolder.IBarHolderListener() {
                        @Override public void onTickEnter(ITickData tickData) {
                            m_dirty = true;
                        }
                        @Override public void onTickExit(ITickData tickData) {}
                    });

                    final BarSplitter.BarHolder oldestTick = m_barSplitter.getOldestTick();
                    oldestTick.addBarHolderListener(new BarSplitter.BarHolder.IBarHolderListener() {
                        @Override public void onTickEnter(ITickData tickData) { }
                        @Override public void onTickExit(ITickData tickData){
                            m_filled = true;
                            oldestTick.removeBarHolderListener(this);
                        }
                    });
                    m_initialized = true;
                }
            }
            iAmChanged = m_filled && m_dirty;
        }
        super.onChanged(this, iAmChanged); // notifyListeners
    }

    @Override public ITickData getLatestTick() {
        if (m_filled) {
            if (m_dirty) {
                Double ret = m_barSplitter.iterateTicks(m_barsProcessor);
                ITickData latestTick = m_parent.getLatestTick();
                long timestamp = latestTick.getTimestamp();
                m_tickData = new TickData(timestamp, ret.floatValue());
                m_dirty = false;
            }
            return m_tickData;
        }
        return null;
    }

    public String log() {
        return "BarsEMA["
                + "\n splitter=" + m_barSplitter.log()
                + "\n]";
    }

    @Override public void onTimeShift(long shift) {
        if (m_tickData != null) {
            m_tickData = m_tickData.newTimeShifted(shift);
        }
        m_dirty = true;
        // todo: call super
        notifyOnTimeShift(shift);
        //super.onTimeShift(shift);
    }

    //-------------------------------------------------------------------------------------
    private class BarsProcessor implements TicksTimesSeriesData.ITicksProcessor<BarSplitter.BarHolder, Double> {
        private int index = 0;
        private double ret = 0;
        private double weight = 0;

        @Override public void init() {
            index = 0;
            ret = 0;
            weight = 0;
        }

        @Override public void processTick(BarSplitter.BarHolder barHolder) {
            BarSplitter.TickNode latestNode = barHolder.getLatestTick();
            if (latestNode != null) { // sometimes bars may have no ticks inside
                ITickData latestTick = latestNode.m_param;
                float closePrice = latestTick.getClosePrice();
                double rate = m_fadingRate[index];
                double val = closePrice * rate;
                ret += val;
                weight += rate;
            }
            index++;
        }

        @Override public Double done() {
            double res = ret / weight;
            return res;
        }
    }
}
