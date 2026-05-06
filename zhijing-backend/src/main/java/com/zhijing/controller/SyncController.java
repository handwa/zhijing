package com.zhijing.controller;

import com.zhijing.entity.User;
import com.zhijing.service.SyncService;
import com.zhijing.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Map;

@RestController
@RequestMapping("/api/sync")
public class SyncController {

    @Resource
    private SyncService syncService;

    @Resource
    private UserService userService;

    @PostMapping("/push")
    public ResponseEntity<Map<String, String>> push(
            @RequestParam Long userId,
            @RequestParam String dataType,
            @RequestBody String dataJson) {

        User user = userService.getUserById(userId);
        if (user == null) return ResponseEntity.badRequest().build();

        syncService.saveSync(userId, dataType, dataJson);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}
