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
import top.dlsloveyy.backendtest.service.AccountService;
import top.dlsloveyy.backendtest.service.UserAddressService;
import top.dlsloveyy.backendtest.service.UserService;
import top.dlsloveyy.backendtest.util.JwtUtil;

import java.math.BigDecimal;
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

    @Autowired
    private UserAddressService userAddressService;

    private final UserService userService;

    private final AccountService accountService;

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
        if (!Boolean.TRUE.equals(user.getEnabled())) {
            return ResponseEntity.ok(Map.of("code", 403, "message", "账号已被封禁，请联系管理员"));
        }

        String accessToken = jwtUtil.generateAccessToken(user.getId(), username);
        String refreshToken = jwtUtil.generateRefreshToken(user.getId());

        redisTemplate.opsForValue().set("refresh_token:" + user.getId(), refreshToken, 7, TimeUnit.DAYS);
        redisTemplate.opsForValue().set("token:" + username, accessToken, 7, TimeUnit.DAYS);

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "登录成功");
        result.put("token", accessToken);
        result.put("accessToken", accessToken);
        result.put("refreshToken", refreshToken);
        result.put("id", user.getId());
        result.put("username", user.getUsername());
        result.put("avatar", user.getAvatar());
        result.put("signature", user.getSignature());
        result.put("gender", user.getGender());
        result.put("age", user.getAge());
        result.put("isAdmin", user.getIsAdmin());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody User user) {
        LambdaQueryWrapper<User> checkWrapper = new LambdaQueryWrapper<>();
        checkWrapper.eq(User::getUsername, user.getUsername());

        if (userMapper.exists(checkWrapper)) {
            return ResponseEntity.ok(Map.of("code", 400, "message", "用户名已存在"));
        }

        if (user.getAvatar() == null || user.getAvatar().trim().isEmpty()) {
            user.setAvatar("/uploads/default-avatar.png");
        }

        user.setIsAdmin(false);
        user.setPoints(0);
        user.setCreditScore(100);
        user.setBalance(BigDecimal.ZERO);

        userMapper.insert(user);
        return ResponseEntity.ok(Map.of("code", 200, "message", "注册成功"));
    }

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
        result.put("profileLng", user.getProfileLng());
        result.put("profileLat", user.getProfileLat());
        result.put("creditScore", user.getCreditScore());
        result.put("balance", user.getBalance());
        result.put("frozenBalance", user.getFrozenBalance());

        return ResponseEntity.ok(result);
    }

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
        BigDecimal profileLng = updateData.get("profileLng") != null ? new BigDecimal(updateData.get("profileLng").toString()) : null;
        BigDecimal profileLat = updateData.get("profileLat") != null ? new BigDecimal(updateData.get("profileLat").toString()) : null;
        BigDecimal previousProfileLng = user.getProfileLng();
        BigDecimal previousProfileLat = user.getProfileLat();
        String previousDormBuilding = user.getDormBuilding();

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

        if (!sameText(previousDormBuilding, user.getDormBuilding())
                && sameBigDecimal(previousProfileLng, profileLng)
                && sameBigDecimal(previousProfileLat, profileLat)) {
            profileLng = null;
            profileLat = null;
        }

        if ((profileLng == null || profileLat == null)
                && user.getDormBuilding() != null
                && !user.getDormBuilding().isBlank()) {
            Map<String, Object> geo = userAddressService.geocodeTextAddress(user.getDormBuilding());
            BigDecimal autoLng = toBigDecimal(geo.get("longitude"));
            BigDecimal autoLat = toBigDecimal(geo.get("latitude"));
            if (autoLng != null && autoLat != null) {
                profileLng = autoLng;
                profileLat = autoLat;
            }
        }

        user.setProfileLng(profileLng);
        user.setProfileLat(profileLat);

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

    @GetMapping("/public-goods/{id}")
    public ResponseEntity<?> getPublicGoods(@PathVariable Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            return ResponseEntity.status(404).body("用户不存在");
        }

        List<Goods> goodsList = goodsMapper.selectList(new LambdaQueryWrapper<Goods>()
                .eq(Goods::getSellerId, id)
                .in(Goods::getStatus, List.of(1, 3))
                .orderByDesc(Goods::getCreateTime));

        return ResponseEntity.ok(goodsList);
    }

    @GetMapping("/my-goods")
    public ResponseEntity<?> getMyGoods(@RequestHeader("Authorization") String authHeader) {
        try {
            String username = jwtUtil.extractUsername(authHeader.replace("Bearer ", ""));
            User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
            if (user == null) {
                return ResponseEntity.status(404).body("用户不存在");
            }

            List<Goods> myGoodsList = goodsMapper.selectList(new LambdaQueryWrapper<Goods>()
                    .eq(Goods::getSellerId, user.getId())
                    .orderByDesc(Goods::getCreateTime));

            return ResponseEntity.ok(myGoodsList);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("加载闲置列表失败");
        }
    }

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
        safeUser.setFrozenBalance(user.getFrozenBalance());

        return ResponseEntity.ok(safeUser);
    }

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

    @PostMapping("/recharge")
    public ResponseEntity<?> recharge(@RequestHeader("Authorization") String token, @RequestBody Map<String, Object> params) {
        try {
            String username = jwtUtil.extractUsername(token.replace("Bearer ", ""));
            User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
            if (user == null) return ResponseEntity.status(404).body(Map.of("code", 404, "message", "用户不存在"));

            BigDecimal amount = new BigDecimal(params.get("amount").toString());
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "充值金额必须大于0"));
            }

            accountService.credit(
                    user.getId(),
                    amount,
                    "USER_RECHARGE",
                    String.valueOf(System.currentTimeMillis()),
                    "USER_RECHARGE:IN:" + user.getId() + ":" + System.currentTimeMillis(),
                    "用户充值"
            );

            return ResponseEntity.ok(Map.of("code", 200, "message", "充值成功"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "充值失败：" + e.getMessage()));
        }
    }

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

            accountService.debit(
                    user.getId(),
                    amount,
                    "USER_WITHDRAW",
                    String.valueOf(System.currentTimeMillis()),
                    "USER_WITHDRAW:OUT:" + user.getId() + ":" + System.currentTimeMillis(),
                    "用户提现"
            );

            return ResponseEntity.ok(Map.of("code", 200, "message", "提现申请已提交，预计24小时内到账微信/支付宝"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", e.getMessage()));
        }
    }

    private boolean sameText(String left, String right) {
        String a = left == null ? "" : left.trim();
        String b = right == null ? "" : right.trim();
        return a.equals(b);
    }

    private boolean sameBigDecimal(BigDecimal left, BigDecimal right) {
        if (left == null && right == null) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return left.compareTo(right) == 0;
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
