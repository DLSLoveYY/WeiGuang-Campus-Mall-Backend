package top.dlsloveyy.backendtest.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import top.dlsloveyy.backendtest.entity.Follow;

@Mapper
public interface FollowMapper extends BaseMapper<Follow> {
}