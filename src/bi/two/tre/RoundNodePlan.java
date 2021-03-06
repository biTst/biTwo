package bi.two.tre;

import bi.two.exch.CurrencyValue;
import bi.two.exch.OrderSide;
import bi.two.exch.Pair;
import bi.two.util.Utils;

import java.util.List;

public class RoundNodePlan {
    public final PairDirectionData m_pdd;
    public final RoundNodeType m_roundNodeType;
    public final double m_rate;
    public final CurrencyValue m_startValue;
    public final CurrencyValue m_outValue;
    public final List<RoundStep> m_steps;

    public RoundNodePlan(PairDirectionData pdd, RoundNodeType roundNodeType, double rate,
                         CurrencyValue startValue, CurrencyValue outValue, List<RoundStep> steps) {
        m_pdd = pdd;
        m_roundNodeType = roundNodeType;
        m_rate = rate;
        m_startValue = startValue;
        m_outValue = outValue;
        m_steps = steps;
    }

    public void log(StringBuilder sb) {
        toString(sb);
        sb.append("\n");
        for (RoundStep step : m_steps) {
            sb.append("    ");
            step.log(sb);
            sb.append("\n");
        }
    }

    private void toString(StringBuilder sb) {
        sb.append("RoundNodePlan ");
        sb.append(m_pdd);
        sb.append(" ");
        sb.append(m_roundNodeType);
        sb.append(" ");
        sb.append(m_rate);
        sb.append(" ");
        sb.append(m_startValue);
        sb.append("->");
        sb.append(m_outValue);
    }

    @Override public String toString() {
        StringBuilder sb = new StringBuilder();
        toString(sb);
        return sb.toString();
    }

    //-------------------------------------------------------
    public static class RoundStep {
        public final Pair m_pair;
        public final OrderSide m_orderSide;
        public final double m_orderSize;
        public final double m_rate;
        public final CurrencyValue m_inValue;
        public final CurrencyValue m_outValue;

        public RoundStep(Pair pair, OrderSide orderSide, double orderSize, double rate, CurrencyValue inValue, CurrencyValue outValue) {
            m_pair = pair;
            m_orderSide = orderSide;
            m_orderSize = orderSize;
            m_rate = rate;
            m_inValue = inValue;
            m_outValue = outValue;
        }

        public void log(StringBuilder sb) {
            sb.append(m_pair);
            sb.append(": ");
            sb.append(m_orderSide);
            sb.append(" ");
            sb.append(Utils.format8(m_orderSize));
            sb.append(" ");
            sb.append(m_pair.m_from);
            sb.append(" @ ");
            sb.append(Utils.format8(m_rate));
            sb.append(" ");
            sb.append(m_inValue.format8());
            sb.append("->");
            sb.append(m_outValue.format8());
        }

        @Override public String toString() {
            StringBuilder sb = new StringBuilder();
            log(sb);
            return sb.toString();
        }
    }
}
