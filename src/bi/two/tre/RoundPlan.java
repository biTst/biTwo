package bi.two.tre;

import bi.two.util.Utils;

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
    public final List<RoundNodePlan> m_roundNodePlans;
    public final double m_roundRate;
    public final long m_timestamp;
    private final CurrencyValue m_startValue;
    private final CurrencyValue m_outValue;
    public long m_liveTime;
    public RoundPlan m_nextPlan; // plan for next tick if plan is changed

    public RoundPlan(RoundDirectedData rdd, RoundPlanType roundPlanType, List<RoundNodePlan> roundNodePlans,
                     CurrencyValue startValue, CurrencyValue outValue, double roundRate) {
        m_rdd = rdd;
        m_roundPlanType = roundPlanType;
        m_roundNodePlans = roundNodePlans;
        m_startValue = startValue;
        m_outValue = outValue;
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

    public String logFull() {
        StringBuilder sb = new StringBuilder();
        sb.append("RoundPlan ");
        sb.append(m_rdd);
        sb.append(" ");
        sb.append(m_roundPlanType);
        sb.append(" ");
        sb.append(m_startValue);
        sb.append("->");
        sb.append(m_outValue);
        sb.append(" ");
        sb.append(m_roundRate);
        sb.append("\n");
        for (RoundNodePlan roundNodePlan : m_roundNodePlans) {
            roundNodePlan.log(sb);
        }
        return sb.toString();
    }

    public void minLog(StringBuilder sb) {
        sb.append(m_rdd.m_shortName);
        sb.append(":");
        sb.append(m_roundPlanType.getShortName());
        sb.append(" ");
        sb.append(Utils.format6(m_roundRate));
    }

    public void setNextPlan(RoundPlan nextPlan) {
        if (nextPlan.m_timestamp < m_timestamp) {
            throw new RuntimeException("error plan->nextPlan timestamp mismatch");
        }
        m_nextPlan = nextPlan;
    }
}
