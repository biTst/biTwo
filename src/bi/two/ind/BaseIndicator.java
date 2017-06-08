package bi.two.ind;

import bi.two.chart.BaseTimesSeriesData;
import bi.two.chart.ITimesSeriesData;
import bi.two.chart.TickData;
import bi.two.chart.TimesSeriesData;

import java.util.List;

public abstract class BaseIndicator extends BaseTimesSeriesData {
    public BaseIndicator(ITimesSeriesData parent) {
        super(parent);
    }

    public abstract TickData getTickValue();

    public TimesSeriesData<TickData> getTS(final boolean joinNonChangedValues) {
        return new IndicatorTimesSeriesData(this, joinNonChangedValues);
    }

    //----------------------------------------------------------
    public class IndicatorTimesSeriesData extends TimesSeriesData<TickData> {

        private final boolean m_joinNonChangedValues;

        public IndicatorTimesSeriesData(ITimesSeriesData parent, boolean joinNonChangedValues) {
            super(parent);
            m_joinNonChangedValues = joinNonChangedValues;
        }

        @Override public void onChanged(ITimesSeriesData ts, boolean changed) {
            if (changed) {
                TickData value = getTickValue();
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
