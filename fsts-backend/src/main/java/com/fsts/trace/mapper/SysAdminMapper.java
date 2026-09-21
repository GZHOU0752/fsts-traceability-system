package com.fsts.trace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fsts.trace.entity.SysAdmin;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

/**
 * 系统管理员 Mapper。
 */
@Mapper
public interface SysAdminMapper extends BaseMapper<SysAdmin> {

    /**
     * 登录成功后更新最近登录时间（只更新必要字段，避免整行更新带来的行锁开销）。
     */
    @Update("UPDATE sys_admin SET last_login_time = NOW() WHERE id = #{id}")
    int updateLastLoginTime(Long id);
}
