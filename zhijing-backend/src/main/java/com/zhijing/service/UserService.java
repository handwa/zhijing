package com.zhijing.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zhijing.entity.User;
import com.zhijing.mapper.UserMapper;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;

@Service
public class UserService {

    @Resource
    private UserMapper userMapper;

    public User getOrCreateUser(String deviceId) {
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("device_id", deviceId);
        User user = userMapper.selectOne(wrapper);

        if (user == null) {
            user = new User();
            user.setDeviceId(deviceId);
            user.setCreatedAt(LocalDateTime.now());
            userMapper.insert(user);
        }

        return user;
    }

    public User getUserById(Long id) {
        return userMapper.selectById(id);
    }
}
