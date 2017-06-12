package bi.two.util;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class MapConfig extends Properties {
    public int getInt(String key) {
        String property = getProperty(key);
        if (property != null) {
            try {
                return Integer.parseInt(property);
            } catch (NumberFormatException e) {
                throw new NumberFormatException("Error parsing property '" + key + "' value '" + property + "' as Integer");
            }
        }
        throw new RuntimeException("property '" + key + "' not found");
    }

    public long getLong(String key) {
        String property = getProperty(key);
        if (property != null) {
            try {
                return Long.parseLong(property);
            } catch (NumberFormatException e) {
                throw new NumberFormatException("Error parsing property '" + key + "' value '" + property + "' as Long");
            }
        }
        throw new RuntimeException("property '" + key + "' not found");
    }

    public float getFloat(String key) {
        String property = getProperty(key);
        if (property != null) {
            try {
                return Float.parseFloat(property);
            } catch (NumberFormatException e) {
                throw new NumberFormatException("Error parsing property '" + key + "' value '" + property + "' as Float");
            }
        }
        throw new RuntimeException("property '" + key + "' not found");
    }

    public double getDouble(String key) {
        String property = getProperty(key);
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
        String property = getProperty(key);
        if (property != null) {
            return property.equals("true") || property.equals("yes");
        }
        throw new RuntimeException("property '" + key + "' not found");
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
}