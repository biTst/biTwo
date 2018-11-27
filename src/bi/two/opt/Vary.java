package bi.two.opt;

import bi.two.Main;
import bi.two.util.StringParser;
import bi.two.util.Utils;

public enum Vary {
    period(VaryType.MILLIS),  // barSize
    turn(VaryType.FLOAT),     // time in bars to confirm turn
    bars(VaryType.INT),         // barsNum
    divider(VaryType.FLOAT),
    multiplier(VaryType.FLOAT),
    slope(VaryType.FLOAT),     // slopeLength
    signal(VaryType.FLOAT),   // signalLength
    power(VaryType.FLOAT),
    smooth(VaryType.FLOAT),   // smooth
    threshold(VaryType.FLOAT), // strong trend threshold
    drop(VaryType.FLOAT),           // trend drop level
    reverse(VaryType.FLOAT),     // direction threshold
    enter(VaryType.FLOAT),
    backLevel(VaryType.FLOAT),

    longEmaLen(VaryType.FLOAT),   // long ema trend len
    shortEmaLen(VaryType.FLOAT), // short ema trend len

    start(VaryType.FLOAT),
    step(VaryType.FLOAT),
    count(VaryType.FLOAT),

    s1(VaryType.FLOAT), // spread proportional start
    s2(VaryType.FLOAT), // gain proportional start
    e1(VaryType.FLOAT), // spread proportional end
    e2(VaryType.FLOAT), // gain proportional end

    target(VaryType.FLOAT),     // qummar target
    reverseMul(VaryType.FLOAT), // qummar multiplicator

    collapse(VaryType.FLOAT),
    p1(VaryType.FLOAT), // prestart 1
    p2(VaryType.FLOAT), // prestart 2
    p3(VaryType.FLOAT), // noStartCollapse

    minOrderMul(VaryType.FLOAT),
    joinTicks(VaryType.LONG),
    commission(VaryType.FLOAT),
    ;
    
    public final VaryType m_varyType;

    Vary(VaryType varyType) {
        m_varyType = varyType;
    }

    //=============================================================================================
    public enum VaryType {
        INT {
            @Override public void iterate(IterateConfig iterateConfig, Main.IParamIterator<Number> paramIterator) {
                Integer fromInt = iterateConfig.m_from.intValue();
                Integer toInt = iterateConfig.m_to.intValue();
                Integer stepInt = Math.max(1, iterateConfig.m_step.intValue());
                for (int i = fromInt; i <= toInt; i += stepInt) {
                    paramIterator.doIteration(i);
                }
            }
            @Override public Number fromString(String str) { return Integer.parseInt(str); }
            @Override public Number fromParser(StringParser parser) { return parser.readInteger(); }
            @Override public Number mulAdd(Number mul1, int mul2, Number add) { return mul1.intValue() * mul2 + add.intValue(); }
        },
        FLOAT {
            @Override public void iterate(IterateConfig iterateConfig, Main.IParamIterator<Number> paramIterator) {
                Float fromFloat = iterateConfig.m_from.floatValue();
                Float toFloat = iterateConfig.m_to.floatValue();
                Float stepFloat = fromFloat.equals(toFloat) ? 1 : iterateConfig.m_step.floatValue();;
                for (float i = fromFloat; i <= toFloat; i += stepFloat) {
                    paramIterator.doIteration(i);
                }
            }
            @Override public Number fromString(String str) { return Float.parseFloat(str); }
            @Override public Number fromParser(StringParser parser) { return parser.readFloat(); }
            @Override public Number mulAdd(Number mul1, int mul2, Number add) { return mul1.floatValue() * mul2 + add.floatValue(); }
        },
        DOUBLE {
            @Override public void iterate(IterateConfig iterateConfig, Main.IParamIterator<Number> paramIterator) {
                Double fromFloat = iterateConfig.m_from.doubleValue();
                Double toFloat = iterateConfig.m_to.doubleValue();
                Double stepFloat = fromFloat.equals(toFloat) ? 1 : iterateConfig.m_step.doubleValue();;
                for (double i = fromFloat; i <= toFloat; i += stepFloat) {
                    paramIterator.doIteration(i);
                }
            }
            @Override public Number fromString(String str) { return Double.parseDouble(str); }
            @Override public Number fromParser(StringParser parser) { return parser.readDouble(); }
            @Override public Number mulAdd(Number mul1, int mul2, Number add) { return mul1.doubleValue() * mul2 + add.doubleValue(); }
        },
        LONG {
            @Override public void iterate(IterateConfig iterateConfig, Main.IParamIterator<Number> paramIterator) {
                Long fromLong = iterateConfig.m_from.longValue();
                Long toLong = iterateConfig.m_to.longValue();
                Long stepLong = Math.max(1, iterateConfig.m_step.longValue());
                for (long i = fromLong; i <= toLong; i += stepLong) {
                    paramIterator.doIteration(i);
                }
            }
            @Override public Number fromString(String str) { return Long.parseLong(str); }
            @Override public Number fromParser(StringParser parser) { return parser.readLong(); }
            @Override public Number mulAdd(Number mul1, int mul2, Number add) { return mul1.longValue() * mul2 + add.longValue(); }
        },
        MILLIS {
            @Override public void iterate(IterateConfig iterateConfig, Main.IParamIterator<Number> paramIterator) {
                Long fromLong = iterateConfig.m_from.longValue();
                Long toLong = iterateConfig.m_to.longValue();
                Long stepLong = Math.max(1, iterateConfig.m_step.longValue());
                for (long i = fromLong; i <= toLong; i += stepLong) {
                    paramIterator.doIteration(i);
                }
            }
            @Override public Number fromString(String str) { return Utils.toMillis(str); }
            public Number fromParser(StringParser parser) {
                // todo: add support like '1min'
                return parser.readLong();
            }
            @Override public Number mulAdd(Number mul1, int mul2, Number add) { return mul1.longValue() * mul2 + add.longValue(); }
        },
        ;

        public void iterate(IterateConfig iterateConfig, Main.IParamIterator<Number> paramIterator) { }

        public Number fromString(String str) { throw new RuntimeException("should be overridden on "+this); }
        public Number fromParser(StringParser parser) { throw new RuntimeException("should be overridden on "+this); }
        public Number mulAdd(Number mul1, int mul2, Number add) { throw new RuntimeException("should be overridden on "+this); }
    }
}
