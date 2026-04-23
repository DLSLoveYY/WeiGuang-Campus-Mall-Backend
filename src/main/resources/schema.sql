-- =====================================================
-- 微光校园二手商城 数据库初始化脚本
-- =====================================================

-- 1. 用户表 (核心)
CREATE TABLE IF NOT EXISTS user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(64) NOT NULL UNIQUE,
  password VARCHAR(255) NOT NULL,
  email VARCHAR(128) NOT NULL,
  points INT NOT NULL DEFAULT 0,
  is_admin BOOLEAN NOT NULL DEFAULT FALSE,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  avatar VARCHAR(255),
  signature VARCHAR(255),
  gender VARCHAR(10),
  age INT,
  contact_phone VARCHAR(20),
  wechat_id VARCHAR(50),
  dorm_building VARCHAR(100),
  credit_score INT NOT NULL DEFAULT 100,
  balance DECIMAL(10,2) NOT NULL DEFAULT 0,
  frozen_balance DECIMAL(10,2) NOT NULL DEFAULT 0,
  create_time DATETIME,
  update_time DATETIME,
  INDEX idx_user_username (username),
  INDEX idx_user_is_admin (is_admin)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2. 商品表 (核心)
CREATE TABLE IF NOT EXISTS goods (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  seller_id BIGINT NOT NULL,
  title VARCHAR(100) NOT NULL,
  description TEXT NOT NULL,
  price DECIMAL(10,2) NOT NULL,
  original_price DECIMAL(10,2),
  category VARCHAR(50) NOT NULL,
  condition_level VARCHAR(20),
  images TEXT,
  stock INT NOT NULL DEFAULT 1,
  status INT NOT NULL DEFAULT 0,
  delivery_method VARCHAR(50),
  delivery_methods VARCHAR(100),
  trade_address VARCHAR(255),
  view_count INT NOT NULL DEFAULT 0,
  create_time DATETIME NOT NULL,
  update_time DATETIME NOT NULL,
  favorites_count INT NOT NULL DEFAULT 0,
  comment_count INT NOT NULL DEFAULT 0,
  is_featured BOOLEAN NOT NULL DEFAULT FALSE,
  is_notice BOOLEAN NOT NULL DEFAULT FALSE,
  INDEX idx_goods_seller_id (seller_id),
  INDEX idx_goods_category (category),
  INDEX idx_goods_status (status),
  INDEX idx_goods_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3. 订单表 (核心 - 这是缺失的关键表！)
CREATE TABLE IF NOT EXISTS trade_order (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_no VARCHAR(64),
  payment_no VARCHAR(128),
  buyer_id BIGINT NOT NULL,
  seller_id BIGINT NOT NULL,
  goods_id BIGINT NOT NULL,
  order_price DECIMAL(10,2) NOT NULL,
  platform_fee DECIMAL(10,2),
  seller_income DECIMAL(10,2),
  delivery_method VARCHAR(50),
  delivery_address VARCHAR(255),
  meetup_address VARCHAR(255),
  meetup_phone VARCHAR(32),
  seller_confirm_phone_suffix VARCHAR(4),
  handoff_confirm_time DATETIME,
  cancel_reason_code VARCHAR(64),
  cancel_reason_desc VARCHAR(500),
  cancel_source VARCHAR(32),
  refund_type INT,
  refund_stage INT DEFAULT 0,
  refund_requested_amount DECIMAL(10,2),
  refund_approved_amount DECIMAL(10,2),
  refund_reason VARCHAR(500),
  refund_reason_code VARCHAR(64),
  refund_apply_packet TEXT,
  return_tracking_no VARCHAR(128),
  carrier_code VARCHAR(64),
  tracking_no VARCHAR(128),
  freight_fee DECIMAL(10,2),
  address_id BIGINT,
  status INT NOT NULL DEFAULT 0,
  create_time DATETIME NOT NULL,
  pay_time DATETIME,
  delivery_time DATETIME,
  finish_time DATETIME,
  update_time DATETIME,
  cancel_time DATETIME,
  INDEX idx_trade_order_buyer_id (buyer_id),
  INDEX idx_trade_order_seller_id (seller_id),
  INDEX idx_trade_order_status (status),
  INDEX idx_trade_order_create_time (create_time),
  INDEX idx_trade_order_order_no (order_no),
  UNIQUE KEY uk_trade_order_no (order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 兼容已存在数据库的增量字段（兼容 MySQL 5.7/8.0）
SET @ddl := (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 'trade_order' AND column_name = 'refund_reason_code'
    ),
    'SELECT 1',
    'ALTER TABLE trade_order ADD COLUMN refund_reason_code VARCHAR(64)'
  )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 'trade_order' AND column_name = 'refund_apply_packet'
    ),
    'SELECT 1',
    'ALTER TABLE trade_order ADD COLUMN refund_apply_packet TEXT'
  )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 'goods' AND column_name = 'delivery_methods'
    ),
    'SELECT 1',
    'ALTER TABLE goods ADD COLUMN delivery_methods VARCHAR(100)'
  )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 'trade_order' AND column_name = 'meetup_address'
    ),
    'SELECT 1',
    'ALTER TABLE trade_order ADD COLUMN meetup_address VARCHAR(255)'
  )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 'trade_order' AND column_name = 'meetup_phone'
    ),
    'SELECT 1',
    'ALTER TABLE trade_order ADD COLUMN meetup_phone VARCHAR(32)'
  )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 'trade_order' AND column_name = 'seller_confirm_phone_suffix'
    ),
    'SELECT 1',
    'ALTER TABLE trade_order ADD COLUMN seller_confirm_phone_suffix VARCHAR(4)'
  )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 'trade_order' AND column_name = 'handoff_confirm_time'
    ),
    'SELECT 1',
    'ALTER TABLE trade_order ADD COLUMN handoff_confirm_time DATETIME'
  )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 交易方式数据兼容归一化
