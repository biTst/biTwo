package bi.two.tre;

import bi.two.exch.*;
import bi.two.util.Utils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

class RoundDirectedData {
    private static final boolean LOG_ROUND_CALC = false;

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

    public void onUpdated(Exchange exchange, List<RoundPlan> plans) {
        if (LOG_ROUND_CALC) {
            System.out.println("onUpdated() on " + this + "; exchange=" + exchange);
        }
        PairDirectionData startPdd = m_pdds.get(0);
        PairData startPairData = startPdd.m_pairData;
        PairDirection startPdPairDirection = startPdd.m_pairDirection;
        Pair startPair = startPdPairDirection.m_pair;
        if (LOG_ROUND_CALC) {
            System.out.println(" startPdd=" + startPdd + "; startPairData=" + startPairData + "; startPairDirection=" + startPdPairDirection + "; startPair=" + startPair);
        }
        Currency startCurrency = startPdPairDirection.getSourceCurrency();
        CurrencyValue startValue = m_roundData.m_minPassThruOrdersSize.get(startPair);
        startValue = new CurrencyValue(startValue.m_value * 2, startValue.m_currency); // simulate start with double min
        Currency startValueCurrency = startValue.m_currency;
        if (LOG_ROUND_CALC) {
            System.out.println("    startCurrency=" + startCurrency + "; startValue(minPassThru)=" + startValue + "; startValueCurrency=" + startValueCurrency);
        }
        if (startCurrency != startValueCurrency) {
            double startValueValue = startValue.m_value;
            if (LOG_ROUND_CALC) {
                System.out.println("       need conversion: value=" + Utils.format8(startValueValue) + "; " + startValueCurrency + " =>" + startCurrency);
            }

            OrderBook orderBook = startPairData.m_orderBook;
            if (LOG_ROUND_CALC) {
                System.out.println("        orderBook" + orderBook);
            }

            OrderBook.Spread topSpread = orderBook.getTopSpread();
            if (LOG_ROUND_CALC) {
                System.out.println("         topSpread=" + topSpread);
            }

            double topPrice = orderBook.m_bids.get(0).m_price;
            if (LOG_ROUND_CALC) {
                System.out.println("          topPrice=" + topPrice + " -> rate=" + Utils.format8(1/topPrice));
            }

            double startValueTranslated = startValueValue * topPrice;
            if (LOG_ROUND_CALC) {
                System.out.println("           startValueTranslated=" + Utils.format8(startValueTranslated));
            }

            startValue = new CurrencyValue(startValueTranslated, startCurrency);
            if (LOG_ROUND_CALC) {
                System.out.println("            startValue'=" + startValue);
            }
        }

        for (RoundPlan.RoundPlanType roundPlanType : RoundPlan.RoundPlanType.values()) {
            plans.add(mkRoundPlan(startValue, roundPlanType));
        }
    }

    @NotNull private RoundPlan mkRoundPlan(CurrencyValue startValue, RoundPlan.RoundPlanType roundPlanType) {
        if (LOG_ROUND_CALC) {
            System.out.println("mkRoundPlan: " + roundPlanType + "; startValue=" + startValue);
        }
        List<RoundPlan.RoundNode> roundNodes = new ArrayList<>();

        CurrencyValue value = startValue;
        int size = m_pdds.size();
        for (int i = 0; i < size; i++) {
            PairDirectionData pdd = m_pdds.get(i);
            double startValueValue = value.m_value;
            Currency inCurrency = value.m_currency;
            PairData pd = pdd.m_pairData;
            Pair pair = pd.m_pair;
            Currency currencyFrom = pair.m_from;
            Currency currencyTo = pair.m_to;
            boolean isForwardTrade = (inCurrency == currencyTo);
            OrderSide orderSide = OrderSide.get(isForwardTrade);
            if (LOG_ROUND_CALC) {
                System.out.println("--- start=" + value + "; value=" + startValueValue + "; inCurrency=" + inCurrency + "; pdd:" + pdd + "; pair=" + pair + "; pd:" + pd
                        + "; isForwardTrade=" + isForwardTrade + "; orderSide="+orderSide);
            }
            OrderBook orderBook = pd.m_orderBook;
            if (LOG_ROUND_CALC) {
                System.out.println("       orderBook:" + orderBook);
            }
            OrderBook.Spread topSpread = orderBook.getTopSpread();
            if (LOG_ROUND_CALC) {
                System.out.println("        topSpread=" + topSpread);
            }
            double bidPrice = topSpread.m_bidEntry.m_price;
            double askPrice = topSpread.m_askEntry.m_price;
            if (LOG_ROUND_CALC) {
                System.out.println("         1 " + currencyFrom + " => " + bidPrice + " " + currencyTo + "  ||  " + askPrice + " " + currencyTo + " => 1 " + currencyFrom);
            }
            ExchPairData exchPairData = pd.m_exchPairData;
            double translatedValue = isForwardTrade
                    ? startValueValue / askPrice
                    : startValueValue * bidPrice;
            Currency outCurrency = isForwardTrade ? currencyFrom : currencyTo;

            RoundPlan.RoundNode.RoundNodeType roundNodeType = roundPlanType.getRoundNodeType(i);

            // todo: take into account size of book entries
            double rate = roundNodeType.rate(exchPairData, isForwardTrade, bidPrice, askPrice);

            double fee = roundNodeType.fee(exchPairData);
            double afterFeeValue = translatedValue * (1 - fee);
            if (LOG_ROUND_CALC) {
                System.out.println("          " + roundNodeType + ": rate=" + rate + "; translatedValue=" + Utils.format8(translatedValue) + "; fee=" + fee + " => result=" + Utils.format8(afterFeeValue));
            }

            CurrencyValue outValue = new CurrencyValue(afterFeeValue, outCurrency);
            if (LOG_ROUND_CALC) {
                System.out.println("          " + value + " => " + outValue);
            }

            RoundPlan.RoundNode roundNode = new RoundPlan.RoundNode(pdd, roundNodeType);
            roundNodes.add(roundNode);

            value = outValue;
        }
        double roundRate = value.m_value / startValue.m_value;
        if (LOG_ROUND_CALC) {
            System.out.println(" " + this + "; rate=" + Utils.format8(roundRate));
        }

        return new RoundPlan(this, roundPlanType, roundNodes, roundRate);
    }


}
