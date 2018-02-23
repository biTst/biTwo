package bi.two.exch;

import bi.two.util.Log;
import bi.two.util.Utils;

import java.util.*;

public class AccountData {
    private static final boolean VERBOSE = false;
    private static final boolean LOG_MOVE = false;

    private final Exchange m_exch;
    private final HashMap<Currency, Double> m_funds = new HashMap<Currency,Double>();
    private final HashMap<Currency, Double> m_allocatedFunds = new HashMap<Currency,Double>();
    public Map<Pair,TopData> m_topDatas = new HashMap<>();

    private static void console(String s) { Log.console(s); }
    private static void log(String s) { Log.log(s); }
    private static void err(String s, Throwable t) { Log.err(s, t); }

    public AccountData(Exchange exch) {
        m_exch = exch;
    }

    public double available(Currency currency) { return notNull(m_funds.get(currency)); }
    public double allocated(Currency currency) { return notNull(m_allocatedFunds.get(currency)); }
    private double notNull(Double aDouble) { return aDouble == null ? 0 : aDouble.doubleValue(); }

    public void setAvailable(Currency currency, double value) {
        setMapValue(m_funds, currency, value);
    }
    public void setAllocated(Currency currency, double value) {
        setMapValue(m_allocatedFunds, currency, value);
    }

    private void setMapValue(HashMap<Currency, Double> map, Currency currency, double value) {
        double round = round(value);
        if (round == 0) {
            map.remove(currency);
        } else {
            map.put(currency, round);
        }
    }

    private double round(double value) {
        return Math.round(value * 1000000000d) / 1000000000d;
    }

    public double evaluateAll(Currency baseCurrency) {
        double allValue = 0;
        for (Currency currency : m_funds.keySet()) {
            double value = getAllValue(currency);
            if (value > 0.000000001) {
                double rate = rate(currency, baseCurrency);
                if(rate != 0) { // if can convert
                    value = value * rate;
                    allValue += value;
                }
            }
        }
        return allValue;
    }

    /** @return 0 if no convert route */
    public double rate(Currency from, Currency to) {
        double rate;
        if (from == to) {
            rate = 1d;
        } else {
            Pair pair = Pair.get(from, to);
            if (pair != null) {
                TopData topData = m_topDatas.get(pair);
                if (topData == null) {
                    rate = 0; // not topData
                } else {
                    boolean forward = (pair.m_from == from);
                    rate = forward ? topData.m_bid : topData.m_ask;
                    if (!forward) {
                        rate = 1 / rate;
                    }
                }
            } else { // no direct pair support - try via base currency
                Currency baseCurrency = m_exch.m_baseCurrency;
                double rate1 = rate(from, baseCurrency);
                double rate2 = rate(baseCurrency, to);
                if ((rate1 != 0) && (rate2 != 0)) {
                    rate = rate1 * rate2;
                } else {
                    rate = 0; // not convert route
                }
            }
        }
        return rate;
    }

    public double getAllValue(Currency currency) {
        double allValue = 0;
        Double available = m_funds.get(currency);
        if (available != null) {
            allValue += available;
        }
        Double allocated = m_allocatedFunds.get(currency);
        if (allocated != null) {
            allValue += allocated;
        }
        return allValue;
    }

    public AccountData copy() {
        AccountData ret = new AccountData(m_exch);
        ret.m_funds.putAll(m_funds);
        ret.m_allocatedFunds.putAll(m_allocatedFunds);
        return ret;
    }

    public double calcNeedBuyTo(Pair pair, float direction) {
        boolean toLog = VERBOSE;
        Currency currencyFrom = pair.m_from; // cnh=from
        Currency currencyTo = pair.m_to;     // btc=to

        double valuateTo = evaluateAll( currencyTo);
        double valuateFrom = evaluateAll( currencyFrom);
        if(toLog) {
            log("  valuate" + currencyTo.m_name + "=" + Utils.format8(valuateTo) + " " + currencyTo.m_name
                    + "; valuate" + currencyFrom.m_name + "=" + Utils.format8(valuateFrom) + " " + currencyFrom.m_name);
        }
        double haveFrom = getValueForCurrency(currencyFrom, currencyTo);
        double haveTo =   getValueForCurrency(currencyTo, currencyFrom);
        if(toLog) {
            log("  have" + currencyTo.m_name + "=" + Utils.format8(haveTo) + " " + currencyTo.m_name
                    + "; have" + currencyFrom.m_name + "=" + Utils.format8(haveFrom) + " " + currencyFrom.m_name + "; on account=" + this);
        }
        double needTo = (1 - direction) / 2 * valuateTo;
        double needFrom = (1 + direction) / 2 * valuateFrom;
        if(toLog) {
            log("  need" + currencyTo.m_name + "=" + Utils.format8(needTo) + " " + currencyTo.m_name
                    + "; need" + currencyFrom.m_name + "=" + Utils.format8(needFrom) + " " + currencyFrom.m_name);
        }
        double needBuyTo = needTo - haveTo;
        double needSellFrom = haveFrom - needFrom;
        if(toLog) {
            log("  direction=" + Utils.format8((double) direction)
                    + "; needBuy" + currencyTo.m_name + "=" + Utils.format8(needBuyTo)
                    + "; needSell" + currencyFrom.m_name + "=" + Utils.format8(needSellFrom));
        }
        return needBuyTo;
    }

