package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import jdk.nashorn.internal.ir.annotations.Reference;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author crow
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IUserService userService;

    @Override
    public Result isFollowed(long followUserId) {
        UserDTO user = UserHolder.getUser();
        if (user == null){
            return Result.fail("请先登录");
        }
        Long userId = user.getId();
        Integer follow = query().eq("follow_user_id", followUserId).eq("user_id", userId).count();
        return Result.ok(follow > 0);
    }

    @Override
    public Result follow(Long followUserId, Boolean isFollowed) {
        UserDTO user = UserHolder.getUser();
        if (user == null){
            return Result.fail("请先登录");
        }
        Long userId = user.getId();
        if (!isFollowed){
            QueryWrapper<Follow> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("follow_user_id", followUserId).eq("user_id", userId);
            boolean remove = remove(queryWrapper);
            if (remove) {
                String key = "follows:" + userId;
                stringRedisTemplate.opsForSet().remove(key, String.valueOf(followUserId));
                return Result.ok();
            }
            return Result.fail("取关失败");
        }else {
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            boolean save = save(follow);
            if (save) {
                String key = "follows:" + userId;
                stringRedisTemplate.opsForSet().add(key, String.valueOf(followUserId));
                return Result.ok();
            }
            return Result.fail("关注失败");
        }

    }

    @Override
    public Result isCommon(Long followUserId) {
        UserDTO user = UserHolder.getUser();
        if (user == null){
            return Result.fail("请先登录!");
        }
        String key2 = "follows:" + user.getId();
        String key1 = "follows:" + followUserId;
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key1, key2);
        if (intersect == null || intersect.isEmpty()){
            return Result.ok();
        }
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        List<User> users = userService.listByIds(ids);
        List<UserDTO> userDTOS = BeanUtil.copyToList(users, UserDTO.class);
        return Result.ok(userDTOS);
    }
}
