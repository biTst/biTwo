package bi.two.tre;

public enum RoundPlanType {
    MKT_MKT_MKT {
        @Override public int getPriority() { return 500; }
        @Override public String getPrefix() { return "mkt_mkt_mkt"; }
        @Override public String getShortName() { return "mmm"; }
        @Override public RoundNodeType getRoundNodeType(int indx) { return RoundNodeType.MKT; }
    },
    TCH_MKT_MKT {
        @Override public int getPriority() { return 400; }
        @Override public String getPrefix() { return "tch_mkt_mkt"; }
        @Override public String getShortName() { return "tmm"; }
        @Override public RoundNodeType getRoundNodeType(int indx) { return (indx == 0) ? RoundNodeType.TCH : RoundNodeType.MKT; }
    },
    MKT_TCH_TCH {
        @Override public int getPriority() { return 300; }
        @Override public String getPrefix() { return "mkt_tch_tch"; }
        @Override public String getShortName() { return "mtt"; }
        @Override public RoundNodeType getRoundNodeType(int indx) { return (indx == 0) ? RoundNodeType.MKT : RoundNodeType.TCH; }
    },
    TCH_TCH_TCH {
        @Override public int getPriority() { return 200; }
        @Override public String getPrefix() { return "tch_tch_tch"; }
        @Override public String getShortName() { return "ttt"; }
        @Override public RoundNodeType getRoundNodeType(int indx) { return RoundNodeType.TCH; }
    },
    LMT_MKT_MKT {
        @Override public int getPriority() { return 10; }
        @Override public String getPrefix() { return "lmt_mkt_mkt"; }
        @Override public String getShortName() { return "lmm"; }
        @Override public RoundNodeType getRoundNodeType(int indx) { return (indx == 0) ? RoundNodeType.LMT : RoundNodeType.MKT; }
    },
    LMT_LMT_MKT {
        @Override public int getPriority() { return 3; }
        @Override public String getPrefix() { return "lmt_lmt_mkt"; }
        @Override public String getShortName() { return "llm"; }
        @Override public RoundNodeType getRoundNodeType(int indx) { return (indx < 2) ? RoundNodeType.LMT : RoundNodeType.MKT; }
    },
    LMT_LMT_LMT {
        @Override public int getPriority() { return 1; }
        @Override public String getPrefix() { return "lmt_lmt_lmt"; }
        @Override public String getShortName() { return "lll"; }
        @Override public RoundNodeType getRoundNodeType(int indx) { return RoundNodeType.LMT; }
    },
    ;

    public abstract int getPriority();
    public abstract String getPrefix();
    public abstract String getShortName();
    public abstract RoundNodeType getRoundNodeType(int indx);
    @Override public String toString() { return getPrefix(); }
}
