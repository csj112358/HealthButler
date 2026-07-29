-- 终身体检报告管家 数据库设计

-- 创建数据库
CREATE DATABASE IF NOT EXISTS health_butler DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE health_butler;

-- 1. 用户表
CREATE TABLE IF NOT EXISTS `user` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '用户ID',
    `openid` VARCHAR(64) UNIQUE COMMENT '微信小程序openid',
    `username` VARCHAR(50) NOT NULL COMMENT '用户名',
    `phone` VARCHAR(20) COMMENT '手机号',
    `gender` TINYINT DEFAULT 0 COMMENT '性别：0-未知，1-男，2-女',
    `birth_date` DATE COMMENT '出生日期',
    `avatar_url` VARCHAR(500) COMMENT '头像URL',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 2. 指标字典表（解决不同医院指标名称差异问题）
CREATE TABLE IF NOT EXISTS `item_dictionary` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '字典ID',
    `standard_name` VARCHAR(100) NOT NULL COMMENT '标准指标名称',
    `category` VARCHAR(50) NOT NULL COMMENT '指标分类（如：肝功能、肾功能、血脂、血糖等）',
    `aliases` JSON COMMENT '别名列表（JSON数组，存储各医院可能使用的不同名称）',
    `unit` VARCHAR(20) NOT NULL COMMENT '标准单位',
    `reference_range` VARCHAR(100) COMMENT '参考范围（通用）',
    `description` TEXT COMMENT '指标说明',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='指标字典表';

-- 3. 体检报告表
CREATE TABLE IF NOT EXISTS `health_report` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '报告ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `report_date` DATE NOT NULL COMMENT '体检日期',
    `hospital` VARCHAR(100) COMMENT '体检医院',
    `report_type` VARCHAR(50) COMMENT '报告类型（如：年度体检、入职体检等）',
    `image_url` VARCHAR(500) COMMENT '原始报告图片URL',
    `ai_raw_response` TEXT COMMENT 'AI原始返回结果（用于调试）',
    `status` TINYINT DEFAULT 0 COMMENT '状态：0-待审核，1-已确认，2-已忽略',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    INDEX `idx_user_date` (`user_id`, `report_date`),
    FOREIGN KEY (`user_id`) REFERENCES `user`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='体检报告表';

-- 4. 检测指标明细表
CREATE TABLE IF NOT EXISTS `health_item` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '指标ID',
    `report_id` BIGINT NOT NULL COMMENT '报告ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `dictionary_id` BIGINT COMMENT '关联的字典ID（标准化后的指标）',
    `original_name` VARCHAR(100) NOT NULL COMMENT '原始指标名称（医院报告中的名称）',
    `standard_name` VARCHAR(100) COMMENT '标准化后的指标名称',
    `category` VARCHAR(50) COMMENT '指标分类',
    `value` DECIMAL(10,4) COMMENT '检测值（数值型）',
    `text_value` VARCHAR(200) COMMENT '检测值（文本型，用于非数值结果）',
    `unit` VARCHAR(20) COMMENT '单位',
    `reference_range` VARCHAR(100) COMMENT '参考范围',
    `status` VARCHAR(10) COMMENT '状态：normal-正常，high-偏高，low-偏低',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX `idx_user_category` (`user_id`, `category`),
    INDEX `idx_user_standard_name` (`user_id`, `standard_name`),
    INDEX `idx_report` (`report_id`),
    FOREIGN KEY (`report_id`) REFERENCES `health_report`(`id`),
    FOREIGN KEY (`user_id`) REFERENCES `user`(`id`),
    FOREIGN KEY (`dictionary_id`) REFERENCES `item_dictionary`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='检测指标明细表';

-- 5. AI诊断报告表
CREATE TABLE IF NOT EXISTS `ai_diagnosis` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '诊断ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `diagnosis_date` DATETIME NOT NULL COMMENT '诊断时间',
    `report_ids` JSON COMMENT '基于哪些报告生成的诊断（JSON数组）',
    `diagnosis_content` TEXT NOT NULL COMMENT 'AI诊断内容（Markdown格式）',
    `model_name` VARCHAR(50) COMMENT '使用的AI模型名称',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX `idx_user_date` (`user_id`, `diagnosis_date`),
    FOREIGN KEY (`user_id`) REFERENCES `user`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI诊断报告表';

