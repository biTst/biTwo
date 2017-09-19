package bi.two.opt;

import bi.two.util.StringParser;

public class IterateConfig {
    public final Vary m_vary;
    public final Number m_from;
    public final Number m_to;
    public final Number m_step;

    public IterateConfig(Vary vary, String from, String to, String step) {
        this(vary, vary.m_varyType.fromString(from), vary.m_varyType.fromString(to), Integer.parseInt(step));
    }

    public IterateConfig(Vary vary, Number from, Number to, Number step) {
        m_vary = vary;
        m_from = from;
        m_to = to;
        m_step = step;
    }

    public static IterateConfig parseIterate(String config, Vary vary) {
        // "15.5+-5*0.5"
        StringParser parser = new StringParser(config);
        Vary.VaryType varyType = vary.m_varyType;
        Number center = varyType.fromParser(parser);
        if (center != null) {
            if (parser.atEnd()) {
                return new IterateConfig(vary, center, center, 1);
            }
            if (parser.read("+-")) {
                Integer count = parser.readInteger();
                if (count != null) {
                    if (parser.read("*")) {
                        Number step = varyType.fromParser(parser);
                        if (step != null) {
                            Number from = varyType.mulAdd(step, -count, center);
                            Number to = varyType.mulAdd(step, count, center);
                            return new IterateConfig(vary, from, to, step);
                        }
                    }
                }
            } else {
                // todo - separate +steps and -steps support
            }
        }
        throw new RuntimeException("invalid IterateConfig: " + config);
    }

    @Override public String toString() {
        return "IterateConfig{" +
                "vary=" + m_vary +
                ", from='" + m_from + '\'' +
                ", to='" + m_to + '\'' +
                ", step='" + m_step + '\'' +
                '}';
    }
}
