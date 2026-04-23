package top.dlsloveyy.backendtest.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import top.dlsloveyy.backendtest.entity.Goods;
import top.dlsloveyy.backendtest.entity.User;
import top.dlsloveyy.backendtest.mapper.GoodsMapper;
import top.dlsloveyy.backendtest.mapper.UserMapper;
import top.dlsloveyy.backendtest.util.JwtUtil;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/checkPost")
@RequiredArgsConstructor
public class CheckPostController {

    @Autowired
    private AdminController adminController;

    @Autowired
    private GoodsMapper goodsMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JwtUtil jwtUtil;

    private boolean hasAdminPrivilege(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) return false;
        String username = jwtUtil.extractUsername(authHeader.substring(7));
        if (username == null || username.isBlank()) return false;
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        return user != null && Boolean.TRUE.equals(user.getIsAdmin()) && Boolean.TRUE.equals(user.getEnabled());
    }

    @GetMapping("/list")
    public ResponseEntity<?> getAllCheckPosts(@RequestHeader("Authorization") String authHeader) {
        if (!hasAdminPrivilege(authHeader)) {
            return ResponseEntity.status(403).body(Map.of("code", 403, "message", "无权限访问"));
        }
        Page<Goods> pageRes = new Page<>(1, 1000);
        goodsMapper.selectPage(pageRes, new LambdaQueryWrapper<Goods>()
                .eq(Goods::getStatus, 0)
                .orderByDesc(Goods::getCreateTime));
        return ResponseEntity.ok(Map.of("code", 200, "data", pageRes.getRecords()));
    }

    @PostMapping("/approve")
    public ResponseEntity<?> approvePost(@RequestBody Map<String, Long> payload,
                                         @RequestHeader("Authorization") String authHeader) {
        return adminController.approveCheckGoods(payload, authHeader);
    }

    @PostMapping("/delete")
    public ResponseEntity<?> deletePost(@RequestBody Map<String, Long> payload,
                                        @RequestHeader("Authorization") String authHeader) {
        return adminController.rejectCheckGoods(payload, authHeader);
    }

    @GetMapping("/page")
    public ResponseEntity<?> getCheckPostPage(@RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "10") int size,
                                              @RequestHeader("Authorization") String authHeader) {
        return adminController.getCheckGoodsPage(page, size, authHeader);
    }

    @GetMapping("/search")
    public ResponseEntity<?> searchPosts(@RequestParam String keyword,
                                         @RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "10") int size,
                                         @RequestHeader("Authorization") String authHeader) {
        return adminController.searchCheckGoods(keyword, page, size, authHeader);
    }

    @GetMapping("/detail")
    public ResponseEntity<?> getCheckPostDetail(@RequestParam Long id,
                                                @RequestHeader("Authorization") String authHeader) {
        Goods goods = goodsMapper.selectById(id);
        if (goods == null) {
            return ResponseEntity.status(404).body(Map.of("code", 404, "message", "审核商品不存在"));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("id", goods.getId());
        result.put("title", goods.getTitle());
        result.put("content", goods.getDescription());
        result.put("createTime", goods.getCreateTime());
        result.put("price", goods.getPrice());
        result.put("originalPrice", goods.getOriginalPrice());
        result.put("category", goods.getCategory());
        result.put("conditionLevel", goods.getConditionLevel());
        result.put("deliveryType", goods.getDeliveryMethod());
        result.put("coverImg", goods.getImages());
        result.put("status", goods.getStatus());
        result.put("views", goods.getViewCount());
        result.put("likes", goods.getFavoritesCount());
        result.put("comments", goods.getCommentCount());

        User author = userMapper.selectById(goods.getSellerId());
        if (author != null) {
            String avatar = author.getAvatar();
            if (avatar != null && !avatar.isBlank() && !avatar.startsWith("http") && !avatar.startsWith("/")) {
                avatar = "/" + avatar;
            }
            result.put("author", Map.of(
                    "id", author.getId(),
                    "username", author.getUsername(),
                    "avatar", avatar == null ? "" : avatar
            ));
        } else {
            result.put("author", Map.of("id", -1, "username", "未知", "avatar", ""));
        }

        return ResponseEntity.ok(Map.of("code", 200, "data", result));
    }
}
