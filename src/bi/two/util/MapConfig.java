package bi.two.util;

import java.util.HashMap;

public class MapConfig extends HashMap<String,String> {
    public int getInt(String key) { return Integer.parseInt(get(key)); }
    public long getLong(String key) { return Long.parseLong(get(key)); }
    public float getFloat(String key) { return Float.parseFloat(get(key)); }
    public double getDouble(String key) { return Double.parseDouble(get(key)); }
}
