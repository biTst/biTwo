package bi.two.tre;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class RoundPlan {
    public static final Comparator<RoundPlan> BY_RATE_COMPARATOR = new Comparator<RoundPlan>() {
        @Override public int compare(RoundPlan o1, RoundPlan o2) {
            return Double.compare(o2.m_roundRate, o1.m_roundRate); // descending
        }
    };

    public static final Comparator<RoundPlan> BY_RATE_PRIO_COMPARATOR = new Comparator<RoundPlan>() {
        @Override public int compare(RoundPlan o1, RoundPlan o2) {
            return Double.compare((o2.m_roundRate - 1) * o2.m_roundPlanType.getPriority(), (o1.m_roundRate - 1) * o1.m_roundPlanType.getPriority()); // descending
        }
    };

    public final RoundDirectedData m_rdd;
    public final RoundPlanType m_roundPlanType;
    public final List<RoundNode> m_roundNodes;
    public final double m_roundRate;
    public final long m_timestamp;
    public long m_liveTime;

    public RoundPlan(RoundDirectedData rdd, RoundPlanType roundPlanType, List<RoundNode> roundNodes, double roundRate) {
        m_rdd = rdd;
        m_roundPlanType = roundPlanType;
        m_roundNodes = roundNodes;
        m_roundRate = roundRate;
        m_timestamp = System.currentTimeMillis();
    }

    @Override public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if ((o == null) || (getClass() != o.getClass())) {
            return false;
        }
        RoundPlan roundPlan = (RoundPlan) o;
        return Objects.equals(m_rdd, roundPlan.m_rdd) &&
                m_roundPlanType == roundPlan.m_roundPlanType;
    }

    @Override public int hashCode() {
        return Objects.hash(m_rdd, m_roundPlanType);
    }


    // -----------------------------------------------------------------------------------------
    public static class RoundNode {
        public final PairDirectionData m_pdd;
        public final RoundNodeType m_roundNodeType;

        public RoundNode(PairDirectionData pdd, RoundNodeType roundNodeType) {
            m_pdd = pdd;
            m_roundNodeType = roundNodeType;
        }

    }
}
