package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * @className: SimpleRedisLock
 * @author: crowgzy
 * @date: 2023/4/1
 **/

public class SimpleRedisLock implements ILock{

    private String name;
    private StringRedisTemplate redisTemplate;
    private static final String KEY_PREFIX = "lock:";

    public SimpleRedisLock(String name, StringRedisTemplate redisTemplate) {
        this.name = name;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean tryLock(long timeoutSec) {

        //获取线程标识
        long id = Thread.currentThread().getId();
        // 获取锁
        Boolean result = redisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + name, id+"", timeoutSec, TimeUnit.SECONDS);

        //防止空指针
        return Boolean.TRUE.equals(result);
    }

    @Override
    public void unlock() {
        //释放锁
        redisTemplate.delete(KEY_PREFIX + name);
    }
}
