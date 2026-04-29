-- 통합 시퀀스 (JPA 기본값 대응)
CREATE SEQUENCE hibernate_sequence START WITH 1000 INCREMENT BY 1;

-- 개별 시퀀스들
CREATE SEQUENCE favorite_stock_seq START WITH 1 INCREMENT BY 1;

CREATE SEQUENCE order_seq START WITH 1000 INCREMENT BY 1;

CREATE SEQUENCE user_seq START WITH 1000 INCREMENT BY 1;

-- 1. 회원 테이블
CREATE TABLE users (
    user_id NUMBER(19) PRIMARY KEY,
    email VARCHAR2(255) NOT NULL UNIQUE,
    password_hash VARCHAR2(255),
    nickname VARCHAR2(50) NOT NULL,
    profile_image_url VARCHAR2(500),
    role VARCHAR2(20) DEFAULT 'USER' NOT NULL,
    status VARCHAR2(20) DEFAULT 'ACTIVE' NOT NULL,
    marketing_consent NUMBER(1) DEFAULT 0 NOT NULL, -- Boolean 대용
    email_verified NUMBER(1) DEFAULT 0 NOT NULL, -- Boolean 대용
    last_login_at TIMESTAMP,
    suspended_at TIMESTAMP,
    suspended_until TIMESTAMP,
    suspension_reason VARCHAR2(500),
    withdrawn_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted_at TIMESTAMP
);

