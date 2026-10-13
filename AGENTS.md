# ZakazPro: project rules (read first, every session)

Offline Windows desktop app for a small/medium order-based workshop in Uzbekistan
(furniture, doors/window frames, sewing and others).
Tracks orders, customer payments, expenses, worker piece-rate wages, and profit per order.
The app will be sold to other workshops: each gets its own DB and license file. No multi-tenant.

## Stack
Java 17, Maven, Spring Boot (NO web, NO security), JavaFX + FXML + CSS,
Spring Data JPA/Hibernate, SQLite (sqlite-jdbc + hibernate-community-dialects),
Flyway, Jakarta Validation, SLF4J/Logback. No Lombok. No extra libraries without asking.
Future (do not implement now): license.lic check, jpackage installer, PostgreSQL option.

## Layering
JavaFX controller -> Service (@Transactional, business rules) -> Repository -> SQLite.
UI never touches repositories or EntityManager. Controllers contain no business logic.
Domain packages under com.mebelsex: common, customers, orders, payments, employees, work,
expenses, report, backup, settings, ui. Profit/debt/balance logic lives ONLY in
OrderFinanceService and EmployeeBalanceService.

## Hard rules
1. Money: whole so'm, stored as BIGINT, Java BigDecimal (never double/float).
   Rounding: scale 0, RoundingMode.HALF_UP. Conversion via AttributeConverter.
2. Quantity (may be fractional, e.g. 15.5 m2): stored as TEXT, Java BigDecimal via converter.
3. Dates: TEXT ISO (yyyy-MM-dd); timestamps TEXT ISO-8601. Display format dd.MM.yyyy.
4. Frozen prices: catalog items (operation_types, expense_items) have default_price.
   When a record (work_log, expense) is created, copy unit_price and compute amount;
   store BOTH. Later catalog price changes must never alter old records.
5. No hard delete for money records (payments, work_logs, salary_payments, expenses).
   Use status ACTIVE/VOIDED + void_reason. VOIDED rows are ignored in all sums.
6. Profit, debt, balances are NEVER stored. Always computed from records.
7. Multi-step writes (e.g. create order + advance payment) run in one @Transactional.
8. SQLite: foreign_keys=ON, WAL, busy_timeout, Hikari pool size 1 (single writer).
33. Long operations (backup, restore, report, export) run in javafx.concurrent.Task,
   with a reusable ProgressDialog shown only if the task takes > 300 ms.
   Quick saves show no dialog. Never block the FX thread.
10. No login, no users, no roles (decided). No warehouse/stock module (decided).
11. Applied Flyway migrations are immutable. Schema changes go into a NEW V<n>__description.sql. Never seed demo data in migrations.

## Domain schema (SQLite, Flyway-managed; every table has id, created_at, updated_at)
- customers: full_name, phone, address, notes
- orders: order_number (unique, sequential), customer_id, product_description (text),
  agreed_price, due_date, status (NEW|IN_PRODUCTION|READY|DELIVERED|COMPLETED|CANCELLED), notes
- payments: order_id, amount (>0), payment_date, kind (ADVANCE|REGULAR|REFUND),
  method (CASH|CARD|TRANSFER), comment, status, void_reason
- positions: name, active
- employees: full_name, phone, position_id, salary_type (only PER_WORK now),
  opening_balance (BIGINT, default 0; positive = workshop owes worker), active
- operation_types: name, unit, default_price, active
- work_logs: order_id, employee_id (required), operation_type_id, work_date,
  quantity, unit_price, amount, comment, status, void_reason
- salary_payments: employee_id, amount (>0), payment_date, comment, status, void_reason
  (not linked to orders)
- expense_categories: name, active
- expense_items: category_id, name, unit, default_price, active
- expenses: order_id (nullable for future general costs), category_id,
  expense_item_id (nullable; one-off costs have none), description, quantity,
  unit_price, amount, expense_date, status, void_reason
