package bi.two.tre;

import bi.two.exch.Currency;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class Round {
    private static final Map<String,Round> s_map = new HashMap<>();

    public final Currency m_c1;
    public final Currency m_c2;
    public final Currency m_c3;
    public final String m_roundName;
    public final Currency[] m_currencies;

    public static Round get(Currency c1, Currency c2, Currency c3) {
        Currency cc1 = c1;
        Currency cc2 = c2;
        Currency cc3 = c3;
        if (cc1.m_name.compareTo(cc2.m_name) > 0) {
            Currency c = cc1;
            cc1 = cc2;
            cc2 = c;
        }
        if (cc1.m_name.compareTo(cc3.m_name) > 0) {
            Currency c = cc1;
            cc1 = cc3;
            cc3 = c;
        }
        if (cc2.m_name.compareTo(cc3.m_name) > 0) {
            Currency c = cc2;
            cc2 = cc3;
            cc3 = c;
        }
        String key = buildName(cc1, cc2, cc3);
        Round round = s_map.get(key);
        if (round == null) {
            round = new Round(cc1, cc2, cc3);
            s_map.put(key, round);
        }
        return round;
    }

    @NotNull private static String buildName(Currency cc1, Currency cc2, Currency cc3) {
        return cc1.m_name + "-" + cc2.m_name + "-" + cc3.m_name;
    }

    private Round(Currency c1, Currency c2, Currency c3) {
        m_c1 = c1;
        m_c2 = c2;
        m_c3 = c3;
        m_currencies = new Currency[]{c1, c2, c3};
        m_roundName = buildName(c1, c2, c3);
    }

    @Override public String toString() {
        return m_roundName;
    }

    @Override public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if ((o == null) || (getClass() != o.getClass())) {
            return false;
        }

        Round roundData = (Round) o;
        return m_roundName.equals(roundData.m_roundName);
    }

    @Override public int hashCode() {
        return m_roundName.hashCode();
    }
}
