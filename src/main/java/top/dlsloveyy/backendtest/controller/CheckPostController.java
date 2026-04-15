package top.dlsloveyy.backendtest.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import top.dlsloveyy.backendtest.entity.Goods;
import top.dlsloveyy.backendtest.entity.User;
import top.dlsloveyy.backendtest.mapper.GoodsMapper;
import top.dlsloveyy.backendtest.mapper.UserMapper;
import top.dlsloveyy.backendtest.util.JwtUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/checkPost")
@RequiredArgsConstructor
public class CheckPostController {

    @Autowired
    private GoodsMapper goodsMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JwtUtil jwtUtil;

    // ✅ 统一的管理员权限校验逻辑 (适配 MyBatis-Plus)
    private boolean hasAdminPrivilege(String token) {
        if (token == null || !token.startsWith("Bearer ")) return false;
        String username = jwtUtil.extractUsername(token.substring(7));

        // 超级管理员直接放行
        if ("dlsloveyy".equals(username)) {
            return true;
        }

        // 普通管理员去数据库查询 is_admin 状态
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        return user != null && user.getIsAdmin();
    }

    // ✅ 获取所有待审核商品
    @GetMapping("/list")
    public ResponseEntity<?> getAllCheckPosts(@RequestHeader("Authorization") String authHeader) {
        if (!hasAdminPrivilege(authHeader)) {
            return ResponseEntity.status(403).body(Map.of("code", 403, "message", "无权限访问"));
        }

        // status = 0 表示待审核
        List<Goods> list = goodsMapper.selectList(new LambdaQueryWrapper<Goods>()
                .eq(Goods::getStatus, 0)
                .orderByDesc(Goods::getCreateTime));

        return ResponseEntity.ok(Map.of("code", 200, "data", list));
    }

    // ✅ 审核通过 (从 0 变 1)
    @PostMapping("/approve")
    @Transactional
    public ResponseEntity<?> approvePost(@RequestBody Map<String, Long> payload,
                                         @RequestHeader("Authorization") String authHeader) {
        if (!hasAdminPrivilege(authHeader)) {
            return ResponseEntity.status(403).body(Map.of("code", 403, "message", "无权限操作"));
        }

        Long id = payload.get("id");
        Goods goods = goodsMapper.selectById(id);

        if (goods == null || goods.getStatus() != 0) {
            return ResponseEntity.status(404).body(Map.of("code", 404, "message", "待审核商品不存在"));
        }

        // 审核通过，正式上架
        goods.setStatus(1);
        goodsMapper.updateById(goods);

        return ResponseEntity.ok(Map.of("code", 200, "message", "已通过审核，商品已上架"));
    }

    // ✅ 审核驳回 / 违规下架 (从 0 变 4，替代原先的 UnPost 归档表)
    @PostMapping("/delete")
    @Transactional
    public ResponseEntity<?> deletePost(@RequestBody Map<String, Long> payload,
                                        @RequestHeader("Authorization") String authHeader) {
        if (!hasAdminPrivilege(authHeader)) {
            return ResponseEntity.status(403).body(Map.of("code", 403, "message", "无权限操作"));
        }

        Long id = payload.get("id");
        Goods goods = goodsMapper.selectById(id);

        if (goods == null) {
            return ResponseEntity.status(404).body(Map.of("code", 404, "message", "商品不存在"));
        }

        // 驳回不作物理删除，而是状态标记为 4（违规下架/驳回），保留证据
        goods.setStatus(4);
        goodsMapper.updateById(goods);

        return ResponseEntity.ok(Map.of("code", 200, "message", "已驳回审核，商品已归档为违规状态"));
    }

    // ✅ 待审核列表分页
    @GetMapping("/page")
    public Map<String, Object> getCheckPostPage(@RequestParam int page,
                                                @RequestParam int size,
                                                @RequestHeader("Authorization") String authHeader) {
        if (!hasAdminPrivilege(authHeader)) {
            return Map.of("code", 403, "message", "无权限");
        }

        Page<Goods> goodsPage = new Page<>(page, size);
        goodsMapper.selectPage(goodsPage, new LambdaQueryWrapper<Goods>()
                .eq(Goods::getStatus, 0)
                .orderByDesc(Goods::getCreateTime));

        return Map.of("code", 200, "data", Map.of(
                "records", goodsPage.getRecords(),
                "total", goodsPage.getTotal()
        ));
    }

    // ✅ 搜索待审核商品
    @GetMapping("/search")
    public ResponseEntity<?> searchPosts(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestHeader("Authorization") String authHeader) {

        if (!hasAdminPrivilege(authHeader)) {
            return ResponseEntity.status(403).body(Map.of("code", 403, "message", "无权限"));
        }

        Page<Goods> goodsPage = new Page<>(page, size);
        goodsMapper.selectPage(goodsPage, new LambdaQueryWrapper<Goods>()
                .eq(Goods::getStatus, 0)
                .and(w -> w.like(Goods::getTitle, keyword).or().like(Goods::getDescription, keyword))
                .orderByDesc(Goods::getCreateTime));

        return ResponseEntity.ok(Map.of(
                "code", 200,
                "data", Map.of(
                        "records", goodsPage.getRecords(),
                        "total", goodsPage.getTotal()
                )
        ));
    }

    // ✅ 获取待审核详情 (平滑兼容前端旧字段名)
    @GetMapping("/detail")
    public ResponseEntity<?> getCheckPostDetail(@RequestParam Long id,
                                                @RequestHeader("Authorization") String authHeader) {
        if (!hasAdminPrivilege(authHeader)) {
            return ResponseEntity.status(403).body(Map.of("code", 403, "message", "无权限"));
        }

        Goods goods = goodsMapper.selectById(id);
        if (goods == null) {
            return ResponseEntity.status(404).body(Map.of("code", 404, "message", "审核商品不存在"));
        }

        // 为了不让前端报错，手动映射 Goods 字段到前端期望的旧属性名
        Map<String, Object> result = new HashMap<>();
        result.put("id", goods.getId());
        result.put("title", goods.getTitle());
        result.put("content", goods.getDescription()); // 对应旧的 content
        result.put("createTime", goods.getCreateTime());
        result.put("price", goods.getPrice());
        result.put("originalPrice", goods.getOriginalPrice());
        result.put("category", goods.getCategory());
        result.put("deliveryType", goods.getDeliveryMethod()); // 对应旧的 deliveryType
        result.put("coverImg", goods.getImages()); // 对应旧的 coverImg
        result.put("status", goods.getStatus());
        result.put("views", goods.getViewCount());
        result.put("likes", goods.getFavoritesCount());
        result.put("comments", goods.getCommentCount());

        User author = userMapper.selectById(goods.getSellerId());
        if (author != null) {
            Map<String, Object> authorMap = new HashMap<>();
            authorMap.put("id", author.getId());
            authorMap.put("username", author.getUsername());
            String avatar = author.getAvatar();
            if (avatar != null && !avatar.startsWith("http")) {
                avatar = "http://localhost:8080" + avatar;
            }
            authorMap.put("avatar", avatar);
            result.put("author", authorMap);
        } else {
            result.put("author", Map.of("id", -1, "username", "未知", "avatar", ""));
        }

        return ResponseEntity.ok(Map.of("code", 200, "data", result));
    }
}