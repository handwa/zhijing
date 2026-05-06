package com.zhijing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sync_record")
public class SyncRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String dataType;

    private String dataJson;

    private LocalDateTime syncedAt;
}
