package bi.two.exch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Exchange {
    private static final List<Exchange> s_exchanges = new ArrayList<Exchange>();
    private static final Map<String,Exchange> s_exchangesMap = new HashMap<String, Exchange>();

    public final String m_name;
    public BaseExchImpl m_impl;
    public Map<Pair,ExchPairData> m_pairsMap = new HashMap<Pair, ExchPairData>();

    public Exchange(String name) {
        m_name = name;
        s_exchanges.add(this);
        s_exchangesMap.put(name, this);
    }

    @Override public String toString() {
        return "Exchange[" + m_name + ']';
    }

    public void addPair(Pair pair) {
        m_pairsMap.put(pair, new ExchPairData());
    }

    public ExchPairData getPairData(Pair pair) { return m_pairsMap.get(pair); }

    // -----------------------------------------------------
    public static class ExchPairData {
        public TopData m_topData;
    }
}
