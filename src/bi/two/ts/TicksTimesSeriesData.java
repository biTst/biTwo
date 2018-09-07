package bi.two.ts;

import bi.two.chart.ITickData;
import bi.two.util.ReverseListIterator;

import java.util.*;

// will hold ALL ticks
public class TicksTimesSeriesData<T extends ITickData>
        extends BaseTicksTimesSeriesData<T> {
    // [0] - oldest tick;  newest ticks at the end
    protected List<T> m_reverseTicks = Collections.synchronizedList(new ArrayList<T>()); // CopyOnWriteArrayList<T>();

    public TicksTimesSeriesData(ITimesSeriesData parent) {
        super(parent);
    }

    public T getOldestTick() { return m_reverseTicks.get(0); }
    @Override public Object syncObject() { return m_reverseTicks; }
    @Override public T getTick(int index) { return m_reverseTicks.get(m_reverseTicks.size()-1-index); }
    public int getTicksNum() { return m_reverseTicks.size(); }

    public Iterator<T> getTicksIterator() { // reverse time iteration - newest first, oldest then
        return new ReverseListIterator<T>(m_reverseTicks);
    }
    public Iterable<T> getTicksIterable() { // reverse time iteration - newest first, oldest then
        return new ReverseListIterator<>(m_reverseTicks);
    }

    @Override public Iterator<T> getReverseTicksIterator() { // forward time iteration - oldest first, newest then
        return m_reverseTicks.iterator();
    }

    @Override public Iterable<T> getReverseTicksIterable() { // forward time iteration - oldest first, newest then
        return m_reverseTicks;
    }


    public void addNewestTick(T t) {
        m_reverseTicks.add(t); // newest ticks at the end
        m_newestTick = t;
        notifyListeners(true);
    }

    public void addOlderTick(T t) { // older tick at the beginning
        int size = m_reverseTicks.size();
        if (size > 0) {
            T last = m_reverseTicks.get(0);
            t.setOlderTick(last);
        } else{
            m_newestTick = t;
        }
        m_reverseTicks.add(0, t);
        notifyListeners(true);
    }

    public <Ret> Ret iterateTicks(ITicksProcessor<T, Ret> iTicksProcessor) {
        iTicksProcessor.init();
        for (T tick : getTicksIterable()) {
            iTicksProcessor.processTick(tick);
        }
        Ret done = iTicksProcessor.done();
        return done;
    }

    @Override public int binarySearch(T key, Comparator<ITickData> comparator) {
        return Collections.binarySearch(m_reverseTicks, key, comparator);
    }

    //----------------------------------------------------------------------
    public interface ITicksProcessor<T extends ITickData, Ret> {
        void init();
        void processTick(T tick);
        Ret done();
    }
}
