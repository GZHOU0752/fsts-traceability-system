package com.fsts.trace.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 当前管理员信息（接口 4.2）。
 */
@Data
@Builder
public class AdminInfoVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    private String loginName;

    private String adminName;

    private String phone;

    private String email;

    private LocalDateTime lastLoginTime;
}
