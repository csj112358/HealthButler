package com.healthbutler.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.healthbutler.entity.HealthItem;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface HealthItemMapper extends BaseMapper<HealthItem> {
}