package bi.two.ind;

import bi.two.chart.*;

public abstract class BaseIndicator extends BaseTimesSeriesData {
    private Float m_prevValue;

    public BaseIndicator(ITimesSeriesData parent) {
        super(parent);
    }

    public abstract TickData getTickValue();
    public abstract TickData calculateTickValue();

    @Override public void onChanged(ITimesSeriesData ts, boolean changed) {
        boolean iAmChanged = false;
        if (changed) {
            TickData calcValue = calculateTickValue();
            if (calcValue != null) {
                float value = calcValue.getPrice();
                if (m_prevValue != null) {
                    if (value == m_prevValue) {
                        notifyListeners(false);
                        return; // value not changed
                    }
                }
                m_prevValue = value;
                iAmChanged = true;
            }
        }
        notifyListeners(iAmChanged);
    }


    public TimesSeriesData<TickData> getTS() {
        return new IndicatorTimesSeriesData(this);
    }

    //----------------------------------------------------------
    public class IndicatorTimesSeriesData extends JoinNonChangedTimesSeriesData {
        public IndicatorTimesSeriesData(ITimesSeriesData parent) {
            super(parent);
        }

        @Override protected TickData getTickValue() {
            return BaseIndicator.this.getTickValue();
        }
    }
}
