package top.dlsloveyy.backendtest.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import top.dlsloveyy.backendtest.entity.Goods;
import top.dlsloveyy.backendtest.entity.GoodsFavorite;
import top.dlsloveyy.backendtest.entity.GoodsVariant;
import top.dlsloveyy.backendtest.entity.User;
import top.dlsloveyy.backendtest.mapper.GoodsDraftMapper;
import top.dlsloveyy.backendtest.mapper.GoodsFavoriteMapper;
import top.dlsloveyy.backendtest.mapper.GoodsMapper;
import top.dlsloveyy.backendtest.mapper.GoodsVariantMapper;
import top.dlsloveyy.backendtest.mapper.TradeOrderMapper;
import top.dlsloveyy.backendtest.mapper.UserMapper;
import top.dlsloveyy.backendtest.service.AuditService;
import top.dlsloveyy.backendtest.service.UserAddressService;
import top.dlsloveyy.backendtest.util.JwtUtil;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
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
    private GoodsDraftMapper goodsDraftMapper;
    @Autowired
    private GoodsVariantMapper goodsVariantMapper;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private AuditService auditService;
    @Autowired
    private UserAddressService userAddressService;
    @Autowired
    private TradeOrderMapper tradeOrderMapper;

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
        Long draftId = goods.getId();
        goods.setId(null);
        goods.setViewCount(0);
        goods.setFavoritesCount(0);
        goods.setCreateTime(LocalDateTime.now());
        goods.setUpdateTime(LocalDateTime.now());
        if (goods.getConditionLevel() == null || goods.getConditionLevel().isBlank()) {
            goods.setConditionLevel("成色未知");
        }

        try {
            normalizeGoodsDeliveryMethods(goods);
            fillTradeCoordinatesIfNeeded(goods);
            prepareGoodsForVariants(goods);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", e.getMessage()));
        }

        List<String> hits = auditService.findSensitiveHits(goods.getTitle(), goods.getDescription());
        boolean hasSensitive = !hits.isEmpty();

        if (hasSensitive) {
            goods.setStatus(0);
            goodsMapper.insert(goods);
            saveGoodsVariants(goods);
            deleteLoadedDraft(user.getId(), draftId);
            return ResponseEntity.ok(Map.of(
                    "code", 200,
                    "message", "系统检测到内容疑似违规，商品已转入人工审核池，请耐心等待！",
                    "hits", hits
            ));
        } else {
            goods.setStatus(1);
            goodsMapper.insert(goods);
            saveGoodsVariants(goods);
            deleteLoadedDraft(user.getId(), draftId);
            return ResponseEntity.ok(Map.of("code", 200, "message", "商品发布成功，已自动上架！", "id", goods.getId()));
        }
    }

    private void deleteLoadedDraft(Long sellerId, Long draftId) {
        if (draftId != null) {
            goodsDraftMapper.delete(new LambdaQueryWrapper<top.dlsloveyy.backendtest.entity.GoodsDraft>()
                .eq(top.dlsloveyy.backendtest.entity.GoodsDraft::getId, draftId)
                .eq(top.dlsloveyy.backendtest.entity.GoodsDraft::getSellerId, sellerId));
            return;
        }
        goodsDraftMapper.delete(new LambdaQueryWrapper<top.dlsloveyy.backendtest.entity.GoodsDraft>()
            .eq(top.dlsloveyy.backendtest.entity.GoodsDraft::getSellerId, sellerId));
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
        original.setStock(updatedGoods.getStock());
        original.setCategory(updatedGoods.getCategory());
        original.setConditionLevel(updatedGoods.getConditionLevel());
        original.setDeliveryMethod(updatedGoods.getDeliveryMethod());
        original.setDeliveryMethods(updatedGoods.getDeliveryMethods());
        original.setTradeAddress(updatedGoods.getTradeAddress());
        original.setTradeLng(updatedGoods.getTradeLng());
        original.setTradeLat(updatedGoods.getTradeLat());
        original.setTradeGeoSource(updatedGoods.getTradeGeoSource());
        original.setVariants(updatedGoods.getVariants());
        try {
            normalizeGoodsDeliveryMethods(original);
            fillTradeCoordinatesIfNeeded(original);
            prepareGoodsForVariants(original);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", e.getMessage()));
        }
        original.setImages(updatedGoods.getImages());
        original.setUpdateTime(LocalDateTime.now());

        if (hasSensitive) {
            original.setStatus(0); // 再次进入待审核状态
            goodsMapper.updateById(original);
            replaceGoodsVariants(original);
            return ResponseEntity.ok(Map.of("code", 200, "message", "修改内容存在敏感词，已重新提交人工审核"));
        } else {
            original.setStatus(1); // 重新上架
            goodsMapper.updateById(original);
            replaceGoodsVariants(original);
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
                                          @RequestParam(required = false) String category,
                                          @RequestParam(required = false) String conditionLevel,
                                          @RequestParam(required = false) String deliveryMethod,
                                          @RequestParam(required = false) BigDecimal minPrice,
                                          @RequestParam(required = false) BigDecimal maxPrice,
                                          @RequestParam(required = false) String sort,
                                          @RequestParam(required = false, defaultValue = "none") String nearbyMode,
                                          @RequestParam(required = false) BigDecimal refLng,
                                          @RequestParam(required = false) BigDecimal refLat,
                                          @RequestParam(required = false) BigDecimal maxDistanceKm,
                                          @RequestParam(required = false, defaultValue = "asc") String distanceSort) {
        LambdaQueryWrapper<Goods> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Goods::getStatus, 1);

        if (category != null && !category.isEmpty()) {
            List<String> categories = Arrays.stream(category.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
            if (!categories.isEmpty()) {
                queryWrapper.in(Goods::getCategory, categories);
            }
        }
        if (conditionLevel != null && !conditionLevel.isEmpty()) {
            List<String> conditionLevels = Arrays.stream(conditionLevel.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
            if (!conditionLevels.isEmpty()) {
                queryWrapper.in(Goods::getConditionLevel, conditionLevels);
            }
        }
        if (deliveryMethod != null && !deliveryMethod.isEmpty()) {
            queryWrapper.like(Goods::getDeliveryMethods, deliveryMethod);
        }
        if (minPrice != null) {
            queryWrapper.ge(Goods::getPrice, minPrice);
        }
        if (maxPrice != null) {
            queryWrapper.le(Goods::getPrice, maxPrice);
        }

        String normalizedKeyword = normalizeQueryText(keyword);
        boolean enableNearby = "buyer_profile".equalsIgnoreCase(nearbyMode) || "custom_point".equalsIgnoreCase(nearbyMode);
        boolean hasReferencePoint = refLng != null && refLat != null;

        List<Goods> baseGoods = new ArrayList<>(goodsMapper.selectList(queryWrapper));
        attachSellerInfo(baseGoods);

        Set<Long> sellerIdsByKeyword = Collections.emptySet();
        List<Goods> matched = baseGoods;
        if (normalizedKeyword != null) {
            Set<Long> matchedSellerIds = findSellerIdsByUsernameLike(normalizedKeyword);
            sellerIdsByKeyword = matchedSellerIds;
            matched = baseGoods.stream()
                    .filter(goods -> matchesBasicKeyword(goods, normalizedKeyword, matchedSellerIds))
                    .toList();
        }

        if (enableNearby && hasReferencePoint && "校园面交".equals(deliveryMethod)) {
            boolean hasAnyGeoGoods = matched.stream().anyMatch(item -> item.getTradeLng() != null && item.getTradeLat() != null);
            if (hasAnyGeoGoods) {
                matched = matched.stream()
                        .filter(item -> item.getTradeLng() != null && item.getTradeLat() != null)
                        .peek(item -> item.setDistanceKm(calcDistanceKm(refLat, refLng, item.getTradeLat(), item.getTradeLng())))
                        .filter(item -> maxDistanceKm == null || item.getDistanceKm().compareTo(maxDistanceKm) <= 0)
                        .sorted((a, b) -> {
                            BigDecimal da = a.getDistanceKm() == null ? new BigDecimal("999999") : a.getDistanceKm();
                            BigDecimal db = b.getDistanceKm() == null ? new BigDecimal("999999") : b.getDistanceKm();
                            return "desc".equalsIgnoreCase(distanceSort) ? db.compareTo(da) : da.compareTo(db);
                        })
                        .toList();
            }
        } else if (normalizedKeyword != null && !normalizedKeyword.isBlank() && (sort == null || sort.isBlank() || "smart".equalsIgnoreCase(sort))) {
            matched = matched.stream().sorted((a, b) -> Integer.compare(calculateSearchScore(b, normalizedKeyword), calculateSearchScore(a, normalizedKeyword))).toList();
        } else if ("price_asc".equalsIgnoreCase(sort)) {
            matched = matched.stream().sorted(Comparator.comparing(Goods::getPrice, Comparator.nullsLast(BigDecimal::compareTo))).toList();
        } else if ("price_desc".equalsIgnoreCase(sort)) {
            matched = matched.stream().sorted((a, b) -> {
                BigDecimal pa = a.getPrice() == null ? BigDecimal.ZERO : a.getPrice();
                BigDecimal pb = b.getPrice() == null ? BigDecimal.ZERO : b.getPrice();
                return pb.compareTo(pa);
            }).toList();
        } else {
            matched = matched.stream().sorted(Comparator.comparing(Goods::getCreateTime, Comparator.nullsLast(LocalDateTime::compareTo)).reversed()).toList();
        }

        long total = matched.size();
        int from = Math.min((page - 1) * size, matched.size());
        int to = Math.min(from + size, matched.size());
        List<Goods> records = new ArrayList<>(matched.subList(from, to));
        records.forEach(g -> {
            normalizeGoodsImages(g);
            if (g.getDistanceKm() != null) {
                g.setDistanceKm(g.getDistanceKm().setScale(2, RoundingMode.HALF_UP));
            }
        });

        return ResponseEntity.ok(Map.of(
                "code", 200,
                "data", records,
                "total", total,
                "searchSellerMatched", !sellerIdsByKeyword.isEmpty()
        ));
    }

    private int calculateSearchScore(Goods goods, String keyword) {
        if (goods == null || keyword == null || keyword.isBlank()) {
            return 0;
        }
        String normalizedKeyword = keyword.trim().toLowerCase();
        String title = goods.getTitle() == null ? "" : goods.getTitle().toLowerCase();
        String description = goods.getDescription() == null ? "" : goods.getDescription().toLowerCase();
        int score = 0;
        if (title.equals(normalizedKeyword)) score += 100;
        if (title.startsWith(normalizedKeyword)) score += 60;
        if (title.contains(normalizedKeyword)) score += 40;
        if (description.contains(normalizedKeyword)) score += 15;
        score += (goods.getFavoritesCount() == null ? 0 : goods.getFavoritesCount()) * 2;
        score += (goods.getViewCount() == null ? 0 : goods.getViewCount());
        if (goods.getCreateTime() != null) {
            score += Math.max(0, 30 - (int) java.time.Duration.between(goods.getCreateTime(), LocalDateTime.now()).toDays());
        }
        return score;
    }

    private String normalizeQueryText(String keyword) {
        if (keyword == null) {
            return null;
        }
        String normalized = keyword.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private Map<Long, User> attachSellerInfo(List<Goods> goodsList) {
        if (goodsList == null || goodsList.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<Long> sellerIds = goodsList.stream().map(Goods::getSellerId).filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        if (sellerIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<User> sellers = userMapper.selectBatchIds(sellerIds);
        Map<Long, User> sellerMap = sellers.stream().collect(java.util.stream.Collectors.toMap(User::getId, u -> u));
        goodsList.forEach(g -> {
            User seller = sellerMap.get(g.getSellerId());
            if (seller != null) {
                g.setSellerName(seller.getUsername());
                g.setSellerAvatar(seller.getAvatar() == null ? "" : seller.getAvatar());
                g.setSellerCreditScore(seller.getCreditScore());
            }
        });
        return sellerMap;
    }

    private Set<Long> findSellerIdsByUsernameLike(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return Collections.emptySet();
        }
        List<User> matchedUsers = userMapper.selectList(new LambdaQueryWrapper<User>().like(User::getUsername, keyword));
        if (matchedUsers == null || matchedUsers.isEmpty()) {
            return Collections.emptySet();
        }
        return matchedUsers.stream().map(User::getId).filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet());
    }

    private boolean matchesBasicKeyword(Goods goods, String keyword, Set<Long> sellerIdsByKeyword) {
        if (goods == null || keyword == null || keyword.isBlank()) {
            return true;
        }
        String normalizedKeyword = keyword.toLowerCase();
        if (containsIgnoreCase(goods.getTitle(), normalizedKeyword)) {
            return true;
        }
        if (containsIgnoreCase(goods.getDescription(), normalizedKeyword)) {
            return true;
        }
        if (containsIgnoreCase(goods.getSellerName(), normalizedKeyword)) {
            return true;
        }
        return sellerIdsByKeyword != null && sellerIdsByKeyword.contains(goods.getSellerId());
    }

    private boolean containsIgnoreCase(String text, String keyword) {
        if (text == null || keyword == null || keyword.isBlank()) {
            return false;
        }
        return text.toLowerCase().contains(keyword.toLowerCase());
    }

    // ==========================================
    // 4. 热度榜单 (融合了你的热度算法)
    // ==========================================
    @GetMapping("/hot")
    public ResponseEntity<?> getHotGoods(@RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "8") int size) {
        // 查出所有在售商品
        List<Goods> allActive = goodsMapper.selectList(new LambdaQueryWrapper<Goods>().eq(Goods::getStatus, 1));

        Map<Long, Integer> orderCountMap = new HashMap<>();
        List<Map<String, Object>> orderCounts = tradeOrderMapper.selectOrderCountsByGoods();
        if (orderCounts != null) {
            for (Map<String, Object> row : orderCounts) {
                Object goodsIdObj = row.get("goodsId");
                Object countObj = row.get("orderCount");
                if (goodsIdObj == null || countObj == null) {
                    continue;
                }
                Long goodsId = Long.valueOf(String.valueOf(goodsIdObj));
                Integer count = Integer.valueOf(String.valueOf(countObj));
                orderCountMap.put(goodsId, count);
            }
        }

        // HackerNews 热榜：score = (votes - 1) / (hours + 2)^1.5
        List<Goods> hotList = allActive.stream()
                .peek(this::normalizeGoodsImages)
                .sorted((a, b) -> Double.compare(
                        calculateHotScore(b, orderCountMap.getOrDefault(b.getId(), 0)),
                        calculateHotScore(a, orderCountMap.getOrDefault(a.getId(), 0))
                ))
                .toList();

        // 内存分页
        int from = Math.min((page - 1) * size, hotList.size());
        int to = Math.min(from + size, hotList.size());
        List<Goods> pageList = hotList.subList(from, to);
        if (!pageList.isEmpty()) {
            Set<Long> sellerIds = pageList.stream().map(Goods::getSellerId).filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet());
            if (!sellerIds.isEmpty()) {
                List<User> sellers = userMapper.selectBatchIds(sellerIds);
                Map<Long, User> sellerMap = sellers.stream().collect(java.util.stream.Collectors.toMap(User::getId, u -> u));
                pageList.forEach(g -> {
                    User seller = sellerMap.get(g.getSellerId());
                    if (seller != null) {
                        g.setSellerName(seller.getUsername());
                        String avatar = seller.getAvatar();
                        g.setSellerAvatar(avatar == null ? "" : avatar);
                        g.setSellerCreditScore(seller.getCreditScore());
                    }
                });
            }
        }
        return ResponseEntity.ok(Map.of("code", 200, "data", pageList, "total", hotList.size()));
    }

    private double calculateHotScore(Goods goods, int orderCount) {
        if (goods == null) {
            return 0;
        }
        int view = goods.getViewCount() == null ? 0 : goods.getViewCount();
        int favorites = goods.getFavoritesCount() == null ? 0 : goods.getFavoritesCount();
        int consult = goods.getCommentCount() == null ? 0 : goods.getCommentCount();
        int orders = Math.max(0, orderCount);
        int weighted = view + favorites * 2 + consult * 3 + orders * 4;
        double votes = Math.max(1, weighted);
        LocalDateTime createTime = goods.getCreateTime() == null ? LocalDateTime.now() : goods.getCreateTime();
        double hours = Math.max(0.0, Duration.between(createTime, LocalDateTime.now()).toMinutes() / 60.0);
        return (votes - 1) / Math.pow(hours + 2, 1.5);
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
        goods.setVariants(loadGoodsVariants(goods.getId()));

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

    private void prepareGoodsForVariants(Goods goods) {
        if (goods == null) {
            return;
        }
        List<GoodsVariant> variants = sanitizeVariants(goods.getVariants());
        goods.setVariants(variants);
        if (variants.isEmpty()) {
            if (goods.getStock() == null || goods.getStock() < 1) {
                goods.setStock(1);
            }
            return;
        }
        BigDecimal minPrice = variants.stream().map(GoodsVariant::getPrice).filter(Objects::nonNull).min(BigDecimal::compareTo).orElse(goods.getPrice());
        BigDecimal minOriginalPrice = variants.stream().map(GoodsVariant::getOriginalPrice).filter(Objects::nonNull).min(BigDecimal::compareTo).orElse(goods.getOriginalPrice());
        int totalStock = variants.stream().map(GoodsVariant::getStock).filter(Objects::nonNull).mapToInt(Integer::intValue).sum();
        goods.setPrice(minPrice);
        goods.setOriginalPrice(minOriginalPrice);
        goods.setStock(totalStock);
    }

    private List<GoodsVariant> sanitizeVariants(List<GoodsVariant> variants) {
        if (variants == null || variants.isEmpty()) {
            return new ArrayList<>();
        }
        List<GoodsVariant> sanitized = new ArrayList<>();
        String sharedName = null;
        int index = 0;
        for (GoodsVariant variant : variants) {
            if (variant == null) {
                continue;
            }
            String optionName = variant.getOptionName() == null ? "" : variant.getOptionName().trim();
            if (optionName.isEmpty()) {
                continue;
            }
            if (variant.getPrice() == null || variant.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("规格选项价格必须大于 0");
            }
            Integer stock = variant.getStock() == null ? 0 : variant.getStock();
            if (stock < 0) {
                throw new IllegalArgumentException("规格选项库存不能小于 0");
            }
            String variantName = variant.getVariantName() == null ? "" : variant.getVariantName().trim();
            if (sharedName == null && !variantName.isEmpty()) {
                sharedName = variantName;
            }
            GoodsVariant sanitizedItem = new GoodsVariant();
            sanitizedItem.setId(variant.getId());
            sanitizedItem.setVariantName(sharedName == null || sharedName.isBlank() ? "商品规格" : sharedName);
            sanitizedItem.setOptionName(optionName);
            sanitizedItem.setPrice(variant.getPrice());
            sanitizedItem.setOriginalPrice(variant.getOriginalPrice());
            sanitizedItem.setStock(stock);
            sanitizedItem.setDescription(variant.getDescription() == null ? "" : variant.getDescription().trim());
            sanitizedItem.setSortOrder(variant.getSortOrder() == null ? index : variant.getSortOrder());
            sanitizedItem.setEnabled(variant.getEnabled() == null ? Boolean.TRUE : variant.getEnabled());
            sanitized.add(sanitizedItem);
            index++;
        }
        if (!sanitized.isEmpty()) {
            String finalName = sharedName == null || sharedName.isBlank() ? "商品规格" : sharedName;
            sanitized.forEach(item -> item.setVariantName(finalName));
        }
        return sanitized;
    }

    private void saveGoodsVariants(Goods goods) {
        if (goods == null || goods.getId() == null) {
            return;
        }
        List<GoodsVariant> variants = goods.getVariants();
        if (variants == null || variants.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (GoodsVariant variant : variants) {
            variant.setId(null);
            variant.setGoodsId(goods.getId());
            variant.setCreateTime(now);
            variant.setUpdateTime(now);
            goodsVariantMapper.insert(variant);
        }
        syncGoodsAggregateFromVariants(goods.getId());
    }

    private void replaceGoodsVariants(Goods goods) {
        if (goods == null || goods.getId() == null) {
            return;
        }
        goodsVariantMapper.delete(new LambdaQueryWrapper<GoodsVariant>().eq(GoodsVariant::getGoodsId, goods.getId()));
        saveGoodsVariants(goods);
    }

    private List<GoodsVariant> loadGoodsVariants(Long goodsId) {
        if (goodsId == null) {
            return List.of();
        }
        return goodsVariantMapper.selectList(new LambdaQueryWrapper<GoodsVariant>()
                .eq(GoodsVariant::getGoodsId, goodsId)
                .eq(GoodsVariant::getEnabled, true)
                .orderByAsc(GoodsVariant::getSortOrder)
                .orderByAsc(GoodsVariant::getId));
    }

    private void syncGoodsAggregateFromVariants(Long goodsId) {
        if (goodsId == null) {
            return;
        }
        Goods goods = goodsMapper.selectById(goodsId);
        if (goods == null) {
            return;
        }
        List<GoodsVariant> variants = loadGoodsVariants(goodsId);
        if (variants.isEmpty()) {
            return;
        }
        BigDecimal minPrice = variants.stream().map(GoodsVariant::getPrice).filter(Objects::nonNull).min(BigDecimal::compareTo).orElse(goods.getPrice());
        BigDecimal minOriginalPrice = variants.stream().map(GoodsVariant::getOriginalPrice).filter(Objects::nonNull).min(BigDecimal::compareTo).orElse(goods.getOriginalPrice());
        int totalStock = variants.stream().map(GoodsVariant::getStock).filter(Objects::nonNull).mapToInt(Integer::intValue).sum();
        goods.setPrice(minPrice);
        goods.setOriginalPrice(minOriginalPrice);
        goods.setStock(totalStock);
        if (goods.getStatus() != null && goods.getStatus() != 0) {
            goods.setStatus(totalStock > 0 ? 1 : 3);
        }
        goodsMapper.updateById(goods);
    }

    // ==========================================
    // 📌 Helper Method: 统一处理图片 URL
    // ==========================================
    /**
     * 将历史数据中硬编码的本地域名前缀移除，统一保留相对路径。
     */
    private void normalizeGoodsImages(Goods goods) {
        if (goods == null || goods.getImages() == null) return;
        String images = goods.getImages();
        images = images.replace("http://localhost:8080", "");
        images = images.replace("https://localhost:8080", "");
        goods.setImages(images);
    }

    private BigDecimal calcDistanceKm(BigDecimal lat1, BigDecimal lng1, BigDecimal lat2, BigDecimal lng2) {
        if (lat1 == null || lng1 == null || lat2 == null || lng2 == null) {
            return null;
        }
        double earthRadiusKm = 6371.0;
        double dLat = Math.toRadians(lat2.doubleValue() - lat1.doubleValue());
        double dLng = Math.toRadians(lng2.doubleValue() - lng1.doubleValue());
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1.doubleValue()))
                * Math.cos(Math.toRadians(lat2.doubleValue()))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return BigDecimal.valueOf(earthRadiusKm * c);
    }

    private void fillTradeCoordinatesIfNeeded(Goods goods) {
        if (goods == null) {
            return;
        }
        if (!containsDeliveryMethod(goods.getDeliveryMethods(), "校园面交")) {
            goods.setTradeLng(null);
            goods.setTradeLat(null);
            goods.setTradeGeoSource(null);
            return;
        }
        if (goods.getTradeLng() != null && goods.getTradeLat() != null) {
            if (goods.getTradeGeoSource() == null || goods.getTradeGeoSource().isBlank()) {
                goods.setTradeGeoSource("manual_geocode");
            }
            return;
        }
        String tradeAddress = goods.getTradeAddress() == null ? "" : goods.getTradeAddress().trim();
        if (tradeAddress.isEmpty()) {
            return;
        }
        Map<String, Object> geo = userAddressService.geocodeTextAddress(tradeAddress);
        BigDecimal longitude = toBigDecimal(geo.get("longitude"));
        BigDecimal latitude = toBigDecimal(geo.get("latitude"));
        if (longitude != null && latitude != null) {
            goods.setTradeLng(longitude);
            goods.setTradeLat(latitude);
            goods.setTradeGeoSource("manual_geocode");
        }
    }

    private boolean containsDeliveryMethod(String deliveryMethods, String expected) {
        if (deliveryMethods == null || deliveryMethods.isBlank()) {
            return false;
        }
        return Arrays.stream(deliveryMethods.split(","))
                .map(item -> item == null ? "" : item.trim())
                .anyMatch(expected::equals);
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void normalizeGoodsDeliveryMethods(Goods goods) {
        if (goods == null) {
            return;
        }

        String raw = goods.getDeliveryMethods();
        String legacy = goods.getDeliveryMethod();
        java.util.LinkedHashSet<String> normalized = new java.util.LinkedHashSet<>();

        collectDeliveryMethods(normalized, raw);
        collectDeliveryMethods(normalized, legacy);

        if (normalized.isEmpty()) {
            normalized.add("校园面交");
        }

        if (!normalized.contains("校园面交") && !normalized.contains("邮寄")) {
            throw new IllegalArgumentException("交易方式仅支持校园面交或邮寄");
        }

        if (normalized.size() > 2) {
            throw new IllegalArgumentException("交易方式最多选择两种");
        }

        String csv = String.join(",", normalized);
        goods.setDeliveryMethods(csv);
        goods.setDeliveryMethod(normalized.iterator().next());
        if (!normalized.contains("校园面交")) {
            goods.setTradeAddress("");
            goods.setTradeLng(null);
            goods.setTradeLat(null);
            goods.setTradeGeoSource(null);
        }
    }

    private void collectDeliveryMethods(java.util.Set<String> holder, String source) {
        if (source == null || source.isBlank()) {
            return;
        }
        String[] parts = source.split(",");
        for (String part : parts) {
            String method = part == null ? "" : part.trim();
            if (method.isEmpty()) {
                continue;
            }
            if ("自提".equals(method) || "买家自提".equals(method)) {
                method = "校园面交";
            }
            if (!"校园面交".equals(method) && !"邮寄".equals(method)) {
                throw new IllegalArgumentException("交易方式仅支持校园面交或邮寄");
            }
            holder.add(method);
        }
    }
}
