package bi.two.calc;

import bi.two.algo.BarSplitter;
import bi.two.chart.ITickData;
import bi.two.chart.TickData;
import bi.two.ts.BaseTimesSeriesData;
import bi.two.ts.ITimesSeriesData;
import bi.two.ts.TimesSeriesData;

import java.util.ArrayList;
import java.util.List;

// EMA
// todo - switch to 'extends BarsBasedCalculator'
public class BarsEMA extends BaseTimesSeriesData<ITickData> {
    private static final double DEF_THRESHOLD = 0.995;
    private static final int MIN_LEN = 3;

    private final BarSplitter m_barSplitter;
    private final List<Double> m_multipliers = new ArrayList<>();
    private final BarsProcessor m_barsProcessor = new BarsProcessor();
    private boolean m_dirty;
    private boolean m_filled;
    private boolean m_initialized;
    private TickData m_tickData;

    public BarsEMA(ITimesSeriesData<ITickData> tsd, float length, long barSize) {
        this(tsd, length, barSize, DEF_THRESHOLD);
    }

    public BarsEMA(ITimesSeriesData<ITickData> tsd, float length, long barSize, double threshold) {
        super();
        if (length <= MIN_LEN) {
            throw new RuntimeException("EMA.length should be bigger than " + MIN_LEN);
        }
        int barsNum = 0;
        double alpha = 2.0 / (length + 1); // 0.33
        double rate = 1 - alpha; // 0.66
        double sum = 0;
        while (sum < threshold) {
            double multiplier = alpha * Math.pow(rate, barsNum);
            m_multipliers.add(multiplier);
            barsNum++;
            sum += multiplier;
        }
        List<Double> multipliers = new ArrayList<>(m_multipliers);
        m_multipliers.clear();
        double rest = 1.0d - sum;
        for (Double multiplier : multipliers) {
            m_multipliers.add(multiplier + rest * multiplier);
        }
        m_barSplitter = new BarSplitter(tsd, barsNum, barSize);
        setParent(m_barSplitter);
    }

    @Override public void onChanged(ITimesSeriesData ts, boolean changed) {
        boolean iAmChanged = false;
        if (changed) {
            if (!m_initialized) {
                m_initialized = true;
                m_barSplitter.m_newestBar.addBarHolderListener(new BarSplitter.BarHolder.IBarHolderListener() {
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

    //-------------------------------------------------------------------------------------
    private class BarsProcessor implements TimesSeriesData.ITicksProcessor<BarSplitter.BarHolder, Double> {
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
                double multiplier = m_multipliers.get(index);
                double val = closePrice * multiplier;
                ret += val;
                weight += multiplier;
            }
            index++;
        }

        @Override public Double done() {
            double res = ret / weight;
            return res;
        }
    }
}
