CREATE TABLE order_executions (
	execution_id	NUMBER		NULL,
	order_id	NUMBER		NOT NULL,
	executed_quantity	NUMBER		NOT NULL,
	executed_price	NUMBER(18,2)		NOT NULL,
	executed_amount	NUMBER(18,2)		NOT NULL,
	executed_at	DATE		NOT NULL
);

CREATE TABLE stock (
	stock_id	NUMBER		NULL,
	stock_code	VARCHAR2(20)		NOT NULL,
	stock_name	VARCHAR2(100)		NOT NULL,
	market_type	VARCHAR2(20)		NOT NULL,
	is_active	NUMBER		NOT NULL,
	created_at	DATE		NOT NULL
);

CREATE TABLE competitions (
	competition_id	NUMBER		NULL,
	title	VARCHAR2(200)		NOT NULL,
	description	VARCHAR2(500)		NULL,
	start_at	DATE		NOT NULL,
	end_at	DATE		NOT NULL,
	initial_seed_money	NUMBER(18,2)		NOT NULL,
	max_participants	NUMBER		NULL,
	status	VARCHAR2(20)	DEFAULT 'SCHEDULED'	NOT NULL,
	created_by_admin_id	NUMBER		NOT NULL,
	created_at	DATE		NOT NULL,
	updated_at	DATE		NOT NULL
);

CREATE TABLE users (
	user_id	NUMBER		NULL,
	email	VARCHAR2(255)		NOT NULL,
	password_hash	VARCHAR2(255)		NULL,
	nickname	VARCHAR2(50)		NOT NULL,
	profile_image_url	VARCHAR2(500)		NULL,
	role	VARCHAR2(20)	DEFAULT 'USER'	NOT NULL,
	status	VARCHAR2(20)	DEFAULT 'ACTIVE'	NOT NULL,
	marketing_consent	NUMBER		NOT NULL,
	email_verified	NUMBER		NOT NULL,
	last_login_at	DATE		NULL,
	suspended_at	DATE		NULL,
	withdrawn_at	DATE		NULL,
	created_at	DATE		NOT NULL,
	updated_at	DATE		NOT NULL,
	deleted_at	DATE		NULL
);

CREATE TABLE notices (
	notice_id	NUMBER		NULL,
	admin_user_id	NUMBER		NOT NULL,
	title	VARCHAR2(200)		NOT NULL,
	content	VARCHAR2(1000)		NOT NULL,
	is_pinned	NUMBER		NOT NULL,
	view_count	NUMBER		NOT NULL,
	created_at	DATE		NOT NULL,
	updated_at	DATE		NOT NULL,
	deleted_at	DATE		NULL
);

CREATE TABLE admin_action_logs (
	admin_action_log_id	NUMBER		NULL,
	admin_user_id	NUMBER		NOT NULL,
	action_type	VARCHAR2(50)		NOT NULL,
	target_type	VARCHAR2(50)		NOT NULL,
	target_id	NUMBER		NULL,
	action_detail	VARCHAR2(200)		NULL,
	ip_address	VARCHAR2(64)		NULL,
	created_at	DATE		NOT NULL
);

CREATE TABLE account_cash_ledgers (
	cash_ledger_id	NUMBER		NULL,
	account_id	NUMBER		NOT NULL,
	ledger_type	VARCHAR2(30)		NOT NULL,
	amount	NUMBER(18,2)		NOT NULL,
	balance_after	NUMBER(18,2)		NOT NULL,
	reference_type	VARCHAR2(30)		NULL,
	reference_id	NUMBER		NULL,
	memo	VARCHAR2(255)		NULL,
	created_at	DATE		NOT NULL
);

CREATE TABLE watchlists (
	watchlist_id	NUMBER		NULL,
	user_id	NUMBER		NOT NULL,
	stock_id	NUMBER		NOT NULL,
	created_at	DATE		NOT NULL
);

CREATE TABLE user_term_agreements (
	agreement_id	NUMBER		NULL,
	user_id	NUMBER		NOT NULL,
	term_id	NUMBER		NOT NULL,
	agreed	NUMBER		NOT NULL,
	agreed_at	DATE		NOT NULL
);

