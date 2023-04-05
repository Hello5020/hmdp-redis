package com.hmdp.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPES_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author crow
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Autowired
    StringRedisTemplate redisTemplate;;

    @Override
    public Result queryTypeList() {
        Set<String> keys = redisTemplate.keys(CACHE_SHOP_TYPES_KEY+"*");
        List<String> shopTypesList = redisTemplate.opsForValue().multiGet(keys);
        List<ShopType> shopTypes = new ArrayList<>();
        List<ShopType> finalShopTypes = shopTypes;
        if (CollectionUtil.isNotEmpty(shopTypesList)){
            shopTypesList.forEach(shopType -> finalShopTypes.add(JSONUtil.toBean(shopType,ShopType.class)));
            return Result.ok(finalShopTypes);
        }
        shopTypes = query().orderByAsc("sort").list();
        if (CollectionUtil.isEmpty(shopTypes)) {
            return Result.fail("数据库中无类型!");
        }
        shopTypes.forEach(shopType ->  shopTypesList.add(JSONUtil.toJsonStr(shopType)));
        Map<String,String> map = new HashMap<>();
        AtomicInteger i = new AtomicInteger();
        shopTypesList.forEach(shopType -> map.put(CACHE_SHOP_TYPES_KEY+i.getAndIncrement(),shopType));
        redisTemplate.opsForValue().multiSet(map);
        return Result.ok(shopTypes);
    }
}
