package bi.two.tre;

import bi.two.exch.*;
import bi.two.util.Log;
import bi.two.util.Utils;

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

    private static void log(String s) { Log.log(s); }
    private static void err(String s, Throwable t) { Log.err(s, t); }

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
            log("onUpdated() on " + this + "; exchange=" + exchange);
        }
        PairDirectionData startPdd = m_pdds.get(0);
        PairData startPairData = startPdd.m_pairData;
        PairDirection startPdPairDirection = startPdd.m_pairDirection;
        Pair startPair = startPdPairDirection.m_pair;
        if (LOG_ROUND_CALC) {
            log(" startPdd=" + startPdd + "; startPairData=" + startPairData + "; startPairDirection=" + startPdPairDirection + "; startPair=" + startPair);
        }
        Currency startCurrency = startPdPairDirection.getSourceCurrency();
        CurrencyValue startValue = m_roundData.m_minPassThruOrdersSize.get(startPair);
        startValue = new CurrencyValue(startValue.m_value * 2, startValue.m_currency); // simulate start with double min
        Currency startValueCurrency = startValue.m_currency;
        if (LOG_ROUND_CALC) {
            log("    startCurrency=" + startCurrency + "; startValue(minPassThru)=" + startValue + "; startValueCurrency=" + startValueCurrency);
        }
        if (startCurrency != startValueCurrency) {
            double startValueValue = startValue.m_value;
            if (LOG_ROUND_CALC) {
                log("       need conversion: value=" + Utils.format8(startValueValue) + "; " + startValueCurrency + " =>" + startCurrency);
            }

            OrderBook orderBook = startPairData.m_orderBook;
            if (LOG_ROUND_CALC) {
                log("        orderBook" + orderBook);
            }

            OrderBook.Spread topSpread = orderBook.getTopSpread();
            if (LOG_ROUND_CALC) {
                log("         topSpread=" + topSpread);
            }

            if (topSpread == null) {
                return; // sometimes book side can become empty
            }

            double topMidPrice = orderBook.m_bids.get(0).m_price;
            if (LOG_ROUND_CALC) {
                log("          topMidPrice=" + topMidPrice + " -> rate=" + Utils.format8(1/topMidPrice));
            }

            double startValueTranslated = startValueValue * topMidPrice;
            if (LOG_ROUND_CALC) {
                log("           startValueTranslated=" + Utils.format8(startValueTranslated));
            }

            startValue = new CurrencyValue(startValueTranslated, startCurrency);
            if (LOG_ROUND_CALC) {
                log("            startValue'=" + startValue);
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
                    log("        startValue scaled from " + from + " to: " + value);
                }
            }
        }
    }

    private double mkRoundPlan(CurrencyValue startValue, RoundPlanType roundPlanType, List<RoundPlan> plans) {
        if (LOG_ROUND_CALC) {
            log("mkRoundPlan: " + roundPlanType + "; startValue=" + startValue);
        }
        List<RoundNodePlan> roundNodePlans = new ArrayList<>();

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
            OrderSide orderSide = OrderSide.get(inCurrency == currencyTo);
            if (LOG_ROUND_CALC) {
                log("--- " + pdd + " " + orderSide + " " + currencyFrom + " start=" + value + "; value=" + startValueValue
                        + "; inCurrency=" + inCurrency + "; pair=" + pair);
            }
            OrderBook orderBook = pd.m_orderBook;
            if (LOG_ROUND_CALC) {
                log("       orderBook: " + orderBook);
            }
            OrderBook.Spread topSpread = orderBook.getTopSpread();
            if (LOG_ROUND_CALC) {
                log("        topSpread: " + topSpread);
            }
            if (topSpread == null) {
                if (LOG_ROUND_CALC) {
                    log(" book site is empty");
                }
                return 0; // sometimes book side can become empty
            }
            double bidPrice = topSpread.m_bidEntry.m_price;
            double askPrice = topSpread.m_askEntry.m_price;
            if (LOG_ROUND_CALC) {
                log("         SELL 1 " + currencyFrom + " => " + bidPrice + " " + currencyTo + "  ||  " + askPrice + " " + currencyTo + " => BUY 1 " + currencyFrom);
            }

            RoundNodeType roundNodeType = roundPlanType.getRoundNodeType(i);
            List<RoundNodePlan.RoundStep> steps = new ArrayList<>();
            double rate = roundNodeType.distribute(pd, m_roundData, orderSide, orderBook, value, steps);
            if (rate < 0) { // need scale
                if (LOG_ROUND_CALC) {
                    log(" need scale a rate " + rate);
                }
                return rate;
            }
            if (rate == 0) {
                if (LOG_ROUND_CALC) {
                    log(" can not distribute");
                }
                return 0;
            }
            boolean isBuy = orderSide.isBuy();
            double translatedValue = isBuy ? startValueValue / rate : startValueValue * rate;

            Currency outCurrency = isBuy ? currencyFrom : currencyTo;
            double fee = roundNodeType.fee(pd.m_exchPairData);
            double afterFeeValue = translatedValue * (1 - fee);
            if (LOG_ROUND_CALC) {
                log("          " + roundNodeType + ": rate=" + rate
                        + "; " + Utils.format8(startValueValue) + inCurrency + " => " + Utils.format8(translatedValue) + outCurrency
                        + "; fee=" + Utils.format8(fee) + " => after fee: " + Utils.format8(afterFeeValue) + outCurrency);
            }

            CurrencyValue outValue = new CurrencyValue(afterFeeValue, outCurrency);
            if (LOG_ROUND_CALC) {
                log("          " + value + " => " + outValue);
            }

            RoundNodePlan roundNodePlan = new RoundNodePlan(pdd, roundNodeType, rate, value, outValue, steps);
            roundNodePlans.add(roundNodePlan);

            value = outValue;
        }
        double roundRate = value.m_value / startValue.m_value;
        if (LOG_ROUND_CALC) {
            log(" " + this + "; rate=" + Utils.format8(roundRate));
        }

        RoundPlan roundPlan = new RoundPlan(this, roundPlanType, roundNodePlans, startValue, value, roundRate);
        plans.add(roundPlan);

        if (LOG_ROUND_CALC) {
            System.out.print(roundPlan.log());
        }

        return 1;
    }
}
