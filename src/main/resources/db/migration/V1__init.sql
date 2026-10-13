-- V1__init.sql — Mebel Sex schema (SQLite)
-- All 12 tables per AGENTS.md. Money: INTEGER (BIGINT semantics). Quantity: TEXT.

-- ============================================================
-- 1. customers
-- ============================================================
CREATE TABLE customers (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    full_name   TEXT    NOT NULL,
    phone       TEXT,
    address     TEXT,
    notes       TEXT,
    created_at  TEXT    NOT NULL DEFAULT (datetime('now')),
    updated_at  TEXT    NOT NULL DEFAULT (datetime('now'))
);

-- ============================================================
-- 2. orders
-- ============================================================
CREATE TABLE orders (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    order_number        TEXT    NOT NULL UNIQUE,
    customer_id         INTEGER NOT NULL REFERENCES customers(id),
    product_description TEXT    NOT NULL,
    agreed_price        INTEGER NOT NULL CHECK (agreed_price >= 0),
    due_date            TEXT,
    status              TEXT    NOT NULL DEFAULT 'NEW'
                        CHECK (status IN ('NEW','IN_PRODUCTION','READY','DELIVERED','COMPLETED','CANCELLED')),
    notes               TEXT,
    created_at          TEXT    NOT NULL DEFAULT (datetime('now')),
    updated_at          TEXT    NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX idx_orders_customer_id ON orders(customer_id);
CREATE INDEX idx_orders_status      ON orders(status);
CREATE INDEX idx_orders_due_date    ON orders(due_date);

-- ============================================================
-- 3. payments
-- ============================================================
CREATE TABLE payments (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    order_id     INTEGER NOT NULL REFERENCES orders(id),
    amount       INTEGER NOT NULL CHECK (amount > 0),
    payment_date TEXT    NOT NULL,
    kind         TEXT    NOT NULL CHECK (kind IN ('ADVANCE','REGULAR','REFUND')),
    method       TEXT    NOT NULL CHECK (method IN ('CASH','CARD','TRANSFER')),
    comment      TEXT,
    status       TEXT    NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','VOIDED')),
    void_reason  TEXT,
    created_at   TEXT    NOT NULL DEFAULT (datetime('now')),
    updated_at   TEXT    NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX idx_payments_order_id     ON payments(order_id);
CREATE INDEX idx_payments_payment_date ON payments(payment_date);

-- ============================================================
-- 4. positions
-- ============================================================
CREATE TABLE positions (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    name       TEXT    NOT NULL,
    active     INTEGER NOT NULL DEFAULT 1 CHECK (active IN (0, 1)),
    created_at TEXT    NOT NULL DEFAULT (datetime('now')),
    updated_at TEXT    NOT NULL DEFAULT (datetime('now'))
);

-- ============================================================
-- 5. employees
-- ============================================================
CREATE TABLE employees (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    full_name       TEXT    NOT NULL,
    phone           TEXT,
    position_id     INTEGER REFERENCES positions(id),
    salary_type     TEXT    NOT NULL DEFAULT 'PER_WORK' CHECK (salary_type IN ('PER_WORK')),
    opening_balance INTEGER NOT NULL DEFAULT 0,
    active          INTEGER NOT NULL DEFAULT 1 CHECK (active IN (0, 1)),
    created_at      TEXT    NOT NULL DEFAULT (datetime('now')),
    updated_at      TEXT    NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX idx_employees_position_id ON employees(position_id);

-- ============================================================
-- 6. operation_types
-- ============================================================
CREATE TABLE operation_types (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    name          TEXT    NOT NULL,
    unit          TEXT,
    default_price INTEGER NOT NULL DEFAULT 0,
    active        INTEGER NOT NULL DEFAULT 1 CHECK (active IN (0, 1)),
    created_at    TEXT    NOT NULL DEFAULT (datetime('now')),
    updated_at    TEXT    NOT NULL DEFAULT (datetime('now'))
);

-- ============================================================
-- 7. work_logs
-- ============================================================
CREATE TABLE work_logs (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    order_id          INTEGER NOT NULL REFERENCES orders(id),
    employee_id       INTEGER NOT NULL REFERENCES employees(id),
    operation_type_id INTEGER NOT NULL REFERENCES operation_types(id),
    work_date         TEXT    NOT NULL,
    quantity          TEXT    NOT NULL,
    unit_price        INTEGER NOT NULL,
    amount            INTEGER NOT NULL CHECK (amount >= 0),
    comment           TEXT,
    status            TEXT    NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','VOIDED')),
    void_reason       TEXT,
    created_at        TEXT    NOT NULL DEFAULT (datetime('now')),
    updated_at        TEXT    NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX idx_work_logs_order_id     ON work_logs(order_id);
CREATE INDEX idx_work_logs_employee_id  ON work_logs(employee_id);
CREATE INDEX idx_work_logs_work_date    ON work_logs(work_date);

-- ============================================================
-- 8. salary_payments
-- ============================================================
CREATE TABLE salary_payments (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    employee_id  INTEGER NOT NULL REFERENCES employees(id),
    amount       INTEGER NOT NULL CHECK (amount > 0),
    payment_date TEXT    NOT NULL,
    comment      TEXT,
    status       TEXT    NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','VOIDED')),
    void_reason  TEXT,
    created_at   TEXT    NOT NULL DEFAULT (datetime('now')),
    updated_at   TEXT    NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX idx_salary_payments_employee_id  ON salary_payments(employee_id);
CREATE INDEX idx_salary_payments_payment_date ON salary_payments(payment_date);

-- ============================================================
-- 9. expense_categories
-- ============================================================
CREATE TABLE expense_categories (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    name       TEXT    NOT NULL,
    active     INTEGER NOT NULL DEFAULT 1 CHECK (active IN (0, 1)),
    created_at TEXT    NOT NULL DEFAULT (datetime('now')),
    updated_at TEXT    NOT NULL DEFAULT (datetime('now'))
);

-- ============================================================
-- 10. expense_items
-- ============================================================
CREATE TABLE expense_items (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    category_id   INTEGER NOT NULL REFERENCES expense_categories(id),
    name          TEXT    NOT NULL,
    unit          TEXT,
    default_price INTEGER NOT NULL DEFAULT 0,
    active        INTEGER NOT NULL DEFAULT 1 CHECK (active IN (0, 1)),
    created_at    TEXT    NOT NULL DEFAULT (datetime('now')),
    updated_at    TEXT    NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX idx_expense_items_category_id ON expense_items(category_id);

-- ============================================================
-- 11. expenses
-- ============================================================
CREATE TABLE expenses (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    order_id        INTEGER REFERENCES orders(id),
    category_id     INTEGER NOT NULL REFERENCES expense_categories(id),
    expense_item_id INTEGER REFERENCES expense_items(id),
    description     TEXT,
    quantity        TEXT    NOT NULL,
    unit_price      INTEGER NOT NULL,
    amount          INTEGER NOT NULL CHECK (amount >= 0),
    expense_date    TEXT    NOT NULL,
    status          TEXT    NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','VOIDED')),
    void_reason     TEXT,
    created_at      TEXT    NOT NULL DEFAULT (datetime('now')),
    updated_at      TEXT    NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX idx_expenses_order_id     ON expenses(order_id);
CREATE INDEX idx_expenses_category_id  ON expenses(category_id);
CREATE INDEX idx_expenses_expense_date ON expenses(expense_date);

-- ============================================================
-- 12. settings
-- ============================================================
CREATE TABLE settings (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    key        TEXT    NOT NULL UNIQUE,
    value      TEXT,
    created_at TEXT    NOT NULL DEFAULT (datetime('now')),
    updated_at TEXT    NOT NULL DEFAULT (datetime('now'))
);

-- ============================================================
-- Seed data
-- ============================================================

-- Expense categories
INSERT INTO expense_categories (name) VALUES ('Material');
INSERT INTO expense_categories (name) VALUES ('Transport');
INSERT INTO expense_categories (name) VALUES ('Bo''yoq');
INSERT INTO expense_categories (name) VALUES ('Boshqa');

-- Settings keys with empty/default values
INSERT INTO settings (key, value) VALUES ('company_name', '');
INSERT INTO settings (key, value) VALUES ('company_phone', '');
INSERT INTO settings (key, value) VALUES ('backup_folder', '');
INSERT INTO settings (key, value) VALUES ('backup_time', '00:00');
