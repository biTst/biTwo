package bi.two.tre;

import bi.two.exch.Currency;
import bi.two.exch.OrderBook;

import java.util.ArrayList;
import java.util.List;

class RoundDirectedData implements OrderBook.IOrderBookListener {
    public final Currency[] m_currencies;
    public final Round m_round;
    public final String m_name;
    private final List<PairDirectionData> m_pdds = new ArrayList<>();
    private boolean m_allLive;

    public RoundDirectedData(Currency[] c) {
        m_currencies = c;
        Currency c0 = c[0];
        Currency c1 = c[1];
        Currency c2 = c[2];
        m_round = Round.get(c0, c1, c2);

        m_name = c0.m_name + "->" + c1.m_name + "->" + c2.m_name;

        int len = c.length;
        for (int i = 0; i < len; i++) {
            Currency from = c[i];
            Currency to = c[(i + 1) % len];
            PairDirectionData pairDirectionData = PairDirectionData.get(from, to);
            m_pdds.add(pairDirectionData);
        }
    }

    @Override public String toString() {
        return m_name;
    }

    @Override public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        RoundDirectedData that = (RoundDirectedData) o;
        return m_round.equals(that.m_round);
    }

    @Override public int hashCode() {
        return m_round.hashCode();
    }

    public void getPairDatas(List<PairData> pds) {
        for (PairDirectionData pdd : m_pdds) {
            PairData pairData = pdd.m_pairData;
            if (!pds.contains(pairData)) {
                pds.add(pairData);
            }

            pairData.addOrderBookListener(this);
        }
    }

    @Override public void onOrderBookUpdated(OrderBook orderBook) {
        if (!m_allLive) { // recheck allLive
            boolean allLive = true;
            for (PairDirectionData pdd : m_pdds) {
                PairData pairData = pdd.m_pairData;
                if (!pairData.m_liveOrderBook) {
                    allLive = false;
                    break;
                }
            }
            m_allLive = allLive;
        }
        if (m_allLive) {
            System.out.println("ALL LIVE for: " + this);
        }
    }
}
