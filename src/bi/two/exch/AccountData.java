package bi.two.exch;

import java.util.HashMap;

public class AccountData {
    private final Exchange m_exch;
    private final HashMap<Currency, Double> m_funds = new HashMap<Currency,Double>();
    private final HashMap<Currency, Double> m_allocatedFunds = new HashMap<Currency,Double>();

    public AccountData(Exchange exch) {
        m_exch = exch;
    }

    public void setAvailable(Currency currency, double value) { m_funds.put(currency, round(value)); }

    private Double round(double value) {
        return Math.round(value * 1000000000d) / 1000000000d;
    }

    public double evaluateAll(Currency baseCurrency) {
        double allValue = 0;
        for (Currency currency : m_funds.keySet()) {
            double value = getAllValue(currency);
            if (value > 0.000000001) {
                double rate = rate(currency, baseCurrency);
                if(rate != 0) { // if can convert
                    value = value / rate;
                    allValue += value;
                }
            }

        }
        return allValue;
    }

    /** @return 0 if no convert route */
    public double rate(Currency from, Currency to) {
        double rate;
        if (from == to) {
            rate = 1d;
        } else {
            Pair pair = m_exch.getPair(from, to);
            if (pair != null) {
                ExchPairData pairData = m_exch.getPairData(pair);
                TopData topData = pairData.m_topData;
                boolean forward = (pair.m_from == from);
                rate = forward ? topData.m_bid : topData.m_ask;
                if (!forward) {
                    rate = 1 / rate;
                }
            } else { // no direct pair support - try via base currency
                Currency baseCurrency = m_exch.m_baseCurrency;
                double rate1 = rate(from, baseCurrency);
                double rate2 = rate(baseCurrency, to);
                if ((rate1 != 0) && (rate2 != 0)) {
                    rate = rate1 * rate2;
                } else {
                    rate = 0; // not convert route
                }
            }
        }
        return rate;
    }

    public double getAllValue(Currency currency) {
        double allValue = 0;
        Double available = m_funds.get(currency);
        if (available != null) {
            allValue += available;
        }
        Double allocated = m_allocatedFunds.get(currency);
        if (allocated != null) {
            allValue += allocated;
        }
        return allValue;
    }

}