-- 插入一些常用的指标字典数据
INSERT INTO `item_dictionary` (`standard_name`, `category`, `aliases`, `unit`, `reference_range`, `description`) VALUES
-- 肝功能
('谷丙转氨酶', '肝功能', '[“ALT”, “GPT”, “丙氨酸氨基转移酶”]', 'U/L', '0-40', '反映肝细胞损伤的敏感指标'),
('谷草转氨酶', '肝功能', '[“AST”, “GOT”, “天门冬氨酸氨基转移酶”]', 'U/L', '0-40', '反映肝细胞损伤的指标'),
('总胆红素', '肝功能', '[“TBIL”, “T-BIL”]', 'μmol/L', '3.4-17.1', '反映肝脏代谢和排泄功能'),

-- 肾功能
('肌酐', '肾功能', '[“Cr”, “CREA”, “血肌酐”]', 'μmol/L', '44-133', '反映肾小球滤过功能的重要指标'),
('尿素氮', '肾功能', '[“BUN”, “尿素”]', 'mmol/L', '2.86-7.14', '反映肾功能的指标'),
('尿酸', '肾功能', '[“UA”]', 'μmol/L', '208-428', '嘌呤代谢产物，与痛风相关'),

-- 血脂
('总胆固醇', '血脂', '[“TC”, “CHOL”, “胆固醇”]', 'mmol/L', '2.8-5.17', '血脂代谢的重要指标'),
('甘油三酯', '血脂', '[“TG”, “三酰甘油”]', 'mmol/L', '0.56-1.7', '血脂代谢指标'),
('高密度脂蛋白', '血脂', '[“HDL”, “HDL-C”, “高密度脂蛋白胆固醇”]', 'mmol/L', '1.03-1.55', '”好”胆固醇，有抗动脉粥样硬化作用'),
('低密度脂蛋白', '血脂', '[“LDL”, “LDL-C”, “低密度脂蛋白胆固醇”]', 'mmol/L', '<3.37', '”坏”胆固醇，与动脉粥样硬化相关'),

-- 血糖
('空腹血糖', '血糖', '[“GLU”, “FBG”, “葡萄糖”]', 'mmol/L', '3.9-6.1', '糖尿病筛查和诊断的重要指标'),
('糖化血红蛋白', '血糖', '[“HbA1c”, “HBA1C”]', '%', '4-6', '反映近2-3个月平均血糖水平'),

-- 血常规
('白细胞', '血常规', '[“WBC”, “白细胞计数”]', '×10^9/L', '4-10', '反映机体免疫状态'),
('红细胞', '血常规', '[“RBC”, “红细胞计数”]', '×10^12/L', '4-5.5', '反映贫血或红细胞增多'),
('血红蛋白', '血常规', '[“Hb”, “HGB”, “血色素”]', 'g/L', '120-160', '反映贫血程度'),
('血小板', '血常规', '[“PLT”, “血小板计数”]', '×10^9/L', '100-300', '反映凝血功能'),

-- 血压
('收缩压', '血压', '[“SBP”, “高压”, “上压”]', 'mmHg', '<140', '心脏收缩时动脉血压最高值'),
('舒张压', '血压', '[“DBP”, “低压”, “下压”]', 'mmHg', '<90', '心脏舒张时动脉血压最低值'),

-- 甲状腺
('促甲状腺激素', '甲状腺', '[“TSH”]', 'mIU/L', '0.35-5.5', '甲状腺功能筛查的首选指标'),
('游离T3', '甲状腺', '[“FT3”, “游离三碘甲状腺原氨酸”]', 'pmol/L', '3.1-6.8', '甲状腺功能指标'),
('游离T4', '甲状腺', '[“FT4”, “游离甲状腺素”]', 'pmol/L', '12-22', '甲状腺功能指标');