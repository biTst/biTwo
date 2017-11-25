package bi.two.exch;

import java.util.Arrays;

public enum Currency {
    USD("usd"),
    EUR("eur"),
    BTC("btc"),
    BCH("bch"),
    CNH("cnh"),
    ;
    
    public final String m_name;

    Currency(String name) {
        m_name = name;
    }

    public static Currency getByName(String str) {
        if (str == null) {
            return null;
        }
        for (Currency curr : values()) {
            if (curr.m_name.equals(str)) {
                return curr;
            }
        }
        throw new RuntimeException("non supported Currency '" + str + "'. supported: " + Arrays.toString(values()) );
    }

    @Override public String toString() {
        return "Currency[" + m_name + ']';
    }

}
