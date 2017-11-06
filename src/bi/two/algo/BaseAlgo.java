package bi.two.algo;

import bi.two.ChartCanvas;
import bi.two.chart.*;
import bi.two.ind.BaseIndicator;
import bi.two.ts.ITimesSeriesData;
import bi.two.ts.TimesSeriesData;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public abstract class BaseAlgo<T extends ITickData> extends TimesSeriesData<T> {
    public static final String COLLECT_VALUES_KEY = "collect.values";
    public static final String ALGO_NAME_KEY = "algoName";
    public static final String COMMISSION_KEY = "commission";

    public List<BaseIndicator> m_indicators = new ArrayList<BaseIndicator>();

    public BaseAlgo(ITimesSeriesData parent) {
        super(parent);
    }

    // override
    public double getDirectionAdjusted() { return 0; } // [-1 ... 1]
    public ITickData getAdjusted() { return null; }
    public void setupChart(boolean collectValues, ChartCanvas chartCanvas, TimesSeriesData<TickData> ticksTs, Watcher firstWatcher) { /*noop*/ }
    public abstract String key(boolean detailed);

    public TimesSeriesData<TickData> getTS(final boolean joinNonChangedValues) {
        return new AlgoTimesSeriesData(this, joinNonChangedValues);
    }

    public void addIndicator(BaseIndicator indicator) {
        m_indicators.add(indicator);
    }

    protected static void addChart(ChartData chartData, ITicksData ticksData, List<ChartAreaLayerSettings> layers, String name, Color color, TickPainter tickPainter) {
        chartData.setTicksData(name, ticksData);
        layers.add(new ChartAreaLayerSettings(name, color, tickPainter));
    }

    
    //----------------------------------------------------------
    public class AlgoTimesSeriesData extends TimesSeriesData<TickData> {

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
                        List<TickData> ticks = getTicks();
                        if (!ticks.isEmpty()) {
                            TickData newestAddedTick = ticks.get(0); // newest
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
    }


    //----------------------------------------------------------
    public static abstract class JoinNonChangedInnerTimesSeriesData extends JoinNonChangedTimesSeriesData {
        protected abstract Float getValue();

        protected JoinNonChangedInnerTimesSeriesData(ITimesSeriesData parent) {
            super(parent);
        }

        @Override protected ITickData getTickValue() {
            ITickData latestTick = getParent().getLatestTick();
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