    private double getValueForCurrency(Currency currency, Currency currency2) {
        double from = 0;
        Double availableFrom = m_funds.get(currency);
        if (availableFrom != null) {
            from += availableFrom;
        }
        Double allocatedTo = m_allocatedFunds.get(currency2);
        if(VERBOSE) {
            log("   available" + currency.m_name + "=" + Utils.format8(from) + " " + currency.m_name +
                    "; allocated" + currency2.m_name + "=" + Utils.format8(allocatedTo) + " " + currency2.m_name);
        }
        if (allocatedTo != null) {
            Double rate = rate(currency2, currency);
            if (rate != null) { // if can convert
                Double allocatedToForFrom = allocatedTo / rate;
                from += allocatedToForFrom;
                if(VERBOSE) {
                    log("    " + currency2.m_name + "->" + currency.m_name + " rate=" + Utils.format8(rate) +
                            "; allocated" + currency2.m_name + "in" + currency.m_name + "=" + Utils.format8(allocatedToForFrom) + " " + currency.m_name +
                            "; total" + currency.m_name + " = " + Utils.format8(from) + " " + currency.m_name);
                }
            }
        }
        return from;
    }

    public void move(Pair pair, double amountTo) {
        move(pair, amountTo, 0);
    }

    public void move(Pair pair, double amountTo, double commission) {
        boolean toLog = LOG_MOVE || VERBOSE;

        Currency currencyFrom = pair.m_from;
        Currency currencyTo = pair.m_to;

        String fromName = currencyFrom.m_name;
        String toName = currencyTo.m_name;
        if (toLog) {
            log("   move() currencyFrom=" + fromName + "; currencyTo=" + toName + "; amountTo=" + amountTo);
        }
        if (amountTo == 0) {
            if (toLog) {
                log("NOTHING to move");
            }
            return;
        }
        if (toLog) {
            log("    account in: " + this);
        }
        double availableFrom = available(currencyFrom);
        double availableTo = available(currencyTo);

        double amountFrom = convert(currencyTo, currencyFrom, amountTo);
        if (amountTo < 0) { // buy 'FROM' sell 'TO'
            if (toLog) {
                String s1 = "    move " + (-amountTo) + toName + " -> " + fromName;
                log(s1);
                String s = "     " + (-amountTo) + toName + " = " + Utils.format8(-amountFrom) + fromName;
                log(s);
            }
            if (commission > 0) {
                amountFrom *= (1 - commission);
                if (toLog) {
                    String s = "      -" + commission + " commission (" + Utils.format8(-amountFrom * commission) + fromName + ") -> " + Utils.format8(-amountFrom) + fromName;
                    log(s);
                }
            }
        } else { // sell 'FROM' buy 'TO'
            if (toLog) {
                log("    move " + amountFrom + fromName + " -> " + toName);
                log("     " + amountFrom + fromName + " = " + Utils.format8(amountTo) + toName);
            }
            if (commission > 0) {
                amountTo *= (1 - commission);
                if (toLog) {
                    log("      - " + commission + " commission (" + Utils.format8(amountTo * commission) + toName + ") -> " + Utils.format8(amountTo) + toName);
                }
            }
        }
        double newAvailableFrom = availableFrom - amountFrom;
        double newAvailableTo = availableTo + amountTo;
        if (toLog) {
            log("        newAvailableFrom=" + Utils.format8(newAvailableFrom) + fromName + "; newAvailableTo=" + Utils.format8(newAvailableTo) + toName);
        }
        if (newAvailableFrom < 0) {
            throw new RuntimeException("Error account move (newAvailableFrom="+newAvailableFrom+"). from=" + fromName + "; to=" + toName + "; amountTo=" + amountTo
                    + "; amountFrom=" + amountFrom + "; availableFrom=" + availableFrom + "; on " + this);
        }
        if (newAvailableTo < 0) {
            throw new RuntimeException("Error account move (newAvailableTo="+newAvailableTo+"). from=" + fromName + "; to=" + toName + "; amountTo=" + amountTo
                    + "; amountFrom=" + amountFrom + "; availableFrom=" + availableFrom + "; availableTo=" + availableTo + "; on " + this);
        }

        setAvailable(currencyFrom, newAvailableFrom);
        setAvailable(currencyTo, newAvailableTo);

        if (toLog) {
            log("    account out: " + this);
        }
    }

    public double convert(Currency fromCurrency, Currency toCurrency, double amountTo) {
        Double rate = rate(fromCurrency, toCurrency);
        if (rate != null) {
            double converted = amountTo * rate;
            return converted;
        }
        throw new RuntimeException("Unable to convert from " + fromCurrency + " to " + toCurrency);
    }

    @Override public String toString() {
        return "AccountData[" +
                "name='" + m_exch.m_name + "\' " +
                "funds=" + toString(m_funds) + "; " +
                "allocated=" + toString(m_allocatedFunds) +
                ']';
    }

    private String toString(HashMap<Currency, Double> funds) {
        if (funds.isEmpty()) { return "{}"; }
        Set<Currency> entries = funds.keySet();
        ArrayList<Currency> list = new ArrayList<Currency>(entries);
        Collections.sort(list);
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        for (Currency currency : list) {
            Double value = funds.get(currency);
            if (Math.abs(value) > 0.0000000001) {
                sb.append(currency.m_name);
                sb.append('=');
                sb.append(Utils.format5(value));
                sb.append(", ");
            }
        }
        int length = sb.length();
        if (length > 2) {
            sb.setLength(length - 2);
        }
        return sb.append('}').toString();
    }

    public boolean hasAllocated() {
        return !m_allocatedFunds.isEmpty();
    }
}
