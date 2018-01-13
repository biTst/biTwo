package bi.two.tre;

public enum RoundPlanType {
    MKT_MKT_MKT {
        @Override public int getPriority() { return 5; }
        @Override public String getPrefix() { return "mkt_mkt_mkt"; }
        @Override public RoundNodeType getRoundNodeType(int indx) { return RoundNodeType.MKT; }
    },
    TCH_MKT_MKT {
        @Override public int getPriority() { return 4; }
        @Override public String getPrefix() { return "tch_mkt_mkt"; }
        @Override public RoundNodeType getRoundNodeType(int indx) { return (indx == 0) ? RoundNodeType.TCH : RoundNodeType.MKT; }
    },
    TCH_TCH_MKT {
        @Override public int getPriority() { return 3; }
        @Override public String getPrefix() { return "tch_tch_mkt"; }
        @Override public RoundNodeType getRoundNodeType(int indx) { return (indx < 2) ? RoundNodeType.TCH : RoundNodeType.MKT; }
    },
    MKT_TCH_TCH {
        @Override public int getPriority() { return 2; }
        @Override public String getPrefix() { return "mkt_tch_tch"; }
        @Override public RoundNodeType getRoundNodeType(int indx) { return (indx == 0) ? RoundNodeType.MKT : RoundNodeType.TCH; }
    },
//    LMT_MKT_MKT {
//        @Override public int getPriority() { return 1; }
//        @Override public String getPrefix() { return "lmt_mkt_mkt"; }
//        @Override public RoundNodeType getRoundNodeType(int indx) { return (indx == 0) ? RoundNodeType.LMT : RoundNodeType.MKT; }
//    },
    ;

    public abstract int getPriority();
    public abstract String getPrefix();
    public abstract RoundNodeType getRoundNodeType(int indx);
    @Override public String toString() { return getPrefix(); }
}
