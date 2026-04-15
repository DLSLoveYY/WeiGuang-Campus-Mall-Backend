package top.dlsloveyy.backendtest.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import top.dlsloveyy.backendtest.entity.Comment;
import top.dlsloveyy.backendtest.entity.Goods;
import top.dlsloveyy.backendtest.entity.User;
import top.dlsloveyy.backendtest.mapper.CommentMapper;
import top.dlsloveyy.backendtest.mapper.GoodsMapper;
import top.dlsloveyy.backendtest.mapper.UserMapper;
import top.dlsloveyy.backendtest.util.JwtUtil;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/comment")
@RequiredArgsConstructor
public class CommentController {

    @Autowired
    private CommentMapper commentMapper;

    @Autowired
    private GoodsMapper goodsMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JwtUtil jwtUtil;

    // ==========================================
    // 1. 添加留言
    // ==========================================
    @PostMapping("/add")
    public ResponseEntity<?> addComment(@RequestHeader("Authorization") String authHeader,
                                        @RequestBody Comment comment) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(401).body(Map.of("code", 401, "message", "未登录用户不能发表留言"));
        }

        String username = jwtUtil.extractUsername(authHeader.replace("Bearer ", ""));
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("code", 401, "message", "用户不存在"));
        }

        // ✅ 直接存 userId，而不是 User 对象
        comment.setUserId(user.getId());
        comment.setCreateTime(LocalDateTime.now());
        commentMapper.insert(comment);

        // ✅ 更新该商品的留言总数
        Goods goods = goodsMapper.selectById(comment.getGoodsId());
        if (goods != null) {
            goods.setCommentCount(goods.getCommentCount() + 1);
            goodsMapper.updateById(goods);
        }

        return ResponseEntity.ok(Map.of("code", 200, "message", "留言成功"));
    }

    // ==========================================
    // 2. 平铺式列表
    // ==========================================
    @GetMapping("/list")
    public Map<String, Object> getFlatCommentList(@RequestParam Long goodsId) {
        List<Comment> comments = commentMapper.selectList(new LambdaQueryWrapper<Comment>()
                .eq(Comment::getGoodsId, goodsId)
                .orderByAsc(Comment::getCreateTime));

        List<Map<String, Object>> result = new ArrayList<>();

        for (Comment c : comments) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", c.getId());
            map.put("content", c.getContent());
            map.put("createTime", c.getCreateTime());

            User user = userMapper.selectById(c.getUserId());
            if (user != null) {
                map.put("user", Map.of(
                        "id", user.getId(),
                        "username", user.getUsername(),
                        "avatar", user.getAvatar() != null ? user.getAvatar() : ""
                ));
            } else {
                map.put("user", null);
            }

            result.add(map);
        }

        return Map.of("code", 200, "comments", result);
    }

    // ==========================================
    // 3. 嵌套结构 (树形) 入口
    // ==========================================
    @GetMapping("/tree")
    public List<Map<String, Object>> getNestedComments(@RequestParam Long goodsId) {
        // 查找所有顶层留言 (parentId 为 null)
        List<Comment> topLevel = commentMapper.selectList(new LambdaQueryWrapper<Comment>()
                .eq(Comment::getGoodsId, goodsId)
                .isNull(Comment::getParentId)
                .orderByAsc(Comment::getCreateTime));

        return buildCommentTree(goodsId, topLevel);
    }

    // 构建树状结构（接收顶层列表）
    private List<Map<String, Object>> buildCommentTree(Long goodsId, List<Comment> topComments) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Comment comment : topComments) {
            result.add(convertCommentToMap(goodsId, comment));
        }
        return result;
    }

    // 原始递归构建（根据 parentId）
    private List<Map<String, Object>> buildCommentTree(Long goodsId, Long parentId) {
        List<Comment> children = commentMapper.selectList(new LambdaQueryWrapper<Comment>()
                .eq(Comment::getGoodsId, goodsId)
                .eq(Comment::getParentId, parentId)
                .orderByAsc(Comment::getCreateTime));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Comment child : children) {
            result.add(convertCommentToMap(goodsId, child));
        }
        return result;
    }

    // 核心转换逻辑：包含 user、replyTo、replies
    private Map<String, Object> convertCommentToMap(Long goodsId, Comment comment) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", comment.getId());
        map.put("content", comment.getContent());
        map.put("createTime", comment.getCreateTime());
        map.put("parentId", comment.getParentId());

        // 获取用户信息
        User user = userMapper.selectById(comment.getUserId());
        if (user != null) {
            map.put("user", Map.of(
                    "id", user.getId(),
                    "username", user.getUsername(),
                    "avatar", user.getAvatar() != null ? user.getAvatar() : ""
            ));
        } else {
            map.put("user", null);
        }

        // replyTo 信息 (谁回复了谁)
        if (comment.getParentId() != null && comment.getParentId() > 0) {
            Comment parent = commentMapper.selectById(comment.getParentId());
            if (parent != null) {
                Map<String, Object> replyTo = new HashMap<>();
                User parentUser = userMapper.selectById(parent.getUserId());
                if (parentUser != null) {
                    replyTo.put("username", parentUser.getUsername());
                }
                replyTo.put("content", simplifyQuotedContent(parent.getContent()));
                map.put("replyTo", replyTo);
            }
        }

        // 递归构建子留言
        List<Map<String, Object>> replies = buildCommentTree(goodsId, comment.getId());
        map.put("replies", replies != null ? replies : new ArrayList<>());

        return map;
    }

    /**
     * 简化引用内容：图片替换为【图片】，截断太长内容
     */
    private String simplifyQuotedContent(String raw) {
        if (raw == null) return "";
        String noImage = raw.replaceAll("!\\[[^\\]]*\\]\\([^\\)]*\\)", "【图片】");
        if (noImage.length() > 60) {
            noImage = noImage.substring(0, 60) + "...";
        }
        return noImage;
    }

    // ==========================================
    // 4. 扁平化数据获取 (包含父级信息)
    // ==========================================
    @GetMapping("/flat")
    public List<Map<String, Object>> getFlatComments(@RequestParam Long goodsId) {
        List<Comment> all = commentMapper.selectList(new LambdaQueryWrapper<Comment>()
                .eq(Comment::getGoodsId, goodsId)
                .orderByAsc(Comment::getCreateTime));

        List<Map<String, Object>> result = new ArrayList<>();

        for (Comment comment : all) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", comment.getId());
            map.put("content", comment.getContent());
            map.put("createTime", comment.getCreateTime());
            map.put("parentId", comment.getParentId());

            // 用户信息
            User user = userMapper.selectById(comment.getUserId());
            if (user != null) {
                map.put("user", Map.of(
                        "id", user.getId(),
                        "username", user.getUsername(),
                        "avatar", user.getAvatar() != null ? user.getAvatar() : ""
                ));
            }

            // replyTo 信息
            if (comment.getParentId() != null && comment.getParentId() > 0) {
                Comment parent = commentMapper.selectById(comment.getParentId());
                if (parent != null) {
                    Map<String, Object> replyTo = new HashMap<>();
                    User parentUser = userMapper.selectById(parent.getUserId());
                    if (parentUser != null) {
                        replyTo.put("username", parentUser.getUsername());
                    }
                    replyTo.put("content", simplifyQuotedContent(parent.getContent()));
                    map.put("replyTo", replyTo);
                }
            }

            result.add(map);
        }

        return result;
    }
}