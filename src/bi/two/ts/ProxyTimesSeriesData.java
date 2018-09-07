package bi.two.ts;

import bi.two.chart.ITickData;

import java.util.Comparator;
import java.util.Iterator;

public class ProxyTimesSeriesData<T extends ITickData> extends BaseTicksTimesSeriesData<T> {
    private final BaseTicksTimesSeriesData<T> m_proxy;

    public ProxyTimesSeriesData(BaseTicksTimesSeriesData<T> proxy) {
        super(null);
        proxy.setParent(this);
        m_proxy = proxy;
    }

    @Override public void addNewestTick(T tickData) {
        m_proxy.addNewestTick(tickData);
    }

    @Override public void addOlderTick(T tickData) {
        m_proxy.addOlderTick(tickData);
    }

    @Override public T getTick(int index) {
        return m_proxy.getTick(index);
    }

    @Override public Iterator<T> getTicksIterator() {
        return m_proxy.getTicksIterator();
    }

    @Override public Iterable<T> getTicksIterable() {
        return m_proxy.getTicksIterable();
    }

    @Override public Iterator<T> getReverseTicksIterator() {
        return m_proxy.getReverseTicksIterator();
    }

    @Override public Iterable<T> getReverseTicksIterable() {
        return m_proxy.getReverseTicksIterable();
    }

    @Override public int getTicksNum() {
        return m_proxy.getTicksNum();
    }

    @Override public Object syncObject() {
        return m_proxy.syncObject();
    }

    @Override public int binarySearch(T o, Comparator<ITickData> comparator) {
        return m_proxy.binarySearch(o, comparator);
    }
}
