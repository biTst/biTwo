package bi.two.exch;

public enum OrderSide {
    BUY {
        @Override public OrderSide opposite() { return SELL; }
    },
    SELL {
        @Override public OrderSide opposite() { return BUY; }
    };

    public static OrderSide get(boolean isBuy) {
        return isBuy ? BUY : SELL;
    }

    public abstract OrderSide opposite();
}
