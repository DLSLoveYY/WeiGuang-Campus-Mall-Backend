CREATE TABLE IF NOT EXISTS operation_audit_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  actor_id BIGINT NOT NULL,
  actor_role VARCHAR(32) NOT NULL,
  action VARCHAR(64) NOT NULL,
  resource_type VARCHAR(64) NOT NULL,
  resource_id VARCHAR(64) NOT NULL,
  detail VARCHAR(1000),
  create_time DATETIME NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

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
