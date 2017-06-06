package bi.two.chart;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class TimesSeriesData<T extends ITickData>
        extends BaseTimesSeriesData<T>
        implements ITicksData, ITimesSeriesData.ITimesSeriesListener {
    private List<T> m_ticks = new CopyOnWriteArrayList<T>();

    public List<T> getTicks() { return m_ticks; }

    public void addNewestTick(T t) {
        m_ticks.add(0, t);
        notifyListeners();
    }

    @Override public void onChanged(ITimesSeriesData ts) {
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
