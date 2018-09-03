package bi.two.chart;

import bi.two.ts.ITimesSeriesData;
import bi.two.util.ReverseListIterator;

import java.util.Comparator;
import java.util.Iterator;

public interface ITicksData<T extends ITickData>
       extends ITimesSeriesData<T> {
    T getTick(int index);
    /** reverse time iteration - newest first, oldest then */
    Iterator<T> getTicksIterator();
    Iterable<T> getTicksIterable(); // reverse time iteration - newest first, oldest then
    /** forward time iteration - oldest first, newest then */
    ReverseListIterator<T> getReverseTicksIterator();
    int getTicksNum();
    Object syncObject();
    int binarySearch(T o, Comparator<ITickData> comparator);
}
