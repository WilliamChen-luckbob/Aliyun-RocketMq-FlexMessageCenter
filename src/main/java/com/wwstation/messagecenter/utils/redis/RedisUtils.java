package com.wwstation.messagecenter.utils.redis;

import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * redis 操作类
 *
 * @author william
 * @description
 * @Date: 2020-12-02 15:56
 */
@Component
@ConditionalOnProperty(prefix = "spring.redis",name = "host")
public class RedisUtils {
    @Autowired
    RedisTemplate<String, Object> redisTemplate;
    @Autowired
    ValueOperations<String, Object> valueOperations;
    @Autowired
    HashOperations<String, String, Object> hashOperations;
    @Autowired
    SetOperations<String, Object> setOperations;
    @Autowired
    StringRedisTemplate stringRedisTemplate;

    private Random random;

    public boolean hasKey(String key) {
        return redisTemplate.hasKey(key);
    }


//    /**
//     * @param key    务必使用值，不要使用表达式否则报错
//     * @param expire
//     * @param unit
//     * @return
//     */
//    public RedisLock newRedisLock(String key, long expire, TimeUnit unit) {
//        expire = randomExpireTime(expire, unit);
//        return RedisLock.newLock(redisTemplate, key, expire, TimeUnit.MILLISECONDS);
//    }

    /**
     * 存值并设定过期时间
     *
     * @param key
     * @param value
     * @param expire
     */
    public void set(String key, Object value, long expire, TimeUnit timeUnit) {
        if (expire != RedisExpireTime.NEVER_EXPIRE.getExpireTime()) {
            expire = randomExpireTime(expire, timeUnit);
            valueOperations.set(key, toJson(value), expire, TimeUnit.MILLISECONDS);
        } else {
            set(key, toJson(value));
        }
    }

    /**
     * 直接存值
     *
     * @param key
     * @param value
     */
    public void set(String key, Object value) {
        valueOperations.set(key, toJson(value));
    }

    /**
     * 取值并为该值续命
     *
     * @param key
     * @param clazz  取值要转成的类型
     * @param expire 续命时间：毫秒
     * @param <T>
     * @return
     */
    public <T> T getAndExpire(String key, Class<T> clazz, long expire) {
        String value = (String) valueOperations.get(key);
        if (StringUtils.isNotEmpty(value) && expire != RedisExpireTime.NEVER_EXPIRE.getExpireTime()) {
            expire = randomExpireTime(expire, TimeUnit.MILLISECONDS);
            redisTemplate.expire(key, expire, TimeUnit.MILLISECONDS);
        }
        return value == null ? null : fromJson(value, clazz);
    }

    /**
     * 直接取值并尝试转成指定格式
     *
     * @param key
     * @param clazz
     * @param <T>
     * @return
     */
    public <T> T getFromJson(String key, Class<T> clazz) {
        String json = getString(key);
        return fromJson(json, clazz);
    }

    public String getString(String key) {
        return (String) valueOperations.get(key);
    }

    /**
     * 直接取值并续命
     *
     * @param key
     * @param expire
     * @return
     */
    public String getAndExpire(String key, long expire) {
        String value = (String) valueOperations.get(key);
        if (StringUtils.isNotEmpty(value) && expire != RedisExpireTime.NEVER_EXPIRE.getExpireTime()) {
            expire = randomExpireTime(expire, TimeUnit.MILLISECONDS);
            redisTemplate.expire(key, expire, TimeUnit.MILLISECONDS);
        }
        return value;
    }

    public void delete(String key) {
        redisTemplate.delete(key);
    }

    public void deleteBatch(Collection<String> keys) {
        Set<String> keys2Remove = new HashSet<>();
        for (String key : keys) {
            if (StringUtils.endsWith(key, "*")) {
                keys2Remove.addAll(redisTemplate.keys(key));
            } else {
                keys2Remove.add(key);
            }
        }
        redisTemplate.delete(keys2Remove);
    }


    public void putMap(String mapName, Map<String, Object> map, RedisExpireTime redisExpireTime) {
        hashOperations.putAll(mapName, map);
        if (redisExpireTime != RedisExpireTime.NEVER_EXPIRE) {
            long expireTime = randomExpireTime(redisExpireTime.getExpireTime(), TimeUnit.SECONDS);
            redisTemplate.expire(mapName, expireTime, TimeUnit.MILLISECONDS);
        }
    }


    public void putMap(String mapName, String key, Object value, RedisExpireTime redisExpireTime) {
        hashOperations.put(mapName, key, value);
        if (redisExpireTime != RedisExpireTime.NEVER_EXPIRE) {
            long expireTime = randomExpireTime(redisExpireTime.getExpireTime(), TimeUnit.SECONDS);
            redisTemplate.expire(mapName, expireTime, TimeUnit.MILLISECONDS);
        }
    }

    public Map getMapAndExpire(String mapName, RedisExpireTime redisExpireTime) {
        Map map = getMap(mapName);
        if (!CollectionUtils.isEmpty(map) && redisExpireTime != RedisExpireTime.NEVER_EXPIRE) {
            long expireTime = randomExpireTime(redisExpireTime.getExpireTime(), TimeUnit.SECONDS);
            redisTemplate.expire(mapName, expireTime, TimeUnit.MILLISECONDS);
        }
        return map;
    }

    public Map getMap(String mapName) {
        return hashOperations.entries(mapName);
    }