CREATE TABLE orders (
	order_id	NUMBER		NULL,
	account_id	NUMBER		NOT NULL,
	stock_id	NUMBER		NOT NULL,
	order_side	VARCHAR2(10)		NOT NULL,
	order_type	VARCHAR2(10)		NOT NULL,
	quantity	NUMBER		NOT NULL,
	price	NUMBER(18,2)		NULL,
	remaining_quantity	NUMBER		NOT NULL,
	order_status	VARCHAR2(20)	DEFAULT 'PENDING'	NOT NULL,
	queued_at	DATE		NULL,
	ordered_at	DATE		NOT NULL,
	canceled_at	DATE		NULL
);

CREATE TABLE post_reports (
	report_id	NUMBER		NULL,
	post_id	NUMBER		NOT NULL,
	reporter_user_id	NUMBER		NOT NULL,
	reason	VARCHAR2(255)		NOT NULL,
	detail	VARCHAR2(200)		NULL,
	report_status	VARCHAR2(20)	DEFAULT 'PENDING'	NOT NULL,
	handled_admin_id	NUMBER		NULL,
	handled_at	DATE		NULL,
	created_at	DATE		NOT NULL
);

CREATE TABLE login_histories (
	login_history_id	NUMBER		NULL,
	user_id	NUMBER		NULL,
	login_identifier	VARCHAR2(255)		NOT NULL,
	ip_address	VARCHAR2(64)		NULL,
	user_agent	VARCHAR2(500)		NULL,
	login_result	VARCHAR2(20)		NOT NULL,
	failure_reason	VARCHAR2(255)		NULL,
	created_at	DATE		NOT NULL
);

CREATE TABLE oauth_accounts (
	oauth_account_id	NUMBER		NULL,
	user_id	NUMBER		NOT NULL,
	provider	VARCHAR2(30)		NOT NULL,
	provider_user_id	VARCHAR2(255)		NOT NULL,
	provider_email	VARCHAR2(255)		NULL,
	created_at	DATE		NOT NULL
);

CREATE TABLE terms (
	term_id	NUMBER		NULL,
	term_code	VARCHAR2(50)		NOT NULL,
	title	VARCHAR2(200)		NOT NULL,
	is_required	NUMBER		NOT NULL,
	version	VARCHAR2(30)		NOT NULL,
	is_active	NUMBER		NOT NULL,
	created_at	DATE		NOT NULL
);

CREATE TABLE competition_participants (
	competition_participant_id	NUMBER		NULL,
	competition_id	NUMBER		NOT NULL,
	user_id	NUMBER		NOT NULL,
	account_id	NUMBER		NOT NULL,
	joined_at	DATE		NOT NULL,
	participation_status	VARCHAR2(20)	DEFAULT 'JOINED'	NOT NULL,
	final_return_rate	NUMBER(10,4)		NULL,
	final_rank	NUMBER		NULL
);

CREATE TABLE accounts (
	account_id	NUMBER		NULL,
	user_id	NUMBER		NOT NULL,
	competition_id	NUMBER		NULL,
	account_type	VARCHAR2(20)		NOT NULL,
	account_name	VARCHAR2(100)		NOT NULL,
	cash_balance	NUMBER(18,2)		NOT NULL,
	is_active	NUMBER		NOT NULL,
	created_at	DATE		NOT NULL,
	updated_at	DATE		NOT NULL
);

CREATE TABLE asset_snapshots (
	asset_snapshot_id	NUMBER		NULL,
	account_id	NUMBER		NOT NULL,
	snapshot_date	DATE		NOT NULL,
	cash_balance	NUMBER(18,2)		NOT NULL,
	stock_value	NUMBER(18,2)		NOT NULL,
	total_asset_value	NUMBER(18,2)		NOT NULL,
	daily_return_rate	NUMBER(10,4)		NULL,
	cumulative_return_rate	NUMBER(10,4)		NULL
);

