package top.dlsloveyy.backendtest.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import top.dlsloveyy.backendtest.entity.UserAddress;

@Mapper
public interface UserAddressMapper extends BaseMapper<UserAddress> {
}
