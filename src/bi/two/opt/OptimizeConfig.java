package bi.two.opt;

import bi.two.util.StringParser;

public class OptimizeConfig {
    public final Vary m_vary;
    public final Number m_start;
    public final Number m_min;
    public final Number m_max;
    public final double m_multiplier;

    public OptimizeConfig(Vary vary, String start, String min, String max) {
        this(vary, vary.m_varyType.fromString(start), vary.m_varyType.fromString(min), Integer.parseInt(max));
    }

    public OptimizeConfig(Vary vary, Number start, Number min, Number max) {
        m_vary = vary;
        m_start = start;
        m_min = min;
        m_max = max;

        double minimum = Math.min(min.doubleValue(), Math.min(max.doubleValue(), start.doubleValue()));
        double log10 = Math.log10(minimum);
        int pow = (int) log10;
        m_multiplier = Math.pow(10, pow);
    }

    public static OptimizeConfig parseOptimize(String config, Vary vary) {
        // "26.985[3,40]"
        StringParser parser = new StringParser(config);
        Vary.VaryType varyType = vary.m_varyType;
        Number start = varyType.fromParser(parser);
        if (start != null) {
            if (parser.read("[")) {
                Number min = varyType.fromParser(parser);
                if (min != null) {
                    if (parser.read(",")) {
                        Number max = varyType.fromParser(parser);
                        if (max != null) {
                            return new OptimizeConfig(vary, start, min, max);
                        }
                    }
                }
            }
        }
        throw new RuntimeException("invalid OptimizeConfig: " + config);
    }

    @Override public String toString() {
        return "IterateConfig{" +
                "vary=" + m_vary +
                ", start='" + m_start + '\'' +
                ", min='" + m_min + '\'' +
                ", max='" + m_max + '\'' +
                '}';
    }
}