UPDATE goods
SET delivery_method = CASE
  WHEN delivery_method = '自提' THEN '校园面交'
  WHEN delivery_method IN ('校园面交', '邮寄') THEN delivery_method
  ELSE '校园面交'
END
WHERE delivery_method IS NOT NULL;

UPDATE goods
SET delivery_methods = CASE
  WHEN delivery_methods IS NOT NULL AND delivery_methods <> '' THEN delivery_methods
  WHEN delivery_method = '邮寄' THEN '邮寄'
  ELSE '校园面交'
END;

UPDATE goods
SET delivery_methods = REPLACE(delivery_methods, '自提', '校园面交')
WHERE delivery_methods LIKE '%自提%';

UPDATE trade_order
SET delivery_method = '校园面交'
WHERE delivery_method = '自提';

-- 4. 商品收藏表
CREATE TABLE IF NOT EXISTS goods_favorite (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  goods_id BIGINT NOT NULL,
  create_time DATETIME NOT NULL,
  INDEX idx_goods_favorite_user_id (user_id),
  INDEX idx_goods_favorite_goods_id (goods_id),
  UNIQUE KEY uk_goods_favorite_user_goods (user_id, goods_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 5. 用户关注表
CREATE TABLE IF NOT EXISTS user_follow (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  follower_id BIGINT NOT NULL,
  followee_id BIGINT NOT NULL,
  create_time DATETIME NOT NULL,
  INDEX idx_user_follow_follower_id (follower_id),
  INDEX idx_user_follow_followee_id (followee_id),
  UNIQUE KEY uk_user_follow (follower_id, followee_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 6. 私聊表
CREATE TABLE IF NOT EXISTS chat_message (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  sender_id BIGINT NOT NULL,
  receiver_id BIGINT NOT NULL,
  content TEXT NOT NULL,
  is_read BOOLEAN NOT NULL DEFAULT FALSE,
  create_time DATETIME NOT NULL,
  INDEX idx_chat_message_sender_id (sender_id),
  INDEX idx_chat_message_receiver_id (receiver_id),
  INDEX idx_chat_message_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 7. 商品评论表
CREATE TABLE IF NOT EXISTS comment (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  goods_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  parent_id BIGINT,
  content TEXT NOT NULL,
  create_time DATETIME NOT NULL,
  INDEX idx_comment_goods_id (goods_id),
  INDEX idx_comment_user_id (user_id),
  INDEX idx_comment_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 8. 商品草稿表
CREATE TABLE IF NOT EXISTS goods_draft (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  seller_id BIGINT NOT NULL,
  title VARCHAR(100),
  content TEXT,
  price DECIMAL(10,2),
  original_price DECIMAL(10,2),
  category VARCHAR(50),
  delivery_type VARCHAR(50),
  cover_img VARCHAR(255),
  create_time DATETIME NOT NULL,
  INDEX idx_goods_draft_seller_id (seller_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 9. 交易评价表
CREATE TABLE IF NOT EXISTS trade_review (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id BIGINT NOT NULL,
  reviewer_id BIGINT NOT NULL,
  reviewee_id BIGINT NOT NULL,
  score INT NOT NULL,
  content TEXT,
  role VARCHAR(20),
  create_time DATETIME NOT NULL,
  INDEX idx_trade_review_order_id (order_id),
  INDEX idx_trade_review_reviewer_id (reviewer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 10. 系统公告表
CREATE TABLE IF NOT EXISTS sys_notice (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  title VARCHAR(255) NOT NULL,
  content TEXT NOT NULL,
  admin_id BIGINT,
  author VARCHAR(100),
  create_time DATETIME NOT NULL,
  INDEX idx_sys_notice_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 11. 操作审计日志表 (保留)
CREATE TABLE IF NOT EXISTS operation_audit_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  actor_id BIGINT NOT NULL,
  actor_role VARCHAR(32) NOT NULL,
  action VARCHAR(64) NOT NULL,
  resource_type VARCHAR(64) NOT NULL,
  resource_id VARCHAR(64) NOT NULL,
  detail VARCHAR(1000),
  create_time DATETIME NOT NULL,
  INDEX idx_operation_audit_log_actor_id (actor_id),
  INDEX idx_operation_audit_log_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 12. 纠纷表 (保留)
CREATE TABLE IF NOT EXISTS trade_dispute (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id BIGINT NOT NULL,
  buyer_id BIGINT NOT NULL,
  seller_id BIGINT NOT NULL,
  status INT NOT NULL DEFAULT 0,
  reason VARCHAR(255),
  buyer_evidence TEXT,
  seller_evidence TEXT,
  resolution VARCHAR(500),
  processor_id BIGINT,
  deadline_time DATETIME,
  create_time DATETIME NOT NULL,
  update_time DATETIME,
  close_time DATETIME,
  INDEX idx_trade_dispute_order_id (order_id),
  INDEX idx_trade_dispute_buyer_id (buyer_id),
  INDEX idx_trade_dispute_seller_id (seller_id),
  INDEX idx_trade_dispute_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 13. 客服工单表 (保留)
CREATE TABLE IF NOT EXISTS customer_service_case (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  case_no VARCHAR(64) NOT NULL,
  order_id BIGINT NOT NULL,
  dispute_id BIGINT NOT NULL,
  buyer_id BIGINT NOT NULL,
  seller_id BIGINT NOT NULL,
  status INT NOT NULL DEFAULT 0,
  priority INT NOT NULL DEFAULT 2,
  source VARCHAR(64),
  assigned_admin_id BIGINT,
  latest_action VARCHAR(255),
  sla_deadline_time DATETIME,
  create_time DATETIME NOT NULL,
  update_time DATETIME,
  close_time DATETIME,
  UNIQUE KEY uk_customer_service_case_no (case_no),
  INDEX idx_customer_service_case_dispute (dispute_id),
  INDEX idx_customer_service_case_order (order_id),
  INDEX idx_customer_service_case_status (status),
  INDEX idx_customer_service_case_assigned (assigned_admin_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 14. 工单操作记录表 (保留)
CREATE TABLE IF NOT EXISTS customer_service_case_action (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  case_id BIGINT NOT NULL,
  actor_id BIGINT NOT NULL,
  actor_role VARCHAR(32) NOT NULL,
  action_type VARCHAR(64) NOT NULL,
  content TEXT,
  attachments TEXT,
  create_time DATETIME NOT NULL,
  INDEX idx_customer_service_case_action_case (case_id),
  INDEX idx_customer_service_case_action_actor (actor_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 15. 账户流水表 (保留)
CREATE TABLE IF NOT EXISTS account_ledger (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  biz_type VARCHAR(64) NOT NULL,
  biz_no VARCHAR(64) NOT NULL,
  change_type VARCHAR(32) NOT NULL,
  amount DECIMAL(10,2) NOT NULL,
  balance_before DECIMAL(10,2) NOT NULL,
  balance_after DECIMAL(10,2) NOT NULL,
  frozen_before DECIMAL(10,2) NOT NULL,
  frozen_after DECIMAL(10,2) NOT NULL,
  idempotency_key VARCHAR(128) NOT NULL,
  remark VARCHAR(500),
  create_time DATETIME NOT NULL,
  UNIQUE KEY uk_account_ledger_idempotency (idempotency_key),
  INDEX idx_account_ledger_user_time (user_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 16. 收货地址表 (保留)
CREATE TABLE IF NOT EXISTS user_address (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  receiver_name VARCHAR(64) NOT NULL,
  receiver_phone VARCHAR(32) NOT NULL,
  province VARCHAR(64),
  city VARCHAR(64),
  district VARCHAR(64),
  detail VARCHAR(255) NOT NULL,
  is_default TINYINT NOT NULL DEFAULT 0,
  create_time DATETIME NOT NULL,
  update_time DATETIME,
  INDEX idx_user_address_user_id (user_id),
  INDEX idx_user_address_default (user_id, is_default)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 17. 运费模板表 (保留)
CREATE TABLE IF NOT EXISTS freight_template (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  seller_id BIGINT NOT NULL,
  template_name VARCHAR(64) NOT NULL,
  base_fee DECIMAL(10,2) NOT NULL DEFAULT 0,
  free_shipping_threshold DECIMAL(10,2),
  extra_fee_per_item DECIMAL(10,2) NOT NULL DEFAULT 0,
  enabled TINYINT NOT NULL DEFAULT 1,
  create_time DATETIME NOT NULL,
  update_time DATETIME,
  INDEX idx_freight_template_seller_enabled (seller_id, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 18. 发货信息表 (保留)
CREATE TABLE IF NOT EXISTS trade_shipment (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id BIGINT NOT NULL,
  seller_id BIGINT NOT NULL,
  buyer_id BIGINT NOT NULL,
  carrier_code VARCHAR(64) NOT NULL,
  tracking_no VARCHAR(128) NOT NULL,
  status INT NOT NULL DEFAULT 0,
  shipped_time DATETIME NOT NULL,
  delivered_time DATETIME,
  create_time DATETIME NOT NULL,
  update_time DATETIME,
  UNIQUE KEY uk_trade_shipment_order_id (order_id),
  INDEX idx_trade_shipment_tracking (carrier_code, tracking_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 19. 物流跟踪表 (保留)
CREATE TABLE IF NOT EXISTS trade_logistics_trace (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  shipment_id BIGINT NOT NULL,
  trace_desc VARCHAR(255) NOT NULL,
  trace_location VARCHAR(255),
  trace_time DATETIME NOT NULL,
  create_time DATETIME NOT NULL,
  INDEX idx_trade_logistics_trace_shipment_time (shipment_id, trace_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;