package bi.two.chart;

import java.util.ArrayList;
import java.util.List;

public class BaseTimesSeriesData<T extends ITickData> implements ITimesSeriesData {
    private List<ITimesSeriesListener> m_listeners = new ArrayList<ITimesSeriesListener>();

    @Override public void addListener(ITimesSeriesListener listener) {
        m_listeners.add(listener);
    }

    public void notifyListeners() {
        for (ITimesSeriesListener listener : m_listeners) {
            listener.onChanged(this);
        }
    }
}
