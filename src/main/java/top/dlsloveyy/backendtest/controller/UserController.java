package top.dlsloveyy.backendtest.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import top.dlsloveyy.backendtest.annotation.RateLimit;
import top.dlsloveyy.backendtest.entity.Follow;
import top.dlsloveyy.backendtest.entity.Goods;
import top.dlsloveyy.backendtest.entity.User;
import top.dlsloveyy.backendtest.mapper.FollowMapper;
import top.dlsloveyy.backendtest.mapper.GoodsMapper;
import top.dlsloveyy.backendtest.mapper.UserMapper;
import top.dlsloveyy.backendtest.service.UserService;
import top.dlsloveyy.backendtest.util.JwtUtil;

import java.math.BigDecimal; // 🚀 必须引入 BigDecimal
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private GoodsMapper goodsMapper;

    @Autowired
    private FollowMapper followMapper;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private final UserService userService;

    // ✅ 登录接口（5次/分钟 IP 限流，防暴力破解）
    @RateLimit(max = 5, window = 60)
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody User loginUser) {
        String username = loginUser.getUsername();
        String password = loginUser.getPassword();

        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getUsername, username)
                .eq(User::getPassword, password);

        User user = userMapper.selectOne(queryWrapper);

        if (user == null) {
            return ResponseEntity.ok(Map.of("code", 401, "message", "用户名或密码错误"));
        }

        // 双Token：生成 AccessToken（15分钟）和 RefreshToken（7天）
        String accessToken  = jwtUtil.generateAccessToken(user.getId(), username);
        String refreshToken = jwtUtil.generateRefreshToken(user.getId());

        // RefreshToken 存入 Redis，key 按 userId，用于刷新校验和登出失效
        redisTemplate.opsForValue().set("refresh_token:" + user.getId(), refreshToken, 7, TimeUnit.DAYS);
        // 保留旧式 key 兼容 logout 删除逻辑
        redisTemplate.opsForValue().set("token:" + username, accessToken, 7, TimeUnit.DAYS);

        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "登录成功",
                "token", accessToken,        // 旧字段，保持前端兼容
                "accessToken", accessToken,
                "refreshToken", refreshToken,
                "id", user.getId(),
                "username", user.getUsername(),
                "isAdmin", user.getIsAdmin()
        ));
    }

    // ✅ 注册接口
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody User user) {
        LambdaQueryWrapper<User> checkWrapper = new LambdaQueryWrapper<>();
        checkWrapper.eq(User::getUsername, user.getUsername());

        if (userMapper.exists(checkWrapper)) {
            return ResponseEntity.ok(Map.of("code", 400, "message", "用户名已存在"));
        }

        if (user.getAvatar() == null || user.getAvatar().trim().isEmpty()) {
            user.setAvatar("/default-avatar.png");
        }

        user.setIsAdmin(false);
        user.setPoints(0);
        user.setCreditScore(100);

        // 🚀 修改位置：将 0.0 修改为 BigDecimal.ZERO
        user.setBalance(BigDecimal.ZERO);

        userMapper.insert(user);
        return ResponseEntity.ok(Map.of("code", 200, "message", "注册成功"));
    }

    // ✅ 获取用户信息
    @GetMapping("/info")
    public ResponseEntity<?> getUserInfo(@RequestHeader("Authorization") String token) {
        String username = jwtUtil.extractUsername(token.replace("Bearer ", ""));

        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getUsername, username);
        User user = userMapper.selectOne(queryWrapper);

        if (user == null) {
            return ResponseEntity.status(404).body("用户不存在");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("username", user.getUsername());
        result.put("email", user.getEmail());
        result.put("avatar", user.getAvatar());
        result.put("signature", user.getSignature());
        result.put("gender", user.getGender());
        result.put("age", user.getAge());
        result.put("points", user.getPoints());
        result.put("contactPhone", user.getContactPhone());
        result.put("wechatId", user.getWechatId());
        result.put("dormBuilding", user.getDormBuilding());
        result.put("creditScore", user.getCreditScore());
        result.put("balance", user.getBalance()); // 这里直接返回 BigDecimal 对象，JSON 序列化会自动处理

        return ResponseEntity.ok(result);
    }

    // ✅ 更新用户信息
    @PutMapping("/update")
    public ResponseEntity<?> updateUserInfo(@RequestHeader("Authorization") String token,
                                            @RequestBody Map<String, Object> updateData) {
        String username = jwtUtil.extractUsername(token.replace("Bearer ", ""));

        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getUsername, username);
        User user = userMapper.selectOne(queryWrapper);

        if (user == null) {
            return ResponseEntity.status(404).body("用户不存在");
        }

        String newUsername = (String) updateData.get("username");
        String email = (String) updateData.get("email");
        String password = (String) updateData.get("password");
        String avatar = (String) updateData.get("avatar");
        String signature = (String) updateData.get("signature");
        String gender = (String) updateData.get("gender");
        Integer age = updateData.get("age") != null ? (Integer) updateData.get("age") : null;

        String contactPhone = (String) updateData.get("contactPhone");
        String wechatId = (String) updateData.get("wechatId");
        String dormBuilding = (String) updateData.get("dormBuilding");

        if (newUsername != null && !newUsername.equals(user.getUsername())) {
            LambdaQueryWrapper<User> checkWrapper = new LambdaQueryWrapper<>();
            checkWrapper.eq(User::getUsername, newUsername);
            if (userMapper.exists(checkWrapper)) {
                return ResponseEntity.badRequest().body("该用户名已被占用");
            }
            user.setUsername(newUsername);
        }

        if (email != null) user.setEmail(email);
        if (password != null && !password.isEmpty()) user.setPassword(password);
        if (avatar != null) user.setAvatar(avatar);
        if (signature != null) user.setSignature(signature);
        if (gender != null) user.setGender(gender);
        if (age != null) user.setAge(age);

        if (contactPhone != null) user.setContactPhone(contactPhone);
        if (wechatId != null) user.setWechatId(wechatId);
        if (dormBuilding != null) user.setDormBuilding(dormBuilding);

        userMapper.updateById(user);
        return ResponseEntity.ok("更新成功");
    }

    @GetMapping("/check-admin")
    public ResponseEntity<?> checkAdmin(@RequestHeader("Authorization") String token) {
        String username = jwtUtil.extractUsername(token.replace("Bearer ", ""));
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getUsername, username);
        User user = userMapper.selectOne(queryWrapper);

        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        return ResponseEntity.ok(Map.of("isAdmin", user.getIsAdmin()));
    }

    // ✅ 更新用户积分
    @PostMapping("/update-points")
    public ResponseEntity<String> updateUserPoints(@RequestHeader("Authorization") String token) {
        String username = jwtUtil.extractUsername(token.replace("Bearer ", ""));
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getUsername, username);
        User user = userMapper.selectOne(queryWrapper);

        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        int newPoints = userService.getUserPrints(user.getId());

        LambdaUpdateWrapper<User> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(User::getId, user.getId())
                .set(User::getPoints, newPoints);

        userMapper.update(null, updateWrapper);

        return ResponseEntity.ok("积分更新成功");
    }

    // ✅ 获取指定用户的公开信息
    @GetMapping("/public-info/{id}")
    public ResponseEntity<?> getPublicUserInfo(@PathVariable Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            return ResponseEntity.status(404).body("用户不存在");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("username", user.getUsername());
        result.put("avatar", user.getAvatar());
        result.put("signature", user.getSignature());
        result.put("gender", user.getGender());
        result.put("age", user.getAge());
        result.put("points", user.getPoints());
        result.put("creditScore", user.getCreditScore());
        result.put("dormBuilding", user.getDormBuilding());

        return ResponseEntity.ok(result);
    }

    // ✅ 获取指定用户的关注对象
    @GetMapping("/public-followees/{id}")
    public ResponseEntity<?> getPublicFollowees(@PathVariable Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            return ResponseEntity.status(404).body("用户不存在");
        }

        List<Follow> followingList = followMapper.selectList(
                new LambdaQueryWrapper<Follow>().eq(Follow::getFollowerId, id)
        );

        if (followingList.isEmpty()) {
            return ResponseEntity.ok(new ArrayList<>());
        }

        List<Long> followeeIds = followingList.stream()
                .map(Follow::getFolloweeId)
                .collect(Collectors.toList());

        List<User> followees = userMapper.selectBatchIds(followeeIds);

        List<Map<String, Object>> result = followees.stream().map(f -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", f.getId());
            map.put("username", f.getUsername());
            map.put("avatar", f.getAvatar());
            map.put("signature", f.getSignature());
            return map;
        }).toList();

        return ResponseEntity.ok(result);
    }

    // ✅ 获取指定卖家的在售商品
    @GetMapping("/public-goods/{id}")
    public ResponseEntity<?> getPublicGoods(@PathVariable Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            return ResponseEntity.status(404).body("用户不存在");
        }

        List<Goods> goodsList = goodsMapper.selectList(new LambdaQueryWrapper<Goods>()
                .eq(Goods::getSellerId, id)
                .eq(Goods::getStatus, 1)
                .orderByDesc(Goods::getCreateTime));

        return ResponseEntity.ok(goodsList);
    }
    // ==================== 卖家商品管理 ====================

    // ✅ 获取当前登录卖家的“全部闲置”（不限制状态，包含已售出和在售）
    @GetMapping("/my-goods")
    public ResponseEntity<?> getMyGoods(@RequestHeader("Authorization") String authHeader) {
        try {
            // 1. 解析当前登录用户
            String username = jwtUtil.extractUsername(authHeader.replace("Bearer ", ""));
            User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
            if (user == null) {
                return ResponseEntity.status(404).body("用户不存在");
            }

            // 2. 查询该用户的所有商品（注意：这里没有 .eq(Goods::getStatus, 1) 的限制）
            List<Goods> myGoodsList = goodsMapper.selectList(new LambdaQueryWrapper<Goods>()
                    .eq(Goods::getSellerId, user.getId())
                    .orderByDesc(Goods::getCreateTime));

            // 3. 返回数据
            return ResponseEntity.ok(myGoodsList);

        } catch (Exception e) {
            return ResponseEntity.badRequest().body("加载闲置列表失败");
        }
    }

    // ✅ 获取当前用户信息
    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(@RequestHeader("Authorization") String authHeader) {
        String username = jwtUtil.extractUsername(authHeader.replace("Bearer ", ""));
        if (username == null) {
            return ResponseEntity.status(401).body("无效的 Token");
        }

        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getUsername, username);
        User user = userMapper.selectOne(queryWrapper);

        if (user == null) {
            return ResponseEntity.status(404).body("用户不存在");
        }

        User safeUser = new User();
        safeUser.setId(user.getId());
        safeUser.setUsername(user.getUsername());
        safeUser.setAvatar(user.getAvatar());
        safeUser.setSignature(user.getSignature());
        safeUser.setGender(user.getGender());
        safeUser.setAge(user.getAge());
        safeUser.setEmail(user.getEmail());
        safeUser.setPoints(user.getPoints());
        safeUser.setIsAdmin(user.getIsAdmin());
        safeUser.setContactPhone(user.getContactPhone());
        safeUser.setWechatId(user.getWechatId());
        safeUser.setDormBuilding(user.getDormBuilding());
        safeUser.setCreditScore(user.getCreditScore());
        safeUser.setBalance(user.getBalance());

        return ResponseEntity.ok(safeUser);
    }

    // ✅ 退出登录
    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader("Authorization") String token) {
        try {
            String rawToken = token.replace("Bearer ", "");
            String username = jwtUtil.extractUsername(rawToken);
            Long userId = jwtUtil.getUserIdFromToken(rawToken);

            redisTemplate.delete("token:" + username);
            if (userId != null) {
                redisTemplate.delete("refresh_token:" + userId);
            }
            return ResponseEntity.ok(Map.of("code", 200, "message", "退出登录成功"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "退出失败或Token无效"));
        }
    }
    // ==================== 资金管理：充值与提现 ====================

    // ✅ 模拟充值接口
    @PostMapping("/recharge")
    public ResponseEntity<?> recharge(@RequestHeader("Authorization") String token, @RequestBody Map<String, Object> params) {
        try {
            String username = jwtUtil.extractUsername(token.replace("Bearer ", ""));
            User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
            if (user == null) return ResponseEntity.status(404).body(Map.of("code", 404, "message", "用户不存在"));

            // 提取金额并转换为 BigDecimal
            BigDecimal amount = new BigDecimal(params.get("amount").toString());
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "充值金额必须大于0"));
            }

            userService.increaseBalance(user.getId(), amount);

            return ResponseEntity.ok(Map.of("code", 200, "message", "充值成功"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "充值失败：" + e.getMessage()));
        }
    }

    // ✅ 模拟提现接口
    @PostMapping("/withdraw")
    public ResponseEntity<?> withdraw(@RequestHeader("Authorization") String token, @RequestBody Map<String, Object> params) {
        try {
            String username = jwtUtil.extractUsername(token.replace("Bearer ", ""));
            User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
            if (user == null) return ResponseEntity.status(404).body(Map.of("code", 404, "message", "用户不存在"));

            BigDecimal amount = new BigDecimal(params.get("amount").toString());
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "提现金额必须大于0"));
            }

            // 调用扣减余额方法 (里面自带了余额不足的校验)
            userService.decreaseBalance(user.getId(), amount);

            return ResponseEntity.ok(Map.of("code", 200, "message", "提现申请已提交，预计24小时内到账微信/支付宝"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", e.getMessage()));
        }
    }
}