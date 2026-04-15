package top.dlsloveyy.backendtest.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import top.dlsloveyy.backendtest.entity.Comment;
import top.dlsloveyy.backendtest.entity.Goods;
import top.dlsloveyy.backendtest.entity.GoodsFavorite;
import top.dlsloveyy.backendtest.entity.SysNotice;
import top.dlsloveyy.backendtest.entity.User;
import top.dlsloveyy.backendtest.mapper.CommentMapper;
import top.dlsloveyy.backendtest.mapper.GoodsFavoriteMapper;
import top.dlsloveyy.backendtest.mapper.GoodsMapper;
import top.dlsloveyy.backendtest.mapper.SysNoticeMapper;
import top.dlsloveyy.backendtest.mapper.UserMapper;
import top.dlsloveyy.backendtest.util.JwtUtil;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@ToString
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    @Value("${admin.username}")
    private String adminUsername;

    @Value("${admin.password}")
    private String adminPassword;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private GoodsMapper goodsMapper;

    @Autowired
    private CommentMapper commentMapper;

    @Autowired
    private GoodsFavoriteMapper goodsFavoriteMapper;

    @Autowired
    private SysNoticeMapper sysNoticeMapper;

    // ✅ 动态判断超级管理员 (不再写死 "dlsloveyy")
    private boolean isSuperAdmin(String username) {
        return adminUsername.equals(username);
    }

    /**
     * 最高管理员登录接口
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> credentials) {
        String username = credentials.get("username");
        String password = credentials.get("password");

        if (adminUsername.equals(username) && adminPassword.equals(password)) {
            String token = jwtUtil.generateToken(username);
            return ResponseEntity.ok(Map.of(
                    "code", 200,
                    "message", "登录成功",
                    "token", token
            ));
        } else {
            return ResponseEntity.status(401).body(Map.of(
                    "code", 401,
                    "message", "账号或密码错误"
            ));
        }
    }

    /**
     * 查看所有商品（兼容旧的 post 接口路径）
     */
    @GetMapping("/post/adminPage")
    public Map<String, Object> getGoodsAdminPage(@RequestParam int page,
                                                 @RequestParam int size,
                                                 @RequestHeader("Authorization") String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        String username = jwtUtil.extractUsername(token);
        if (!isSuperAdmin(username)) {
            return Map.of("code", 403, "message", "无权限");
        }

        Page<Goods> goodsPage = new Page<>(page, size);
        // 超级管理员可以看到所有状态的商品
        goodsMapper.selectPage(goodsPage, new LambdaQueryWrapper<Goods>().orderByDesc(Goods::getCreateTime));

        return Map.of("code", 200, "data", Map.of(
                "records", goodsPage.getRecords(),
                "total", goodsPage.getTotal()
        ));
    }

    /**
     * 查看所有注册用户
     */
    @GetMapping("/users")
    public ResponseEntity<?> listUsers(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        String username = jwtUtil.extractUsername(token);
        if (!isSuperAdmin(username)) {
            return ResponseEntity.status(403).body(Map.of("code", 403, "message", "无权限访问"));
        }

        List<User> users = userMapper.selectList(null);
        return ResponseEntity.ok(Map.of("code", 200, "users", users));
    }

    /**
     * 分页查看用户
     */
    @GetMapping("/userPage")
    public Map<String, Object> getUserPage(@RequestParam int page,
                                           @RequestParam int size,
                                           @RequestHeader("Authorization") String authHeader) {
        String username = jwtUtil.extractUsername(authHeader.replace("Bearer ", ""));
        if (!isSuperAdmin(username)) {
            return Map.of("code", 403, "message", "无权限");
        }

        Page<User> userPage = new Page<>(page, size);
        userMapper.selectPage(userPage, null);

        return Map.of("code", 200, "data", Map.of(
                "records", userPage.getRecords(),
                "total", userPage.getTotal()
        ));
    }

    /**
     * 用户搜索 (支持 ID 或 用户名)
     */
    @GetMapping("/userSearch")
    public ResponseEntity<?> searchUsers(@RequestParam String keyword,
                                         @RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "10") int size) {
        Page<User> userPage = new Page<>(page, size);
        LambdaQueryWrapper<User> query = new LambdaQueryWrapper<>();

        if (keyword.matches("\\d+")) {
            Long userId = Long.parseLong(keyword);
            query.eq(User::getId, userId).or().like(User::getUsername, keyword);
        } else {
            query.like(User::getUsername, keyword);
        }

        userMapper.selectPage(userPage, query);

        return ResponseEntity.ok(Map.of(
                "code", 200,
                "data", Map.of(
                        "records", userPage.getRecords(),
                        "total", userPage.getTotal()
                )
        ));
    }

    // ================= 用户权限控制 =================

    @PostMapping("/setAdmin")
    public ResponseEntity<?> setAdmin(@RequestBody Map<String, String> payload, @RequestHeader("Authorization") String authHeader) {
        return changeUserStatus(payload.get("username"), authHeader, "admin", true);
    }

    @PostMapping("/makeAdmin")
    public ResponseEntity<?> makeAdmin(@RequestBody Map<String, String> payload, @RequestHeader("Authorization") String authHeader) {
        return changeUserStatus(payload.get("username"), authHeader, "admin", true);
    }

    @PostMapping("/unsetAdmin")
    public ResponseEntity<?> unsetAdmin(@RequestBody Map<String, String> payload, @RequestHeader("Authorization") String authHeader) {
        return changeUserStatus(payload.get("username"), authHeader, "admin", false);
    }

    @PostMapping("/ban")
    public ResponseEntity<?> banUser(@RequestBody Map<String, String> payload, @RequestHeader("Authorization") String authHeader) {
        return changeUserStatus(payload.get("username"), authHeader, "ban", false);
    }

    @PostMapping("/unban")
    public ResponseEntity<?> unbanUser(@RequestBody Map<String, String> payload, @RequestHeader("Authorization") String authHeader) {
        return changeUserStatus(payload.get("username"), authHeader, "ban", true);
    }

    // 内部复用：改变用户状态
    private ResponseEntity<?> changeUserStatus(String targetUsername, String authHeader, String action, boolean status) {
        String operator = jwtUtil.extractUsername(authHeader.replace("Bearer ", ""));
        if (!isSuperAdmin(operator)) return ResponseEntity.status(403).body(Map.of("code", 403, "message", "无权限"));

        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, targetUsername));
        if (user == null) return ResponseEntity.status(404).body(Map.of("code", 404, "message", "用户不存在"));

        if ("admin".equals(action)) {
            user.setIsAdmin(status);
        } else if ("ban".equals(action)) {
            user.setEnabled(status);
        }

        userMapper.updateById(user);
        return ResponseEntity.ok(Map.of("code", 200, "message", "操作成功"));
    }

    // ================= 商品与系统管理 =================

    /**
     * 删除商品及关联数据 (超级管理员物理删除)
     */
    @PostMapping("/deletePost")
    @Transactional
    public ResponseEntity<?> deletePost(@RequestBody Map<String, Long> payload,
                                        @RequestHeader("Authorization") String authHeader) {
        String username = jwtUtil.extractUsername(authHeader.replace("Bearer ", ""));
        if (!isSuperAdmin(username)) {
            return ResponseEntity.status(403).body(Map.of("code", 403, "message", "无权限"));
        }

        Long goodsId = payload.get("id");
        if (goodsMapper.selectById(goodsId) == null) {
            return ResponseEntity.status(404).body(Map.of("code", 404, "message", "商品不存在"));
        }

        try {
            // 级联删除留言
            commentMapper.delete(new LambdaQueryWrapper<Comment>().eq(Comment::getGoodsId, goodsId));
            // 级联删除收藏
            goodsFavoriteMapper.delete(new LambdaQueryWrapper<GoodsFavorite>().eq(GoodsFavorite::getGoodsId, goodsId));
            // 删除商品本身
            goodsMapper.deleteById(goodsId);

            return ResponseEntity.ok(Map.of("code", 200, "message", "删除成功"));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("code", 500, "message", "删除失败：" + e.getMessage()));
        }
    }

    /**
     * 切换商品精选状态
     */
    @PostMapping("/toggleFeatured")
    public ResponseEntity<?> toggleFeatured(@RequestBody Map<String, Long> payload,
                                            @RequestHeader("Authorization") String authHeader) {
        String username = jwtUtil.extractUsername(authHeader.replace("Bearer ", ""));
        if (!isSuperAdmin(username)) return ResponseEntity.status(403).body(Map.of("code", 403, "message", "无权限"));

        Long goodsId = payload.get("id");
        Goods goods = goodsMapper.selectById(goodsId);
        if (goods == null) return ResponseEntity.status(404).body(Map.of("code", 404, "message", "商品不存在"));

        goods.setIsFeatured(!Boolean.TRUE.equals(goods.getIsFeatured()));
        goodsMapper.updateById(goods);

        return ResponseEntity.ok(Map.of("code", 200, "message", goods.getIsFeatured() ? "已设为精选" : "已取消精选"));
    }

    /**
     * 发布系统公告 (使用全新的 SysNotice 表)
     */
    @PostMapping("/announce")
    public ResponseEntity<?> publishAnnouncement(@RequestBody Map<String, String> payload,
                                                 @RequestHeader("Authorization") String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        String username = jwtUtil.extractUsername(token);
        if (!isSuperAdmin(username)) {
            return ResponseEntity.status(403).body(Map.of("code", 403, "message", "无权限"));
        }

        String title = payload.get("title");
        String content = payload.get("content");

        if (title == null || title.trim().isEmpty() || content == null || content.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "标题和内容不能为空"));
        }

        User adminUser = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, adminUsername));
        if (adminUser == null) {
            return ResponseEntity.status(500).body(Map.of("code", 500, "message", "管理员账号数据异常"));
        }

        SysNotice notice = new SysNotice();
        notice.setTitle(title);
        notice.setContent(content);
        notice.setAdminId(adminUser.getId());
        notice.setAuthor("超级管理员_" + adminUser.getUsername());
        notice.setCreateTime(LocalDateTime.now());

        sysNoticeMapper.insert(notice);

        return ResponseEntity.ok(Map.of("code", 200, "message", "公告发布成功"));
    }
}