-- 2. 소셜 계정 연동
CREATE TABLE oauth_accounts (
    oauth_account_id NUMBER(19) PRIMARY KEY,
    user_id NUMBER(19) NOT NULL,
    provider VARCHAR2(30) NOT NULL, -- Google, Kakao 등
    provider_user_id VARCHAR2(255) NOT NULL,
    provider_email VARCHAR2(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_oauth_user FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT uk_provider_id UNIQUE (provider, provider_user_id)
);

-- 3. 이메일 인증 기록
CREATE TABLE email_verifications (
    email_verification_id NUMBER(19) PRIMARY KEY,
    email VARCHAR2(255) NOT NULL,
    verification_type VARCHAR2(30) NOT NULL,
    verification_code VARCHAR2(20) NOT NULL,
    is_verified NUMBER(1) DEFAULT 0 NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    verified_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- 4. 로그인 이력
CREATE TABLE login_histories (
    login_history_id NUMBER(19) PRIMARY KEY,
    user_id NUMBER(19),
    login_identifier VARCHAR2(255) NOT NULL,
    ip_address VARCHAR2(64),
    user_agent VARCHAR2(500),
    login_result VARCHAR2(20) NOT NULL,
    failure_reason VARCHAR2(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_login_hist_user FOREIGN KEY (user_id) REFERENCES users (user_id)
);

-- 5. 약관 마스터
CREATE TABLE terms (
    term_id NUMBER(19) PRIMARY KEY,
    term_code VARCHAR2(50) NOT NULL UNIQUE,
    title VARCHAR2(200) NOT NULL,
    is_required NUMBER(1) NOT NULL,
    version VARCHAR2(30) NOT NULL,
    is_active NUMBER(1) DEFAULT 1 NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- 6. 사용자 약관 동의 이력
CREATE TABLE user_term_agreements (
    agreement_id NUMBER(19) PRIMARY KEY,
    user_id NUMBER(19) NOT NULL,
    term_id NUMBER(19) NOT NULL,
    agreed NUMBER(1) NOT NULL,
    agreed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_agreement_user FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT fk_agreement_term FOREIGN KEY (term_id) REFERENCES terms (term_id)
);

-- 7. 대회 정보
CREATE TABLE competitions (
    competition_id NUMBER(19) PRIMARY KEY,
    title VARCHAR2(200) NOT NULL,
    description CLOB, -- 긴 텍스트 대비
    start_at TIMESTAMP NOT NULL,
    end_at TIMESTAMP NOT NULL,
    initial_seed_money NUMBER(18, 2) NOT NULL,
    max_participants NUMBER(10),
    status VARCHAR2(20) DEFAULT 'SCHEDULED' NOT NULL,
    created_by_admin_id NUMBER(19) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_comp_admin FOREIGN KEY (created_by_admin_id) REFERENCES users (user_id)
);

-- 8. 주식 종목 마스터
CREATE TABLE stock (
    stock_id NUMBER(19) PRIMARY KEY,
    stock_code VARCHAR2(20) NOT NULL UNIQUE,
    stock_name VARCHAR2(100) NOT NULL,
    market_type VARCHAR2(20) NOT NULL,
    is_active NUMBER(1) DEFAULT 1 NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- 9. 계좌 테이블
CREATE TABLE accounts (
    account_id NUMBER(19) PRIMARY KEY,
    user_id NUMBER(19) NOT NULL,
    competition_id NUMBER(19),
    account_type VARCHAR2(20) NOT NULL, -- GENERAL, COMPETITION
    account_name VARCHAR2(100) NOT NULL,
    cash_balance NUMBER(18, 2) DEFAULT 0 NOT NULL,
    is_active NUMBER(1) DEFAULT 1 NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_account_user FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT fk_account_comp FOREIGN KEY (competition_id) REFERENCES competitions (competition_id)
);

-- 10. 대회 참가자 정보 (계좌 생성 후)
CREATE TABLE competition_participants (
    competition_participant_id NUMBER(19) PRIMARY KEY,
    competition_id NUMBER(19) NOT NULL,
    user_id NUMBER(19) NOT NULL,
    account_id NUMBER(19) NOT NULL,
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    participation_status VARCHAR2(20) DEFAULT 'JOINED' NOT NULL,
    final_return_rate NUMBER(10, 4),
    final_rank NUMBER(10),
    CONSTRAINT fk_part_comp FOREIGN KEY (competition_id) REFERENCES competitions (competition_id),
    CONSTRAINT fk_part_user FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT fk_part_account FOREIGN KEY (account_id) REFERENCES accounts (account_id)
);

-- 11. 주문
CREATE TABLE orders (
    order_id NUMBER(19) PRIMARY KEY,
    account_id NUMBER(19) NOT NULL,
    stock_id NUMBER(19) NOT NULL,
    order_side VARCHAR2(10) NOT NULL, -- BUY, SELL
    order_type VARCHAR2(10) NOT NULL, -- MARKET, LIMIT
    quantity NUMBER(19) NOT NULL,
    price NUMBER(18, 2),
    remaining_quantity NUMBER(19) NOT NULL,
    order_status VARCHAR2(20) DEFAULT 'PENDING' NOT NULL,
    queued_at TIMESTAMP,
    ordered_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    canceled_at TIMESTAMP,
    CONSTRAINT fk_order_account FOREIGN KEY (account_id) REFERENCES accounts (account_id),
    CONSTRAINT fk_order_stock FOREIGN KEY (stock_id) REFERENCES stock (stock_id)
);

-- 12. 체결 내역
CREATE TABLE order_executions (
    execution_id NUMBER(19) PRIMARY KEY,
    order_id NUMBER(19) NOT NULL,
    executed_quantity NUMBER(19) NOT NULL,
    executed_price NUMBER(18, 2) NOT NULL,
    executed_amount NUMBER(18, 2) NOT NULL,
    executed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_exec_order FOREIGN KEY (order_id) REFERENCES orders (order_id)
);

-- 13. 보유 주식
CREATE TABLE holdings (
    holding_id NUMBER(19) PRIMARY KEY,
    account_id NUMBER(19) NOT NULL,
    stock_id NUMBER(19) NOT NULL,
    quantity NUMBER(19) NOT NULL,
    average_buy_price NUMBER(18, 2) NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_holding_account FOREIGN KEY (account_id) REFERENCES accounts (account_id),
    CONSTRAINT fk_holding_stock FOREIGN KEY (stock_id) REFERENCES stock (stock_id),
    CONSTRAINT uk_account_stock UNIQUE (account_id, stock_id)
);

-- 14. 현금 원장
CREATE TABLE account_cash_ledgers (
    cash_ledger_id NUMBER(19) PRIMARY KEY,
    account_id NUMBER(19) NOT NULL,
    ledger_type VARCHAR2(30) NOT NULL,
    amount NUMBER(18, 2) NOT NULL,
    balance_after NUMBER(18, 2) NOT NULL,
    reference_type VARCHAR2(30),
    reference_id NUMBER(19),
    memo VARCHAR2(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_ledger_account FOREIGN KEY (account_id) REFERENCES accounts (account_id)
);

-- 15. 게시판 마스터
CREATE TABLE boards (
    board_id NUMBER(19) PRIMARY KEY,
    board_name VARCHAR2(100) NOT NULL,
    board_code VARCHAR2(50) NOT NULL UNIQUE,
    description VARCHAR2(500),
    is_active NUMBER(1) DEFAULT 1 NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- 16. 게시글
CREATE TABLE posts (
    post_id NUMBER(19) PRIMARY KEY,
    board_id NUMBER(19) NOT NULL,
    user_id NUMBER(19) NOT NULL,
    title VARCHAR2(200) NOT NULL,
    content CLOB NOT NULL,
    view_count NUMBER(10) DEFAULT 0 NOT NULL,
    like_count NUMBER(10) DEFAULT 0 NOT NULL,
    comment_count NUMBER(10) DEFAULT 0 NOT NULL,
    report_count NUMBER(10) DEFAULT 0 NOT NULL,
    status VARCHAR2(20) DEFAULT 'NORMAL' NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted_at TIMESTAMP,
    CONSTRAINT fk_post_board FOREIGN KEY (board_id) REFERENCES boards (board_id),
    CONSTRAINT fk_post_user FOREIGN KEY (user_id) REFERENCES users (user_id)
);

-- 17. 댓글
CREATE TABLE comments (
    comment_id NUMBER(19) PRIMARY KEY,
    post_id NUMBER(19) NOT NULL,
    user_id NUMBER(19) NOT NULL,
    parent_comment_id NUMBER(19),
    content VARCHAR2(1000) NOT NULL,
    status VARCHAR2(20) DEFAULT 'NORMAL' NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted_at TIMESTAMP,
    CONSTRAINT fk_comment_post FOREIGN KEY (post_id) REFERENCES posts (post_id),
    CONSTRAINT fk_comment_user FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT fk_comment_parent FOREIGN KEY (parent_comment_id) REFERENCES comments (comment_id)
);

-- 18. 관심종목
CREATE TABLE favorite_stocks (
    id NUMBER(19) PRIMARY KEY,
    user_id NUMBER(19) NOT NULL,
    stock_symbol VARCHAR2(20) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    alert_level VARCHAR2(20) DEFAULT 'STRONG_BUY',
    CONSTRAINT fk_fav_user FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT uk_user_stock UNIQUE (user_id, stock_symbol)
);

-- 19. 목표가 알림
CREATE TABLE price_alerts (
    price_alert_id NUMBER(19) PRIMARY KEY,
    user_id NUMBER(19) NOT NULL,
    stock_id NUMBER(19) NOT NULL,
    target_price NUMBER(18, 2) NOT NULL,
    direction VARCHAR2(10) NOT NULL, -- ABOVE, BELOW
    is_active NUMBER(1) DEFAULT 1 NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_alert_user FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT fk_alert_stock FOREIGN KEY (stock_id) REFERENCES stock (stock_id)
);

-- 20. 시스템 알림
CREATE TABLE notifications (
    notification_id NUMBER(19) PRIMARY KEY,
    user_id NUMBER(19) NOT NULL,
    notification_type VARCHAR2(30) NOT NULL,
    title VARCHAR2(200) NOT NULL,
    message VARCHAR2(500) NOT NULL,
    related_type VARCHAR2(30),
    related_id NUMBER(19),
    is_read NUMBER(1) DEFAULT 0 NOT NULL,
    read_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_noti_user FOREIGN KEY (user_id) REFERENCES users (user_id)
);

-- 21. 1:1 문의 테이블
CREATE TABLE inquiries (
    inquiry_id NUMBER(19) PRIMARY KEY,
    user_id NUMBER(19) NOT NULL,
    category VARCHAR2(50) NOT NULL,
    title VARCHAR2(2000) NOT NULL,
    content CLOB NOT NULL,
    inquiry_status VARCHAR2(20) DEFAULT 'OPEN' NOT NULL, -- OPEN, ANSWERED, CLOSED
    is_read_by_user NUMBER(1) DEFAULT 1 NOT NULL, -- Boolean 대용 (사용자 읽음 여부)
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_inquiry_user FOREIGN KEY (user_id) REFERENCES users (user_id)
);

-- 22. 문의 답변 테이블
CREATE TABLE inquiry_answers (
    inquiry_answer_id NUMBER(19) PRIMARY KEY,
    inquiry_id NUMBER(19) NOT NULL,
    admin_user_id NUMBER(19) NOT NULL,
    content CLOB NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_answer_inquiry FOREIGN KEY (inquiry_id) REFERENCES inquiries (inquiry_id),
    CONSTRAINT fk_answer_admin FOREIGN KEY (admin_user_id) REFERENCES users (user_id)
);

-- 23. 공지사항 테이블
CREATE TABLE notices (
    notice_id NUMBER(19) PRIMARY KEY,
    admin_user_id NUMBER(19) NOT NULL,
    title VARCHAR2(200) NOT NULL,
    content CLOB NOT NULL,
    is_pinned NUMBER(1) DEFAULT 0 NOT NULL,
    view_count NUMBER(10) DEFAULT 0 NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted_at TIMESTAMP,
    CONSTRAINT fk_notice_admin FOREIGN KEY (admin_user_id) REFERENCES users (user_id)
);

-- 24. 관리자 활동 로그
CREATE TABLE admin_action_logs (
    admin_action_log_id NUMBER(19) PRIMARY KEY,
    admin_user_id NUMBER(19) NOT NULL,
    action_type VARCHAR2(50) NOT NULL,
    target_type VARCHAR2(50) NOT NULL,
    target_id NUMBER(19),
    action_detail VARCHAR2(500),
    ip_address VARCHAR2(64),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_log_admin FOREIGN KEY (admin_user_id) REFERENCES users (user_id)
);

-- 25. 게시글 신고 테이블
CREATE TABLE post_reports (
    report_id NUMBER(19) PRIMARY KEY,
    post_id NUMBER(19) NOT NULL,
    reporter_user_id NUMBER(19) NOT NULL,
    reason VARCHAR2(255) NOT NULL,
    detail VARCHAR2(500),
    report_status VARCHAR2(20) DEFAULT 'PENDING' NOT NULL, -- PENDING, RESOLVED
    handled_admin_id NUMBER(19),
    handled_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_report_post FOREIGN KEY (post_id) REFERENCES posts (post_id),
    CONSTRAINT fk_report_user FOREIGN KEY (reporter_user_id) REFERENCES users (user_id),
    CONSTRAINT fk_report_admin FOREIGN KEY (handled_admin_id) REFERENCES users (user_id)
);

-- 26. 계좌 자산 스냅샷 (일별 정산용)
CREATE TABLE asset_snapshots (
    asset_snapshot_id NUMBER(19) PRIMARY KEY,
    account_id NUMBER(19) NOT NULL,
    snapshot_date DATE NOT NULL,
    cash_balance NUMBER(18, 2) NOT NULL,
    stock_value NUMBER(18, 2) NOT NULL,
    total_asset_value NUMBER(18, 2) NOT NULL,
    daily_return_rate NUMBER(10, 4),
    cumulative_return_rate NUMBER(10, 4),
    CONSTRAINT fk_snapshot_account FOREIGN KEY (account_id) REFERENCES accounts (account_id)
);

-- 27. 주식 시세 스냅샷
CREATE TABLE stock_price_snapshots (
    stock_price_snapshot_id NUMBER(19) PRIMARY KEY,
    stock_id NUMBER(19) NOT NULL,
    snapshot_at TIMESTAMP NOT NULL,
    current_price NUMBER(18, 2) NOT NULL,
    change_amount NUMBER(18, 2) NOT NULL,
    change_rate NUMBER(8, 4) NOT NULL,
    trading_volume NUMBER(19) NOT NULL,
    market_status VARCHAR2(20) DEFAULT 'OPEN' NOT NULL,
    CONSTRAINT fk_price_stock FOREIGN KEY (stock_id) REFERENCES stock (stock_id)
);

-- 28. 관심종목 리스트 (시트 2 기준)
CREATE TABLE watchlists (
    watchlist_id NUMBER(19) PRIMARY KEY,
    user_id NUMBER(19) NOT NULL,
    stock_id NUMBER(19) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_watch_user FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT fk_watch_stock FOREIGN KEY (stock_id) REFERENCES stock (stock_id),
    CONSTRAINT uk_user_watchlist UNIQUE (user_id, stock_id) -- 중복 등록 방지
);
