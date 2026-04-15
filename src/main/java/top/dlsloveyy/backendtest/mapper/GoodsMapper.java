package top.dlsloveyy.backendtest.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import top.dlsloveyy.backendtest.entity.Goods;

@Mapper
public interface GoodsMapper extends BaseMapper<Goods> {
    // 继承即可拥有 CRUD 神力！
}