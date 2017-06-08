package bi.two.exch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Exchange {
    public static final List<Exchange> s_exchanges = new ArrayList<Exchange>();
    public static final Map<String,Exchange> s_exchangesMap = new HashMap<String, Exchange>();

    public final String m_name;
    public final Currency m_baseCurrency;
    public BaseExchImpl m_impl;
    public Map<Pair,ExchPairData> m_pairsMap = new HashMap<Pair, ExchPairData>();

    public Exchange(String name, Currency baseCurrency) {
        m_name = name;
        m_baseCurrency = baseCurrency;
        s_exchanges.add(this);
        s_exchangesMap.put(name, this);
    }

    public Currency baseCurrency() { return m_baseCurrency; }

    @Override public String toString() {
        return "Exchange[" + m_name + ']';
    }

    public void addPair(Pair pair) {
        m_pairsMap.put(pair, new ExchPairData());
    }

    public ExchPairData getPairData(Pair pair) { return m_pairsMap.get(pair); }

    public boolean supportPair(Currency from, Currency to) {
        for (Pair pair : m_pairsMap.keySet()) {
            if ((pair.m_from == from) && (pair.m_to == to)) {
                return true;
            }
            if ((pair.m_to == from) && (pair.m_from == to)) {
                return true;
            }
        }
        return false;
    }
}
