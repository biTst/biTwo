package bi.two.exch;

public class ExchPairData {
    public double m_minOrderToCreate = 0;
    public double m_commission = 0;
    public double m_makerCommission = 0;
    public double m_initBalance;


    public ExchPairData() {
    }

    public double minOrderToCreate() {
        if (m_minOrderToCreate != 0) {
            return m_minOrderToCreate;
        }
        throw new RuntimeException("no minOrderToCreate defined");
    }
}
