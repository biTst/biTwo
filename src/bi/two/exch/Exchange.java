package bi.two.exch;

import java.util.*;

public class Exchange {
    public static final List<Exchange> s_exchanges = new ArrayList<Exchange>();
    public static final Map<String,Exchange> s_exchangesMap = new HashMap<String, Exchange>();

    public final String m_name;
    public final Currency m_baseCurrency;
    public BaseExchImpl m_impl;
    public Map<Pair,ExchPairData> m_pairsMap = new HashMap<Pair, ExchPairData>();
    public Schedule m_schedule;

    public Exchange(String name, Currency baseCurrency) {
        m_name = name;
        m_baseCurrency = baseCurrency;
        s_exchanges.add(this);
        s_exchangesMap.put(name, this);
    }

    public static Exchange get(String name) { return s_exchangesMap.get(name); }

    @Override public String toString() {
        return "Exchange[" + m_name + ']';
    }

    public ExchPairData addPair(Pair pair) {
        ExchPairData value = new ExchPairData();
        m_pairsMap.put(pair, value);
        return value;
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

    public Pair getPair(Currency from, Currency to) {
        for (Pair pair : m_pairsMap.keySet()) {
            if ((pair.m_from == from) && (pair.m_to == to)) {
                return pair;
            }
            if ((pair.m_to == from) && (pair.m_from == to)) {
                return pair;
            }
        }
        return null;
    }

    public double minOrderToCreate(Pair pair) {
        ExchPairData pairData = getPairData(pair);
        return pairData.minOrderToCreate();
    }

    public Date getNextTradeCloseTime(long tickTime) {
        if (m_schedule != null) {
            return m_schedule.getNextTradeCloseTime(tickTime);
        }
        return null;
    }

    public boolean hasSchedule() {
        return (m_schedule != null);
    }

    public void connect(IExchangeConnectListener iExchangeConnectListener) throws Exception {
        m_impl.connect(iExchangeConnectListener);
    }


    //----------------------------------------------------------------------------------------
    public interface IExchangeConnectListener {

        void onConnected();
    }
}
