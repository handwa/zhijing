package com.zhijing.service;

import com.zhijing.entity.SyncRecord;
import com.zhijing.mapper.SyncRecordMapper;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;

@Service
public class SyncService {

    @Resource
    private SyncRecordMapper syncRecordMapper;

    public void saveSync(Long userId, String dataType, String dataJson) {
        SyncRecord record = new SyncRecord();
        record.setUserId(userId);
        record.setDataType(dataType);
        record.setDataJson(dataJson);
        record.setSyncedAt(LocalDateTime.now());
        syncRecordMapper.insert(record);
    }
}
