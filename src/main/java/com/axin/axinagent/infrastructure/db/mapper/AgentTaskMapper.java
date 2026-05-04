package com.axin.axinagent.infrastructure.db.mapper;

import com.axin.axinagent.infrastructure.db.entity.AgentTaskEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 任务持久化 Mapper。
 */
@Mapper
public interface AgentTaskMapper extends BaseMapper<AgentTaskEntity> {
}
