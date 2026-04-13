package com.example.notify.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.notify.entity.TargetConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TargetConfigMapper extends BaseMapper<TargetConfig> {
    
    @Select("SELECT * FROM target_config WHERE target = #{target} AND enabled = TRUE LIMIT 1")
    TargetConfig selectByTarget(@Param("target") String target);
}
