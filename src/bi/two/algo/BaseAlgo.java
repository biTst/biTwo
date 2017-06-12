package bi.two.algo;

import bi.two.chart.BaseTimesSeriesData;
import bi.two.chart.ITimesSeriesData;
import bi.two.chart.TickData;
import bi.two.chart.TimesSeriesData;
import bi.two.ind.BaseIndicator;

import java.util.ArrayList;
import java.util.List;

public class BaseAlgo extends BaseTimesSeriesData {
    public List<BaseIndicator> m_indicators = new ArrayList<BaseIndicator>();

    public BaseAlgo(BaseTimesSeriesData parent) {
        super(parent);
    }

    // override
    public double getDirectionAdjusted() { return 0; } // [-1 ... 1]
    public TickData getAdjusted() { return null; }

    public TimesSeriesData<TickData> getTS(final boolean joinNonChangedValues) {
        return new AlgoTimesSeriesData(this, joinNonChangedValues);
    }

    public void addIndicator(BaseIndicator indicator) {
        m_indicators.add(indicator);
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
                TickData value = getAdjusted();
                if (value != null) {
                    if (m_joinNonChangedValues) {
                        List<TickData> ticks = getTicks();
                        if (!ticks.isEmpty()) {
                            TickData newestAddedTick = ticks.get(0); // newest
                            float newestAddedPrice = newestAddedTick.getPrice();
                            float nowPrice = value.getPrice();
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
}