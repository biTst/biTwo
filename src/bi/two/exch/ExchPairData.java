package bi.two.exch;

import bi.two.chart.ITickData;

public class ExchPairData {
    public TopData m_topData = new TopData(0,0,0);
    public ITickData m_newestTick;
    public double minOrderToCreate = 0;

    public double minOrderToCreate() {
        if(minOrderToCreate != 0) {
            return minOrderToCreate;
        }
        throw new RuntimeException("no minOrderToCreate defined");
    }
}
