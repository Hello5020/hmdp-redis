package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * @className: RedisIdWorker
 * @author: crowgzy
 * @date: 2023/3/18
 **/
@Component
public class RedisIdWorker {

    private static final  long BEGIN_TIMESTAMP = 1672531200L;

    private static final  long COUNT_BITS = 32L;

    private StringRedisTemplate stringRedisTemplate;

    public RedisIdWorker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public long nextId(String keyPrefix){
        //生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond - BEGIN_TIMESTAMP;

        //生成序列号
        String day = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        //序列号
        long count = stringRedisTemplate.opsForValue().increment("icr" + keyPrefix + ":" + day);

        //拼接时间戳和序列号
        long result = timestamp << COUNT_BITS | count;

        return result;
    }

}
