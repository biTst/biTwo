package bi.two.chart;

import java.util.ArrayList;
import java.util.List;

public class BaseTimesSeriesData implements ITimesSeriesData, ITimesSeriesData.ITimesSeriesListener {
    private List<ITimesSeriesListener> m_listeners = new ArrayList<ITimesSeriesListener>();

    public BaseTimesSeriesData(ITimesSeriesData parent) {
        if (parent != null) {
            parent.addListener(this);
        }
    }

    @Override public void onChanged(ITimesSeriesData ts, boolean changed) {
        notifyListeners(changed);
    }

    @Override public void addListener(ITimesSeriesListener listener) {
        m_listeners.add(listener);
    }

    protected void notifyListeners(boolean changed) {
        for (ITimesSeriesListener listener : m_listeners) {
            listener.onChanged(this, changed);
        }
    }
}
