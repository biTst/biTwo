package bi.two.tre;

import bi.two.exch.*;
import bi.two.util.Utils;

import java.util.ArrayList;
import java.util.List;

class RoundDirectedData {
    public final RoundData m_roundData;
    public final Currency[] m_currencies;
    public final Round m_round;
    public final String m_name;
    public final List<PairDirectionData> m_pdds = new ArrayList<>();

    public RoundDirectedData(RoundData roundData, Currency[] c) {
        m_roundData = roundData;
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
        if ((o == null) || (getClass() != o.getClass())) {
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
        }
    }

    public void onUpdated(Exchange exchange) {
        System.out.println("onUpdated() on " + this + "; exchange=" + exchange);
        PairDirectionData startPdd = m_pdds.get(0);
        System.out.println(" startPdd=" + startPdd);
        PairData startPairData = startPdd.m_pairData;
        System.out.println("  startPairData=" + startPairData);
        PairDirection startPdPairDirection = startPdd.m_pairDirection;
        System.out.println("  startPairDirection=" + startPdPairDirection);
        Pair startPair = startPdPairDirection.m_pair;
        System.out.println("   startPair=" + startPair);
        Currency startCurrency = startPdPairDirection.getSourceCurrency();
        System.out.println("    startCurrency=" + startCurrency);
        CurrencyValue startValue = m_roundData.m_minPassThruOrdersSize.get(startPair);
        System.out.println("     startValue(minPassThru)=" + startValue);
        Currency startValueCurrency = startValue.m_currency;
        System.out.println("      startValueCurrency=" + startValueCurrency);
        if (startCurrency != startValueCurrency) {
            double startValueValue = startValue.m_value;
            System.out.println("       need conversion: value=" + Utils.format8(startValueValue) + "; " + startValueCurrency + " =>" + startCurrency);

            OrderBook orderBook = startPairData.m_orderBook;
            System.out.println("        orderBook" + orderBook);

            OrderBook.Spread topSpread = orderBook.getTopSpread();
            System.out.println("         topSpread=" + topSpread);

            double topPrice = orderBook.m_bids.get(0).m_price;
            System.out.println("          topPrice=" + topPrice + " -> rate=" + Utils.format8(1/topPrice));

            double startValueTranslated = startValueValue * topPrice;
            System.out.println("           startValueTranslated=" + Utils.format8(startValueTranslated));

            startValue = new CurrencyValue(startValueTranslated, startCurrency);
            System.out.println("            startValue'=" + startValue);
        }

//        for (PairDirectionData pdd : m_pdds) {
//        }
    }
}
