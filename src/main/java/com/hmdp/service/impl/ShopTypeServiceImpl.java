package com.hmdp.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.List;

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

    @Resource
    RedisTemplate redisTemplate;

    @Override
    public Result queryTypeList() {
        String key = CACHE_SHOP_TYPES_KEY;
        List<ShopType> shopTypesList = redisTemplate.opsForList().range(key, 0, -1);
        if (CollectionUtil.isNotEmpty(shopTypesList)){
            Result.ok(shopTypesList);
        }
        shopTypesList = query().orderByAsc("sort").list();
        if (CollectionUtil.isEmpty(shopTypesList)) {
            return Result.fail("数据库中无类型!");
        }
        redisTemplate.opsForList().leftPushAll(key, shopTypesList);
        return Result.ok(shopTypesList);
    }
}
