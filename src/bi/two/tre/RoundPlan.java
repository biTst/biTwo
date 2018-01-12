package bi.two.tre;

import bi.two.exch.*;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class RoundPlan {
    public static final Comparator<RoundPlan> BY_RATE_COMPARATOR = new Comparator<RoundPlan>() {
        @Override public int compare(RoundPlan o1, RoundPlan o2) {
            return Double.compare(o2.m_roundRate, o1.m_roundRate); // descending
        }
    };

    public final RoundDirectedData m_rdd;
    public final RoundPlanType m_roundPlanType;
    public final List<RoundNode> m_roundNodes;
    public final double m_roundRate;
    public final long m_timestamp;
    public long m_liveTime;

    public RoundPlan(RoundDirectedData rdd, RoundPlanType roundPlanType, List<RoundNode> roundNodes, double roundRate) {
        m_rdd = rdd;
        m_roundPlanType = roundPlanType;
        m_roundNodes = roundNodes;
        m_roundRate = roundRate;
        m_timestamp = System.currentTimeMillis();
    }

    @Override public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if ((o == null) || (getClass() != o.getClass())) {
            return false;
        }
        RoundPlan roundPlan = (RoundPlan) o;
        return Objects.equals(m_rdd, roundPlan.m_rdd) &&
                m_roundPlanType == roundPlan.m_roundPlanType;
    }

    @Override public int hashCode() {
        return Objects.hash(m_rdd, m_roundPlanType);
    }

    // -----------------------------------------------------------------------------------------
    public enum RoundPlanType {
        MKT_MKT {
            @Override public String getPrefix() { return "mkt_mkt"; }
            @Override public RoundNode.RoundNodeType getRoundNodeType(int indx) { return RoundNode.RoundNodeType.MKT; }
        },
        LMT_MKT {
            @Override public String getPrefix() { return "lmt_mkt"; }
            @Override public RoundNode.RoundNodeType getRoundNodeType(int indx) { return (indx == 0) ? RoundNode.RoundNodeType.LMT : RoundNode.RoundNodeType.MKT; }
        },
        MKT_TCH {
            @Override public String getPrefix() { return "mkt_tch"; }
            @Override public RoundNode.RoundNodeType getRoundNodeType(int indx) { return (indx == 0) ? RoundNode.RoundNodeType.MKT : RoundNode.RoundNodeType.TCH; }
        },
        TCH_MKT {
            @Override public String getPrefix() { return "tch_mkt"; }
            @Override public RoundNode.RoundNodeType getRoundNodeType(int indx) { return (indx == 0) ? RoundNode.RoundNodeType.TCH : RoundNode.RoundNodeType.MKT; }
        }
        ;

        public abstract String getPrefix();

        @Override public String toString() { return getPrefix(); }
        public abstract RoundNode.RoundNodeType getRoundNodeType(int indx);
    }


    // -----------------------------------------------------------------------------------------
    public static class RoundNode {
        public final PairDirectionData m_pdd;
        public final RoundNodeType m_roundNodeType;

        public RoundNode(PairDirectionData pdd, RoundNodeType roundNodeType) {
            m_pdd = pdd;
            m_roundNodeType = roundNodeType;
        }

        // -----------------------------------------------------------------------------------------
        public enum RoundNodeType {
            MKT {
                @Override public String getPrefix() { return "mkt"; }
                @Override public double fee(ExchPairData exchPairData) { return exchPairData.m_commission; }
                @Override public double rate(PairData pd, RoundData roundData, boolean isForwardTrade, OrderBook orderBook, double orderSize, CurrencyValue value) {
                    ExchPairData exchPairData = pd.m_exchPairData;
                    // we should match book size
                    OrderSide orderSide = isForwardTrade ? OrderSide.BUY : OrderSide.SELL;
                    OrderSide oppositeSide = orderSide.opposite();
                    Pair pair = exchPairData.m_pair;
                    Currency bookCurrency = pair.m_from;
                    Currency bookCurrency2 = pair.m_to;

                    List<OrderBook.OrderBookEntry> bookSide = isForwardTrade
                            ? orderBook.m_asks // byu
                            : orderBook.m_bids; // sell

                    CurrencyValue minOrderStep = exchPairData.m_minOrderStep;
                    System.out.println("          rate for " + value + "; minOrderStep=" + minOrderStep.format8() + "; pair=" + pair + "; bookSide: " + bookSide);

                    double volume = 0;
                    int index = 0;
                    double toDistribute = value.m_value;
                    double remainedSize = toDistribute;
                    while(remainedSize > 0) {
                        OrderBook.OrderBookEntry bookEntry = bookSide.get(index);
                        double entrySize = bookEntry.m_size;
                        double entryPrice = bookEntry.m_price;
                        System.out.println("          " + orderSide + ": book entry[" + index + "]: " + bookEntry +
                                "; we can " + orderSide + " " + entrySize + " " + bookCurrency + " @ " + entryPrice + " " + bookCurrency2 + " per " + bookCurrency);
                        if (orderSide.isSell()) {
                            if (entrySize < remainedSize) {
                                CurrencyValue minPassThruOrderSize = roundData.m_minPassThruOrdersSize.get(pair);
                                System.out.println("           book entry has not enough. want " + remainedSize + " " + bookCurrency + " but have only " + entrySize + " " + bookCurrency
                                        + "; minPassThruOrderSize=" + minPassThruOrderSize);
                                double minPassThruOrderSizeValue = minPassThruOrderSize.m_value;
                                if (entrySize < minPassThruOrderSizeValue) {
                                    System.out.println("            entry has " + entrySize + " but minPassThru is " + minPassThruOrderSizeValue + " -> need use next book level");
                                    double entryVolume = entrySize * entryPrice;
                                    volume += entryVolume;
                                    remainedSize -= entrySize;
                                    System.out.println("             entry gives " + entryVolume + " " + bookCurrency2 + "; volume=" + volume + "; remainedSize=" + remainedSize);
                                } else {
                                    System.out.println("            !entry has enough " + entrySize + " for minPassThru " + minPassThruOrderSizeValue + " -> need scale");
                                    // todo
                                    break;
                                }
                            } else {
                                System.out.println("           book entry has enough. want " + orderSide + " " + remainedSize + " " + bookCurrency + ", available " + entrySize + " " + bookCurrency);
                                double sizeVolume = remainedSize * entryPrice;
                                volume += sizeVolume;
                                remainedSize = 0;
                                System.out.println("            remained gives " + sizeVolume + " " + bookCurrency2 + "; volume=" + volume);
                            }
                        } else { // buy case
                            double entryGives = entrySize * entryPrice;
                            System.out.println("           book entry " + entrySize + " " + bookCurrency + " gives " + entryGives + " " + bookCurrency2 );
                            if (entryGives < remainedSize) {
                                CurrencyValue minPassThruOrderSize = roundData.m_minPassThruOrdersSize.get(pair);
                                System.out.println("           not enough on book. want " + remainedSize + " " + bookCurrency2 + " entry gives only " + entryGives + " " + bookCurrency2
                                        + "; minPassThruOrderSize=" + minPassThruOrderSize);
                                double minPassThruOrderSizeValue = minPassThruOrderSize.m_value;
                                if (entrySize < minPassThruOrderSizeValue) {
                                    System.out.println("            entry has " + entrySize + " but minPassThru is " + minPassThruOrderSizeValue + " -> need use next book level");
                                    volume += entrySize;
                                    remainedSize -= entryGives;
                                    System.out.println("             entry gives " + entryGives + " " + bookCurrency2 + "; volume=" + volume + "; remainedSize=" + remainedSize);
                                } else {
                                    System.out.println("            !entry has enough " + entrySize + " for minPassThru " + minPassThruOrderSizeValue + " -> need scale");
                                    // todo
                                    break;
                                }
                            } else {
                                double sizeVolume = remainedSize / entryPrice;
                                String oppositeName = oppositeSide.toString().toLowerCase();
                                System.out.println("            book entry gives enough: want " + oppositeName + " " + remainedSize + " " + bookCurrency2
                                        + " can " + oppositeName + " " + entryGives + " " + bookCurrency2);
                                volume += sizeVolume;
                                remainedSize = 0;
                                System.out.println("             remained gives " + sizeVolume + " " + bookCurrency + "; volume=" + volume);
                            }
                        }
                        index++;
                    }

                    if (remainedSize == 0) {
                        double rate = orderSide.isSell()
                                      ? volume / toDistribute
                                      : toDistribute / volume ;
                        System.out.println("          all distributed(steps=" + index + "): volume=" + volume + "; toDistribute=" + toDistribute + " => rate=" + rate);
                        return rate;
                    }

                    return bookSide.get(0).m_price;
                }
            },
            LMT { // best limit price
                @Override public String getPrefix() { return "lmt"; }
                @Override public double fee(ExchPairData exchPairData) { return exchPairData.m_makerCommission; }
                @Override public double rate(PairData pd, RoundData roundData, boolean isForwardTrade, OrderBook orderBook, double orderSize, CurrencyValue value) {
                    ExchPairData exchPairData = pd.m_exchPairData;
                    double step = exchPairData.m_minPriceStep;
                    return isForwardTrade
                            ? orderBook.getTopBidPrice() + step
                            : orderBook.getTopAskPrice() - step;
                }
            },
            TCH {
                @Override public String getPrefix() { return "tch"; }
                @Override public double fee(ExchPairData exchPairData) { return exchPairData.m_makerCommission; }
                @Override public double rate(PairData pd, RoundData roundData, boolean isForwardTrade, OrderBook orderBook, double orderSize, CurrencyValue value) {
                    ExchPairData exchPairData = pd.m_exchPairData;
                    double step = exchPairData.m_minPriceStep;
                    return isForwardTrade
                            ? orderBook.getTopAskPrice() - step // byu
                            : orderBook.getTopBidPrice() + step; // sell
                }
            },
            ;

            public abstract String getPrefix();
            public abstract double fee(ExchPairData exchPairData);
            public abstract double rate(PairData pd, RoundData roundData, boolean isForwardTrade, OrderBook orderBook, double orderSize, CurrencyValue value);

            @Override public String toString() { return getPrefix(); }
        }
    }
}
