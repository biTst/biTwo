package bi.two.tre;

import bi.two.exch.*;
import bi.two.util.Utils;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

class RoundDirectedData {
    private static final boolean LOG_ROUND_CALC = Tre.LOG_ROUND_CALC;

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
        return Objects.equals(m_name, that.m_name);
    }

    @Override public int hashCode() {
        return Objects.hash(m_name);
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

            if (topSpread == null) {
                return; // sometimes book side can become empty
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

        for (RoundPlanType roundPlanType : RoundPlanType.values()) {
            CurrencyValue value = startValue;
            while (true) {
                double rate = mkRoundPlan(value, roundPlanType, plans);
                if (rate >= 0) {
                    break;
                }
                // need scale
                CurrencyValue from = value;
                value = new CurrencyValue(-value.m_value * rate, value.m_currency);
                if (LOG_ROUND_CALC) {
                    System.out.println("        startValue scaled from " + from + " to: " + value);
                }
            }
        }
    }

    @Nullable private double mkRoundPlan(CurrencyValue startValue, RoundPlanType roundPlanType, List<RoundPlan> plans) {
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
                System.out.println("--- " + pdd + " " + orderSide + " " + currencyFrom + " start=" + value + "; value=" + startValueValue + "; inCurrency=" + inCurrency + "; pair=" + pair
                        + "; isForwardTrade=" + isForwardTrade);
            }
            OrderBook orderBook = pd.m_orderBook;
            if (LOG_ROUND_CALC) {
                System.out.println("       orderBook: " + orderBook);
            }
            OrderBook.Spread topSpread = orderBook.getTopSpread();
            if (LOG_ROUND_CALC) {
                System.out.println("        topSpread: " + topSpread);
            }
            if (topSpread == null) {
                if (LOG_ROUND_CALC) {
                    System.out.println(" book site is empty");
                }
                return 0; // sometimes book side can become empty
            }
            double bidPrice = topSpread.m_bidEntry.m_price;
            double askPrice = topSpread.m_askEntry.m_price;
            if (LOG_ROUND_CALC) {
                System.out.println("         SELL 1 " + currencyFrom + " => " + bidPrice + " " + currencyTo + "  ||  " + askPrice + " " + currencyTo + " => BUY 1 " + currencyFrom);
            }
            Currency outCurrency = isForwardTrade ? currencyFrom : currencyTo;

            RoundNodeType roundNodeType = roundPlanType.getRoundNodeType(i);

            ExchPairData exchPairData = pd.m_exchPairData;
            double rate = roundNodeType.rate(pd, m_roundData, isForwardTrade, orderBook, startValueValue, value);
            if (rate < 0) { // need scale
                if (LOG_ROUND_CALC) {
                    System.out.println(" need scale a rate " + rate);
                }
                return rate;
            }
            if(rate == 0) {
                if (LOG_ROUND_CALC) {
                    System.out.println(" can not distribute");
                }
                return 0;
            }
            double translatedValue = isForwardTrade
                    ? startValueValue / rate
                    : startValueValue * rate;

            double fee = roundNodeType.fee(exchPairData);
            double afterFeeValue = translatedValue * (1 - fee);
            if (LOG_ROUND_CALC) {
                System.out.println("          " + roundNodeType + ": rate=" + rate
                        + "; " + Utils.format8(startValueValue) + inCurrency + " => " + Utils.format8(translatedValue) + outCurrency
                        + "; fee=" + Utils.format8(fee) + " => after fee: " + Utils.format8(afterFeeValue) + outCurrency);
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

        RoundPlan roundPlan = new RoundPlan(this, roundPlanType, roundNodes, roundRate);
        plans.add(roundPlan);
        return 1;
    }
}