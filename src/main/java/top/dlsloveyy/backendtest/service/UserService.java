package top.dlsloveyy.backendtest.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.dlsloveyy.backendtest.entity.Goods;
import top.dlsloveyy.backendtest.entity.User;
import top.dlsloveyy.backendtest.mapper.GoodsMapper;
import top.dlsloveyy.backendtest.mapper.UserMapper;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService extends ServiceImpl<UserMapper, User> {

    private final GoodsMapper goodsMapper;
    private final UserMapper userMapper;

    /**
     * 核心逻辑：增加用户余额（用于订单结算）
     * @param userId 卖家ID
     * @param amount 增加的金额 (sellerIncome)
     */
    @Transactional(rollbackFor = Exception.class)
    public void increaseBalance(Long userId, BigDecimal amount) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("结算失败：卖家用户不存在");
        }

        // 1. 既然 balance 已经是 BigDecimal 了，直接获取即可
        BigDecimal currentBalance = (user.getBalance() != null) ? user.getBalance() : BigDecimal.ZERO;

        // 2. 进行高精度加法运算
        BigDecimal newBalance = currentBalance.add(amount);

        // 3. 直接存入，不再需要 .doubleValue()
        user.setBalance(newBalance);

        userMapper.updateById(user);
    }

    /**
     * 原有逻辑：获取用户社区积分
     */
    public int getUserPrints(Long userId) {
        // 1. 查询该用户发布的所有商品
        List<Goods> myGoods = goodsMapper.selectList(
                new LambdaQueryWrapper<Goods>()
                        .eq(Goods::getSellerId, userId)
                        .select(Goods::getId, Goods::getFavoritesCount, Goods::getCommentCount)
        );

        if (myGoods == null || myGoods.isEmpty()) {
            return 0;
        }

        // 2. 统计数据
        int goodsCount = myGoods.size();
        int favoriteCount = 0;
        int commentCount = 0;

        for (Goods goods : myGoods) {
            favoriteCount += (goods.getFavoritesCount() != null ? goods.getFavoritesCount() : 0);
            commentCount += (goods.getCommentCount() != null ? goods.getCommentCount() : 0);
        }

        // 3. 计算社区积分
        return goodsCount * 5 + favoriteCount * 2 + commentCount * 3;
    }
    /**
     * 核心逻辑：扣减用户余额（用于提现或余额支付）
     */
    @Transactional(rollbackFor = Exception.class)
    public void decreaseBalance(Long userId, BigDecimal amount) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("操作失败：用户不存在");
        }

        BigDecimal currentBalance = (user.getBalance() != null) ? user.getBalance() : BigDecimal.ZERO;

        // 校验余额是否充足
        if (currentBalance.compareTo(amount) < 0) {
            throw new RuntimeException("账户余额不足");
        }

        // 执行扣减
        BigDecimal newBalance = currentBalance.subtract(amount);
        user.setBalance(newBalance);

        userMapper.updateById(user);
    }
}