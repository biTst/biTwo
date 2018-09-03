package bi.two.util;

import java.util.Iterator;
import java.util.List;

public class ReverseListIterator<T> implements Iterator<T>, Iterable<T> {
    private final List<T> m_list;
    private int m_position;

    public ReverseListIterator(List<T> list) {
        m_list = list;
        m_position = list.size() - 1;
    }

    @Override public Iterator<T> iterator() { return this; }

    @Override public boolean hasNext() { return (m_position >= 0); }

    @Override public T next() { return m_list.get(m_position--); }

    @Override public void remove() {
        throw new UnsupportedOperationException();
    }

    public int size() { return m_list.size(); }
}
