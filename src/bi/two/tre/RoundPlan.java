package bi.two.tre;

import java.util.Comparator;
import java.util.List;

// -----------------------------------------------------------------------------------------
public class RoundPlan {
    public static final Comparator<RoundPlan> BY_RATE_COMPARATOR = new Comparator<RoundPlan>() {
        @Override public int compare(RoundPlan o1, RoundPlan o2) {
            return Double.compare(o2.m_roundRate, o1.m_roundRate); // descending
        }
    };

    public final RoundDirectedData m_rdd;
    public final RoundDirectedData.RoundType m_roundType;
    public final List<RoundNode> m_roundNodes;
    public final double m_roundRate;

    public RoundPlan(RoundDirectedData rdd, RoundDirectedData.RoundType roundType, List<RoundNode> roundNodes, double roundRate) {
        m_rdd = rdd;
        m_roundType = roundType;
        m_roundNodes = roundNodes;
        m_roundRate = roundRate;
    }


    // -----------------------------------------------------------------------------------------
    public static class RoundNode {
        public final PairDirectionData m_pdd;

        public RoundNode(PairDirectionData pdd) {
            m_pdd = pdd;
        }
    }
}
