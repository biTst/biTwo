package bi.two.ts;

import bi.two.chart.ITickData;
import bi.two.chart.JoinNonChangedTimesSeriesData;
import bi.two.chart.TickData;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public abstract class BaseTimesSeriesData<T extends ITickData>
        implements ITimesSeriesData<T>, ITimesSeriesListener<T> {
    public ITimesSeriesData m_parent;
    private List<ITimesSeriesListener<T>> m_listeners = new CopyOnWriteArrayList<>();

    public ITimesSeriesData<T> getActive() { return this; }
    public ITimesSeriesData getParent() { return m_parent; }

    public BaseTimesSeriesData() {  }

    public BaseTimesSeriesData(ITimesSeriesData<? extends ITickData> parent) {
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

    @Override public void onChanged(ITimesSeriesData<T> ts, boolean changed) {
        notifyListeners(changed);
    }

    @Override public void addListener(ITimesSeriesListener listener) {
        m_listeners.add(listener);
    }

    @Override public void removeListener(ITimesSeriesListener<T> listener) {
        m_listeners.remove(listener);
    }

    protected void notifyListeners(boolean changed) {
        for (int i = 0, size = m_listeners.size(); i < size; i++) {
            ITimesSeriesListener<T> listener = m_listeners.get(i);
            listener.onChanged(this, changed);
        }
    }

    // no more ticks - call from parent
    @Override public void notifyNoMoreTicks() {
        for (ITimesSeriesListener<T> listener : m_listeners) {
            listener.notifyNoMoreTicks();
        }
    }

    @Override public void waitWhenFinished() { /* noop */ }

    public void waitAllFinished() {
        for (ITimesSeriesListener<T> listener : m_listeners) {
            listener.waitWhenFinished();
        }
    }


    public TicksTimesSeriesData<TickData> getJoinNonChangedTs() {
        return new JoinNonChangedTimesSeriesData(this);
    }
}
