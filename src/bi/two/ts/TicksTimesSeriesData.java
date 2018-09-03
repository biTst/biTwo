package bi.two.ts;

import bi.two.chart.ITickData;
import bi.two.util.ReverseListIterator;

import java.util.*;

// will hold ALL ticks
public class TicksTimesSeriesData<T extends ITickData>
        extends BaseTicksTimesSeriesData<T> {
    protected List<T> m_ticks = Collections.synchronizedList(new ArrayList<T>()); // CopyOnWriteArrayList<T>();

    public TicksTimesSeriesData(ITimesSeriesData parent) {
        super(parent);
    }

    public T getOldestTick() { return m_ticks.get(m_ticks.size() - 1); }
    @Override public Object syncObject() { return m_ticks; }
    @Override public T getTick(int index) { return m_ticks.get(index); }
    public int getTicksNum() { return m_ticks.size(); }

    public Iterator<T> getTicksIterator() { // reverse time iteration - newest first, oldest then
        return m_ticks.iterator();
    }

    @Override public ReverseListIterator getReverseTicksIterator() { // forward time iteration - oldest first, newest then
        return new ReverseListIterator<>(m_ticks);
    }

    public Iterable<T> getTicksIterable() { // reverse time iteration - newest first, oldest then
        return m_ticks;
    }

    public void addNewestTick(T t) {
        m_ticks.add(0, t);
        m_newestTick = t;
        notifyListeners(true);
    }

    public void addOlderTick(T t) {
        int size = m_ticks.size();
        if (size > 0) {
            T last = m_ticks.get(size - 1);
            t.setOlderTick(last);
        } else{
            m_newestTick = t;
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

    @Override public int binarySearch(T key, Comparator<ITickData> comparator) {
        return Collections.binarySearch(m_ticks, key, comparator);
    }

    //----------------------------------------------------------------------
    public interface ITicksProcessor<T extends ITickData, Ret> {
        void init();
        void processTick(T tick);
        Ret done();
    }
}
