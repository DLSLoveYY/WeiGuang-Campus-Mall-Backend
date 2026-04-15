package top.dlsloveyy.backendtest.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import top.dlsloveyy.backendtest.entity.Follow;
import top.dlsloveyy.backendtest.entity.User;
import top.dlsloveyy.backendtest.mapper.FollowMapper;
import top.dlsloveyy.backendtest.mapper.UserMapper;
import top.dlsloveyy.backendtest.util.JwtUtil;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/follow")
@RequiredArgsConstructor
public class FollowController {

    @Autowired
    private FollowMapper followMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JwtUtil jwtUtil;

    // ✅ 添加关注
    @PostMapping("/add")
    public ResponseEntity<?> follow(@RequestHeader("Authorization") String token,
                                    @RequestBody Map<String, Long> body) {
        String username = jwtUtil.extractUsername(token.replace("Bearer ", ""));
        Long followeeId = body.get("followeeId");

        User follower = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        User followee = userMapper.selectById(followeeId);

        if (follower == null || followee == null || follower.getId().equals(followee.getId())) {
            return ResponseEntity.badRequest().body("无效用户");
        }

        // 检查是否已经关注
        boolean exists = followMapper.exists(new LambdaQueryWrapper<Follow>()
                .eq(Follow::getFollowerId, follower.getId())
                .eq(Follow::getFolloweeId, followee.getId()));

        if (exists) {
            return ResponseEntity.ok("已经关注");
        }

        Follow follow = new Follow();
        follow.setFollowerId(follower.getId());
        follow.setFolloweeId(followee.getId());
        follow.setCreateTime(LocalDateTime.now());
        followMapper.insert(follow);

        return ResponseEntity.ok(Map.of("code", 200, "message", "关注成功"));
    }

    // ✅ 取消关注
    @PostMapping("/remove")
    public ResponseEntity<?> unfollow(@RequestHeader("Authorization") String token,
                                      @RequestBody Map<String, Long> body) {
        String username = jwtUtil.extractUsername(token.replace("Bearer ", ""));
        Long followeeId = body.get("followeeId");

        User follower = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));

        if (follower == null) {
            return ResponseEntity.badRequest().body("无效用户");
        }

        // 删除关注记录
        followMapper.delete(new LambdaQueryWrapper<Follow>()
                .eq(Follow::getFollowerId, follower.getId())
                .eq(Follow::getFolloweeId, followeeId));

        return ResponseEntity.ok(Map.of("code", 200, "message", "已取消关注"));
    }

    // ✅ 查询是否已关注
    @GetMapping("/is-following/{followeeId}")
    public ResponseEntity<?> isFollowing(@RequestHeader("Authorization") String token,
                                         @PathVariable Long followeeId) {
        if (token == null || !token.startsWith("Bearer ")) {
            return ResponseEntity.ok(Map.of("following", false));
        }

        String username = jwtUtil.extractUsername(token.replace("Bearer ", ""));
        User follower = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));

        if (follower == null) {
            return ResponseEntity.ok(Map.of("following", false));
        }

        boolean following = followMapper.exists(new LambdaQueryWrapper<Follow>()
                .eq(Follow::getFollowerId, follower.getId())
                .eq(Follow::getFolloweeId, followeeId));

        return ResponseEntity.ok(Map.of("following", following));
    }

    // ✅ 获取我的关注列表
    @GetMapping("/my-following")
    public ResponseEntity<?> getMyFollowing(@RequestHeader("Authorization") String token) {
        String username = jwtUtil.extractUsername(token.replace("Bearer ", ""));
        User follower = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));

        if (follower == null) {
            return ResponseEntity.status(401).body("未登录");
        }

        // 1. 查出当前用户关注的所有记录
        List<Follow> followingList = followMapper.selectList(
                new LambdaQueryWrapper<Follow>().eq(Follow::getFollowerId, follower.getId())
        );

        if (followingList.isEmpty()) {
            return ResponseEntity.ok(new ArrayList<>());
        }

        // 2. 提取出所有被关注者的 ID 集合
        List<Long> followeeIds = followingList.stream()
                .map(Follow::getFolloweeId)
                .collect(Collectors.toList());

        // 3. 批量查询用户信息 (性能优化：用 selectBatchIds 一次性查出所有用户，而不是在 for 循环里挨个查)
        List<User> users = userMapper.selectBatchIds(followeeIds);

        // 4. 组装返回给前端的数据格式
        List<Map<String, Object>> result = new ArrayList<>();
        for (User u : users) {
            String avatar = u.getAvatar();
            if (avatar != null && !avatar.startsWith("http")) {
                avatar = "http://localhost:8080" + avatar;
            }
            Map<String, Object> map = new HashMap<>();
            map.put("id", u.getId());
            map.put("username", u.getUsername());
            map.put("avatar", avatar);
            map.put("signature", u.getSignature() != null ? u.getSignature() : "这个人很懒，什么都没写");
            result.add(map);
        }

        return ResponseEntity.ok(result);
    }
}