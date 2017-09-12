package bi.two;

import bi.two.algo.impl.RegressionAlgo;
import bi.two.util.Utils;

public enum Vary {
    period(RegressionAlgo.BARS_SIZE_KEY, VaryType.MILLIS),
    bars(RegressionAlgo.REGRESSION_BARS_NUM_KEY, VaryType.INT),
    threshold(RegressionAlgo.THRESHOLD_KEY, VaryType.FLOAT),
    slope(RegressionAlgo.SLOPE_LEN_KEY, VaryType.INT),
    signal(RegressionAlgo.SIGNAL_LEN_KEY, VaryType.INT),
    power(RegressionAlgo.POWER_KEY, VaryType.FLOAT),
    smooth(RegressionAlgo.SMOOTHER_KEY, VaryType.FLOAT),;

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
}
