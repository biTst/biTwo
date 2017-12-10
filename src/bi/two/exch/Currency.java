package bi.two.exch;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public enum Currency {
    USD("usd"),
    EUR("eur"),
    BTC("btc"),
    BCH("bch"),
    BTG("btg"),
    CNH("cnh"),
    ETH("eth"),
    DASH("dash"),
    ;
    
    public final String m_name;

    Currency(String name) {
        m_name = name;
    }

    public static @NotNull Currency getByName(String str) {
        if (str == null) {
            throw new RuntimeException("invalid parameter - null passed" );
        }
        Currency currency = get(str);
        if (currency == null) {
            throw new RuntimeException("non supported Currency '" + str + "'. supported: " + Arrays.toString(values()) );
        }
        return currency;
    }

    public static @Nullable Currency get(String str) {
        if (str == null) {
            return null;
        }
        for (Currency curr : values()) {
            if (curr.m_name.equals(str)) {
                return curr;
            }
        }
        return null;
    }

    @Override public String toString() {
        return "Currency[" + m_name + ']';
    }

}
