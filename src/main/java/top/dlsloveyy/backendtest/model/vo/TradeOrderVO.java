package top.dlsloveyy.backendtest.model.vo;

import lombok.Data;
import top.dlsloveyy.backendtest.entity.TradeOrder;

@Data
public class TradeOrderVO extends TradeOrder {
    private String goodsTitle;  // 关联的商品标题
    private String goodsImages; // 关联的商品图片
}