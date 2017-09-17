package bi.two;

import bi.two.util.Utils;

public enum Vary {
    period("barSize", VaryType.MILLIS),
    bars("barsNum", VaryType.INT),
    divider("divider", VaryType.FLOAT),
    slope("slopeLength", VaryType.INT),
    signal("signalLength", VaryType.INT),
    power("power", VaryType.FLOAT),
    smooth("smoother", VaryType.FLOAT),
    threshold("threshold", VaryType.FLOAT),
    ;
    
    public final String m_key;
    public final VaryType m_varyType;

    Vary(String key, VaryType varyType) {
        m_key = key;
        m_varyType = varyType;
    }


    public enum VaryType {
        INT {
            @Override public void iterate(String from, String to, String step, Main.IParamIterator<String> paramIterator) {
                Integer fromInt = Integer.parseInt(from);
                Integer toInt = Integer.parseInt(to);
                Integer stepInt = Integer.parseInt(step);
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
                Float stepFloat = Float.parseFloat(step);
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
                Long stepLong = Long.parseLong(step);
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
                Long stepLong = Utils.toMillis(step);
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
        final Vary m_vary;
        public final String m_from;
        public final String m_to;
        public final String m_step;

        public VaryItem(Vary vary, String from, String to, String step) {
            m_vary = vary;
            m_from = from;
            m_to = to;
            m_step = step;
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
