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
import top.dlsloveyy.backendtest.mapper.GoodsFavoriteMapper;
import top.dlsloveyy.backendtest.mapper.GoodsMapper;
import top.dlsloveyy.backendtest.mapper.SysNoticeMapper;
import top.dlsloveyy.backendtest.mapper.UserMapper;
import top.dlsloveyy.backendtest.util.JwtUtil;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/nadmin")
@RequiredArgsConstructor
public class NormalAdminController {

    @Autowired
    private GoodsMapper goodsMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private GoodsFavoriteMapper goodsFavoriteMapper;

    @Autowired
    private SysNoticeMapper sysNoticeMapper;

    @Autowired
    private JwtUtil jwtUtil;

    // ✅ 权限校验辅助方法
    private boolean isAdmin(String token) {
        if (token == null || !token.startsWith("Bearer ")) return false;
        String username = jwtUtil.extractUsername(token.substring(7));
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (user == null) {
            System.out.println("【权限校验】用户未找到: " + username);
            return false;
        }
        return user.getIsAdmin();
    }

    private User getAdminUser(String token) {
        String username = jwtUtil.extractUsername(token.substring(7));
        return userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
    }


    // ==========================================
    // 模块一：在售商品管理 (替代原 Post 管理)
    // ==========================================

    // ✅ 获取所有在售商品（后台分页）
    @GetMapping("/goods/adminPage")
    public Map<String, Object> getGoodsPage(@RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "10") int size,
                                            @RequestHeader("Authorization") String authHeader) {
        if (!isAdmin(authHeader)) return Map.of("code", 403, "message", "无权限");

        Page<Goods> goodsPage = new Page<>(page, size);
        LambdaQueryWrapper<Goods> query = new LambdaQueryWrapper<Goods>()
                .eq(Goods::getStatus, 1) // 仅查在售商品
                .orderByDesc(Goods::getCreateTime);

        goodsMapper.selectPage(goodsPage, query);

        return Map.of("code", 200, "data", Map.of(
                "records", goodsPage.getRecords(),
                "total", goodsPage.getTotal()));
    }

    // ✅ 搜索在售商品
    @GetMapping("/goods/search")
    public Map<String, Object> searchGoods(@RequestParam String keyword,
                                           @RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "10") int size,
                                           @RequestHeader("Authorization") String authHeader) {
        if (!isAdmin(authHeader)) return Map.of("code", 403, "message", "无权限");

        Page<Goods> goodsPage = new Page<>(page, size);
        LambdaQueryWrapper<Goods> query = new LambdaQueryWrapper<Goods>()
                .eq(Goods::getStatus, 1)
                .and(w -> w.like(Goods::getTitle, keyword).or().like(Goods::getDescription, keyword))
                .orderByDesc(Goods::getCreateTime);

        goodsMapper.selectPage(goodsPage, query);

        return Map.of("code", 200, "data", Map.of(
                "records", goodsPage.getRecords(),
                "total", goodsPage.getTotal()));
    }

    // ✅ 管理员删除商品 (将商品状态设为4-已下架，而非物理删除，保护交易凭证)
    @PostMapping("/goods/delete")
    @Transactional
    public ResponseEntity<?> deleteGoodsByNadmin(@RequestBody Map<String, Long> payload,
                                                 @RequestHeader("Authorization") String authHeader) {
        if (!isAdmin(authHeader)) return ResponseEntity.status(403).body(Map.of("code", 403, "message", "无权限"));

        Long goodsId = payload.get("id");
        Goods goods = goodsMapper.selectById(goodsId);
        if (goods == null) return ResponseEntity.status(404).body(Map.of("code", 404, "message", "商品不存在"));

        // 逻辑删除（下架）
        goods.setStatus(4);
        goodsMapper.updateById(goods);

        // 可选：清理相关的收藏记录以释放空间
        // goodsFavoriteMapper.delete(new LambdaQueryWrapper<GoodsFavorite>().eq(GoodsFavorite::getGoodsId, goodsId));

        return ResponseEntity.ok(Map.of("code", 200, "message", "商品已强制下架"));
    }

    // ✅ 切换商品精选状态
    @PostMapping("/goods/toggleFeatured")
    public ResponseEntity<?> toggleFeatured(@RequestBody Map<String, Long> payload,
                                            @RequestHeader("Authorization") String authHeader) {
        if (!isAdmin(authHeader)) return ResponseEntity.status(403).body(Map.of("code", 403, "message", "无权限"));

        Long id = payload.get("id");
        Goods goods = goodsMapper.selectById(id);
        if (goods == null) return ResponseEntity.status(404).body(Map.of("code", 404, "message", "商品不存在"));

        goods.setIsFeatured(!Boolean.TRUE.equals(goods.getIsFeatured()));
        goodsMapper.updateById(goods);

        return ResponseEntity.ok(Map.of("code", 200, "message", goods.getIsFeatured() ? "已设为精选" : "已取消精选"));
    }


    // ==========================================
    // 模块二：待审核商品管理 (替代原 CheckPost)
    // ==========================================

    // ✅ 待审核商品分页（人工审核池）
    @GetMapping("/checkGoods/page")
    public Map<String, Object> getCheckGoodsPage(@RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "10") int size,
                                                 @RequestHeader("Authorization") String authHeader) {
        if (!isAdmin(authHeader)) return Map.of("code", 403, "message", "无权限");

        Page<Goods> goodsPage = new Page<>(page, size);
        LambdaQueryWrapper<Goods> query = new LambdaQueryWrapper<Goods>()
                .eq(Goods::getStatus, 0) // status=0 代表待审核
                .orderByDesc(Goods::getCreateTime);

        goodsMapper.selectPage(goodsPage, query);

        return Map.of("code", 200, "data", Map.of(
                "records", goodsPage.getRecords(),
                "total", goodsPage.getTotal()
        ));
    }

    // ✅ 搜索待审核商品
    @GetMapping("/checkGoods/search")
    public Map<String, Object> searchCheckGoods(@RequestParam String keyword,
                                                @RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "10") int size,
                                                @RequestHeader("Authorization") String authHeader) {
        if (!isAdmin(authHeader)) return Map.of("code", 403, "message", "无权限");

        Page<Goods> goodsPage = new Page<>(page, size);
        LambdaQueryWrapper<Goods> query = new LambdaQueryWrapper<Goods>()
                .eq(Goods::getStatus, 0)
                .and(w -> w.like(Goods::getTitle, keyword).or().like(Goods::getDescription, keyword))
                .orderByDesc(Goods::getCreateTime);

        goodsMapper.selectPage(goodsPage, query);

        return Map.of("code", 200, "data", Map.of(
                "records", goodsPage.getRecords(),
                "total", goodsPage.getTotal()
        ));
    }

    // ✅ 审核通过 (将状态从 0 改为 1)
    @PostMapping("/checkGoods/approve")
    public ResponseEntity<?> approveGoods(@RequestBody Map<String, Long> payload,
                                          @RequestHeader("Authorization") String authHeader) {
        if (!isAdmin(authHeader)) return ResponseEntity.status(403).body(Map.of("code", 403, "message", "无权限"));

        Long id = payload.get("id");
        Goods goods = goodsMapper.selectById(id);
        if (goods == null || goods.getStatus() != 0) {
            return ResponseEntity.status(404).body(Map.of("code", 404, "message", "该商品不在待审核状态"));
        }

        goods.setStatus(1); // 审核通过，正式上架
        goodsMapper.updateById(goods);

        return ResponseEntity.ok(Map.of("code", 200, "message", "审核通过，商品已正式发布"));
    }

    // ✅ 拒绝并删除违规商品 (物理删除)
    @PostMapping("/checkGoods/reject")
    public ResponseEntity<?> rejectCheckGoods(@RequestBody Map<String, Long> payload,
                                              @RequestHeader("Authorization") String authHeader) {
        if (!isAdmin(authHeader)) return ResponseEntity.status(403).body(Map.of("code", 403, "message", "无权限"));

        Long id = payload.get("id");
        Goods goods = goodsMapper.selectById(id);
        if (goods == null || goods.getStatus() != 0) {
            return ResponseEntity.status(404).body(Map.of("code", 404, "message", "该商品不在待审核状态"));
        }

        goodsMapper.deleteById(id);
        return ResponseEntity.ok(Map.of("code", 200, "message", "审核拒绝，违规商品已删除"));
    }


    // ==========================================
    // 模块三：系统公告 (独立于商品体系)
    // ==========================================

    @PostMapping("/announce")
    public ResponseEntity<?> publishAnnouncement(@RequestBody Map<String, String> payload,
                                                 @RequestHeader("Authorization") String authHeader) {
        if (!isAdmin(authHeader)) return ResponseEntity.status(403).body(Map.of("code", 403, "message", "无权限"));

        String title = payload.get("title");
        String content = payload.get("content");

        if (title == null || title.trim().isEmpty() || content == null || content.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "标题和内容不能为空"));
        }

        User admin = getAdminUser(authHeader);

        SysNotice notice = new SysNotice();
        notice.setTitle(title);
        notice.setContent(content);
        notice.setAdminId(admin.getId());
        notice.setAuthor("管理员_" + admin.getUsername());
        notice.setCreateTime(LocalDateTime.now());

        sysNoticeMapper.insert(notice);

        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "公告发布成功",
                "author", notice.getAuthor()
        ));
    }
}