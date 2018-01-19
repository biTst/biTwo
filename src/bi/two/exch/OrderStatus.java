package bi.two.exch;

public enum OrderStatus {
    NEW,
    SUBMITTED { // order was sent to exh
        @Override public boolean isActive() {
            return true;
        }
    },
    PARTIALLY_FILLED {
        @Override public boolean partialOrFilled() { return true; }
        @Override public boolean isActive() {
            return true;
        }
    },
    FILLED {
        @Override public boolean partialOrFilled() { return true; }
    },
    REJECTED,
    CANCELING,
    CANCELLED,
    ERROR;

    public boolean isActive() {
        return false;
    }
    public boolean partialOrFilled() { return false; }
}
