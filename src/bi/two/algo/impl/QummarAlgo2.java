package bi.two.algo.impl;

import bi.two.exch.Exchange;
import bi.two.ts.ITimesSeriesData;
import bi.two.util.MapConfig;
import bi.two.util.Utils;

public class QummarAlgo2 extends BaseRibbonAlgo3 {
    private static final boolean ADJUST_TAIL = true;

    public QummarAlgo2(MapConfig algoConfig, ITimesSeriesData inTsd, Exchange exchange) {
        super(algoConfig, inTsd, exchange, ADJUST_TAIL);
    }

    @Override protected void recalc3(float lastPrice, float emasMin, float emasMax, float leadEmaValue, boolean goUp,
                                     boolean directionChanged, float ribbonSpread, float maxRibbonSpread, float ribbonSpreadTop,
                                     float ribbonSpreadBottom, float mid, float head, float tail) {

    }

    @Override public String key(boolean detailed) {
        detailed = true;
        return  ""
                + (detailed ? ",start=" : ",") + m_start
                + (detailed ? ",step=" : ",") + m_step
                + (detailed ? ",count=" : ",") + m_count
                + (detailed ? ",linRegMult=" : ",") + m_linRegMultiplier
                + (detailed ? ",collapse=" : ",") + m_collapse
                + (detailed ? "|minOrdMul=" : "|") + m_minOrderMul
                + (detailed ? "|joinTicks=" : "|") + m_joinTicks
                + (detailed ? "|turn=" : "|") + m_turnLevel
                + (detailed ? "|commiss=" : "|") + Utils.format8(m_commission)
                + ", " + m_barSize
//                + ", " + Utils.millisToYDHMSStr(m_barSize)
                ;
    }
}
