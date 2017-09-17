package bi.two.chart;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public abstract class BaseTimesSeriesData<T extends ITickData>
        implements ITimesSeriesData<T>, ITimesSeriesData.ITimesSeriesListener {
    public ITimesSeriesData m_parent;
    private List<ITimesSeriesListener> m_listeners = new CopyOnWriteArrayList<ITimesSeriesListener>();

    public ITimesSeriesData getActive() { return this; }
    public ITimesSeriesData getParent() { return m_parent; }

    public BaseTimesSeriesData() {
    }

    public BaseTimesSeriesData(ITimesSeriesData parent) {
        setParent(parent);
    }

    public void setParent(ITimesSeriesData parent) {
        if (parent != null) {
            m_parent = parent.getActive();
            m_parent.addListener(this);
        } else {
            m_parent = null;
        }
    }

    @Override public void onChanged(ITimesSeriesData ts, boolean changed) {
        notifyListeners(changed);
    }

    @Override public void addListener(ITimesSeriesListener listener) {
        m_listeners.add(listener);
    }

    @Override public void removeListener(ITimesSeriesListener listener) {
        m_listeners.remove(listener);
    }

    protected void notifyListeners(boolean changed) {
        for (ITimesSeriesListener listener : m_listeners) {
            listener.onChanged(this, changed);
        }
    }

    @Override public void waitWhenFinished() { /* noop */ }

    public void waitAllFinished() {
        for (ITimesSeriesListener listener : m_listeners) {
            listener.waitWhenFinished();
        }
    }


    public TimesSeriesData<TickData> getJoinNonChangedTs() {
        return new JoinNonChangedTimesSeriesData(this);
    }
}
