package bi.two.tre;

import bi.two.exch.Currency;
import bi.two.util.Utils;

public class CurrencyValue {
    public final double m_value;
    public final Currency m_currency;

    public CurrencyValue(double value, Currency currency) {
        m_value = value;
        m_currency = currency;
    }

    @Override public String toString() {
        return "<" + m_value + m_currency.m_name + ">";
    }

    public String format8() { return "<" + Utils.format8(m_value) + m_currency.m_name + ">"; }
}
