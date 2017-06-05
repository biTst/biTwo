package bi.two.chart;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class TimesSeriesData<T extends ITickData> implements ITimesSeriesData, ITicksData, ITimesSeriesData.ITimesSeriesListener {
    private List<T> m_ticks = new CopyOnWriteArrayList<T>();
    private List<ITimesSeriesListener> m_listeners = new ArrayList<ITimesSeriesListener>();

    public List<T> getTicks() { return m_ticks; }

    public void add(T t) {
        m_ticks.add(0, t);
        notifyListeners();
    }

    @Override public void addListener(ITimesSeriesListener listener) {
        m_listeners.add(listener);
    }

    public void notifyListeners() {
        for (ITimesSeriesListener listener : m_listeners) {
            listener.onChanged();
        }
    }

    @Override public void onChanged() {
        new Exception("not implemented");
    }

    protected void addTick(T tickData) {
        int size = m_ticks.size();
        if (size > 0) {
            T last = m_ticks.get(size - 1);
            tickData.setOlderTick(last);
        }
        m_ticks.add(tickData);
    }
}
