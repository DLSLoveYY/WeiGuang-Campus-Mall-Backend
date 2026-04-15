package top.dlsloveyy.backendtest.model.dto;

import lombok.Data;
import jakarta.validation.constraints.NotNull;

@Data
public class OrderCreateDTO {

    @NotNull(message = "商品ID不能为空")
    private Long goodsId;

    @NotNull(message = "请选择交易方式")
    private String deliveryMethod; // 自提 / 校园面交 / 邮寄

    private String deliveryAddress;
}