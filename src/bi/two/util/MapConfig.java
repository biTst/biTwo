package bi.two.util;

import bi.two.opt.Vary;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class MapConfig extends Properties {
    public String getString(String key) {
        String property = getPropertyNoComment(key);
        if (property != null) {
            return property;
        }
        throw new RuntimeException("property '" + key + "' not found");
    }

    public int getInt(String key) {
        return getIntOrDefault(key, null);
    }

    public int getIntOrDefault(String key, Integer def) {
        String property = getPropertyNoComment(key);
        if (property != null) {
            try {
                return Integer.parseInt(property);
            } catch (NumberFormatException e) {
                throw new NumberFormatException("Error parsing property '" + key + "' value '" + property + "' as Integer");
            }
        }
        if (def != null) {
            return def;
        }
        throw new RuntimeException("property '" + key + "' not found");
    }

    public long getLong(String key) {
        return getLongOrDefault(key, null);
    }

    public long getLongOrDefault(String key, Long def) {
        String property = getPropertyNoComment(key);
        if (property != null) {
            try {
                return Long.parseLong(property);
            } catch (NumberFormatException e) {
                throw new NumberFormatException("Error parsing property '" + key + "' value '" + property + "' as Long");
            }
        }
        if (def != null) {
            return def;
        }
        throw new RuntimeException("property '" + key + "' not found");
    }

    public long getPeriodInMillis(String key) {
        String periodStr = getString(key);
        long period = Utils.toMillis(periodStr);
        return period;
    }

    public float getFloat(String key) {
        return getFloatOrDefault(key, null);
    }

    public float getFloatOrDefault(String key, Float def) {
        String property = getPropertyNoComment(key);
        if (property != null) {
            try {
                return Float.parseFloat(property);
            } catch (NumberFormatException e) {
                throw new NumberFormatException("Error parsing property '" + key + "' value '" + property + "' as Float");
            }
        }
        if (def != null) {
            return def;
        }
        throw new RuntimeException("property '" + key + "' not found");
    }

    public double getDouble(String key) {
        String property = getPropertyNoComment(key);
        if (property != null) {
            try {
                return Double.parseDouble(property);
            } catch (NumberFormatException e) {
                throw new NumberFormatException("Error parsing property '" + key + "' value '" + property + "' as Double");
            }
        }
        throw new RuntimeException("property '" + key + "' not found");
    }

    public boolean getBoolean(String key) {
        String property = getPropertyNoComment(key);
        if (property != null) {
            return property.equals("true") || property.equals("yes");
        }
        throw new RuntimeException("property '" + key + "' not found");
    }

    public String getPropertyNoComment(String key) {
        String property = getProperty(key);
        if (property != null) {
            int indx = property.indexOf('#'); // remove comment
            if (indx != -1) {
                property = property.substring(0, indx).trim();
            }
        }
        return property;
    }

    public void load(String file) throws IOException {
        load(new File(file));
    }

    public void load(File file) throws IOException {
        load(new FileReader(file));
    }

    public void load(InputStream reader) throws IOException {
        try {
            super.load(reader);
        } finally {
            reader.close();
        }
        filter();
    }

    private void filter() {
        for (String name : stringPropertyNames()) {
            String property = getProperty(name);
            int index = property.indexOf('#');
            if (index != -1) {
                property = property.substring(0, index);
                setProperty(name, property);
            }
        }
    }

    public Number getNumber(Vary vary) {
        String str = getPropertyNoComment(vary.m_key);
        if (str == null) {
            Object obj = get(vary.m_key);
            if (obj instanceof Number) {
                return (Number) obj;
            }
            return null;
        }
        Number number = vary.m_varyType.fromString(str);
        return number;
    }
}