- settings: key (unique), value (backup folder, backup time, company name, phone...)
  Use CHECK constraints (amount > 0, valid enums), FKs, and indexes on FKs and dates.

## Formulas (ACTIVE records only)
expenses_total   = sum(expenses.amount)
labor_total      = sum(work_logs.amount)
profit_before_labor = agreed_price - expenses_total
net_profit       = profit_before_labor - labor_total
net_profit_pct   = net_profit / agreed_price * 100   (agreed_price = 0 -> show "—")
paid_by_customer = sum(ADVANCE + REGULAR) - sum(REFUND)
customer_debt    = agreed_price - paid_by_customer
worker_balance(date) = opening_balance + sum(work_logs.amount up to date)
- sum(salary_payments.amount up to date)   (negative = worker owes)

## UI rules
- 100% Uzbek (Latin). No hardcoded strings: all text in
  src/main/resources/i18n/messages_uz.properties (UTF-8), FXML uses %key.
  Careful with apostrophes (o', g') in MessageFormat texts: use '' there.
- Enum labels translated separately (OrderStatus.IN_PRODUCTION -> "Ishlab chiqarishda").
- Formats via one Formatters class: money "25 000 000 so'm", date dd.MM.yyyy.
- Colors as CSS variables in application.css: primary #1E3A8A, primary-dark #172554, hover #1D4ED8,
  background #F3F6FB, surface #FFFFFF, border #E5E7EB, text #1F2937, muted #6B7280,
  profit #15803D, debt #B91C1C, focus #93C5FD. Radius 8px/12px. Font Segoe UI.
- Font >= 14px, tall table rows. Users are not computer-savvy: simple words
  ("Bekor qilish", not "void"), few buttons, safe undo, Excel-like quick-add row + Enter.
- Order card is the main screen: tabs To'lovlar, Ishlar, Xarajatlar, Xulosa.
- Main menu: Bosh sahifa, Zakazlar, Mijozlar, Ishchilar, Hisobotlar, Katalog, Sozlamalar.

## Backup (later phase)
Daily auto backup at 00:00 (configurable) + manual; on startup, if today's backup is missing, run it.
Use `VACUUM INTO`, never plain file copy. Folder: <chosen>/<yyyy-MM-dd>/zakazpro_<yyyy-MM-dd_HH-mm>.db
(older mebel_*.db files are still recognised).
Restore: validate file, auto-backup current DB first, replace, restart. No Telegram code.

## Working rules for agents
- Do ONE phase per session; stay inside the requested scope. Do not build ahead.
- Ask before adding dependencies or changing decisions in this file.
- If a business rule is unclear, ask; do not guess.
- Keep changes small and compile after each step (`mvn -q compile`); report what you ran.
- Do not touch the DB file, resources outside the task, or this file unless asked.
- Visible product name lives in AppInfo; internal identifiers (com.mebelsex, license header,
  Machine ID salt, mebel.db) must not be renamed.
- Graph (graphify-out/) may not exist yet; if missing, do not build it, just read files.
- docs/HANDOFF_ARCHIVE.md is history; do not read it unless asked.
- End each session with: what changed, how to verify, what is next.

## Handoff (mandatory at end of every session)
Overwrite HANDOFF.md in the project root. It describes the CURRENT state, not history.
Max ~120 lines. No code dumps: file/class names, one-line purposes, signatures only.

Sections:
1. Status: phase done / partially done / blocked.
2. What exists: per package, class names + one-line purpose; Flyway migrations list.
3. Deviations: anything that differs from AGENTS.md or the prompt, and why.
4. Decisions made by the agent (versions, libs, config choices).
5. Verification: exact commands run and their real result (pass/fail, error text).
   Mark clearly what was NOT verified.
6. Known issues / TODO.
7. Open questions that need the owner's decision.
8. Next step suggestion.
9. Changelog: last 10 one-line entries (date, session, summary).

Never claim something works unless you ran it. If you could not run it, say so.