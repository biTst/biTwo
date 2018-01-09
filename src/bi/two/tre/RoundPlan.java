package bi.two.tre;

import bi.two.exch.ExchPairData;

import java.util.Comparator;
import java.util.List;

public class RoundPlan {
    public static final Comparator<RoundPlan> BY_RATE_COMPARATOR = new Comparator<RoundPlan>() {
        @Override public int compare(RoundPlan o1, RoundPlan o2) {
            return Double.compare(o2.m_roundRate, o1.m_roundRate); // descending
        }
    };

    public final RoundDirectedData m_rdd;
    public final RoundPlanType m_roundPlanType;
    public final List<RoundNode> m_roundNodes;
    public final double m_roundRate;

    public RoundPlan(RoundDirectedData rdd, RoundPlanType roundPlanType, List<RoundNode> roundNodes, double roundRate) {
        m_rdd = rdd;
        m_roundPlanType = roundPlanType;
        m_roundNodes = roundNodes;
        m_roundRate = roundRate;
    }

    // -----------------------------------------------------------------------------------------
    public enum RoundPlanType {
        ALL_MKT {
            @Override public String getPrefix() { return "mkt_mkt"; }
            @Override public RoundNode.RoundNodeType getRoundNodeType(int indx) { return RoundNode.RoundNodeType.MKT; }
        },
        LMT_MKT {
            @Override public String getPrefix() { return "lmt_mkt"; }
            @Override public RoundNode.RoundNodeType getRoundNodeType(int indx) { return indx == 0 ? RoundNode.RoundNodeType.LMT : RoundNode.RoundNodeType.MKT; }
        };

        public abstract String getPrefix();

        @Override public String toString() { return getPrefix(); }
        public abstract RoundNode.RoundNodeType getRoundNodeType(int indx);
    }


    // -----------------------------------------------------------------------------------------
    public static class RoundNode {
        public final PairDirectionData m_pdd;
        public final RoundNodeType m_roundNodeType;

        public RoundNode(PairDirectionData pdd, RoundNodeType roundNodeType) {
            m_pdd = pdd;
            m_roundNodeType = roundNodeType;
        }

        // -----------------------------------------------------------------------------------------
        public enum RoundNodeType {
            MKT {
                @Override public String getPrefix() { return "mkt"; }
                @Override public double fee(ExchPairData exchPairData) { return exchPairData.m_commission; }
                @Override public double rate(ExchPairData exchPairData, boolean isForwardTrade, double bidPrice, double askPrice) {
                    return isForwardTrade
                            ? askPrice // byu
                            : bidPrice; // sell
                }
            },
            LMT {
                @Override public String getPrefix() { return "lmt"; }
                @Override public double fee(ExchPairData exchPairData) { return exchPairData.m_makerCommission; }
                @Override public double rate(ExchPairData exchPairData, boolean isForwardTrade, double bidPrice, double askPrice) {
                    double step = exchPairData.m_minPriceStep;
                    return isForwardTrade
                            ? bidPrice + step
                            : askPrice - step;
                }
            };

            public abstract String getPrefix();
            public abstract double fee(ExchPairData exchPairData);
            public abstract double rate(ExchPairData exchPairData, boolean isForwardTrade, double bidPrice, double askPrice);

            @Override public String toString() { return getPrefix(); }
        }
    }
}
