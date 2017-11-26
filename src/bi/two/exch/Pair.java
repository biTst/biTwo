package bi.two.exch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Pair {
    private static final List<Pair> s_pairs = new ArrayList<Pair>();
    private static final Map<String,Pair> s_pairsMap = new HashMap<String, Pair>();

    public final Currency m_from;
    public final Currency m_to;
    public final String m_name;

    Pair(Currency from, Currency to) {
        m_from = from;
        m_to = to;
        m_name = from.m_name + "_" + to.m_name;

        s_pairs.add(this);
        s_pairsMap.put(m_name, this);
    }

    public static Pair getByName(String name) {
        Pair pair = getByNameInt(name);
        if (pair != null) {
            return pair;
        }
        throw new RuntimeException("no pair with name=" + name);
    }

    public static Pair getByNameInt(String name) {
        return s_pairsMap.get(name);
    }

    @Override public String toString() {
        return "Pair[" + m_name + ']';
    }

    @Override public int hashCode() {
        return m_name.hashCode();
    }

    @Override public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Pair pair = (Pair) o;
        return m_name.equals(pair.m_name);
    }
}
