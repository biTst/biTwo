package bi.two.exch;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public enum Currency {
    USD("usd", "U"),
    EUR("eur", "E"),
    GBP("gbp", "G"),
    CNH("cnh", "C"),
    BTC("btc", "b"),
    ETH("eth", "e"),
    BCH("bch", "h"),
    BTG("btg", "g"),
    DASH("dash", "d"),
    XRP("xrp", "x"),
    XBT("xbt", "b"), // bitmex btc interpretation
    RUB("rub", "r"),
    FUT("FUT", "F"), // FUTures contract
    ;
    
    public final String m_name;
    public final String m_shortName;

    Currency(String name, String shortName) {
        m_name = name;
        m_shortName = shortName;
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
        return "[" + m_name + ']';
    }

}
