package bi.two.chart;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class TimesSeriesData<T extends ITickData>
        extends BaseTimesSeriesData
        implements ITicksData, ITimesSeriesData.ITimesSeriesListener {
    private List<T> m_ticks = new CopyOnWriteArrayList<T>();

    public TimesSeriesData(ITimesSeriesData parent) {
        super(parent);
    }

    public List<T> getTicks() { return m_ticks; }

    public void addNewestTick(T t) {
        m_ticks.add(0, t);
        notifyListeners(true);
    }

    protected void addTick(T t) {
        int size = m_ticks.size();
        if (size > 0) {
            T last = m_ticks.get(size - 1);
            t.setOlderTick(last);
        }
        m_ticks.add(t);
        notifyListeners(true);
    }
}
