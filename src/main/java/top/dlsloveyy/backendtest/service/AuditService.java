package top.dlsloveyy.backendtest.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.dlsloveyy.backendtest.entity.Goods;
import top.dlsloveyy.backendtest.mapper.GoodsMapper;
import top.dlsloveyy.backendtest.util.SensitiveWordFilter;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AuditService {

    @Autowired
    private GoodsMapper goodsMapper;

    // 保留你的敏感词库备份，防止后续需要扩展本地字典
    private static final List<String> SENSITIVE_WORDS = List.of(
            "暴力", "枪支", "毒品", "爆炸", "政治", "辱骂",
            "恐怖", "反动", "攻击", "诈骗", "黑市", "走私"
    );

    /**
     * ✅ 批量自动审核 (状态机版本)
     * 可用于定时任务，或者管理员手动触发：
     * 扫描所有待审核(status=0)的商品，如果由于敏感词库更新导致它现在安全了，就自动上架(status=1)
     */
    @Transactional
    public void autoAuditAll() {
        // 1. 查找所有待审核状态的商品
        List<Goods> pendingGoods = goodsMapper.selectList(
                new LambdaQueryWrapper<Goods>().eq(Goods::getStatus, 0)
        );

        int passCount = 0;
        for (Goods goods : pendingGoods) {
            String risk = assessRisk(goods.getTitle(), goods.getDescription());

            if ("low".equals(risk)) {
                // ✅ 风险低，自动审核通过，原地将状态改为出售中(1)
                goods.setStatus(1);
                goods.setUpdateTime(LocalDateTime.now());
                goodsMapper.updateById(goods);
                passCount++;
            }
            // 🚨 风险高的话就继续保持 0，等管理员去 CheckPostController 里人工处理，或直接驳回(4)
        }

        System.out.println("批量自动审核完成，共扫描 " + pendingGoods.size() + " 个商品，自动通过 " + passCount + " 个。");
    }

    /**
     * 简单关键词自动审核：返回 low / high
     */
    public String assessRisk(String title, String content) {
        List<String> hits = SensitiveWordFilter.findAll((title == null ? "" : title) + " " + (content == null ? "" : content));
        if (!hits.isEmpty()) {
            return "high";
        }
        return "low";
    }

    public List<String> findSensitiveHits(String title, String content) {
        return SensitiveWordFilter.findAll((title == null ? "" : title) + " " + (content == null ? "" : content));
    }

}