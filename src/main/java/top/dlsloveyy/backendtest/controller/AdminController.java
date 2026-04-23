package top.dlsloveyy.backendtest.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import top.dlsloveyy.backendtest.entity.Goods;
import top.dlsloveyy.backendtest.entity.SysNotice;
import top.dlsloveyy.backendtest.entity.User;
import top.dlsloveyy.backendtest.mapper.GoodsMapper;
import top.dlsloveyy.backendtest.mapper.SysNoticeMapper;
import top.dlsloveyy.backendtest.mapper.UserMapper;
import top.dlsloveyy.backendtest.util.JwtUtil;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private GoodsMapper goodsMapper;

    @Autowired
    private SysNoticeMapper sysNoticeMapper;

    private User requireAdmin(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) return null;
        String username = jwtUtil.extractUsername(authHeader.substring(7));
        if (username == null || username.isBlank()) return null;
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (user == null || !Boolean.TRUE.equals(user.getIsAdmin()) || !Boolean.TRUE.equals(user.getEnabled())) return null;
        return user;
    }

    @GetMapping("/userPage")
    public ResponseEntity<?> getUserPage(@RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "10") int size,
                                         @RequestParam(required = false) String keyword,
                                         @RequestHeader("Authorization") String authHeader) {
        if (requireAdmin(authHeader) == null) {
            return ResponseEntity.status(403).body(Map.of("code", 403, "message", "无权限"));
        }

        Page<User> userPage = new Page<>(page, size);
        LambdaQueryWrapper<User> query = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            if (keyword.matches("\\d+")) {
                Long id = Long.parseLong(keyword);
                query.and(w -> w.eq(User::getId, id).or().like(User::getUsername, keyword));
            } else {
                query.like(User::getUsername, keyword);
            }
        }
        query.orderByDesc(User::getId);
        userMapper.selectPage(userPage, query);

        return ResponseEntity.ok(Map.of("code", 200, "data", Map.of(
                "records", userPage.getRecords(),
                "total", userPage.getTotal()
        )));
    }

    @GetMapping("/userSearch")
    public ResponseEntity<?> searchUsers(@RequestParam String keyword,
                                         @RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "10") int size,
                                         @RequestHeader("Authorization") String authHeader) {
        return getUserPage(page, size, keyword, authHeader);
    }

    @PostMapping("/ban")
    public ResponseEntity<?> banUser(@RequestBody Map<String, String> payload,
                                     @RequestHeader("Authorization") String authHeader) {
        return changeUserEnabled(payload.get("username"), false, authHeader);
    }

    @PostMapping("/unban")
    public ResponseEntity<?> unbanUser(@RequestBody Map<String, String> payload,
                                       @RequestHeader("Authorization") String authHeader) {
        return changeUserEnabled(payload.get("username"), true, authHeader);
    }

    @PostMapping("/makeAdmin")
    public ResponseEntity<?> makeAdmin(@RequestBody Map<String, String> payload,
                                       @RequestHeader("Authorization") String authHeader) {
        return changeUserAdmin(payload.get("username"), true, authHeader);
    }

    @PostMapping("/unsetAdmin")
    public ResponseEntity<?> unsetAdmin(@RequestBody Map<String, String> payload,
                                        @RequestHeader("Authorization") String authHeader) {
        return changeUserAdmin(payload.get("username"), false, authHeader);
    }

    @PostMapping("/setAdmin")
    public ResponseEntity<?> setAdmin(@RequestBody Map<String, String> payload,
                                      @RequestHeader("Authorization") String authHeader) {
        return changeUserAdmin(payload.get("username"), true, authHeader);
    }

    @PutMapping("/user/update")
    public ResponseEntity<?> updateUserByAdmin(@RequestBody Map<String, Object> payload,
                                               @RequestHeader("Authorization") String authHeader) {
        User operator = requireAdmin(authHeader);
        if (operator == null) {
            return ResponseEntity.status(403).body(Map.of("code", 403, "message", "无权限"));
        }

        Object idObj = payload.get("id");
        if (idObj == null) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "缺少用户ID"));
        }

        Long userId = Long.valueOf(String.valueOf(idObj));
        User target = userMapper.selectById(userId);
        if (target == null) {
            return ResponseEntity.status(404).body(Map.of("code", 404, "message", "用户不存在"));
        }

        String username = payload.get("username") == null ? null : String.valueOf(payload.get("username"));
        if (username != null && !username.isBlank() && !username.equals(target.getUsername())) {
            User conflict = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
            if (conflict != null && !conflict.getId().equals(target.getId())) {
                return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "用户名已被占用"));
            }
            target.setUsername(username);
        }

        if (payload.get("email") != null) target.setEmail(String.valueOf(payload.get("email")));
        if (payload.get("signature") != null) target.setSignature(String.valueOf(payload.get("signature")));
        if (payload.get("gender") != null) target.setGender(String.valueOf(payload.get("gender")));
        if (payload.get("avatar") != null) target.setAvatar(String.valueOf(payload.get("avatar")));
        if (payload.get("contactPhone") != null) target.setContactPhone(String.valueOf(payload.get("contactPhone")));
        if (payload.get("wechatId") != null) target.setWechatId(String.valueOf(payload.get("wechatId")));
        if (payload.get("dormBuilding") != null) target.setDormBuilding(String.valueOf(payload.get("dormBuilding")));
        if (payload.get("password") != null && !String.valueOf(payload.get("password")).isBlank()) {
            target.setPassword(String.valueOf(payload.get("password")));
        }
        if (payload.get("age") != null) {
            target.setAge(Integer.valueOf(String.valueOf(payload.get("age"))));
        }

        userMapper.updateById(target);
        return ResponseEntity.ok(Map.of("code", 200, "message", "用户信息已更新"));
    }

    @GetMapping("/post/adminPage")
    public ResponseEntity<?> getGoodsAdminPage(@RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "10") int size,
                                               @RequestHeader("Authorization") String authHeader) {
        return getGoodsPage(page, size, null, authHeader);
    }

    @GetMapping("/goods/page")
    public ResponseEntity<?> getGoodsPage(@RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "10") int size,
                                          @RequestParam(required = false) String keyword,
                                          @RequestHeader("Authorization") String authHeader) {
        if (requireAdmin(authHeader) == null) {
            return ResponseEntity.status(403).body(Map.of("code", 403, "message", "无权限"));
        }

        Page<Goods> goodsPage = new Page<>(page, size);
        LambdaQueryWrapper<Goods> query = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            query.and(w -> w.like(Goods::getTitle, keyword).or().like(Goods::getDescription, keyword));
        }
        query.orderByDesc(Goods::getCreateTime);
        goodsMapper.selectPage(goodsPage, query);

        return ResponseEntity.ok(Map.of("code", 200, "data", Map.of(
                "records", goodsPage.getRecords(),
                "total", goodsPage.getTotal()
        )));
    }

    @GetMapping("/goods/search")
    public ResponseEntity<?> searchGoods(@RequestParam String keyword,
                                         @RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "10") int size,
                                         @RequestHeader("Authorization") String authHeader) {
        return getGoodsPage(page, size, keyword, authHeader);
    }

    @PutMapping("/goods/update")
    public ResponseEntity<?> updateGoodsByAdmin(@RequestBody Goods payload,
                                                @RequestHeader("Authorization") String authHeader) {
        if (requireAdmin(authHeader) == null) {
            return ResponseEntity.status(403).body(Map.of("code", 403, "message", "无权限"));
        }

        if (payload.getId() == null) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "缺少商品ID"));
        }

        Goods goods = goodsMapper.selectById(payload.getId());
        if (goods == null) {
            return ResponseEntity.status(404).body(Map.of("code", 404, "message", "商品不存在"));
        }

        if (payload.getTitle() != null) goods.setTitle(payload.getTitle());
        if (payload.getDescription() != null) goods.setDescription(payload.getDescription());
        if (payload.getPrice() != null) goods.setPrice(payload.getPrice());
        if (payload.getOriginalPrice() != null) goods.setOriginalPrice(payload.getOriginalPrice());
        if (payload.getCategory() != null) goods.setCategory(payload.getCategory());
        if (payload.getConditionLevel() != null) goods.setConditionLevel(payload.getConditionLevel());
        if (payload.getDeliveryMethod() != null) goods.setDeliveryMethod(payload.getDeliveryMethod());
        if (payload.getDeliveryMethods() != null || payload.getDeliveryMethod() != null) {
            try {
                normalizeGoodsDeliveryMethods(goods);
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(Map.of("code", 400, "message", e.getMessage()));
            }
        }
        if (payload.getImages() != null) goods.setImages(payload.getImages());
        if (payload.getStatus() != null) goods.setStatus(payload.getStatus());
        goods.setUpdateTime(LocalDateTime.now());

        goodsMapper.updateById(goods);
        return ResponseEntity.ok(Map.of("code", 200, "message", "商品信息已更新"));
    }

    @PostMapping("/deletePost")
    @Transactional
    public ResponseEntity<?> deletePost(@RequestBody Map<String, Long> payload,
                                        @RequestHeader("Authorization") String authHeader) {
        return takedownGoods(payload, authHeader);
    }

    @PostMapping("/goods/delete")
    @Transactional
    public ResponseEntity<?> takedownGoods(@RequestBody Map<String, Long> payload,
                                           @RequestHeader("Authorization") String authHeader) {
        if (requireAdmin(authHeader) == null) {
            return ResponseEntity.status(403).body(Map.of("code", 403, "message", "无权限"));
        }

        Long goodsId = payload.get("id");
        Goods goods = goodsMapper.selectById(goodsId);
        if (goods == null) {
            return ResponseEntity.status(404).body(Map.of("code", 404, "message", "商品不存在"));
        }

        goods.setStatus(4);
        goods.setUpdateTime(LocalDateTime.now());
        goodsMapper.updateById(goods);

        return ResponseEntity.ok(Map.of("code", 200, "message", "商品已下架"));
    }

    @PostMapping("/goods/restore")
    public ResponseEntity<?> restoreGoods(@RequestBody Map<String, Long> payload,
                                          @RequestHeader("Authorization") String authHeader) {
        if (requireAdmin(authHeader) == null) {
            return ResponseEntity.status(403).body(Map.of("code", 403, "message", "无权限"));
        }

        Long goodsId = payload.get("id");
        Goods goods = goodsMapper.selectById(goodsId);
        if (goods == null) {
            return ResponseEntity.status(404).body(Map.of("code", 404, "message", "商品不存在"));
        }

        goods.setStatus(1);
        goods.setUpdateTime(LocalDateTime.now());
        goodsMapper.updateById(goods);

        return ResponseEntity.ok(Map.of("code", 200, "message", "商品已恢复上架"));
    }

    @PostMapping("/toggleFeatured")
    public ResponseEntity<?> toggleFeatured(@RequestBody Map<String, Long> payload,
                                            @RequestHeader("Authorization") String authHeader) {
        return toggleGoodsFeatured(payload, authHeader);
    }

    @PostMapping("/goods/toggleFeatured")
    public ResponseEntity<?> toggleGoodsFeatured(@RequestBody Map<String, Long> payload,
                                                 @RequestHeader("Authorization") String authHeader) {
        if (requireAdmin(authHeader) == null) {
            return ResponseEntity.status(403).body(Map.of("code", 403, "message", "无权限"));
        }

        Long goodsId = payload.get("id");
        Goods goods = goodsMapper.selectById(goodsId);
        if (goods == null) {
            return ResponseEntity.status(404).body(Map.of("code", 404, "message", "商品不存在"));
        }

        goods.setIsFeatured(!Boolean.TRUE.equals(goods.getIsFeatured()));
        goods.setUpdateTime(LocalDateTime.now());
        goodsMapper.updateById(goods);

        return ResponseEntity.ok(Map.of("code", 200, "message", goods.getIsFeatured() ? "已设为精选" : "已取消精选"));
    }

    @GetMapping("/checkGoods/page")
    public ResponseEntity<?> getCheckGoodsPage(@RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "10") int size,
                                               @RequestHeader("Authorization") String authHeader) {
        if (requireAdmin(authHeader) == null) {
            return ResponseEntity.status(403).body(Map.of("code", 403, "message", "无权限"));
        }

        Page<Goods> goodsPage = new Page<>(page, size);
        goodsMapper.selectPage(goodsPage, new LambdaQueryWrapper<Goods>()
                .eq(Goods::getStatus, 0)
                .orderByDesc(Goods::getCreateTime));

        return ResponseEntity.ok(Map.of("code", 200, "data", Map.of(
                "records", goodsPage.getRecords(),
                "total", goodsPage.getTotal()
        )));
    }

    @GetMapping("/checkGoods/search")
    public ResponseEntity<?> searchCheckGoods(@RequestParam String keyword,
                                              @RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "10") int size,
                                              @RequestHeader("Authorization") String authHeader) {
        if (requireAdmin(authHeader) == null) {
            return ResponseEntity.status(403).body(Map.of("code", 403, "message", "无权限"));
        }

        Page<Goods> goodsPage = new Page<>(page, size);
        goodsMapper.selectPage(goodsPage, new LambdaQueryWrapper<Goods>()
                .eq(Goods::getStatus, 0)
                .and(w -> w.like(Goods::getTitle, keyword).or().like(Goods::getDescription, keyword))
                .orderByDesc(Goods::getCreateTime));

        return ResponseEntity.ok(Map.of("code", 200, "data", Map.of(
                "records", goodsPage.getRecords(),
                "total", goodsPage.getTotal()
        )));
    }

    @PostMapping("/checkGoods/approve")
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<?> approveCheckGoods(@RequestBody Map<String, Long> payload,
                                               @RequestHeader("Authorization") String authHeader) {
        if (requireAdmin(authHeader) == null) {
            return ResponseEntity.status(403).body(Map.of("code", 403, "message", "无权限"));
        }

        Long id = payload.get("id");
        Goods goods = goodsMapper.selectById(id);
        if (goods == null || goods.getStatus() != 0) {
            return ResponseEntity.status(404).body(Map.of("code", 404, "message", "该商品不在待审核状态"));
        }

        goods.setStatus(1);
        goods.setUpdateTime(LocalDateTime.now());
        goodsMapper.updateById(goods);
        return ResponseEntity.ok(Map.of("code", 200, "message", "审核通过，商品已上架"));
    }

    @PostMapping("/checkGoods/reject")
    public ResponseEntity<?> rejectCheckGoods(@RequestBody Map<String, Long> payload,
                                              @RequestHeader("Authorization") String authHeader) {
        if (requireAdmin(authHeader) == null) {
            return ResponseEntity.status(403).body(Map.of("code", 403, "message", "无权限"));
        }

        Long id = payload.get("id");
        Goods goods = goodsMapper.selectById(id);
        if (goods == null || goods.getStatus() != 0) {
            return ResponseEntity.status(404).body(Map.of("code", 404, "message", "该商品不在待审核状态"));
        }

        goods.setStatus(4);
        goods.setUpdateTime(LocalDateTime.now());
        goodsMapper.updateById(goods);
        return ResponseEntity.ok(Map.of("code", 200, "message", "审核驳回，商品已下架"));
    }

    @PostMapping("/announce")
    public ResponseEntity<?> publishAnnouncement(@RequestBody Map<String, String> payload,
                                                 @RequestHeader("Authorization") String authHeader) {
        User admin = requireAdmin(authHeader);
        if (admin == null) {
            return ResponseEntity.status(403).body(Map.of("code", 403, "message", "无权限"));
        }

        String title = payload.get("title");
        String content = payload.get("content");
        if (title == null || title.trim().isEmpty() || content == null || content.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "标题和内容不能为空"));
        }

        SysNotice notice = new SysNotice();
        notice.setTitle(title);
        notice.setContent(content);
        notice.setAdminId(admin.getId());
        notice.setAuthor("管理员_" + admin.getUsername());
        notice.setCreateTime(LocalDateTime.now());
        sysNoticeMapper.insert(notice);

        return ResponseEntity.ok(Map.of("code", 200, "message", "公告发布成功"));
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

        if (normalized.size() > 2) {
            throw new IllegalArgumentException("交易方式最多选择两种");
        }

        String csv = String.join(",", normalized);
        goods.setDeliveryMethods(csv);
        goods.setDeliveryMethod(normalized.iterator().next());
        if (!normalized.contains("校园面交")) {
            goods.setTradeAddress("");
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

    private ResponseEntity<?> changeUserEnabled(String username, boolean enabled, String authHeader) {
        if (requireAdmin(authHeader) == null) {
            return ResponseEntity.status(403).body(Map.of("code", 403, "message", "无权限"));
        }
        if (username == null || username.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "用户名不能为空"));
        }

        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (user == null) {
            return ResponseEntity.status(404).body(Map.of("code", 404, "message", "用户不存在"));
        }

        user.setEnabled(enabled);
        userMapper.updateById(user);
        return ResponseEntity.ok(Map.of("code", 200, "message", enabled ? "已解封用户" : "已封禁用户"));
    }

    private ResponseEntity<?> changeUserAdmin(String username, boolean isAdmin, String authHeader) {
        User operator = requireAdmin(authHeader);
        if (operator == null) {
            return ResponseEntity.status(403).body(Map.of("code", 403, "message", "无权限"));
        }
        if (username == null || username.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "用户名不能为空"));
        }

        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (user == null) {
            return ResponseEntity.status(404).body(Map.of("code", 404, "message", "用户不存在"));
        }

        if (!isAdmin && operator.getId().equals(user.getId())) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "不能取消自己的管理员权限"));
        }

        user.setIsAdmin(isAdmin);
        userMapper.updateById(user);
        return ResponseEntity.ok(Map.of("code", 200, "message", isAdmin ? "已设置为管理员" : "已取消管理员权限"));
    }
}
