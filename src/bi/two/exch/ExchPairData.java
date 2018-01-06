package bi.two.exch;

import bi.two.tre.CurrencyValue;

public class ExchPairData {
    public CurrencyValue m_minOrderToCreate = null;
    public CurrencyValue m_minOrderStep = null;
    public double m_minPriceStep = 0;
    public double m_commission = 0;
    public double m_makerCommission = 0;
    public double m_initBalance;


    public ExchPairData() {
    }

    public CurrencyValue getMinOrderToCreate() {
        if (m_minOrderToCreate != null) {
            return m_minOrderToCreate;
        }
        throw new RuntimeException("no minOrderToCreate defined");
    }
}
