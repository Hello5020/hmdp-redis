package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
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
    private static final String ID_PREFIX = UUID.randomUUID().toString(true)+"-";
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    public SimpleRedisLock(String name, StringRedisTemplate redisTemplate) {
        this.name = name;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean tryLock(long timeoutSec) {

        //获取线程标识
        String id = ID_PREFIX + Thread.currentThread().getId();

        // 获取锁
        Boolean result = redisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + name, id, timeoutSec, TimeUnit.SECONDS);

        //防止空指针
        return Boolean.TRUE.equals(result);
    }

    @Override
    public void unlock() {
        //调用lua脚本
        redisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(KEY_PREFIX + name),
                ID_PREFIX + Thread.currentThread().getId()
                );
    }
//    @Override
//    public void unlock() {
//        String id1 = ID_PREFIX + Thread.currentThread().getId();
//        String id = redisTemplate.opsForValue().get(KEY_PREFIX + name);
//        if (id.equals(id1)) {
//            //释放锁
//            redisTemplate.delete(KEY_PREFIX + name);
//        }
//    }
}