CREATE TABLE notifications (
	notification_id	NUMBER		NULL,
	user_id	NUMBER		NOT NULL,
	notification_type	VARCHAR2(30)		NOT NULL,
	title	VARCHAR2(200)		NOT NULL,
	message	VARCHAR2(500)		NOT NULL,
	related_type	VARCHAR2(30)		NULL,
	related_id	NUMBER		NULL,
	is_read	NUMBER		NOT NULL,
	read_at	DATE		NULL,
	created_at	DATE		NOT NULL
);

CREATE TABLE boards (
	board_id	NUMBER		NULL,
	board_name	VARCHAR2(100)		NOT NULL,
	board_code	VARCHAR2(50)		NOT NULL,
	description	VARCHAR2(500)		NULL,
	is_active	NUMBER		NOT NULL,
	created_at	DATE		NOT NULL
);

CREATE TABLE inquiries (
	inquiry_id	NUMBER		NULL,
	user_id	NUMBER		NOT NULL,
	category	VARCHAR2(50)		NOT NULL,
	title	VARCHAR2(200)		NOT NULL,
	content	VARCHAR2(200)		NOT NULL,
	inquiry_status	VARCHAR2(20)	DEFAULT 'OPEN'	NOT NULL,
	created_at	DATE		NOT NULL,
	updated_at	DATE		NOT NULL
);

CREATE TABLE inquiry_answers (
	inquiry_answer_id	NUMBER		NULL,
	inquiry_id	NUMBER		NOT NULL,
	admin_user_id	NUMBER		NOT NULL,
	content	VARCHAR2(100)		NOT NULL,
	created_at	DATE		NOT NULL,
	updated_at	DATE		NOT NULL
);

CREATE TABLE posts (
	post_id	NUMBER		NULL,
	board_id	NUMBER		NOT NULL,
	user_id	NUMBER		NOT NULL,
	title	VARCHAR2(200)		NOT NULL,
	content	VARCHAR2(1000)		NOT NULL,
	view_count	NUMBER		NOT NULL,
	like_count	NUMBER		NOT NULL,
	comment_count	NUMBER		NOT NULL,
	report_count	NUMBER		NOT NULL,
	status	VARCHAR2(20)	DEFAULT 'NORMAL'	NOT NULL,
	created_at	DATE		NOT NULL,
	updated_at	DATE		NOT NULL,
	deleted_at	DATE		NULL
);

CREATE TABLE stock_price_snapshots (
	stock_price_snapshot_id	NUMBER		NULL,
	stock_id	NUMBER		NOT NULL,
	snapshot_at	DATE		NOT NULL,
	current_price	NUMBER(18,2)		NOT NULL,
	change_amount	NUMBER(18,2)		NOT NULL,
	change_rate	NUMBER(8,4)		NOT NULL,
	trading_volume	NUMBER		NOT NULL,
	market_status	VARCHAR2(20)	DEFAULT 'OPEN'	NOT NULL
);

CREATE TABLE holdings (
	holding_id	NUMBER		NULL,
	account_id	NUMBER		NOT NULL,
	stock_id	NUMBER		NOT NULL,
	quantity	NUMBER		NOT NULL,
	average_buy_price	NUMBER(18,2)		NOT NULL,
	updated_at	DATE		NOT NULL
);

CREATE TABLE email_verifications (
	email_verification_id	NUMBER		NULL,
	email	VARCHAR2(255)		NOT NULL,
	verification_type	VARCHAR2(30)		NOT NULL,
	verification_code	VARCHAR2(20)		NOT NULL,
	is_verified	NUMBER		NOT NULL,
	expires_at	DATE		NOT NULL,
	verified_at	DATE		NULL,
	created_at	DATE		NOT NULL
);

CREATE TABLE comments (
	comment_id	NUMBER		NULL,
	post_id	NUMBER		NOT NULL,
	user_id	NUMBER		NOT NULL,
	parent_comment_id	NUMBER		NULL,
	content	VARCHAR2(100)		NOT NULL,
	status	VARCHAR2(20)	DEFAULT 'NORMAL'	NOT NULL,
	created_at	DATE		NOT NULL,
	updated_at	DATE		NOT NULL,
	deleted_at	DATE		NULL
);

