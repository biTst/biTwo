package bi.two.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class Post {
    public static String createHttpPostString(Map<String, String> params, boolean sortByKeys) {
        List<String> keys = new ArrayList<String>(params.keySet());
        if (sortByKeys) {
            Collections.sort(keys);
        }
        StringBuilder buff = new StringBuilder();
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            String value = params.get(key);
            if (i != 0) {
                buff.append("&");
            }
            buff.append(key);
            buff.append("=");
            if (value == null) {
                throw new RuntimeException("null value for key '" + key + "'");
            }
            //buff.append(Utils.replaceAll(value, " ", "+"));
            buff.append(value.replace(' ', '+'));
        }
        return buff.toString();
    }

    public static String buildPostQueryString(List<NameValue> postParams) {
        StringBuilder buffer = new StringBuilder();
        for (NameValue postParam : postParams) {
            if (buffer.length() > 0) {
                buffer.append("&");
            }
            buffer.append(postParam.m_name);
            buffer.append("=");
            buffer.append(postParam.m_value.replace(' ', '+'));
        }
        return buffer.toString();
    }



    //*************************************************************************
    public static class NameValue {
        public String m_name;
        public String m_value;

        public NameValue (String name, String value) {
            m_name = name;
            m_value = value;
        }
    }
}
