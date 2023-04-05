package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IVoucherService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author crow
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService iSeckillVoucherService;

    @Resource
    RedisIdWorker redisIdWorker;

    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Resource
    RedissonClient redissonClient;

    @Override
    public Result seckillVoucher(Long voucherId) {
        //查询优惠券
        SeckillVoucher voucher = iSeckillVoucherService.getById(voucherId);
        //判断是否开始
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())){
            return Result.fail("秒杀未开始!");
        }
        if (voucher.getEndTime().isBefore(LocalDateTime.now())){
            return Result.fail("秒杀已结束!");
        }
        if (voucher.getStock() < 1) {
            return Result.fail("库存不足!");
        }
        //intern()作用返回字符串的值,而不是对比对象地址
        Long userId = UserHolder.getUser().getId();
//        SimpleRedisLock simpleRedisLock = new SimpleRedisLock("order:"+userId,stringRedisTemplate);
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        //获取锁
//        boolean isLock = simpleRedisLock.tryLock(1200);
        boolean isLock = lock.tryLock();
        if (!isLock) {
            return Result.fail("不允许重复下单!");
        }
        //spring事务失效场景:方法内部调用
            /*
             我们看到在事务方法seckillVoucher中，直接调用事务方法createVoucherOrder。从前面介绍的内容可以知道，
             createVoucherOrder方法拥有事务的能力是因为spring aop生成代理了对象，但是这种方法直接
             调用了this对象的方法，所以createVoucherOrder方法不会生成事务。
             */
        //解决方法:在该Service类中使用AopContext.currentProxy()获取代理对象,通过代理对象调用此方法
        // (前提:引入aspectjweaver包,在启动类开启暴露代理对象注解:@EnableAspectJAutoProxy(exposeProxy = true))
        try {
            IVoucherOrderService proxy = (IVoucherOrderService)AopContext.currentProxy();

            return proxy.createVoucherOrder(voucherId);
        }finally {
            //释放锁
//            simpleRedisLock.unlock();
            lock.unlock();
        }

    }

    @Override
    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        //判断库存是否充足
        Long userId = UserHolder.getUser().getId();

            Integer count = query().eq("user_id", userId)
                .eq("voucher_id", voucherId).count();
            if (count > 0) {
                return Result.fail("用戶限量购入一次!");
            }
            boolean result = iSeckillVoucherService.update()
                    .setSql("stock = stock - 1")
                    .eq("voucher_id", voucherId)
                    .gt("stock",0).update();
            if (!result) {
                return Result.fail("券已售空!");
            }
            //创建订单
            VoucherOrder voucherOrder = new VoucherOrder();
            voucherOrder.setUserId(userId);
            voucherOrder.setId(redisIdWorker.nextId("order"));
            voucherOrder.setVoucherId(voucherId);
            save(voucherOrder);
            return Result.ok(voucherOrder.getId());
    }
}
