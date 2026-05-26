package top.dlsloveyy.backendtest.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import top.dlsloveyy.backendtest.config.JwtFilter;
import top.dlsloveyy.backendtest.entity.Goods;
import top.dlsloveyy.backendtest.entity.TradeOrder;
import top.dlsloveyy.backendtest.entity.TradeReview;
import top.dlsloveyy.backendtest.entity.User;
import top.dlsloveyy.backendtest.mapper.GoodsMapper;
import top.dlsloveyy.backendtest.mapper.TradeOrderMapper;
import top.dlsloveyy.backendtest.mapper.TradeReviewMapper;
import top.dlsloveyy.backendtest.mapper.UserMapper;
import top.dlsloveyy.backendtest.service.UserNotificationService;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/review")
public class TradeReviewController {

    @Autowired
    private TradeReviewMapper tradeReviewMapper;

    @Autowired
    private TradeOrderMapper tradeOrderMapper;

    @Autowired
    private GoodsMapper goodsMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserNotificationService userNotificationService;

    /**
     * 提交评价（买家评卖家）
     * 要求：order.status=3，且同一 orderId 买家只能评一次
     * 请求体：{ orderId, score(1-5), content }
     */
    @PostMapping("/submit")
    public ResponseEntity<?> submitReview(@RequestBody Map<String, Object> body) {
        User currentUser = JwtFilter.currentUser.get();
        if (currentUser == null) {
            return ResponseEntity.status(401).body(Map.of("code", 401, "message", "请先登录"));
        }

        Long orderId = Long.valueOf(body.get("orderId").toString());
        Integer score = Integer.valueOf(body.get("score").toString());
        String content = body.getOrDefault("content", "").toString().trim();

        if (score < 1 || score > 5) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "评分须在 1-5 分之间"));
        }

        // 查订单
        TradeOrder order = tradeOrderMapper.selectById(orderId);
        if (order == null) {
            return ResponseEntity.status(404).body(Map.of("code", 404, "message", "订单不存在"));
        }
        if (order.getStatus() != 3) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "仅交易完成的订单可以评价"));
        }
        if (!order.getBuyerId().equals(currentUser.getId())) {
            return ResponseEntity.status(403).body(Map.of("code", 403, "message", "仅买家可以评价"));
        }

        // 防止重复评价
        boolean alreadyReviewed = tradeReviewMapper.exists(new LambdaQueryWrapper<TradeReview>()
                .eq(TradeReview::getOrderId, orderId)
                .eq(TradeReview::getReviewerId, currentUser.getId()));
        if (alreadyReviewed) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "已评价过该订单，无法重复评价"));
        }

        TradeReview review = new TradeReview();
        review.setOrderId(orderId);
        review.setReviewerId(currentUser.getId());
        review.setRevieweeId(order.getSellerId());
        review.setScore(score);
        review.setContent(content);
        review.setRole("buyer");
        review.setCreateTime(LocalDateTime.now());
        tradeReviewMapper.insert(review);

        User seller = userMapper.selectById(order.getSellerId());
        if (seller != null) {
            int current = seller.getCreditScore() == null ? 100 : seller.getCreditScore();
            int delta = Math.max(-4, Math.min(4, (score - 3) * 2));
            seller.setCreditScore(Math.max(0, Math.min(200, current + delta)));
            userMapper.updateById(seller);
            userNotificationService.notifyUser(
                    seller.getId(),
                    "REVIEW_RECEIVED",
                    "您收到了一条交易评价",
                    "买家已对订单进行评价，当前信用分已同步更新。",
                    "TRADE_REVIEW",
                    review.getId());
        }

        return ResponseEntity.ok(Map.of("code", 200, "message", "评价成功，感谢您的反馈！"));
    }

    /**
     * 查询当前用户是否已评价某订单（供前端控制按钮状态）
     */
    @GetMapping("/check/{orderId}")
    public ResponseEntity<?> checkReviewed(@PathVariable Long orderId) {
        User currentUser = JwtFilter.currentUser.get();
        if (currentUser == null) {
            return ResponseEntity.status(401).body(Map.of("code", 401, "message", "请先登录"));
        }
        boolean reviewed = tradeReviewMapper.exists(new LambdaQueryWrapper<TradeReview>()
                .eq(TradeReview::getOrderId, orderId)
                .eq(TradeReview::getReviewerId, currentUser.getId()));
        return ResponseEntity.ok(Map.of("code", 200, "reviewed", reviewed));
    }

    /**
     * 获取某卖家收到的所有评价（公开接口，供 UserPublic 页面展示）
     */
    @GetMapping("/seller/{sellerId}")
    public ResponseEntity<?> getSellerReviews(@PathVariable Long sellerId) {
        List<TradeReview> reviews = tradeReviewMapper.selectList(
                new LambdaQueryWrapper<TradeReview>()
                        .eq(TradeReview::getRevieweeId, sellerId)
                        .orderByDesc(TradeReview::getCreateTime)
        );

        if (reviews.isEmpty()) {
            return ResponseEntity.ok(Map.of("code", 200, "data", List.of(), "avgScore", 0.0, "total", 0));
        }

        // 计算平均分
        double avgScore = reviews.stream()
                .mapToInt(TradeReview::getScore)
                .average()
                .orElse(0.0);

        // 批量查评价人信息（展示用户名+头像）
        List<Long> reviewerIds = reviews.stream().map(TradeReview::getReviewerId).distinct().collect(Collectors.toList());
        Map<Long, User> reviewerMap = userMapper.selectBatchIds(reviewerIds)
                .stream().collect(Collectors.toMap(User::getId, u -> u));

        // 批量查订单与商品（展示评价对应的商品信息）
        List<Long> orderIds = reviews.stream().map(TradeReview::getOrderId).distinct().collect(Collectors.toList());
        Map<Long, TradeOrder> orderMap = tradeOrderMapper.selectBatchIds(orderIds)
            .stream().collect(Collectors.toMap(TradeOrder::getId, o -> o));
        List<Long> goodsIds = orderMap.values().stream()
            .map(TradeOrder::getGoodsId)
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());
        Map<Long, Goods> goodsMap = goodsIds.isEmpty()
            ? Map.of()
            : goodsMapper.selectBatchIds(goodsIds).stream()
                .collect(Collectors.toMap(Goods::getId, g -> g));

        List<Map<String, Object>> result = reviews.stream().map(r -> {
            Map<String, Object> item = new HashMap<>();
            item.put("id", r.getId());
            item.put("orderId", r.getOrderId());
            item.put("score", r.getScore());
            item.put("content", r.getContent());
            item.put("createTime", r.getCreateTime());
            User reviewer = reviewerMap.get(r.getReviewerId());
            if (reviewer != null) {
                item.put("reviewerName", reviewer.getUsername());
                item.put("reviewerAvatar", reviewer.getAvatar() != null ? reviewer.getAvatar() : "");
            }
            TradeOrder order = orderMap.get(r.getOrderId());
            if (order != null) {
                item.put("goodsId", order.getGoodsId());
                Goods goods = goodsMap.get(order.getGoodsId());
                if (goods != null) {
                    item.put("goodsTitle", goods.getTitle());
                }
            }
            return item;
        }).collect(Collectors.toList());

        // 四舍五入到一位小数
        double roundedAvg = Math.round(avgScore * 10.0) / 10.0;
        return ResponseEntity.ok(Map.of("code", 200, "data", result, "avgScore", roundedAvg, "total", reviews.size()));
    }

    /**
     * 获取某商品对应订单完成后的公开评价（供商品详情页展示）
     */
    @GetMapping("/goods/{goodsId}")
    public ResponseEntity<?> getGoodsReviews(@PathVariable Long goodsId) {
        List<TradeOrder> orders = tradeOrderMapper.selectList(
                new LambdaQueryWrapper<TradeOrder>()
                        .eq(TradeOrder::getGoodsId, goodsId)
                        .orderByDesc(TradeOrder::getFinishTime)
        );

        if (orders.isEmpty()) {
            return ResponseEntity.ok(Map.of("code", 200, "data", List.of(), "avgScore", 0.0, "total", 0));
        }

        List<Long> orderIds = orders.stream().map(TradeOrder::getId).toList();
        Map<Long, TradeOrder> orderMap = orders.stream().collect(Collectors.toMap(TradeOrder::getId, o -> o));

        List<TradeReview> reviews = tradeReviewMapper.selectList(
                new LambdaQueryWrapper<TradeReview>()
                        .in(TradeReview::getOrderId, orderIds)
                        .eq(TradeReview::getRole, "buyer")
                        .orderByDesc(TradeReview::getCreateTime)
        );

        if (reviews.isEmpty()) {
            return ResponseEntity.ok(Map.of("code", 200, "data", List.of(), "avgScore", 0.0, "total", 0));
        }

        double avgScore = reviews.stream()
                .mapToInt(TradeReview::getScore)
                .average()
                .orElse(0.0);

        List<Long> reviewerIds = reviews.stream().map(TradeReview::getReviewerId).distinct().collect(Collectors.toList());
        Map<Long, User> reviewerMap = userMapper.selectBatchIds(reviewerIds)
                .stream().collect(Collectors.toMap(User::getId, u -> u));

        Goods goods = goodsMapper.selectById(goodsId);
        String goodsTitle = goods != null ? goods.getTitle() : ("商品 #" + goodsId);

        List<Map<String, Object>> result = reviews.stream().map(r -> {
            Map<String, Object> item = new HashMap<>();
            item.put("id", r.getId());
            item.put("orderId", r.getOrderId());
            item.put("score", r.getScore());
            item.put("content", r.getContent());
            item.put("createTime", r.getCreateTime());
            item.put("goodsId", goodsId);
            item.put("goodsTitle", goodsTitle);
            TradeOrder order = orderMap.get(r.getOrderId());
            if (order != null) {
                item.put("finishTime", order.getFinishTime());
            }
            User reviewer = reviewerMap.get(r.getReviewerId());
            if (reviewer != null) {
                item.put("reviewerName", reviewer.getUsername());
                item.put("reviewerAvatar", reviewer.getAvatar() != null ? reviewer.getAvatar() : "");
            }
            return item;
        }).collect(Collectors.toList());

        double roundedAvg = Math.round(avgScore * 10.0) / 10.0;
        return ResponseEntity.ok(Map.of("code", 200, "data", result, "avgScore", roundedAvg, "total", reviews.size()));
    }
}
