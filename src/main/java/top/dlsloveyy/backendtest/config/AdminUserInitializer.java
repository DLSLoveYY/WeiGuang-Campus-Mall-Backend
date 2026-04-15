package top.dlsloveyy.backendtest.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import top.dlsloveyy.backendtest.entity.User;
import top.dlsloveyy.backendtest.mapper.UserMapper;

import java.math.BigDecimal; // 🚀 必须引入这个包

@Component
public class AdminUserInitializer {

    @Autowired
    private UserMapper userMapper;

    private final String ADMIN_USERNAME = "dlsloveyy";
    private final String ADMIN_PASSWORD = "123456";
    private final String ADMIN_EMAIL = "admin@example.com";

    @PostConstruct
    public void initAdminUser() {
        LambdaQueryWrapper<User> query = new LambdaQueryWrapper<>();
        query.eq(User::getUsername, ADMIN_USERNAME);

        if (userMapper.selectOne(query) == null) {
            User admin = new User();
            admin.setUsername(ADMIN_USERNAME);
            admin.setPassword(ADMIN_PASSWORD);
            admin.setEmail(ADMIN_EMAIL);
            admin.setIsAdmin(true);
            admin.setEnabled(true);
            admin.setAvatar("/uploads/default-avatar.png");
            admin.setSignature("我是最高管理员");
            admin.setGender("保密");
            admin.setPoints(9999);
            // 初始化商城专属字段
            admin.setCreditScore(100);

            // 🚀 修复位置：将 0.0 修改为 BigDecimal.ZERO
            admin.setBalance(BigDecimal.ZERO);

            userMapper.insert(admin);
            System.out.println("✅ 已初始化超级管理员账号：dlsloveyy");
        } else {
            System.out.println("ℹ️ 超级管理员已存在，无需初始化");
        }
    }
}