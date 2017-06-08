package bi.two.algo;

import bi.two.chart.ITimesSeriesData;
import bi.two.chart.TickData;
import bi.two.chart.TimesSeriesData;
import bi.two.exch.*;

public class Watcher extends TimesSeriesData<TickData> {
    private final BaseAlgo m_algo;
    private final Exchange m_exch;
    private final Pair m_pair;
    private final ExchPairData m_exchPairData;
    private AccountData m_initAcctData;
    private TopData m_initTopData;
    private double m_valuateToInit;
    private double m_valuateFromInit;

    public Watcher(BaseAlgo algo, Exchange exch, Pair pair) {
        super(algo);
        m_algo = algo;
        m_exch = exch;
        m_pair = pair;
        m_exchPairData = exch.getPairData(pair);
    }

    @Override public void onChanged(ITimesSeriesData ts) {
        TickData tickAdjusted = m_algo.getTickAdjusted();
        if (tickAdjusted != null) {
            if (m_initAcctData == null) { // first run
                init();
            }
        }
    }

    private void init() {
        TopData topData = m_exchPairData.m_topData;
        if (topData != null) {
            m_initTopData = new TopData(topData);
//            double bid = topData.m_bid;
//            double ask = topData.m_ask;
            double lastPrice = topData.m_last;

            Currency currencyFrom = m_pair.m_from;
            Currency currencyTo = m_pair.m_to;

            m_initAcctData = new AccountData(m_exch);
            m_initAcctData.setAvailable(currencyFrom, lastPrice);
            m_initAcctData.setAvailable(currencyTo, 1);

            m_valuateToInit = m_initAcctData.evaluateAll(currencyTo);
            m_valuateFromInit = m_initAcctData.evaluateAll(currencyFrom);


        }
    }
}
