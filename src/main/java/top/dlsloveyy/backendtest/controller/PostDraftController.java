package top.dlsloveyy.backendtest.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import top.dlsloveyy.backendtest.entity.GoodsDraft;
import top.dlsloveyy.backendtest.entity.GoodsVariant;
import top.dlsloveyy.backendtest.entity.User;
import top.dlsloveyy.backendtest.mapper.GoodsDraftMapper;
import top.dlsloveyy.backendtest.mapper.UserMapper;
import top.dlsloveyy.backendtest.util.JwtUtil;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/draft")
@RequiredArgsConstructor
public class PostDraftController {

    private final GoodsDraftMapper goodsDraftMapper;

    private final UserMapper userMapper;

    private final JwtUtil jwtUtil;

    private final ObjectMapper objectMapper;

    // ✅ 保存商品草稿
    @PostMapping("/save")
    @Transactional
    public ResponseEntity<?> saveDraft(@RequestBody GoodsDraft draftPayload,
                                       HttpServletRequest request) {
        if (draftPayload.getTitle() == null || draftPayload.getTitle().trim().isEmpty() ||
                draftPayload.getContent() == null || draftPayload.getContent().trim().isEmpty()) {
            return ResponseEntity.ok(Map.of("code", 1, "message", "标题和内容不能为空"));
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(401).body(Map.of("code", 2, "message", "未授权"));
        }

        String token = authHeader.substring(7);
        String username = jwtUtil.extractUsername(token);
        if (username == null) {
            return ResponseEntity.status(401).body(Map.of("code", 3, "message", "无效的 token"));
        }

        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (user == null) {
            return ResponseEntity.status(404).body(Map.of("code", 4, "message", "用户不存在"));
        }

        // 1. 删除该用户的旧草稿
        goodsDraftMapper.delete(new LambdaQueryWrapper<GoodsDraft>().eq(GoodsDraft::getSellerId, user.getId()));

        // 2. 保存新草稿
        draftPayload.setSellerId(user.getId());
        draftPayload.setId(null); // 设为 null 确保是新增
        draftPayload.setCreateTime(LocalDateTime.now());
        if ((draftPayload.getImages() == null || draftPayload.getImages().isBlank()) && draftPayload.getCoverImg() != null && !draftPayload.getCoverImg().isBlank()) {
            draftPayload.setImages(draftPayload.getCoverImg());
        }
        if ((draftPayload.getCoverImg() == null || draftPayload.getCoverImg().isBlank()) && draftPayload.getImages() != null && !draftPayload.getImages().isBlank()) {
            draftPayload.setCoverImg(draftPayload.getImages().split(",")[0].trim());
        }
        if ((draftPayload.getDeliveryMethods() == null || draftPayload.getDeliveryMethods().isBlank()) && draftPayload.getDeliveryType() != null) {
            draftPayload.setDeliveryMethods(draftPayload.getDeliveryType());
        }
        if ((draftPayload.getDeliveryType() == null || draftPayload.getDeliveryType().isBlank()) && draftPayload.getDeliveryMethods() != null && !draftPayload.getDeliveryMethods().isBlank()) {
            draftPayload.setDeliveryType(draftPayload.getDeliveryMethods().split(",")[0].trim());
        }
        if (draftPayload.getVariants() != null) {
            try {
                draftPayload.setVariantsJson(objectMapper.writeValueAsString(draftPayload.getVariants()));
            } catch (Exception e) {
                return ResponseEntity.ok(Map.of("code", 5, "message", "规格草稿序列化失败"));
            }
        } else {
            draftPayload.setVariantsJson(null);
        }

        goodsDraftMapper.insert(draftPayload);

        return ResponseEntity.ok(Map.of("code", 0, "message", "商品草稿保存成功"));
    }

    // ✅ 获取最新草稿
    @GetMapping("/latest")
    public ResponseEntity<?> getLatestDraft(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(401).body(Map.of("code", 1, "message", "未授权"));
        }

        String token = authHeader.substring(7);
        String username = jwtUtil.extractUsername(token);
        if (username == null) {
            return ResponseEntity.status(401).body(Map.of("code", 2, "message", "无效 token"));
        }

        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (user == null) {
            return ResponseEntity.status(404).body(Map.of("code", 3, "message", "用户不存在"));
        }

        // 查询该卖家最新的草稿
        List<GoodsDraft> drafts = goodsDraftMapper.selectList(
                new LambdaQueryWrapper<GoodsDraft>()
                        .eq(GoodsDraft::getSellerId, user.getId())
                        .orderByDesc(GoodsDraft::getCreateTime)
        );

        if (drafts.isEmpty()) {
            Map<String, Object> response = new HashMap<>();
            response.put("code", 0);
            response.put("message", "无草稿");
            response.put("data", null);
            return ResponseEntity.ok(response);
        }

        GoodsDraft latest = drafts.get(0);
        if (latest.getVariantsJson() != null && !latest.getVariantsJson().isBlank()) {
            try {
                latest.setVariants(objectMapper.readValue(latest.getVariantsJson(), new TypeReference<List<GoodsVariant>>() {}));
            } catch (Exception e) {
                latest.setVariants(List.of());
            }
        }

        return ResponseEntity.ok(Map.of(
                "code", 0,
                "message", "成功",
                "data", latest
        ));
    }
}