    public Boolean hasMapKey(String mapName, String keyName) {
        return hashOperations.hasKey(mapName, keyName);
    }

    public void removeMapObject(String mapName, String key) {
        hashOperations.delete(mapName, key);
    }

    public void removeMapObjectBatch(String mapName, String[] keys) {
        hashOperations.delete(mapName, keys);
    }

    public Object getMapValue(String mapName, String key) {
        return hashOperations.get(mapName, key);
    }

    public Object getMapValueAndExpire(String mapName, String key, RedisExpireTime redisExpireTime) {

        Object o = hashOperations.get(mapName, key);

        if (redisExpireTime != RedisExpireTime.NEVER_EXPIRE) {
            long expireTime = randomExpireTime(redisExpireTime.getExpireTime(), TimeUnit.SECONDS);
            redisTemplate.expire(mapName, expireTime, TimeUnit.MILLISECONDS);
        }
        return o;
    }


    public void putSet(String setName, Object value) {
        setOperations.add(setName, value);
    }

    public void putSet(String setName, Set set, RedisExpireTime redisExpireTime) {
        // 获取key编码方式
        StringRedisSerializer stringRedisSerializer = (StringRedisSerializer) redisTemplate.getKeySerializer();
        //获取值编码方式
        RedisSerializer<Object> valueSerializer = (RedisSerializer<Object>) redisTemplate.getValueSerializer();
        //获取key对应的byte[]
        final byte[] rawKey = stringRedisSerializer.serialize(setName);
        redisTemplate.executePipelined(new RedisCallback() {
            @Override
            public Object doInRedis(RedisConnection redisConnection) throws DataAccessException {
                Iterator iterator = set.iterator();
                while (iterator.hasNext()) {
                    Object next = iterator.next();
                    byte[] value = valueSerializer.serialize(next);
                    redisConnection.sAdd(rawKey, value);
                }
                redisConnection.closePipeline();
                return null;
            }
        });
        if (redisExpireTime != RedisExpireTime.NEVER_EXPIRE) {
            long expireTime = randomExpireTime(redisExpireTime.getExpireTime(), TimeUnit.SECONDS);
            redisTemplate.expire(setName, expireTime, TimeUnit.MILLISECONDS);
        }
    }

    public Set getSet(String key) {
        Set<Object> members = setOperations.members(key);
        if (Set.class.isAssignableFrom(members.getClass())) {
            return (Set) members;
        }
        return null;
    }

//    public void putMultiMap(String folder, List<Map.Entry<String, Map<String, Object>>> maps) {
//        // 获取key编码方式
//        StringRedisSerializer stringRedisSerializer = (StringRedisSerializer) redisTemplate.getKeySerializer();
//        //获取值编码方式
//        RedisSerializer<Object> valueSerializer = (RedisSerializer<Object>) redisTemplate.getValueSerializer();
//        redisTemplate.executePipelined((RedisCallback) redisConnection -> {
//            Iterator<Map.Entry<String, Map<String, Object>>> iterator = maps.iterator();
//            while (iterator.hasNext()) {
//                Map.Entry<String, Map<String, Object>> next = iterator.next();
//                //获取key对应的map名称byte[]
//                final byte[] rawKey = stringRedisSerializer.serialize(folder + ":" + next.getKey());
//                Map<String, Object> value = (Map<String, Object>) next.getValue();
//                for (Map.Entry<String, Object> entry : value.entrySet()) {
//                    byte[] k = stringRedisSerializer.serialize(entry.getKey());
//                    byte[] v = valueSerializer.serialize(entry.getValue());
//                    redisConnection.hashCommands().hSet(rawKey, k, v);
//                }
//                redisConnection.closePipeline();
//            }
//            return null;
//        });
//    }

    /**
     * Object转成JSON数据
     */
    private String toJson(Object object) {
        if (object instanceof Integer || object instanceof Long || object instanceof Float ||
            object instanceof Double || object instanceof Boolean || object instanceof String) {
            return String.valueOf(object);
        }
        return JSONObject.toJSONString(object);
    }

    /**
     * JSON数据，转成Object
     */
    private <T> T fromJson(String json, Class<T> clazz) {
        T targetType;
        try {
            targetType = JSONObject.parseObject(json, clazz);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return targetType;
    }

    public Set keys(String s) {
        return redisTemplate.keys(s);
    }

    public void expire(String key, RedisExpireTime redisExpireTime) {
        long expireTime = randomExpireTime(redisExpireTime.getExpireTime(), TimeUnit.SECONDS);
        redisTemplate.expire(key, expireTime, TimeUnit.SECONDS);
    }

    public Boolean hasSetMember(String setName, Object setValue) {
        return setOperations.isMember(setName, setValue);
    }

    /**
     * 在指定的过期时间的基础上添加一个±20%的随机时间，避免缓存雪崩
     *
     * @param expireTime
     * @return
     */
    private long randomExpireTime(Long expireTime, TimeUnit timeUnit) {
        if (random == null) {
            random = new Random();
        }
        Random random = new Random();
        Long originMilliseconds = TimeUnit.MILLISECONDS.convert(expireTime, timeUnit);

        //20%的浮动区间
        Double r = originMilliseconds * 0.2;
        Long range = r.longValue();

        //基础值
        Long min = originMilliseconds - range;

        //浮动值
        Double ao = random.nextDouble() * range * 2;
        Long addOn = ao.longValue();

        return (min + addOn);
    }
}
