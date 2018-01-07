package bi.two.tre;

import bi.two.exch.Currency;
import bi.two.exch.PairDirection;

import java.util.HashMap;
import java.util.Map;

public class PairDirectionData {
    private static Map<PairDirection, PairDirectionData> s_map = new HashMap<>();

    public final PairDirection m_pairDirection;
    public final PairData m_pairData;

    public static PairDirectionData get(Currency from, Currency to) {
        PairDirection pairDirection = PairDirection.get(from, to);
        PairDirectionData pdd = s_map.get(pairDirection);
        if (pdd == null) {
            pdd = new PairDirectionData(pairDirection);
        }
        return pdd;
    }

    public PairDirectionData(PairDirection pairDirection) {
        m_pairDirection = pairDirection;
        m_pairData = PairData.get(pairDirection.m_pair);
    }

    @Override public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        PairDirectionData that = (PairDirectionData) o;
        return m_pairDirection.equals(that.m_pairDirection);
    }

    @Override public int hashCode() {
        return m_pairDirection.hashCode();
    }
}
