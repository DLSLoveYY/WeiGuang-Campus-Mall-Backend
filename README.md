# ✨ 微光校园二手商城 (Glimmer Campus Mall) —— 本科毕业设计项目

本项为个人**本科毕业设计作品**。系统旨在通过全栈开发技术，解决校内二手交易中的信息不对称、并发安全及用户体验痛点。项目深度整合了 **Spring Boot 3.x、MyBatis-Plus、Redis 及 AI 大模型**，并在架构上实现了 **多级缓存、事件驱动及高阶算法过滤**。

---

## 🛠️ 全栈技术架构

### 后端 (Backend Core)
- **核心框架**：Spring Boot 3.x (企业级微服务基石)
- **持久层方案**：MyBatis-Plus (集成分页插件、乐观锁拦截器，支撑 $O(1)$ 复杂度查询) 
- **多级缓存架构**：构建 **L1 (Caffeine 本地内存)** + **L2 (Redis ZSet)** 体系，实现高响应率 
- **安全鉴权**：Spring Security + **双 Token (Access & Refresh)** 无状态认证，配合 **Redis 黑名单**  
- **并发控制**：Redis 分布式策略 + **MyBatis-Plus 乐观锁 (@Version)** 严格防超卖 
- **事件驱动**：基于 **Spring Event + @Async** 异步模型，实现核心业务与通知/邮件逻辑解耦 
- **高阶算法**： **DFA (确定有穷自动机)** 结合 **Trie 树** 实现敏感词过滤 

### 前端 (Frontend Experience)
- **核心框架**：Vue 3 (Composition API) + Pinia (状态管理)
- **UI 组件库**：Element Plus (响应式 UI 交互)
- **交互体验**：引入 **SSE (Server-Sent Events)** 流式传输技术，实现 AI 文案的“打字机特效”
- **富文本能力**：集成支持文字、Emoji 及图片混排的富文本编辑器 

---

## 🌟 核心功能亮点与毕设硬核技术

### 1. 深度安全防御体系 (Security Base)
- **双 Token 策略**：Access Token 短效校验，Refresh Token 长效续期（存入 HttpOnly Cookie），平衡安全性与用户无感体验 。
- **安全加固**：全量密码采用 **BCrypt 加盐哈希** 存储；利用 Redis 黑名单机制在退出登录时强制废弃旧 Token 。

### 2. 高性能热榜与缓存管理 (Performance Optimization)
- **实时热榜算法**：参考 **Hacker News 排名算法**，引入时间衰减因子 (Gravity)，利用 **Redis ZSet** 进行写时计算，彻底消灭全表扫描 。
- **多级缓存闭环**：请求优先拦截于 JVM 进程内的 Caffeine 缓存，失效后回退至 Redis 并自动回填，极大提升接口吞吐量 。
- **缓存双盾**：采用“缓存空对象 + 短 TTL”拦截**缓存穿透**；利用“随机抖动因子”打散过期时间规避**缓存雪崩** 。

### 3. 交易一致性与数据库调优 (Transaction & DB)
- **乐观锁防超卖**：针对二手商品“库存为 1”的特性，利用 **CAS 思想** 拦截并发抢购，比悲观锁具有更高的吞吐性能 。
- **反范式快照设计**：在订单表中冗余存储商品历史快照，消灭多表 JOIN，实现“我的订单”极速查询 。
- **慢 SQL 专项治理**：针对分类瀑布流构建满足**“最左前缀法则”**的联合索引，将查询从 ALL 优化至 ref 级别 。

### 4. AI 赋能与 LBS 社交特性 (Innovation)
- **AI 文案助手**：集成大模型 API 与 **Prompt Engineering (提示词工程)**，一键生成吸睛商品描述，并支持 **SSE 流式展示** 。
- **附近商品推荐**：利用 **Redis GEO** 地理位置服务，实现商品由近到远的精准排序，提升面交转化率 。
- **语义召回优化**：通过 AI 提取语义标签，将模糊查询转化为精准的 IN 查询，规避 LIKE 索引失效问题 。

---

## ⚙️ 环境要求

| 工具 | 版本建议 | 说明 |
| :--- | :--- | :--- |
| **JDK** | 17 | 核心运行环境 |
| **MySQL** | 8.0+ | [cite_start]数据持久化（需支持 utf8mb4 字符集以兼容 Emoji）[cite: 11, 69] |
| **Redis** | 5.0+ | **必须启动**，支撑 ZSet 热榜、GEO 位置及分布式锁 |
| **Node.js** | 16.x+ | 前端构建环境 |
| **Maven** | 3.6+ | 后端依赖管理工具 |

---

# 🚀 启动方式

## 1. 克隆项目

### 后端仓库
git clone [https://gitee.com/loveyy2023/glimmer-campus-mall-backend](https://gitee.com/loveyy2023/glimmer-campus-mall-backend)
### 前端仓库
git clone [https://gitee.com/loveyy2023/glimmer-campus-mall-frontend](https://gitee.com/loveyy2023/glimmer-campus-mall-frontend)
## 2. 数据库准备
确保本地 MySQL 服务正常运行，手动创建商城专属数据库：

CREATE DATABASE `onlineshop` CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

## 3. 后端配置与启动
修改 src/main/resources/application.properties，填入你的本地数据库及 Redis 信息（以下为项目预设值）：

### 服务端口
server.port=8080

### 数据库配置
spring.datasource.url=jdbc:mysql://localhost:3306/onlineshop?serverTimezone=Asia/Shanghai&useUnicode=true&characterEncoding=utf8
spring.datasource.username=root
spring.datasource.password=123456

### MyBatis-Plus 配置
mybatis-plus.mapper-locations=classpath:mapper/*.xml
mybatis-plus.type-aliases-package=top.dlsloveyy.backendtest.entity
mybatis-plus.configuration.map-underscore-to-camel-case=true

### Redis 配置 (必填项，请确保 Redis 已启动)
spring.data.redis.host=localhost
spring.data.redis.port=6379
spring.data.redis.database=0

### JWT 安全秘钥
jwt.secret=MySuperSecureJwtKeyMustBeAtLeast32Chars!

### 系统管理员初始化账号 (系统启动时自动创建)
admin.username=dlsloveyy
admin.password=123456

启动运行：在 IntelliJ IDEA 中运行主类 top.dlsloveyy.backendtest.BackendTestApplication。

## 4. 前端运行方法
   进入前端目录（如 front-main），依次执行以下命令：

### 1. 安装项目依赖包
npm install

### 2. 启动前端开发服务器
npm run dev

## 5. 访问地址
   商城首页 (买家端)：http://localhost:5173/

个人中心 (钱包/闲置管理)：http://localhost:5173/profile

后台管理系统入口：http://localhost:5173/admin/login

# 📁 静态资源说明
用户上传的头像、商品图片均保存在项目根目录下的 /uploads/ 文件夹中。
该目录在后端通过 WebMvcConfigurer 已配置静态资源映射，访问 URL 格式为：http://localhost:8080/uploads/文件名。

# 📝 许可证 (License)
本项目仅用于个人学习交流、毕业设计参考及简历作品展示，禁止用于任何形式的商业盈利活动。