package top.dlsloveyy.backendtest.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import top.dlsloveyy.backendtest.entity.Goods;
import top.dlsloveyy.backendtest.entity.User;
import top.dlsloveyy.backendtest.mapper.GoodsMapper;
import top.dlsloveyy.backendtest.mapper.UserMapper;
import top.dlsloveyy.backendtest.service.AuditService;
import top.dlsloveyy.backendtest.util.JwtUtil;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@RestController
// ✅ 保持旧的路由不改，兼容前端
@RequestMapping("/api/unpost")
@RequiredArgsConstructor
public class UnPostController {

    @Autowired
    private GoodsMapper goodsMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private AuditService auditService;

    @PostMapping("/submit")
    public ResponseEntity<?> submitPost(@RequestBody Map<String, String> payload,
                                        HttpServletRequest request) {
        String title = payload.get("title");
        String content = payload.get("content");

        if (title == null || title.trim().isEmpty() || content == null || content.trim().isEmpty()) {
            return ResponseEntity.ok(Map.of("code", 400, "message", "标题或内容不能为空"));
        }

        String token = request.getHeader("Authorization");
        if (token == null || !token.startsWith("Bearer ")) {
            return ResponseEntity.status(401).body(Map.of("code", 401, "message", "未登录或Token缺失"));
        }

        token = token.substring(7);
        String username = jwtUtil.extractUsername(token);
        if (username == null) {
            return ResponseEntity.status(401).body(Map.of("code", 401, "message", "Token无效"));
        }

        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("code", 401, "message", "用户不存在"));
        }

        // 🚀 核心转变：将前端传来的“旧帖子”，转化为“待审核的商品”存入数据库
        Goods goods = new Goods();
        goods.setTitle(title);
        goods.setDescription(content); // 把 content 映射给 description
        goods.setSellerId(user.getId());

        // 关键状态控制：0 代表进入待审核池
        goods.setStatus(0);
        goods.setCreateTime(LocalDateTime.now());
        goods.setUpdateTime(LocalDateTime.now());

        // 兜底赋值，防止数据库非空约束报错
        goods.setPrice(BigDecimal.ZERO);
        goods.setViewCount(0);
        goods.setFavoritesCount(0);
        goods.setCommentCount(0);

        goodsMapper.insert(goods);

        // ✅ 调用你刚才重构的新版自动审核服务
        auditService.autoAuditAll();

        return ResponseEntity.ok(Map.of("code", 200, "message", "发布成功，系统正在进行安全审核"));
    }
}