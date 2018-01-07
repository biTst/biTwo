package bi.two.tre;

import bi.two.exch.Currency;

import java.util.ArrayList;
import java.util.List;

class RoundData {
    public final Round m_round;
    private final List<RoundDirectedData> m_directedRounds = new ArrayList<>();

    public RoundData(Currency[] currencies) {
        m_round = Round.get(currencies[0], currencies[1], currencies[2]);

        int length = currencies.length;
        for(int i = 0; i < length; i++) {
            add(i, true);
            add(i, false);
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
}
