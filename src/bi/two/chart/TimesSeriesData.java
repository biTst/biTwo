package bi.two.chart;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// will hold ALL ticks
public class TimesSeriesData<T extends ITickData>
        extends BaseTimesSeriesData
        implements ITicksData {
    protected List<T> m_ticks = Collections.synchronizedList(new ArrayList<T>()); // CopyOnWriteArrayList<T>();

    public TimesSeriesData(ITimesSeriesData parent) {
        super(parent);
    }

    public T getLatestTick() { return m_ticks.get(0); }
    public T getOldestTick() { return m_ticks.get(m_ticks.size() - 1); }
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
        iTicksProcessor.init();
        for (T tick : m_ticks) {
            iTicksProcessor.processTick(tick);
        }
        Ret done = iTicksProcessor.done();
        return done;
    }

    //----------------------------------------------------------------------
    public interface ITicksProcessor<T extends ITickData, Ret> {
        void init();
        void processTick(T tick);
        Ret done();
    }
}
