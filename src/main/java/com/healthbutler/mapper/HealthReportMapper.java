package com.healthbutler.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.healthbutler.entity.HealthReport;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface HealthReportMapper extends BaseMapper<HealthReport> {
}