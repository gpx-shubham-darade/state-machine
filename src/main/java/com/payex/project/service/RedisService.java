package com.payex.project.service;

import com.payex.project.config.ConfigLoader;
import redis.clients.jedis.Jedis;

public class RedisService {
    private final Jedis jedis;

    public RedisService() {
        ConfigLoader config = ConfigLoader.loadConfig();
        this.jedis = new Jedis(config.getRedisHost(), config.getRedisPort());
    }

    public void saveState(String orderId, String state) {
        jedis.set(orderId, state);
    }

    public String getState(String orderId) {
        return jedis.get(orderId);
    }

}


