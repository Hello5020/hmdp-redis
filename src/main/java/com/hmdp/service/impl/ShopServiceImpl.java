package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSON;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import com.hmdp.utils.SystemConstants;
import org.springframework.data.geo.*;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author crow
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Resource
    CacheClient cacheClient;

    @Override
    public Result queryById(Long id) {
       //缓存穿透
        // Shop shop = queryWithPassTrough(id);

        //互斥锁解决缓存击穿
//        Shop shop = queryWithMutex(id);

        //逻辑过期解决缓存击穿
//        Shop shop = queryWithLogicExpire(id);
        Shop shop = cacheClient.queryWithPassTrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        if (shop == null){
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }
//    public Shop queryWithMutex(Long id){
//        String key = CACHE_SHOP_KEY + id;
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//        if (StrUtil.isNotBlank(shopJson)) {
//            return JSONUtil.toBean(shopJson, Shop.class);
//        }
//        if (shopJson != null){
//            // 返回一个错误信息
//            return null;
//        }
//        //实现缓存重建
//        String lockKey = "lock:shop:"+id;
//        Shop shopInfo = null;
//        try {
//            boolean lock = tryLock(lockKey);
//            if (!lock) {
//                Thread.sleep(50);
//                return queryWithMutex(id);
//            }
//           shopInfo = getById(id);
//            if (shopInfo == null){
//                stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
//                return null;
//            }
//            String jsonStr = JSONUtil.toJsonStr(shopInfo);
//            stringRedisTemplate.opsForValue().set(key,jsonStr,CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        } catch (InterruptedException e) {
//           throw new RuntimeException(e);
//        }finally {
//            //释放互斥锁
//            unLock(lockKey);
//        }
//        return shopInfo;
//    }
//    public Shop queryWithPassTrough(Long id){
//        String key = CACHE_SHOP_KEY + id;
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//        if (StrUtil.isNotBlank(shopJson)) {
//            return JSONUtil.toBean(shopJson, Shop.class);
//        }
//        if (shopJson != null){
//            // 返回一个错误信息
//            return null;
//        }
//        Shop shopInfo = getById(id);
//        if (shopInfo == null){
//            stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
//            return null;
//        }
//        String jsonStr = JSONUtil.toJsonStr(shopInfo);
//        stringRedisTemplate.opsForValue().set(key,jsonStr,CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        return shopInfo;
//    }
//
//    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
//
//    public Shop queryWithLogicExpire(Long id){
//        String key = CACHE_SHOP_KEY + id;
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//        if (StrUtil.isBlank(shopJson)) {
//            return null;
//        }
//        //判断过期时间
//        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
//        JSONObject data = (JSONObject) redisData.getData();
//        Shop shop = JSONUtil.toBean(data, Shop.class);
//        LocalDateTime expireTime = redisData.getExpireTime();
//        if (expireTime.isAfter(LocalDateTime.now())) {
//            return shop;
//        }
//        String lockKey = LOCK_SHOP_KEY + id;
//        boolean lock = tryLock(lockKey);
//        if (lock) {
//            CACHE_REBUILD_EXECUTOR.submit(()->{
//                try {
//                    this.saveShop2Redis(id,30L);
//                } catch (Exception e) {
//                    throw new RuntimeException(e);
//                }finally {
//                    unLock(lockKey);
//                }
//            });
//
//        }
//
//        return shop;
//    }
//    private boolean tryLock(String key){
//        Boolean aBoolean = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
//        return BooleanUtil.isTrue(aBoolean);
//    }
//
//    private void  unLock(String key){
//        stringRedisTemplate.delete(key);
//    }
//
//    public void saveShop2Redis(Long id,Long expireTime){
//        //查询店铺数据
//        Shop shop = getById(id);
//        //封装逻辑过期
//        RedisData redisData = new RedisData();
//        redisData.setData(shop);
//        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireTime));
//        //写入redis
//        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
//    }

    @Override
    @Transactional
    public Result updateShop(Shop shop) {
        // 更新数据库
        updateById(shop);
        // 删除缓存
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不存在!");
        }
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
    }

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        if (x == null || y == null){
            // 根据类型分页查询
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            // 返回数据
            return Result.ok(page.getRecords());
        }
        //计算分页参数
        int from = (current-1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;
        String key = SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo()
                .radius(key, new Circle(x,y,5000),
                        RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
                                .includeDistance().limit(end));
        //解析结果,完成分页
        if (results == null){
            return Result.ok(Collections.emptyList());
        }

        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
        if (list.size()<=from){
            return Result.ok(Collections.emptyList());
        }
        ArrayList<Long> ids = new ArrayList<>(list.size());
        Map<Long,Distance> map = new HashMap<>(list.size());
        list.stream().skip(from).collect(Collectors.toList())
                .forEach(result -> {
                    String shopIdStr = result.getContent().getName();
                    Long id = Long.valueOf(shopIdStr);
                    ids.add(id);
                    Distance distance = result.getDistance();
                    map.put(id,distance);
                });
        String idStr = StrUtil.join(",", ids);
        List<Shop> shops = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
        shops.forEach(shop -> {
            Distance distance = map.get(shop.getId());
            shop.setDistance(distance.getValue());
        });
        return Result.ok(shops);
    }
}
