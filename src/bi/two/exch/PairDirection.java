package bi.two.exch;

public class PairDirection {
    public final Pair m_pair;
    public final boolean m_forward;

    public PairDirection(Pair pair, boolean forward) {
        m_pair = pair;
        m_forward = forward;
    }

    @Override public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
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

    public Currency getSourceCurrency() {
        return m_forward ? m_pair.m_from : m_pair.m_to;
    }

    public Currency getDestinationCurrency() {
        return m_forward ? m_pair.m_to : m_pair.m_from;
    }
}
