package wrx.xing;

import java.util.*;

public class OrderedProperties extends Properties {

    private final LinkedHashSet<Object> keys = new LinkedHashSet<>();

    @Override
    public Enumeration<Object> keys() {
        return Collections.enumeration(keys);
    }

    @Override
    public Object put(Object key, Object value) {
        keys.add(key);
        return super.put(key, value);
    }

    @Override
    public Set<String> stringPropertyNames() {
        Set<String> set = new LinkedHashSet<>();
        for (Object key : this.keys) {
            set.add((String) key);
        }
        return set;
    }

    @Override
    public Set<Object> keySet() {
        return keys;
    }

    @Override
    public Enumeration<?> propertyNames() {
        return Collections.enumeration(keys);
    }
}
