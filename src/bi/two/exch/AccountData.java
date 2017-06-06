package bi.two.exch;

import java.util.HashMap;

public class AccountData {
    private final Exchange m_exch;
    private final HashMap<Currency, Double> m_funds = new HashMap<Currency,Double>();

    public AccountData(Exchange exch) {
        m_exch = exch;
    }

    public void setAvailable(Currency currency, double value) { m_funds.put(currency, round(value)); }

    private Double round(double value) {
        return Math.round(value * 1000000000d) / 1000000000d;
    }
}
