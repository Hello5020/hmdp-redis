package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Service;

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
                return Result.ok();
            }
            return Result.fail("取关失败");
        }else {
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            boolean save = save(follow);
            if (save) {
                return Result.ok();
            }
            return Result.fail("关注失败");
        }

    }
}
