package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author crow
 * @since 2021-12-22
 */
@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService iSeckillVoucherService;

    @Resource
    RedisIdWorker redisIdWorker;

    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Resource
    RedissonClient redissonClient;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    private BlockingQueue<VoucherOrder> orderTask = new ArrayBlockingQueue<>(1024 * 1024);
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    // @PostConstruct在类初始化完成后执行
    @PostConstruct
    private void init(){
        SECKILL_ORDER_EXECUTOR.submit(new VousherOrderHandler());
    }

    private class VousherOrderHandler implements Runnable{
        @Override
        public void run() {
            while (true){

                try {
                    //获取队列中的订单信息
                    VoucherOrder voucherOrder = orderTask.take();
                    //创建订单
                    handleVoucherOrder(voucherOrder);

                } catch (InterruptedException e) {
                    log.error("处理订单异常",e);
                }
            }
        }
    }

    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        //获取用户
        Long userId = voucherOrder.getUserId();
        //获取锁
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        boolean isLock = lock.tryLock();
        if (!isLock) {
            log.error("用户不能重复下单!");
            return;
        }
        try {
            proxy.createVoucherOrder(voucherOrder);
        }finally {
            //释放锁
//            simpleRedisLock.unlock();
            lock.unlock();
        }
    }

    private IVoucherOrderService proxy;
    @Override
    public Result seckillVoucher(Long voucherId) {
        //执行lua脚本
        //execute()传三个参数,第一个为脚本,第二个为key参数,第三个为AGV参数
        Long userId = UserHolder.getUser().getId();
        Long result = stringRedisTemplate.execute(SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString()
        );
        int r = result.intValue();
        if (r != 0) {
            return Result.fail(r == 1 ? "库存不足!":"用户不能重复下单!");
        }
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setUserId(userId);
        voucherOrder.setId(redisIdWorker.nextId("order"));
        voucherOrder.setVoucherId(voucherId);
        //保存阻塞队列
        long orderId = redisIdWorker.nextId("order");
        orderTask.add(voucherOrder);
        //获取代理对象
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        //返回id
        return Result.ok(orderId);
    }

//    @Override
//    public Result seckillVoucher(Long voucherId) {
//        //查询优惠券
//        SeckillVoucher voucher = iSeckillVoucherService.getById(voucherId);
//        //判断是否开始
//        if (voucher.getBeginTime().isAfter(LocalDateTime.now())){
//            return Result.fail("秒杀未开始!");
//        }
//        if (voucher.getEndTime().isBefore(LocalDateTime.now())){
//            return Result.fail("秒杀已结束!");
//        }
//        if (voucher.getStock() < 1) {
//            return Result.fail("库存不足!");
//        }
//        //intern()作用返回字符串的值,而不是对比对象地址
//        Long userId = UserHolder.getUser().getId();
////        SimpleRedisLock simpleRedisLock = new SimpleRedisLock("order:"+userId,stringRedisTemplate);
//        RLock lock = redissonClient.getLock("lock:order:" + userId);
//        //获取锁
////        boolean isLock = simpleRedisLock.tryLock(1200);
//        boolean isLock = lock.tryLock();
//        if (!isLock) {
//            return Result.fail("不允许重复下单!");
//        }
//        //spring事务失效场景:方法内部调用
//            /*
//             我们看到在事务方法seckillVoucher中，直接调用事务方法createVoucherOrder。从前面介绍的内容可以知道，
//             createVoucherOrder方法拥有事务的能力是因为spring aop生成代理了对象，但是这种方法直接
//             调用了this对象的方法，所以createVoucherOrder方法不会生成事务。
//             */
//        //解决方法:在该Service类中使用AopContext.currentProxy()获取代理对象,通过代理对象调用此方法
//        // (前提:引入aspectjweaver包,在启动类开启暴露代理对象注解:@EnableAspectJAutoProxy(exposeProxy = true))
//        try {
//            IVoucherOrderService proxy = (IVoucherOrderService)AopContext.currentProxy();
//
//            return proxy.createVoucherOrder(voucherId);
//        }finally {
//            //释放锁
////            simpleRedisLock.unlock();
//            lock.unlock();
//        }
//
//    }

    @Override
    @Transactional(rollbackFor=Exception.class)
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        //判断库存是否充足
        Long userId = voucherOrder.getUserId();

            Integer count = query().eq("user_id", userId)
                .eq("voucher_id", voucherOrder.getVoucherId()).count();
            if (count > 0) {
                log.error("用戶限量购入一次!");
                return;
            }
            boolean result = iSeckillVoucherService.update()
                    .setSql("stock = stock - 1")
                    .eq("voucher_id", voucherOrder.getVoucherId())
                    .gt("stock",0).update();
            if (!result) {
                log.error("券已售空!");
                return;
            }
            save(voucherOrder);
    }

//    @Transactional
//    public Result createVoucherOrder(Long voucherId) {
//        // 5.一人一单
//        Long userId = UserHolder.getUser().getId();
//
//        // 创建锁对象
//        RLock redisLock = redissonClient.getLock("lock:order:" + userId);
//        // 尝试获取锁
//        boolean isLock = redisLock.tryLock();
//        // 判断
//        if(!isLock){
//            // 获取锁失败，直接返回失败或者重试
//            return Result.fail("不允许重复下单！");
//        }
//
//        try {
//            // 5.1.查询订单
//            int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
//            // 5.2.判断是否存在
//            if (count > 0) {
//                // 用户已经购买过了
//                return Result.fail("用户已经购买过一次！");
//            }
//
//            // 6.扣减库存
//            boolean success = seckillVoucherService.update()
//                    .setSql("stock = stock - 1") // set stock = stock - 1
//                    .eq("voucher_id", voucherId).gt("stock", 0) // where id = ? and stock > 0
//                    .update();
//            if (!success) {
//                // 扣减失败
//                return Result.fail("库存不足！");
//            }
//
//            // 7.创建订单
//            VoucherOrder voucherOrder = new VoucherOrder();
//            // 7.1.订单id
//            long orderId = redisIdWorker.nextId("order");
//            voucherOrder.setId(orderId);
//            // 7.2.用户id
//            voucherOrder.setUserId(userId);
//            // 7.3.代金券id
//            voucherOrder.setVoucherId(voucherId);
//            save(voucherOrder);
//
//            // 7.返回订单id
//            return Result.ok(orderId);
//        } finally {
//            // 释放锁
//            redisLock.unlock();
//        }
//
//    }
}
