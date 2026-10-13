-- V2__order_date.sql — add the order date (zakaz sanasi) to orders.
-- SQLite cannot add a NOT NULL column without a default; the entity and the
-- service always set a value, and existing rows are backfilled from created_at.

ALTER TABLE orders ADD COLUMN order_date TEXT;

UPDATE orders SET order_date = substr(created_at, 1, 10) WHERE order_date IS NULL;

CREATE INDEX idx_orders_order_date ON orders(order_date);
