package bi.two.opt;

import bi.two.Main;
import bi.two.util.StringParser;
import bi.two.util.Utils;

public enum Vary {
    period("period", VaryType.MILLIS),  // barSize
    bars("bars", VaryType.INT),         // barsNum
    divider("divider", VaryType.FLOAT),
    slope("slope", VaryType.INT),       // slopeLength
    signal("signal", VaryType.FLOAT),   // signalLength
    power("power", VaryType.FLOAT),
    smooth("smooth", VaryType.FLOAT),   // smooth
    threshold("threshold", VaryType.FLOAT), // strong trend threshold
    drop("drop", VaryType.FLOAT),           // trend drop level
    reverse("reverse", VaryType.FLOAT),        // direction threshold
    ;
    
    public final String m_key;
    public final VaryType m_varyType;

    Vary(String key, VaryType varyType) {
        m_key = key;
        m_varyType = varyType;
    }


    //=============================================================================================
    public enum VaryType {
        INT {
            @Override public void iterate(String from, String to, String step, Main.IParamIterator<String> paramIterator) {
                Integer fromInt = Integer.parseInt(from);
                Integer toInt = Integer.parseInt(to);
                Integer stepInt = Math.max(1, Integer.parseInt(step));
                for (int i = fromInt; i <= toInt; i += stepInt) {
                    String value = Integer.toString(i);
                    paramIterator.doIteration(value);
                }
            }
        },
        FLOAT {
            @Override public void iterate(String from, String to, String step, Main.IParamIterator<String> paramIterator) {
                Float fromFloat = Float.parseFloat(from);
                Float toFloat = Float.parseFloat(to);
                Float stepFloat = fromFloat.equals(toFloat) ? 1 : Float.parseFloat(step);
                for (float i = fromFloat; i <= toFloat; i += stepFloat) {
                    String value = Float.toString(i);
                    paramIterator.doIteration(value);
                }
            }
        },
        LONG {
            @Override public void iterate(String from, String to, String step, Main.IParamIterator<String> paramIterator) {
                Long fromLong = Long.parseLong(from);
                Long toLong = Long.parseLong(to);
                Long stepLong = Math.max(1, Long.parseLong(step));
                for (long i = fromLong; i <= toLong; i += stepLong) {
                    String value = Long.toString(i);
                    paramIterator.doIteration(value);
                }
            }
        },
        MILLIS {
            @Override public void iterate(String from, String to, String step, Main.IParamIterator<String> paramIterator) {
                Long fromLong = Utils.toMillis(from);
                Long toLong = Utils.toMillis(to);
                Long stepLong = Math.max(1, Utils.toMillis(step));
                for (long i = fromLong; i <= toLong; i += stepLong) {
                    String value = Long.toString(i);
                    paramIterator.doIteration(value);
                }
            }
        },;

        public void iterate(String from, String to, String step, Main.IParamIterator<String> paramIterator) { }
    }

    
    //=============================================================================================
    public static class VaryItem {
        public final Vary m_vary;
        public final String m_from;
        public final String m_to;
        public final String m_step;

        public VaryItem(Vary vary, String from, String to, String step) {
            m_vary = vary;
            m_from = from;
            m_to = to;
            m_step = step;
        }

        public static VaryItem parseVary(String config, Vary vary) {
            // "15.5+-5*0.5"
            StringParser parser = new StringParser(config);
            Float center = parser.readFloat();
            if (center != null) {
                if (parser.atEnd()) {
                    return new VaryItem(vary, config, config, "0");
                }
                if (parser.read("+-")) {
                    Integer count = parser.readInteger();
                    if (count != null) {
                        if (parser.read("*")) {
                            Float step = parser.readFloat();
                            if (step != null) {
                                float from = center - count * step;
                                String fromStr = Float.toString(from);
                                float to = center + count * step;
                                String toStr = Float.toString(to);
                                String stepStr = Float.toString(step);
                                return new VaryItem(vary, fromStr, toStr, stepStr);
                            }
                        }
                    }
                }
            }
            throw new RuntimeException("invalid vary config: " + config);
        }

        @Override public String toString() {
            return "VaryItem{" +
                    "vary=" + m_vary +
                    ", from='" + m_from + '\'' +
                    ", to='" + m_to + '\'' +
                    ", step='" + m_step + '\'' +
                    '}';
        }
    }
}
