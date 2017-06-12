package bi.two.exch;

import bi.two.chart.ITickData;

public class ExchPairData {
    public TopData m_topData = new TopData(0,0,0);
    public ITickData m_newestTick;
    public double m_minOrderToCreate = 0;
    public double m_commission = 0;

    public double minOrderToCreate() {
        if(m_minOrderToCreate != 0) {
            return m_minOrderToCreate;
        }
        throw new RuntimeException("no minOrderToCreate defined");
    }
}
