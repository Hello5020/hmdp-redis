package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.util.concurrent.TimeUnit;

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

    @Override
    public Result queryById(Long id) {
       //缓存穿透
        // Shop shop = queryWithPassTrough(id);

        //互斥锁解决缓存击穿
        Shop shop = queryWithMutex(id);
        if (shop == null){
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }
    public Shop queryWithMutex(Long id){
        String key = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(shopJson)) {
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        if (shopJson != null){
            // 返回一个错误信息
            return null;
        }
        //实现缓存重建
        String lockKey = "lock:shop:"+id;
        Shop shopInfo = null;
        try {
            boolean lock = tryLock(lockKey);
            if (!lock) {
                Thread.sleep(50);
                return queryWithMutex(id);
            }
           shopInfo = getById(id);
            if (shopInfo == null){
                stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            String jsonStr = JSONUtil.toJsonStr(shopInfo);
            stringRedisTemplate.opsForValue().set(key,jsonStr,CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
           throw new RuntimeException(e);
        }finally {
            //释放互斥锁
            unLock(lockKey);
        }
        return shopInfo;
    }
    public Shop queryWithPassTrough(Long id){
        String key = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(shopJson)) {
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        if (shopJson != null){
            // 返回一个错误信息
            return null;
        }
        Shop shopInfo = getById(id);
        if (shopInfo == null){
            stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        String jsonStr = JSONUtil.toJsonStr(shopInfo);
        stringRedisTemplate.opsForValue().set(key,jsonStr,CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return shopInfo;
    }

    private boolean tryLock(String key){
        Boolean aBoolean = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(aBoolean);
    }

    private void  unLock(String key){
        stringRedisTemplate.delete(key);
    }
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
}
