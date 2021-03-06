package bi.two.ind;

import bi.two.chart.TickData;
import bi.two.ts.BaseTimesSeriesData;
import bi.two.ts.ITimesSeriesData;

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
                float value = calcValue.getClosePrice();
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
}
