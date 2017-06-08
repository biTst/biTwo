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
                Double rate = rate(currency, baseCurrency);
                if(rate != null) { // if can convert
                    value = value / rate;
                    allValue += value;
                }
            }

        }
        return allValue;
    }

    public Double rate(Currency from, Currency to) {
        Double rate;
        if (from == to) {
            rate = 1d;
        } else {
            boolean support = m_exch.supportPair(from, to);
            if(support) {
                rate = rate(from, to);
            } else {
                Currency baseCurrency = m_exch.baseCurrency();
                Double rate1 = rate(from, baseCurrency);
                Double rate2 = rate(baseCurrency, to);
                if ((rate1 != null) && (rate2 != null)) {
                    rate = rate1 * rate2;
                } else {
                    rate = null;
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
