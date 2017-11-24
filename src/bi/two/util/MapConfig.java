package bi.two.util;

import bi.two.opt.Vary;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class MapConfig extends Properties {
    private static final String INCLUDE_KEY = "include";
    private static final String ENCRYPTED_KEY = "encrypted";

    public MapConfig() {}

    public MapConfig(MapConfig algoConfig) {
        putAll(algoConfig);
    }

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
        return getDoubleOrDefault(key, null);
    }
    
    public double getDoubleOrDefault(String key, Double def) {
        String property = getPropertyNoComment(key);
        if (property != null) {
            try {
                return Double.parseDouble(property);
            } catch (NumberFormatException e) {
                throw new NumberFormatException("Error parsing property '" + key + "' value '" + property + "' as Double");
            }
        }
        if (def != null) {
            return def;
        }
        throw new RuntimeException("property '" + key + "' not found");
    }

    public boolean getBoolean(String key) {
        return getBooleanOrDefault(key, null);
    }
    
    public boolean getBooleanOrDefault(String key, Boolean def) {
        String property = getPropertyNoComment(key);
        if (property != null) {
            return property.equals("true") || property.equals("yes");
        }
        if (def != null) {
            return def;
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

    public File load(String fileName) throws IOException {
        return load(null, fileName);
    }

    public File load(File parentDir, String fileName) throws IOException {
        File file = (parentDir == null) ? new File(fileName) : new File(parentDir, fileName);
        load(file);
        String include = (String) remove(INCLUDE_KEY);
        if (include != null) {
System.out.println("loading include = " + include);
            MapConfig included = new MapConfig();
            included.load(include);
            putAll(included);
        }
        return file.getParentFile();
    }

    public boolean needDecrypt() {
        return containsKey(ENCRYPTED_KEY);
    }

    public void loadAndEncrypted(String file) throws Exception {
        File parent = load(file);
        if (needDecrypt()) {
            String pwd = ConsoleReader.readConsolePwd("pwd>");
            if (pwd == null) {
                throw new RuntimeException("no console - use real console, not inside IDE");
            }
            loadEncrypted(parent, pwd);
        }
    }

    public void loadEncrypted(File parent, String pwd) throws Exception {
        String encryptedFileName = (String) remove(ENCRYPTED_KEY);
        if (encryptedFileName != null) {
System.out.println("loading encrypt = " + encryptedFileName);
            MapConfig encrypted = new MapConfig();
            encrypted.load(parent, encryptedFileName);
            encrypted.decryptAll(pwd);
            putAll(encrypted);
        }
    }

    private void decryptAll(String pwd) throws Exception {
        for (String name : stringPropertyNames()) {
            String encrypted = getProperty(name);
            String decrypted = Encryptor.decrypt(encrypted, pwd);
            setProperty(name, decrypted);
        }
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
            int index = property.indexOf('#'); // remove comments at the end of strings
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

    public MapConfig copy() {
        return new MapConfig(this);
    }
}
