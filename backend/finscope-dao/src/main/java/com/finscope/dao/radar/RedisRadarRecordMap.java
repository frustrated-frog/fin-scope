package com.finscope.dao.radar;

import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 一次 DAO 操作持有的按需读取视图；嵌套对象的修改也会在提交时比较原始 JSON。 */
final class RedisRadarRecordMap<K, V> extends AbstractMap<K, V> {
    private final RedisRadarCacheSession session;
    private final RadarCacheLayout.Section section;
    private final Map<K, V> loaded = new LinkedHashMap<>();
    private final Map<K, String> originals = new LinkedHashMap<>();
    private boolean fullyLoaded;

    RedisRadarRecordMap(RedisRadarCacheSession session, RadarCacheLayout.Section section) {
        this.session = session;
        this.section = section;
    }

    @Override
    @SuppressWarnings("unchecked")
    public V get(Object key) {
        if (key == null) {
            return null;
        }
        K typedKey = (K) key;
        prefetch(List.of(typedKey));
        return loaded.get(typedKey);
    }

    @Override
    public boolean containsKey(Object key) {
        return get(key) != null;
    }

    @Override
    public V put(K key, V value) {
        V previous = get(key);
        loaded.put(key, value);
        return previous;
    }

    @Override
    @SuppressWarnings("unchecked")
    public V remove(Object key) {
        V previous = get(key);
        loaded.put((K) key, null);
        return previous;
    }

    void prefetch(Collection<K> keys) {
        List<K> missing = keys.stream().filter(java.util.Objects::nonNull)
                .filter(key -> !loaded.containsKey(key)).distinct().toList();
        for (int start = 0; start < missing.size(); start += RadarCacheLayout.BATCH_SIZE) {
            List<K> batch = missing.subList(start, Math.min(start + RadarCacheLayout.BATCH_SIZE, missing.size()));
            List<String> payloads = session.readRecords(section, batch);
            session.prefetchDependencies(section, batch, payloads);
            for (int index = 0; index < batch.size(); index++) {
                K key = batch.get(index);
                String payload = payloads.get(index);
                V value = session.decode(section, key, payload);
                originals.put(key, payload);
                loaded.put(key, value);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void loadAll() {
        if (fullyLoaded) {
            return;
        }
        Set<String> members = session.members(section);
        List<K> keys = new ArrayList<>();
        for (String member : members) {
            String id = new String(java.util.Base64.getUrlDecoder().decode(member), StandardCharsets.UTF_8);
            keys.add((K) section.parseKey(id));
        }
        prefetch(keys);
        fullyLoaded = true;
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        loadAll();
        return new AbstractSet<>() {
            @Override
            public Iterator<Entry<K, V>> iterator() {
                List<K> keys = new ArrayList<>();
                for (Entry<K, V> entry : loaded.entrySet()) {
                    if (entry.getValue() != null) {
                        keys.add(entry.getKey());
                    }
                }
                Iterator<K> iterator = keys.iterator();
                return new Iterator<>() {
                    private K current;

                    @Override
                    public boolean hasNext() {
                        return iterator.hasNext();
                    }

                    @Override
                    public Entry<K, V> next() {
                        current = iterator.next();
                        K key = current;
                        return new SimpleEntry<>(key, loaded.get(key)) {
                            @Override
                            public V setValue(V value) {
                                V previous = loaded.put(key, value);
                                super.setValue(value);
                                return previous;
                            }
                        };
                    }

                    @Override
                    public void remove() {
                        if (current == null) {
                            throw new IllegalStateException("尚未选择 Redis 记录");
                        }
                        loaded.put(current, null);
                        current = null;
                    }
                };
            }

            @Override
            public int size() {
                return (int) loaded.values().stream().filter(java.util.Objects::nonNull).count();
            }
        };
    }

    List<RedisRadarCacheSession.Change> changes() {
        List<RedisRadarCacheSession.Change> result = new ArrayList<>();
        // deadline 可能按需读取父记录，因此先冻结当前条目的键。
        for (K key : new LinkedHashSet<>(loaded.keySet())) {
            V value = loaded.get(key);
            String payload = value == null ? null : session.encode(value);
            if (java.util.Objects.equals(payload, originals.get(key))) {
                continue;
            }
            long deadline = value == null ? 0L : session.deadline(section, key, value);
            String parent = session.parentKey(section, key, value);
            if (value != null && deadline == 0L && !parent.isEmpty()) {
                throw new IllegalStateException("雷达父记录不存在或已过期，拒绝写入悬空关联");
            }
            result.add(new RedisRadarCacheSession.Change(section, key, deadline, payload,
                    parent));
        }
        return result;
    }
}
