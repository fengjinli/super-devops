/*
 * Copyright 2017 ~ 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.wl4g.devops.support.cache;

import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.wl4g.devops.common.utils.lang.StringUtils2;
import com.wl4g.devops.common.utils.serialize.JacksonUtils;
import com.wl4g.devops.common.utils.serialize.ObjectUtils;
import com.wl4g.devops.common.utils.serialize.ProtostuffUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.ScanParams;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.wl4g.devops.common.utils.serialize.JacksonUtils.parseJSON;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.trimToEmpty;

public class JedisService {
	final protected Logger log = LoggerFactory.getLogger(getClass());

	private JedisCluster jedisCluster;

	public JedisService(JedisCluster jedisCluster) {
		Assert.isTrue(jedisCluster != null, "Redis cluster object creation failed.");
		this.jedisCluster = jedisCluster;
	}

	public JedisCluster getJedisCluster() {
		return this.jedisCluster;
	}

	public String get(final String key) {
		return (String) doInRedis(cluster -> {
			String value = cluster.get(key);
			value = StringUtils2.isNotBlank(value) && !"nil".equalsIgnoreCase(value) ? value : null;
			if (log.isDebugEnabled())
				log.debug("get {} = {}", key, value);
			return value;
		});
	}

	public String set(final String key, final String value, final int cacheSeconds) {
		return (String) doInRedis(cluster -> {
			String result = null;
			if (cacheSeconds != 0) {
				result = cluster.setex(key, cacheSeconds, value);
			} else {
				result = cluster.set(key, value);
			}
			if (log.isDebugEnabled()) {
				log.debug("set {} = {}", key, value);
			}
			return result;
		});

	}

	public <T> ScanCursor<T> scan(final String pattern, final int batch, final Class<T> clazz) {
		byte[] match = trimToEmpty(pattern).getBytes(Charsets.UTF_8);
		ScanParams params = new ScanParams().count(batch).match(match);
		return new ScanCursor<T>(getJedisCluster(), clazz, params) {
		}.open();
	}

	@SuppressWarnings("unchecked")
	public <T> T getObjectT(final String key, Class<T> clazz) {
		return (T) doInRedis(cluster -> {
			T value = ProtostuffUtils.deserialize(cluster.get(getBytesKey(key)), clazz);
			if (log.isDebugEnabled()) {
				log.debug("getObjectT {} = {}", key, value);
			}
			return value;
		});
	}

	public <T> String setObjectT(final String key, final T value, final int cacheSeconds) {
		return (String) doInRedis(cluster -> {
			String result = null;
			if (cacheSeconds > 0) {
				result = cluster.setex(getBytesKey(key), cacheSeconds, ProtostuffUtils.serialize(value));
			} else {
				result = cluster.set(getBytesKey(key), ProtostuffUtils.serialize(value));
			}
			if (log.isDebugEnabled()) {
				log.debug("setObjectT {} = {}", key, value);
			}
			return result;
		});
	}

	public <T> String setObjectAsJson(final String key, final T value, final int cacheSeconds) {
		return (String) doInRedis(cluster -> {
			String result = null;
			if (cacheSeconds != 0) {
				result = cluster.setex(key, cacheSeconds, JacksonUtils.toJSONString(value));
			} else {
				result = cluster.set(key, JacksonUtils.toJSONString(value));
			}
			if (log.isDebugEnabled()) {
				log.debug("setObjectAsJson {} = {}", key, value);
			}
			return result;
		});
	}

	@SuppressWarnings("unchecked")
	public <T> T getObjectAsJson(final String key, Class<T> clazz) {
		return (T) doInRedis(cluster -> {
			String json = cluster.get(key);
			if (isBlank(json)) {
				return null;
			}
			T value = parseJSON(json, clazz);
			if (log.isDebugEnabled()) {
				log.debug("getObjectAsJson {} = {}", key, value);
			}
			return value;
		});
	}

	public Object getObject(final String key) {
		return doInRedis(cluster -> {
			Object value = toObject(cluster.get(getBytesKey(key)));
			if (log.isDebugEnabled()) {
				log.debug("getObject {} = {}", key, value);
			}
			return value;
		});
	}

	public String setObject(final String key, final Object value, final int cacheSeconds) {
		return (String) doInRedis(cluster -> {
			String result = cluster.set(getBytesKey(key), toBytes(value));
			if (cacheSeconds != 0) {
				cluster.expire(key, cacheSeconds);
			}
			if (log.isDebugEnabled()) {
				log.debug("setObject {} = {}", key, value);
			}
			return result;
		});
	}

	@SuppressWarnings("unchecked")
	public List<String> getList(final String key) {
		return (List<String>) doInRedis(cluster -> {
			List<String> value = cluster.lrange(key, 0, -1);
			if (log.isDebugEnabled()) {
				log.debug("getList {} = {}", key, value);
			}
			return value;
		});
	}

	@SuppressWarnings("unchecked")
	public List<Object> getObjectList(final String key) {
		return (List<Object>) doInRedis(cluster -> {
			List<Object> value = Lists.newArrayList();
			List<byte[]> list = cluster.lrange(getBytesKey(key), 0, -1);
			for (byte[] bs : list) {
				value.add(toObject(bs));
			}
			return value;
		});
	}

	public Long setList(final String key, final List<String> value, final int cacheSeconds) {
		return (Long) doInRedis(cluster -> {
			Long result = cluster.rpush(key, value.toArray(new String[] {}));
			if (cacheSeconds > 0) {
				cluster.expire(key, cacheSeconds);
			}
			if (log.isDebugEnabled()) {
				log.debug("setList {} = {}", key, value);
			}
			return result;
		});
	}

	public Long setObjectList(final String key, final List<Object> value, final int cacheSeconds) {
		return (Long) doInRedis(cluster -> {
			Long result = 0L;
			if (value != null && !value.isEmpty()) {
				byte[][] members = new byte[value.size()][0];
				int i = 0;
				for (Object o : value) {
					members[i] = toBytes(o);
					++i;
				}
				result = cluster.sadd(getBytesKey(key), members);
			}
			if (cacheSeconds != 0) {
				cluster.expire(key, cacheSeconds);
			}
			if (log.isDebugEnabled()) {
				log.debug("setObjectList {} = {}", key, value);
			}
			return result;
		});
	}

	public Long listAdd(final String key, final String... value) {
		return (Long) doInRedis(cluster -> {
			Long result = cluster.rpush(key, value);
			if (log.isDebugEnabled())
				log.debug("listAdd {} = {}", key, value);
			return result;
		});
	}

	public Long listObjectAdd(final String key, final Object... value) {
		return (Long) doInRedis(cluster -> {
			Long result = 0L;
			if (value != null && value.length != 0) {
				byte[][] members = new byte[value.length][0];
				int i = 0;
				for (Object o : value) {
					members[i] = this.toBytes(o);
					++i;
				}
				result = cluster.rpush(getBytesKey(key), members);
			}
			if (log.isDebugEnabled()) {
				log.debug("listObjectAdd {} = {}", key, value);
			}
			return result;
		});
	}

	@SuppressWarnings("unchecked")
	public Set<String> getSet(final String key) {
		return (Set<String>) doInRedis(cluster -> {

			Set<String> value = cluster.smembers(key);
			if (log.isDebugEnabled())
				log.debug("getSet {} = {}", key, value);
			return value;
		});

	}

	@SuppressWarnings("unchecked")
	public <T> Set<T> getObjectSet(final String key) {
		return (Set<T>) doInRedis(cluster -> {

			Set<T> value = Sets.newHashSet();
			Set<byte[]> set = cluster.smembers(getBytesKey(key));
			for (byte[] bs : set) {
				value.add((T) toObject(bs));
			}
			if (log.isDebugEnabled()) {
				log.debug("getObjectSet {} = {}", key, value);
			}
			return value;
		});

	}

	public Long setSet(final String key, final Set<String> value, final int cacheSeconds) {
		return (Long) doInRedis(cluster -> {
			Long result = 0L;
			if (value != null && !value.isEmpty())
				result = cluster.sadd(key, value.toArray(new String[] {}));
			if (cacheSeconds != 0)
				cluster.expire(key, cacheSeconds);
			if (log.isDebugEnabled())
				log.debug("setSet {} = {}", key, value);
			return result;
		});

	}

	/**
	 * Set caching
	 * 
	 * @param key
	 * @param value
	 * @param cacheSeconds
	 *            Time-out, 0 is no time-out
	 * @return
	 */
	public Long setObjectSet(final String key, final Set<Object> value, final int cacheSeconds) {
		return (Long) doInRedis(cluster -> {
			Long result = 0L;
			if (value != null && !value.isEmpty()) {
				byte[][] members = new byte[value.size()][0];
				int i = 0;
				for (Object o : value) {
					members[i] = this.toBytes(o);
					++i;
				}
				result = cluster.sadd(getBytesKey(key), members);
			}
			if (cacheSeconds != 0)
				cluster.expire(key, cacheSeconds);

			if (log.isDebugEnabled()) {
				log.debug("setObjectSet {} = {}", key, value);
			}
			return result;
		});
	}

	/**
	 * Adding values to Set cache
	 * 
	 * @param key
	 * @param value
	 * @return
	 */
	public Long setSetAdd(final String key, final String... value) {

		return (Long) doInRedis(cluster -> {

			Long result = 0L;
			if (value != null && value.length != 0)
				result = cluster.sadd(key, value);
			if (log.isDebugEnabled()) {
				log.debug("setSetAdd {} = {}", key, value);
			}
			return result;
		});

	}

	/**
	 * Adding values to Set cache
	 * 
	 * @param key
	 * @param value
	 * @return
	 */
	public Long setSetObjectAdd(final String key, final Object... value) {
		return (Long) doInRedis(cluster -> {
			Long result = 0L;
			if (value != null && value.length != 0) {
				byte[][] members = new byte[value.length][0];
				int i = 0;
				for (Object o : value) {
					members[i] = toBytes(o);
					++i;
				}
				result = cluster.sadd(getBytesKey(key), members);
			}
			log.debug("setSetObjectAdd {} = {}", key, value);
			return result;
		});

	}

	/**
	 * Delete ordinary members from Set cache
	 * 
	 * @param key
	 * @param members
	 * @return
	 */
	public Long delSetMember(final String key, final String... members) {
		return (Long) doInRedis(cluster -> {
			Long result = 0L;
			if (members != null && members.length != 0)
				result = cluster.srem(key, members);
			if (log.isDebugEnabled()) {
				log.debug("delSetMember {}", key);
			}
			return result;
		});

	}

	/**
	 * Delete Object Members in Set Cache
	 * 
	 * @param key
	 * @param members
	 * @return
	 */
	public Long delSetObjectMember(final String key, final Object... members) {
		return (Long) doInRedis(cluster -> {
			Long result = 0L;
			if (members != null && members.length != 0) {
				byte[][] members0 = new byte[members.length][0];
				int i = 0;
				for (Object o : members) {
					members0[i] = toBytes(o);
					++i;
				}
				result = cluster.srem(getBytesKey(key), members0);
			}
			if (log.isDebugEnabled()) {
				log.debug("delSetMember {}", key);
			}
			return result;
		});
	}

	/**
	 * Getting Map Cache
	 * 
	 * @param key
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public Map<String, String> getMap(final String key) {
		return (Map<String, String>) doInRedis(cluster -> {
			Map<String, String> value = cluster.hgetAll(key);
			if (log.isDebugEnabled()) {
				log.debug("getMap {} = {}", key, value);
			}
			return value;
		});
	}

	/**
	 * Getting Map Cache
	 * 
	 * @param key
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public Map<String, Object> getObjectMap(final String key) {
		return (Map<String, Object>) doInRedis(cluster -> {
			Map<String, Object> value = Maps.newHashMap();
			Map<byte[], byte[]> map = cluster.hgetAll(getBytesKey(key));
			for (Map.Entry<byte[], byte[]> e : map.entrySet()) {
				value.put(StringUtils2.toString(e.getKey()), toObject(e.getValue()));
			}
			if (log.isDebugEnabled()) {
				log.debug("getObjectMap {} = {}", key, value);
			}
			return value;
		});
	}

	/**
	 * Setting up Map Cache
	 * 
	 * @param key
	 * @param value
	 * @param cacheSeconds
	 *            Time-out, 0 is no time-out
	 * @return
	 */
	public String setMap(final String key, final Map<String, String> value, final int cacheSeconds) {
		return (String) doInRedis(cluster -> {
			String result = cluster.hmset(key, value);
			if (cacheSeconds != 0) {
				cluster.expire(key, cacheSeconds);
			}
			if (log.isDebugEnabled()) {
				log.debug("setMap {} = {}", key, value);
			}
			return result;
		});
	}

	/**
	 * Setting up Map Cache
	 * 
	 * @param key
	 * @param value
	 * @param cacheSeconds
	 *            Time-out, 0 is no time-out
	 * @return
	 */
	public String setObjectMap(final String key, final Map<String, Object> value, final int cacheSeconds) {
		return (String) doInRedis(cluster -> {
			Map<byte[], byte[]> map = Maps.newHashMap();
			for (Map.Entry<String, Object> e : value.entrySet()) {
				map.put(getBytesKey(e.getKey()), toBytes(e.getValue()));
			}
			String result = cluster.hmset(getBytesKey(key), (Map<byte[], byte[]>) map);
			if (cacheSeconds != 0) {
				cluster.expire(key, cacheSeconds);
			}
			if (log.isDebugEnabled()) {
				log.debug("setObjectMap {} = {}", key, value);
			}
			return result;
		});
	}

	/**
	 * Adding values to the Map cache
	 * 
	 * @param key
	 * @param value
	 * @return
	 */
	public String mapPut(final String key, final Map<String, String> value) {
		return (String) doInRedis(cluster -> {
			String result = cluster.hmset(key, value);
			if (log.isDebugEnabled()) {
				log.debug("mapPut {} = {}", key, value);
			}
			return result;
		});
	}

	/**
	 * Adding values to the Map cache
	 * 
	 * @param key
	 * @param value
	 * @return
	 */
	public String mapObjectPut(final String key, final Map<String, Object> value) {
		return (String) doInRedis(cluster -> {
			String result = null;
			Map<byte[], byte[]> map = Maps.newHashMap();
			for (Map.Entry<String, Object> e : value.entrySet()) {
				map.put(getBytesKey(e.getKey()), toBytes(e.getValue()));
			}
			result = cluster.hmset(getBytesKey(key), map);
			if (log.isDebugEnabled()) {
				log.debug("mapObjectPut {} = {}", key, value);
			}
			return result;
		});
	}

	/**
	 * Remove the value from the Map cache
	 * 
	 * @param key
	 * @param mapKey
	 * @return
	 */
	public Long mapRemove(final String key, final String mapKey) {
		return (Long) doInRedis(cluster -> {
			Long result = cluster.hdel(key, mapKey);
			if (log.isDebugEnabled()) {
				log.debug("mapRemove {}  {}", key, mapKey);
			}
			return result;
		});
	}

	public Long mapObjectRemove(final String key, final String mapKey) {
		return (Long) doInRedis(cluster -> {
			Long result = cluster.hdel(getBytesKey(key), getBytesKey(mapKey));
			if (log.isDebugEnabled()) {
				log.debug("mapObjectRemove {}  {}", key, mapKey);
			}
			return result;
		});
	}

	public Boolean mapExists(final String key, final String mapKey) {
		return (Boolean) doInRedis(cluster -> {
			Boolean result = cluster.hexists(key, mapKey);
			if (log.isDebugEnabled()) {
				log.debug("mapObjectExists {}  {}", key, mapKey);
			}
			return result;
		});

	}

	public Boolean mapObjectExists(final String key, final String mapKey) {
		return (Boolean) doInRedis(cluster -> {
			Boolean result = cluster.hexists(getBytesKey(key), getBytesKey(mapKey));
			if (log.isDebugEnabled()) {
				log.debug("mapObjectExists {}  {}", key, mapKey);
			}
			return result;
		});

	}

	public Long del(final String key) {
		return (Long) doInRedis(cluster -> {
			Long result = cluster.del(key);
			if (log.isDebugEnabled()) {
				log.debug("del {}", key);
			}
			return result;
		});

	}

	public Long delObject(final String key) {
		return (Long) doInRedis(cluster -> {

			long result = cluster.del(getBytesKey(key));
			if (log.isDebugEnabled()) {
				log.debug("delObject {}", key);
			}
			return result;
		});

	}

	public Boolean exists(final String key) {
		return (Boolean) doInRedis(cluster -> {

			Boolean result = cluster.exists(key);
			if (log.isDebugEnabled())
				log.debug("exists {}", key);
			return result;
		});

	}

	public Boolean existsObject(final String key) {
		return (Boolean) doInRedis(cluster -> {
			boolean result = cluster.exists(getBytesKey(key));
			if (log.isDebugEnabled()) {
				log.debug("existsObject {}", key);
			}
			return result;
		});
	}

	public byte[] getBytesKey(Object object) {
		if (object instanceof String) {
			return StringUtils2.getBytes((String) object);
		} else {
			return ObjectUtils.serialize(object);
		}
	}

	public byte[] toBytes(Object object) {
		return ObjectUtils.serialize(object);
	}

	public Object toObject(byte[] bytes) {
		return ObjectUtils.unserialize(bytes);
	}

	private Object doInRedis(Callback callback) {
		try {
			return callback.execute(jedisCluster);
		} catch (Throwable t) {
			log.error("Redis processing fail.", t);
			throw t;
		} finally {
			// Redis cluster mode does not need to display the release of
			// resources.
		}
	}

	public static abstract interface Callback {
		public Object execute(JedisCluster jedisCluster);
	}

}