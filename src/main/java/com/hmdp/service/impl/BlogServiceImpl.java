package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author crow
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    IUserService userService;

    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryBlogById(Long id) {
        if (id == null){
            return Result.fail("博客不存在或已删除!");
        }
        Blog blog = getById(id);
        if (blog == null){
            return Result.fail("博客不存在或已删除!");
        }
        queryBlogUser(blog);
        //查询当前博客是否被点赞
        isBlogLiked(blog);
        return Result.ok(blog);
    }

    private void isBlogLiked(Blog blog) {
        //判断当前登录用户是否已经点赞
        UserDTO user = UserHolder.getUser();
        if (user==null){
            //用户未登录
            return;
        }
        Long userId = user.getId();
        //判断当前用户是否点赞
        String key = BLOG_LIKED_KEY + blog.getId();
        Double result = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        blog.setIsLike(result != null);
    }

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            queryBlogUser(blog);
            isBlogLiked(blog);
        });

        return Result.ok(records);
    }

    @Override
    public Result likeBlog(Long id) {
        //判断当前登录用户是否已经点赞
        Long userId = UserHolder.getUser().getId();
        //判断当前用户是否点赞
        String key = BLOG_LIKED_KEY + id;
        Double result = stringRedisTemplate.opsForZSet().score(key, userId.toString());;
        //已经点了,就是取消
        if (result != null) {
            //改数据库
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            if (isSuccess) {
                //改redis
                Long remove = stringRedisTemplate.opsForZSet().remove(key, userId.toString());
                if (remove == null || remove == 0){
                  return Result.fail("点赞操作失败!");
                }
            }
            return Result.ok();
        }
            //如果没有,可以点
            //改数据库
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            //保存redis
            if (isSuccess) {
                Boolean add = stringRedisTemplate.opsForZSet().add(key, userId.toString(),System.currentTimeMillis());
                if (!add) {
                    return Result.fail("点赞操作失败!");
                }
            }
        return Result.ok();
    }

    @Override
    public Result queryBlogLikesUser(Long id) {
        String key = BLOG_LIKED_KEY + id;
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if (top5 == null || top5.isEmpty()){
            return Result.ok();
        }
        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        String join = StrUtil.join(",", ids);
        List<UserDTO> users = userService.query().in("id",ids).last("ORDER BY FIELD(id"+join+")").list()
                .stream().map(user -> {
                    return BeanUtil.copyProperties(user, UserDTO.class);
                })
                .collect(Collectors.toList());
        return Result.ok(users);
    }

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }
}
