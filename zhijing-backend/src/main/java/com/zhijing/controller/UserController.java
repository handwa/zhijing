package com.zhijing.controller;

import com.zhijing.entity.User;
import com.zhijing.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserController {

    @Resource
    private UserService userService;

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestParam String deviceId) {
        User user = userService.getOrCreateUser(deviceId);
        Map<String, Object> resp = new HashMap<>();
        resp.put("userId", user.getId());
        resp.put("deviceId", user.getDeviceId());
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/{id}")
    public ResponseEntity<User> getUser(@PathVariable Long id) {
        User user = userService.getUserById(id);
        if (user == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(user);
    }
}
