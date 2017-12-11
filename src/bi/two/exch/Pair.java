package bi.two.exch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Pair {
    private static final List<Pair> s_pairsList = new ArrayList<>();
    private static final Map<String,Pair> s_pairsByNameMap = new HashMap<>();
    private static final Map<Currency,Map<Currency,Pair>> s_map = new HashMap<>();

    public final Currency m_from;
    public final Currency m_to;
    public final String m_name;

    /** Can be found in both directions */
    public static Pair get(Currency from, Currency to) {
        Map<Currency, Pair> map = s_map.get(from);
        if (map != null) {
            Pair pair = map.get(to);
            if (pair != null) {
                return pair;
            }
        }
        
        Pair pair = new Pair(from, to);
        insert(from, to, pair);
        insert(to, from, pair);

        return pair;
    }

    private static void insert(Currency from, Currency to, Pair pair) {
        Map<Currency, Pair> map = s_map.get(from);
        if (map == null) {
            map = new HashMap<>();
            s_map.put(from, map);
        }
        map.put(to, pair);
    }

    private Pair(Currency from, Currency to) {
        m_from = from;
        m_to = to;
        m_name = from.m_name + "_" + to.m_name;

        s_pairsList.add(this);
        s_pairsByNameMap.put(m_name, this);
    }

    public static Pair getByName(String name) {
        Pair pair = getByNameInt(name);
        if (pair != null) {
            return pair;
        }
        throw new RuntimeException("no pair with name=" + name);
    }

    public static Pair getByNameInt(String name) {
        return s_pairsByNameMap.get(name);
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
