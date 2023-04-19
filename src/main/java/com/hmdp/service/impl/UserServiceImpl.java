package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author crow
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //校验手机号,如果不符合,返回错误信息
        if (RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号有误!");
        }
        //保存验证码到session
        String code = RandomUtil.randomNumbers(6);
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY+phone,code,LOGIN_CODE_TTL, TimeUnit.MINUTES);
        //发送验证码
        log.debug("短信验证码发送成功,验证码: {}",code);
        //返回ok
        return Result.ok("成功!");
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //校验手机号,如果不符合,返回错误信息
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号有误!");
        }
        //校验验证码或密码
        String code = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY+phone);
        String inputCode = loginForm.getCode();

        if (code == null || !code.equals(inputCode)){
            return Result.fail("验证码有误!");
        }

//        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
//        queryWrapper.eq("phone",phone);
//        User user = getOne(queryWrapper);

        User user = query().eq("phone", phone).one();
        if (user == null){
           user = createUserByPhone(phone);
        }
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        String token = UUID.randomUUID().toString(true);
        String key = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(key,BeanUtil.beanToMap(userDTO,new HashMap<>(),
                CopyOptions.create().setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName,fieldValue) -> fieldValue.toString())));
        stringRedisTemplate.expire(key,LOGIN_USER_TTL,TimeUnit.SECONDS);
        return Result.ok(token);
    }

    @Override
    public Result getUserById(Long userId) {
        User user = getById(userId);
        if (user == null){
            return Result.fail("用户已注销或不存在!");
        }
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        return Result.ok(userDTO);
    }

    private User createUserByPhone(String phone) {
        User user = new User();
        user.setNickName(USER_NICK_NAME_PREFIX+RandomUtil.randomString(10));
        user.setPhone(phone);
        save(user);
        return user;
    }
}
