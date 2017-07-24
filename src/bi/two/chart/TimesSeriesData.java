package bi.two.chart;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

// will hold ALL ticks
public class TimesSeriesData<T extends ITickData>
        extends BaseTimesSeriesData
        implements ITicksData, ITimesSeriesData.ITimesSeriesListener {
    protected List<T> m_ticks = new CopyOnWriteArrayList<T>();

    public TimesSeriesData(ITimesSeriesData parent) {
        super(parent);
    }

    public T getLastTick() { return m_ticks.get(0); }
    public List<T> getTicks() { return m_ticks; }

    public void addNewestTick(T t) {
        m_ticks.add(0, t);
        notifyListeners(true);
    }

    public void addOlderTick(T t) {
        int size = m_ticks.size();
        if (size > 0) {
            T last = m_ticks.get(size - 1);
            t.setOlderTick(last);
        }
        m_ticks.add(t);
        notifyListeners(true);
    }

    public <Ret> Ret iterateTicks(ITicksProcessor<T, Ret> iTicksProcessor) {
        for (T tick : m_ticks) {
            iTicksProcessor.processTick(tick);
        }
        Ret done = iTicksProcessor.done();
        return done;
    }

    //----------------------------------------------------------------------
    public interface ITicksProcessor<T extends ITickData, Ret> {
        void processTick(T tick);
        Ret done();
    }
}
