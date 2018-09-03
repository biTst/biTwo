package bi.two.ts;

import bi.two.chart.ITickData;
import bi.two.util.ReverseListIterator;

import java.util.Comparator;
import java.util.Iterator;

public class NoTicksTimesSeriesData<T extends ITickData> extends BaseTicksTimesSeriesData<T> {
    public NoTicksTimesSeriesData(ITimesSeriesData parent) {
        super(parent);
    }

    @Override public void addNewestTick(T tickData) {
        m_newestTick = tickData;
        notifyListeners(true);
    }

    @Override public void addOlderTick(T tickData) {
        m_newestTick = tickData;
        notifyListeners(true);
    }

    @Override public T getTick(int index) {
        throw new RuntimeException("should not be called");
    }

    public int getTicksNum() {
        throw new RuntimeException("should not be called");
    }

    @Override public Object syncObject() {
        throw new RuntimeException("should not be called");
    }

    @Override public Iterator<T> getTicksIterator() {
        throw new RuntimeException("should not be called");
    }

    @Override public Iterable getTicksIterable() {
        throw new RuntimeException("should not be called");
    }

    @Override public ReverseListIterator getReverseTicksIterator() {
        throw new RuntimeException("should not be called");
    }

    @Override public Iterable<T> getReverseTicksIterable() {
        throw new RuntimeException("should not be called");
    }

    @Override public int binarySearch(T o, Comparator<ITickData> comparator) {
        throw new RuntimeException("should not be called");
    }
}
