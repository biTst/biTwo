package bi.two.exch;

import java.util.HashMap;
import java.util.Map;

public class PairDirection {
    private static final Map<Currency, Map<Currency, PairDirection>> s_map = new HashMap<>();

    public final Pair m_pair;
    public final boolean m_forward;

    public static PairDirection get(Currency from, Currency to) {
        Map<Currency, PairDirection> map = s_map.get(from);
        if (map == null) {
            map = new HashMap<>();
            s_map.put(from, map);
        }
        PairDirection pairDirection = map.get(to);
        if (pairDirection == null) {
            Pair pair = Pair.get(from, to);
            boolean forward = pair.m_from == from;
            pairDirection = new PairDirection(pair, forward);
            map.put(to, pairDirection);
        }
        return pairDirection;
    }

    private PairDirection(Pair pair, boolean forward) {
        m_pair = pair;
        m_forward = forward;
    }

    @Override public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if ((o == null) || (getClass() != o.getClass())) {
            return false;
        }
        PairDirection that = (PairDirection) o;
        if (m_forward != that.m_forward) {
            return false;
        }
        return m_pair.equals(that.m_pair);
    }

    @Override public int hashCode() {
        int result = m_pair.hashCode();
        result = 31 * result + (m_forward ? 1 : 0);
        return result;
    }


    @Override public String toString() {
        return m_forward
                ? m_pair.m_from + "->" + m_pair.m_to
                : m_pair.m_to + "->" + m_pair.m_from;
    }

    public Currency getSourceCurrency() {
        return m_forward ? m_pair.m_from : m_pair.m_to;
    }

    public Currency getDestinationCurrency() {
        return m_forward ? m_pair.m_to : m_pair.m_from;
    }
}
