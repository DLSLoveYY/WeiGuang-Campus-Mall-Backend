package top.dlsloveyy.backendtest.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import top.dlsloveyy.backendtest.entity.TradeOrder;
import top.dlsloveyy.backendtest.model.vo.TradeOrderVO;

import java.util.List;

@Mapper
public interface TradeOrderMapper extends BaseMapper<TradeOrder> {

    @Select("SELECT o.*, g.title as goodsTitle, g.images as goodsImages " +
            "FROM trade_order o " +
            "LEFT JOIN goods g ON o.goods_id = g.id " +
            "WHERE o.buyer_id = #{userId} " +
            "ORDER BY o.create_time DESC")
    List<TradeOrderVO> selectMyPurchases(@Param("userId") Long userId);

    @Select("SELECT o.*, g.title as goodsTitle, g.images as goodsImages " +
            "FROM trade_order o " +
            "LEFT JOIN goods g ON o.goods_id = g.id " +
            "WHERE o.seller_id = #{userId} " +
            "ORDER BY o.create_time DESC")
    List<TradeOrderVO> selectMySales(@Param("userId") Long userId);

}