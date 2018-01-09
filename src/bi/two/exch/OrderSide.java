package bi.two.exch;

public enum OrderSide {
    BUY,
    SELL;

    public static OrderSide get(boolean isBuy) {
        return isBuy ? BUY : SELL;
    }
}
