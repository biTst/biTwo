package bi.two.chart;

import java.util.ArrayList;
import java.util.List;

public abstract class BaseTimesSeriesData<T extends ITickData>
        implements ITimesSeriesData<T>, ITimesSeriesData.ITimesSeriesListener {
    public final ITimesSeriesData m_parent;
    private List<ITimesSeriesListener> m_listeners = new ArrayList<ITimesSeriesListener>();

    public BaseTimesSeriesData(ITimesSeriesData parent) {
        m_parent = parent;
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
