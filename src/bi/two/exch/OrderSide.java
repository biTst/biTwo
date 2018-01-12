package bi.two.exch;

public enum OrderSide {
    BUY {
        @Override public OrderSide opposite() { return SELL; }
        @Override public boolean isBuy() { return true; }
        @Override public boolean isSell() { return false; }
    },
    SELL {
        @Override public OrderSide opposite() { return BUY; }
        @Override public boolean isBuy() { return false; }
        @Override public boolean isSell() { return true; }
    };

    public static OrderSide get(boolean isBuy) {
        return isBuy ? BUY : SELL;
    }

    public abstract OrderSide opposite();
    public abstract boolean isBuy();
    public abstract boolean isSell();
}
