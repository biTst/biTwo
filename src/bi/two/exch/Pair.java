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

    Pair(Currency to, Currency from) {
        m_from = from;
        m_to = to;
        m_name = from.name() + "_" + to.name();

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
}
