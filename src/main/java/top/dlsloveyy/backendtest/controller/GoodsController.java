package top.dlsloveyy.backendtest.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import top.dlsloveyy.backendtest.entity.Goods;
import top.dlsloveyy.backendtest.entity.GoodsFavorite;
import top.dlsloveyy.backendtest.entity.User;
import top.dlsloveyy.backendtest.mapper.GoodsFavoriteMapper;
import top.dlsloveyy.backendtest.mapper.GoodsMapper;
import top.dlsloveyy.backendtest.mapper.UserMapper;
import top.dlsloveyy.backendtest.util.JwtUtil;
import top.dlsloveyy.backendtest.service.AuditService;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/goods")
@RequiredArgsConstructor
public class GoodsController {

    @Autowired
    private GoodsMapper goodsMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private GoodsFavoriteMapper goodsFavoriteMapper;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private AuditService auditService;

    // ==========================================
    // 1. 发布商品 (完美融合：敏感词检测 + 自动/人工审核分流)
    // ==========================================
    @PostMapping("/publish")
    @Transactional
    public ResponseEntity<?> publishGoods(@RequestHeader("Authorization") String token,
                                          @RequestBody Goods goods) {
        String username = jwtUtil.extractUsername(token.replace("Bearer ", ""));
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));

        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("code", 401, "message", "未登录，禁止发布商品"));
        }

        goods.setSellerId(user.getId());
        goods.setViewCount(0);
        goods.setFavoritesCount(0);
        goods.setCreateTime(LocalDateTime.now());
        goods.setUpdateTime(LocalDateTime.now());
        if (goods.getConditionLevel() == null || goods.getConditionLevel().isBlank()) {
            goods.setConditionLevel("成色未知");
        }

        List<String> hits = auditService.findSensitiveHits(goods.getTitle(), goods.getDescription());
        boolean hasSensitive = !hits.isEmpty();

        if (hasSensitive) {
            goods.setStatus(0);
            goodsMapper.insert(goods);
            return ResponseEntity.ok(Map.of(
                    "code", 200,
                    "message", "系统检测到内容疑似违规，商品已转入人工审核池，请耐心等待！",
                    "hits", hits
            ));
        } else {
            goods.setStatus(1);
            goodsMapper.insert(goods);
            return ResponseEntity.ok(Map.of("code", 200, "message", "商品发布成功，已自动上架！", "id", goods.getId()));
        }
    }

    // ==========================================
    // 2. 修改商品 (融合了重新审核机制)
    // ==========================================
    @PutMapping("/update")
    @Transactional
    public ResponseEntity<?> updateGoods(@RequestHeader("Authorization") String token,
                                         @RequestBody Goods updatedGoods) {
        String username = jwtUtil.extractUsername(token.replace("Bearer ", ""));
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));

        Goods original = goodsMapper.selectById(updatedGoods.getId());
        if (original == null) return ResponseEntity.status(404).body(Map.of("code", 404, "message", "商品不存在"));
        if (!original.getSellerId().equals(user.getId())) return ResponseEntity.status(403).body(Map.of("code", 403, "message", "无权修改"));

        List<String> hits = auditService.findSensitiveHits(updatedGoods.getTitle(), updatedGoods.getDescription());
        boolean hasSensitive = !hits.isEmpty();

        // 更新内容
        original.setTitle(updatedGoods.getTitle());
        original.setDescription(updatedGoods.getDescription());
        original.setPrice(updatedGoods.getPrice());
        original.setOriginalPrice(updatedGoods.getOriginalPrice());
        original.setCategory(updatedGoods.getCategory());
        original.setConditionLevel(updatedGoods.getConditionLevel());
        original.setDeliveryMethod(updatedGoods.getDeliveryMethod());
        original.setImages(updatedGoods.getImages());
        original.setUpdateTime(LocalDateTime.now());

        if (hasSensitive) {
            original.setStatus(0); // 再次进入待审核状态
            goodsMapper.updateById(original);
            return ResponseEntity.ok(Map.of("code", 200, "message", "修改内容存在敏感词，已重新提交人工审核"));
        } else {
            original.setStatus(1); // 重新上架
            goodsMapper.updateById(original);
            return ResponseEntity.ok(Map.of("code", 200, "message", "修改成功，商品已更新"));
        }
    }

    // ==========================================
    // 3. 商城大厅列表 (支持分页、分类、关键字搜索)
    // ==========================================
    @GetMapping("/list")
    public ResponseEntity<?> getGoodsList(@RequestParam(defaultValue = "1") Integer page,
                                          @RequestParam(defaultValue = "10") Integer size,
                                          @RequestParam(required = false) String keyword,
                                          @RequestParam(required = false) String category) {
        Page<Goods> goodsPage = new Page<>(page, size);
        LambdaQueryWrapper<Goods> queryWrapper = new LambdaQueryWrapper<>();

        queryWrapper.eq(Goods::getStatus, 1); // 必须是出售中的商品

        if (category != null && !category.isEmpty()) {
            queryWrapper.eq(Goods::getCategory, category);
        }
        if (keyword != null && !keyword.isEmpty()) {
            queryWrapper.and(w -> w.like(Goods::getTitle, keyword).or().like(Goods::getDescription, keyword));
        }

        queryWrapper.orderByDesc(Goods::getCreateTime);
        goodsMapper.selectPage(goodsPage, queryWrapper);

        List<Goods> records = goodsPage.getRecords();
        if (!records.isEmpty()) {
            Set<Long> sellerIds = records.stream().map(Goods::getSellerId).filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet());
            if (!sellerIds.isEmpty()) {
                List<User> sellers = userMapper.selectBatchIds(sellerIds);
                Map<Long, User> sellerMap = sellers.stream().collect(java.util.stream.Collectors.toMap(User::getId, u -> u));
                records.forEach(g -> {
                    User seller = sellerMap.get(g.getSellerId());
                    if (seller != null) {
                        g.setSellerName(seller.getUsername());
                        String avatar = seller.getAvatar();
                        g.setSellerAvatar(avatar == null ? "" : avatar);
                    }
                });
            }
        }

        return ResponseEntity.ok(Map.of("code", 200, "data", records, "total", goodsPage.getTotal()));
    }

    // ==========================================
    // 4. 热度榜单 (融合了你的热度算法)
    // ==========================================
    @GetMapping("/hot")
    public ResponseEntity<?> getHotGoods(@RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "8") int size) {
        // 查出所有在售商品
        List<Goods> allActive = goodsMapper.selectList(new LambdaQueryWrapper<Goods>().eq(Goods::getStatus, 1));

        // 计算热度：浏览量*1.2 + 收藏数*4
        List<Map<String, Object>> hotList = allActive.stream().map(g -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", g.getId());
            map.put("title", g.getTitle());
            map.put("price", g.getPrice());
            map.put("images", g.getImages());
            map.put("score", g.getViewCount() * 1.2 + g.getFavoritesCount() * 4.0);
            return map;
        }).sorted((a, b) -> Double.compare((Double) b.get("score"), (Double) a.get("score"))).toList();

        // 内存分页
        int from = Math.min((page - 1) * size, hotList.size());
        int to = Math.min(from + size, hotList.size());
        return ResponseEntity.ok(Map.of("code", 200, "data", hotList.subList(from, to), "total", hotList.size()));
    }

    // ==========================================
    // 5. 详情与卖家信息聚合
    // ==========================================
    @GetMapping("/detail/{id}")
    public ResponseEntity<?> getGoodsDetail(@PathVariable Long id,
                                            @RequestHeader(value = "Authorization", required = false) String token) {
        Goods goods = goodsMapper.selectById(id);
        if (goods == null) return ResponseEntity.status(404).body(Map.of("code", 404, "message", "商品不存在"));

        // 增加浏览量
        goods.setViewCount(goods.getViewCount() + 1);
        goodsMapper.updateById(goods);

        Map<String, Object> result = new HashMap<>();
        result.put("goods", goods);

        // 封装卖家信息
        User seller = userMapper.selectById(goods.getSellerId());
        if (seller != null) {
            result.put("seller", Map.of(
                    "id", seller.getId(),
                    "username", seller.getUsername(),
                    "avatar", seller.getAvatar() != null ? seller.getAvatar() : "",
                    "creditScore", seller.getCreditScore(),
                    "dormBuilding", seller.getDormBuilding() != null ? seller.getDormBuilding() : "未公开"
            ));
        }

        // 判断当前用户是否已收藏
        boolean isFavorited = false;
        if (token != null && token.startsWith("Bearer ")) {
            String username = jwtUtil.extractUsername(token.substring(7));
            User currentUser = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
            if (currentUser != null) {
                isFavorited = goodsFavoriteMapper.exists(new LambdaQueryWrapper<GoodsFavorite>()
                        .eq(GoodsFavorite::getUserId, currentUser.getId())
                        .eq(GoodsFavorite::getGoodsId, id));
            }
        }
        result.put("isFavorited", isFavorited);

        return ResponseEntity.ok(Map.of("code", 200, "data", result));
    }

    // ==========================================
    // 6. 收藏与取消收藏 (替代原来的 Like)
    // ==========================================
    @PostMapping("/{id}/favorite")
    @Transactional
    public ResponseEntity<?> toggleFavorite(@PathVariable Long id, @RequestHeader("Authorization") String token) {
        String username = jwtUtil.extractUsername(token.replace("Bearer ", ""));
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (user == null) return ResponseEntity.status(401).body(Map.of("code", 401, "message", "未登录"));

        Goods goods = goodsMapper.selectById(id);
        if (goods == null) return ResponseEntity.status(404).body(Map.of("code", 404, "message", "商品不存在"));

        LambdaQueryWrapper<GoodsFavorite> query = new LambdaQueryWrapper<GoodsFavorite>()
                .eq(GoodsFavorite::getUserId, user.getId())
                .eq(GoodsFavorite::getGoodsId, id);

        GoodsFavorite existFav = goodsFavoriteMapper.selectOne(query);

        if (existFav != null) {
            // 已收藏则取消
            goodsFavoriteMapper.deleteById(existFav.getId());
            goods.setFavoritesCount(Math.max(0, goods.getFavoritesCount() - 1));
            goodsMapper.updateById(goods);
            return ResponseEntity.ok(Map.of("code", 200, "message", "已取消想要", "isFavorited", false, "count", goods.getFavoritesCount()));
        } else {
            // 未收藏则添加
            GoodsFavorite fav = new GoodsFavorite();
            fav.setUserId(user.getId());
            fav.setGoodsId(id);
            fav.setCreateTime(LocalDateTime.now());
            goodsFavoriteMapper.insert(fav);

            goods.setFavoritesCount(goods.getFavoritesCount() + 1);
            goodsMapper.updateById(goods);
            return ResponseEntity.ok(Map.of("code", 200, "message", "已添加到我想要的", "isFavorited", true, "count", goods.getFavoritesCount()));
        }
    }

    // ==========================================
    // 7. 我的闲置
    // ==========================================
    @GetMapping("/my-goods")
    public ResponseEntity<?> getMyGoods(@RequestHeader("Authorization") String token) {
        String username = jwtUtil.extractUsername(token.replace("Bearer ", ""));
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));

        List<Goods> myGoods = goodsMapper.selectList(new LambdaQueryWrapper<Goods>()
                .eq(Goods::getSellerId, user.getId())
                .orderByDesc(Goods::getCreateTime));

        return ResponseEntity.ok(Map.of("code", 200, "data", myGoods));
    }

    // ==========================================
    // 8. 我的收藏列表
    // ==========================================
    @GetMapping("/favorites/my")
    public ResponseEntity<?> getMyFavorites(@RequestHeader("Authorization") String token) {
        String username = jwtUtil.extractUsername(token.replace("Bearer ", ""));
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (user == null) return ResponseEntity.status(401).body(Map.of("code", 401, "message", "未登录"));

        List<GoodsFavorite> favorites = goodsFavoriteMapper.selectList(
                new LambdaQueryWrapper<GoodsFavorite>()
                        .eq(GoodsFavorite::getUserId, user.getId())
                        .orderByDesc(GoodsFavorite::getCreateTime)
        );
        if (favorites.isEmpty()) return ResponseEntity.ok(Map.of("code", 200, "data", List.of()));

        List<Long> goodsIds = favorites.stream().map(GoodsFavorite::getGoodsId).toList();
        List<Goods> goodsList = goodsMapper.selectBatchIds(goodsIds);
        return ResponseEntity.ok(Map.of("code", 200, "data", goodsList));
    }
}