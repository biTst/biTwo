package bi.two.algo;

import bi.two.ChartCanvas;
import bi.two.chart.*;
import bi.two.ind.BaseIndicator;
import bi.two.opt.Vary;
import bi.two.ts.BaseTicksTimesSeriesData;
import bi.two.ts.ITimesSeriesData;
import bi.two.ts.TicksTimesSeriesData;
import bi.two.util.MapConfig;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public abstract class BaseAlgo<T extends ITickData> extends TicksTimesSeriesData<T> {
    public static final String COLLECT_VALUES_KEY = "collect.values";
    public static final String ALGO_NAME_KEY = "algoName";
    public static final String COMMISSION_KEY = "commission";

    protected final float m_minOrderMul; // used in Watcher, used on algos for logging
    protected final boolean m_collectValues;

    public List<BaseIndicator> m_indicators = new ArrayList<>();

    public BaseAlgo(ITimesSeriesData parent, MapConfig algoConfig) {
        super(parent);

        m_minOrderMul = algoConfig.getNumber(Vary.minOrderMul).floatValue();
        m_collectValues = algoConfig.getBoolean(BaseAlgo.COLLECT_VALUES_KEY);
    }

    // override
    public double getDirectionAdjusted() { return 0; } // [-1 ... 1]
    public ITickData getAdjusted() { return null; }
    public void setupChart(boolean collectValues, ChartCanvas chartCanvas, BaseTicksTimesSeriesData<TickData> ticksTs, Watcher firstWatcher) { /*noop*/ }
    public abstract String key(boolean detailed);
    public int getTurnsCount() { return 0; } // unknown

    public TicksTimesSeriesData<TickData> getTS(final boolean joinNonChangedValues) {
        return new AlgoTimesSeriesData(this, joinNonChangedValues);
    }

    public void addIndicator(BaseIndicator indicator) {
        m_indicators.add(indicator);
    }

    public static void addChart(ChartData chartData, ITicksData ticksData, List<ChartAreaLayerSettings> layers, String name, Color color, TickPainter tickPainter) {
        chartData.setTicksData(name, ticksData);
        layers.add(new ChartAreaLayerSettings(name, color, tickPainter));
    }

    public long getPreloadPeriod() {
        throw new RuntimeException("need to override");
    }

    public void reset() {
        throw new RuntimeException("not implemented");
    }

    public void notifyFinish() { /*noop*/ }


    //----------------------------------------------------------
    public class AlgoTimesSeriesData extends TicksTimesSeriesData<TickData> {

        private final boolean m_joinNonChangedValues;

        public AlgoTimesSeriesData(ITimesSeriesData parent, boolean joinNonChangedValues) {
            super(parent);
            m_joinNonChangedValues = joinNonChangedValues;
        }

        @Override public void onChanged(ITimesSeriesData ts, boolean changed) {
            if (changed) {
                ITickData value = getAdjusted();
                if (value != null) {
                    if (m_joinNonChangedValues) {
                        int ticksNum = getTicksNum();
                        if (ticksNum > 0) {
                            TickData newestAddedTick = getTick(0); // newest
                            float newestAddedPrice = newestAddedTick.getClosePrice();
                            float nowPrice = value.getClosePrice();
                            if (newestAddedPrice == nowPrice) {
                                newestAddedTick.init(value); // just update newest added tick
                                notifyListeners(false);
                                return;
                            }
                        }
                    }
                    TickData tickData = new TickData(value); // close
                    addNewestTick(tickData);
                    return;
                }
            }
            notifyListeners(false);
        }

        @Override public void onTimeShift(long shift) {
            notifyOnTimeShift(shift);
        }
    }


    //----------------------------------------------------------
    public static abstract class JoinNonChangedInnerTimesSeriesData extends JoinNonChangedTimesSeriesData {
        protected abstract Float getValue();

        protected JoinNonChangedInnerTimesSeriesData(ITimesSeriesData parent) {
            super(parent);
        }

        protected JoinNonChangedInnerTimesSeriesData(ITimesSeriesData parent, boolean horizontal) {
            super(parent, horizontal);
        }

        @Override protected ITickData getTickValue() {
            ITickData latestTick = m_parent.getLatestTick();
            if (latestTick != null) {
                Float value = getValue();
                if (value != null) {
                    long timestamp = latestTick.getTimestamp();
                    return new TickData(timestamp, value);
                }
            }
            return null;
        }
    }
}
