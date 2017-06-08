package bi.two.chart;

import java.util.ArrayList;
import java.util.List;

public class BaseTimesSeriesData<T extends ITickData> implements ITimesSeriesData, ITimesSeriesData.ITimesSeriesListener {
    private List<ITimesSeriesListener> m_listeners = new ArrayList<ITimesSeriesListener>();

    public BaseTimesSeriesData(ITimesSeriesData parent) {
        if (parent != null) {
            parent.addListener(this);
        }
    }

    @Override public void onChanged(ITimesSeriesData ts) {
        // to override
    }

    @Override public void addListener(ITimesSeriesListener listener) {
        m_listeners.add(listener);
    }

    public void notifyListeners() {
        for (ITimesSeriesListener listener : m_listeners) {
            listener.onChanged(this);
        }
    }
}
