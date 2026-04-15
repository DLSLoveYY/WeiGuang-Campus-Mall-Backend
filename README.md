# 🛠️ BBS论坛 后端项目

这是一个基于 **Spring Boot + JPA + MySQL** 的论坛后端系统，提供了用户注册登录、发帖、评论、点赞、关注、草稿保存、人工审核、后台管理等接口服务。

---

## 📦 项目结构说明

- 使用框架：Spring Boot 3.x
- 构建工具：Maven
- 数据库：MySQL 8.x
- ORM：Spring Data JPA
- 安全认证：基于自定义 JWT
- 文件上传目录：`uploads/`

---

## ⚙️ 环境要求

| 工具      | 版本建议    |
|-----------|-----------|
| JDK       | 17      |
| MySQL     | 8.0+      |
| Maven     | 3.6+      |
| IDE       | IntelliJ IDEA 推荐 |

---

## 🚀 启动方式

### 1. 克隆项目
后端项目
git clone https://gitee.com/loveyy2023/backend
前端项目
git clone https://gitee.com/loveyy2023/front

### 2. 数据库准备
确保你的本地 MySQL 服务运行中。

创建数据库：
CREATE DATABASE bbsTest CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
⚠️ 数据库名需与配置文件一致（bbsTest）。

### 3.配置文件说明
查看 src/main/resources/application.properties：在这里修改为你的数据库用户名和密码

# 服务端口
server.port=8080

# 数据源配置
spring.datasource.url=jdbc:mysql://localhost:3306/bbsTest?serverTimezone=Asia/Shanghai&useUnicode=true&characterEncoding=utf8
spring.datasource.username=root
spring.datasource.password=123456

# JPA 自动建表
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.database-platform=org.hibernate.dialect.MySQL8Dialect

# 静态资源目录（含上传文件）
spring.web.resources.static-locations=classpath:/static/,file:uploads/

# JWT 秘钥
jwt.secret=MySuperSecureJwtKeyMustBeAtLeast32Chars!

# 超级管理员账号（仅用于后台登录）
后台登录界面http://localhost:5173/admin/login
admin.username=dlsloveyy
admin.password=123456

### 4.启动项目
进入 IDEA，打开项目，运行主类：top.dlsloveyy.backendtest.BackendTestApplication
接着启动前端项目
最后访问地址http://localhost:5173/

### 5.前端项目启动方法
在前端目录下输入npm install
等待下载完成后输入npm run dev即可成功运行

### 6.上传文件存储说明
上传的图片与头像会保存到本地目录：根目录/uploads/
该目录在访问时通过配置已映射为静态资源路径。

License
本项目仅用于学习与交流，禁止用于商业用途。如需引用请注明出处。