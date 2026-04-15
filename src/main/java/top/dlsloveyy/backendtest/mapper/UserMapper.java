package top.dlsloveyy.backendtest.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import top.dlsloveyy.backendtest.entity.User;

@Mapper
public interface UserMapper extends BaseMapper<User> {
    // 继承 BaseMapper 之后，insert, selectById, selectOne 等方法已经自动可用了
    // 除非遇到多表关联或者非常复杂的特殊查询，才需要在这里手写方法
}