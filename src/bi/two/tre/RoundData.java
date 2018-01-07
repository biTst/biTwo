package bi.two.tre;

import bi.two.exch.Currency;
import bi.two.exch.Exchange;
import bi.two.exch.OrderBook;
import bi.two.exch.Pair;

import java.util.ArrayList;
import java.util.List;

class RoundData implements OrderBook.IOrderBookListener {
    public final Round m_round;
    private final Exchange m_exchange;
    public final List<RoundDirectedData> m_directedRounds = new ArrayList<>();
    public final List<PairData> m_pds = new ArrayList<>();
    public boolean m_allLive;

    public RoundData(Currency[] currencies, Exchange exchange) {
        m_round = Round.get(currencies[0], currencies[1], currencies[2]);
        m_exchange = exchange;

        int length = currencies.length;
        for(int i = 0; i < length; i++) {
            add(i, true);
            add(i, false);

            int j = (i + 1) % length;
            Pair pair = Pair.get(currencies[i], currencies[j]);
            PairData pairData = PairData.get(pair);
            m_pds.add(pairData);
            pairData.addOrderBookListener(this);
        }
    }

    @Override public String toString() {
        return m_round.toString();
    }

    private void add(int startIndex, boolean forward) {
        Currency[] roundCurrencies = m_round.m_currencies;
        int length = roundCurrencies.length;
        Currency[] currencies = new Currency[length];
        int index = startIndex;
        for (int i = 0; i < length; i++) {
            Currency currency = roundCurrencies[index];
            currencies[i] = currency;
            index = (index + (forward ? 1 : -1) + length) % length;
        }

        RoundDirectedData roundDirectedData = new RoundDirectedData(currencies);
        m_directedRounds.add(roundDirectedData);
    }

    public void getPairDatas(List<PairData> pds) {
        for (RoundDirectedData directedRound : m_directedRounds) {
            directedRound.getPairDatas(pds);
        }
    }

    @Override public void onOrderBookUpdated(OrderBook orderBook) {
        if (!m_allLive) { // recheck allLive
            boolean allLive = true;
            for (PairData pairData : m_pds) {
                if (!pairData.m_orderBookIsLive) {
                    allLive = false;
                    break;
                }
            }
            m_allLive = allLive;
            if (m_allLive) {
                System.out.println("ALL becomes LIVE for: " + this);
                onBecomesLive();
            }
        }
        if (m_allLive) {
            System.out.println("ALL LIVE for: " + this);
        }
    }

    private void onBecomesLive() {
//        m_exchange
    }
}
