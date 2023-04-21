package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import javax.annotation.Resource;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    private ShopServiceImpl shopService;

    @Resource
    private CacheClient cacheClient;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Test
    void testSaveShop(){
        Shop shop = shopService.getById(1l);
        cacheClient.setWithLogicExpire(CACHE_SHOP_KEY + 1L,shop,30l, TimeUnit.SECONDS);
    }

    private ExecutorService es = Executors.newFixedThreadPool(500);
    @Test
    void testIdWorker() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(300);

        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                long id = redisIdWorker.nextId("order");
                System.out.println("id = " + id);
            }
            latch.countDown();
        };
        long begin = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }
        latch.await();
        long end = System.currentTimeMillis();
        System.out.println("time = " + (end - begin));
    }

    @Test
    void test(){
        Set<ZSetOperations.TypedTuple<String>> tuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores("feed:1010", 0, 	1681996193103L, 1, 2);
        if (tuples==null){
            System.out.println("wrong");
        }
    }

    @Test
    void loadShopDate(){
        //查询店铺信息
        List<Shop> list = shopService.list();
        //对店铺进行分组按类型
        Map<Long,List<Shop>> shopMap = list.stream().collect(Collectors.groupingBy(shop -> shop.getTypeId()));
        //分批完成写入redis
        for (Map.Entry<Long,List<Shop>> entry:
             shopMap.entrySet()) {
            Long typeId = entry.getKey();
            List<Shop> shopList = entry.getValue();
            String key = "shop:geo:"+typeId;
            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>();
            shopList.forEach(shop -> {
                //stringRedisTemplate.opsForGeo().add(key,new Point(shop.getX(),shop.getY()),shop.getId().toString());
                locations.add(new RedisGeoCommands.GeoLocation<>(shop.getId().toString(),new Point(shop.getX(),shop.getY())));
            });
            stringRedisTemplate.opsForGeo().add(key,locations);
        }

    }
}
