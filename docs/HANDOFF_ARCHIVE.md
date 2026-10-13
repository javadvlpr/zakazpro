ARCHIVE: complete history up to 2026-10-06; do not read in normal sessions.
﻿# Mebel Sex - Project State (HANDOFF)

Rules for agents: sections 3, 4 and 9 are append-only (never delete earlier lines). Update sections 1, 2, 5, 6, 7, 8
IN PLACE at the end of every session (do not add "Session Update" sections). No marketing words. Report only what you ran.

## 1. Status
- Done: dark theme (Tungi rejim) - token system, ThemeManager live switching, Sozlamalar "Ko'rinish" toggles, sidebar moon/sun button, dark palette (Parts A-C).
- Done: Phase 1 (foundation), 2A (customers, orders, payments backend), 2B-1 (UI shell, Mijozlar), 2B-2a (Zakazlar list,
  order form, status dialog), 2B-2b (order card, real Bosh sahifa), Phase 3 (expenses: Katalog screen, order expenses,
  Xulosa with "Ish haqisiz foyda"), Phase 4 (employees, positions, operation types, work logs, salary payments, worker
  balances, labor/net-profit formulas, Katalog tabs, Ishchilar screen, employee card, order-card "Ishlar" tab, Xulosa
  wages + net profit, dashboard "Ishchilarga qarzimiz"), Phase 5 (order date, migration V2, report backend,
  Hisobotlar screen with 5 reports, Excel export, dashboard "Bu oy foyda"), Phase 6 Part A (settings service, backup service,
  restore staging, pending-restore swap, scheduler, close hook), Phase 6 Part B (real Sozlamalar screen, restore preview dialog,
  startup restore-result dialog, dashboard "Oxirgi backup" card).
- Done: Stability (Barqarorlik) — Part 0 (dark text inputs), A (single instance + startup-failure dialog), B (file logging + diagnostics ZIP), C (startup DB safety + recovery dialog), D (cleanup: single enum-label mapper, one Messages accessor, hardcoded strings moved, window geometry remembered).
- Done: table alignment — every table's data cells are now horizontally centred so they line up under their (already centred) column headers; the old left/right per-column overrides are gone.
- Done: license core + generator (package com.mebelsex.license + owner CLI + tools). Now wired into the app (see the license integration line below).
- Done: license integration — backend (LicensePaths/LicenseGate/LicenseService/LicenseWriteGuard/ExceptionHandler unwrap) + UI (activation window, content-area banner, Sozlamalar "Litsenziya" card). BLOCKED shows only the activation window (no main window, no backups); READ_ONLY opens read-only with a red banner; expiring within 14 days shows an amber banner.
- Done: numeric input fix — MoneyField/SignedMoneyField no longer reformat themselves from their own text listener (that threw `IllegalArgumentException: The start must be <= the end` on Backspace in "Yangi zakaz"); grouping/sign now run in one TextFormatter filter over the pure MoneyInputFormatter / SignedMoneyInputFormatter / QuantityInputFormatter.
- Last build: `mvn -l build.log clean verify` = 372 tests, 0 failures, BUILD SUCCESS (2026-10-06).
- Owner manually confirmed: Mijozlar, Zakazlar, order create with and without advance, order edit, order card payments,
  Bosh sahifa; the owner reported the phase finished after Part A (UI polish) and Phase 3.
- Placeholder screens: none (Sozlamalar is now built).
- Not started: license wiring into the app, release/installer.

## 2. What exists
FXML (src/main/resources/fxml): main, dashboard, customers/customers, customers/customers_form, orders/orders_screen,
orders/order_form, orders/status_dialog, orders/order_card, catalog (+ catalog/category_form, catalog/item_form,
catalog/operation_form, catalog/position_form), employees, employees_form, employees/employee_card,
reports (Hisobotlar), settings (Ko'rinish + Korxona + Backup + Tiklash + Dastur haqida cards). CSS: css/application.css,
which holds the app colours as `-mx-*` tokens on `.root` (light) and the dark overrides + Modena keys on `.root.dark`.
Messages: i18n/messages_uz.properties.
Flyway: `V1__init.sql` (12 tables; seeds expense_categories Material/Transport/Bo'yoq/Boshqa and settings keys) and
`V2__order_date.sql` (orders.order_date TEXT + index, backfilled from created_at).
Applied migrations are immutable; a schema change needs a NEW V<n>__*.sql. No demo data in migrations.
Phase 5 message keys (messages_uz.properties): order.orderDate, order.error.date_required, dashboard.month_profit,
report.type, report.period.* (label/today/week/month/prev_month/year/custom/from/to), report.customers.only_debtors,
report.export (+title/done/filter), report.empty, report.loading, report.total, report.subtotal,
report.expenses.other_item, report.error.period_required, report.error.period_invalid, the five report titles
(report.period.title/cashflow.title/expenses.title/employees.title/customers.title), all report.col.* and the
report.*.summary labels.
Phase 6 Part A message keys (messages_uz.properties): settings.company.*, settings.backup.* (folder/choose/open_folder/
auto/time/on_close/keep_days/keep_days.hint/now/last/never/today/days_ago/warning/table.*/show/restore/empty/done.*/failed.*/
running), settings.restore.* (title/choose/warning/preview.*/confirm/scheduled/relaunch_failed/choose.title/filter),
settings.about.* (title/version/db_path), settings.save.success, settings.error.* (time_format/keep_days/save),
backup.error.* (folder/failed/verify), backup.notify.* (title/text), restore.error.* (invalid/not_sqlite/corrupt/
missing_tables/newer_schema/failed), restore.result.* (title/success/failed), dashboard.backup.title.

Java (com.mebelsex):
- root: Launcher (DB path, env MEBELSEX_DB_PATH, single-instance lock, `--relaunch` wait, already-running dialog),
  MebelSexApplication (JavaFX shell, splash, Spring start, global uncaught-exception handler, startup-failure dialog).
- common: BaseEntity, BusinessException, Formatters, LocalDateConverter, MoneyConverter, QuantityConverter, RecordStatus,
  TimestampConverter, BooleanIntConverter (Boolean <-> INTEGER 0/1 for V1 `active` columns), SingleInstanceGuard
  (FileChannel/FileLock single instance, takes a Path), StartupFailureMapper (exception -> Uzbek reason key),
  FileExplorer (best-effort Explorer open/select), AppInfo (version), DiagnosticsBuilder (diagnostics ZIP from
  directories: newest 3 logs + info.txt, never a DB).
  logging: `src/main/resources/logback-spring.xml` (daily rotation, 14 days, 50 MB cap, mebelsex.log, console+file).
- backup (stability additions): BackupFileInspector (pure file inspection: header, integrity, core tables, Flyway
  version, counts; RestoreService delegates), StartupDatabaseCheck (pure OK/FIRST_RUN/DAMAGED/LOCKED check),
  BackupFolderMarker (backup-folder.txt), RecoveryBackups (recent valid backups finder).
- common (stability additions): Messages (the single i18n bundle accessor — get/format/bundle), AppInfo (version).
- ui: RecoveryApplication (minimal JavaFX recovery dialog, no Spring), WindowGeometry (pure saved-window clamp).
- settings: WindowState (saved window geometry record); SettingsService.loadWindow/saveWindow on `ui.window.*` keys.
- customers: Customer, CustomerRepository, CustomerService.
- orders: Order (+orderDate), OrderStatus, OrderRepository, OrderService (getCard, getForEdit, listItems; create/update
  overloads with orderDate), OrderFinanceService (paid, debt, expensesTotal, expensesByCategory, profitBeforeLabor(+percent),
  laborTotal, netProfit(+percent), laborByEmployee), OrderListItem, OrderCardDto (+orderDate, laborTotal, laborEmployees,
  netProfit, netProfitPercent), OrderEditDto (+orderDate), LaborEmployeeTotal, DashboardSummary (+workersOwed,
  monthNetProfit), ExpenseCategoryTotal (records), DashboardService.
- payments: Payment, PaymentKind, PaymentMethod, PaymentRepository, PaymentService (listRows), PaymentRow.
- expenses: ExpenseCategory, ExpenseItem, Expense, three repositories, CategoryRow, ItemRow, ExpenseRow (records),
  ExpenseCatalogService, ExpenseService.
- settings: Setting, SettingRepository, SettingsService (typed get/set with defaults + validation, lazily creates keys),
  AppSettings (DTO record).
- backup: BackupReason (MANUAL/SCHEDULED/STARTUP/ON_CLOSE/BEFORE_RESTORE), BackupResult, BackupStatus, BackupFileRow,
  RestorePreview, RestoreApplyResult (records), BackupPolicy (pure startup/scheduled/close decisions), BackupService
  (VACUUM INTO backup + verify + retention + listRecent + status + dataChangedSince), BackupScheduler (daily/startup
  auto backups, disabled in dev/test), RestoreService (inspect + scheduleRestore), PendingRestoreApplier (pure swap on
  startup, called from Launcher), RelaunchHelper (best-effort self restart).
- employees: SalaryType (PER_WORK), Position, Employee, PositionRepository, EmployeeRepository, PositionRow,
  EmployeeRow, EmployeeBalanceRow (records), PositionService (unique names, activate), EmployeeService (create/update,
  openingBalance may be negative), EmployeeBalanceService (the only balance logic: balanceAsOf, balancesAsOf,
  totalOwedToWorkers; no N+1).
- work: OperationType, WorkLog, SalaryPayment, OperationTypeRepository, WorkLogRepository, SalaryPaymentRepository,
  OperationTypeRow, WorkLogRow, SalaryPaymentRow (records), OperationTypeService (unique names, default price),
  WorkLogService (frozen price, amount = quantity x price, void + reason, listForOrder/listForEmployee),
  SalaryPaymentService (amount > 0, void + reason, inactive workers may still be paid).
- report: CellType, Align, Tone, Cell, Column, Row, SummaryItem, ReportTable (JavaFX-free model), PeriodReportRow/
  PeriodReportTotals/PeriodReport, CashFlowRow/CashFlowReport, ExpenseReportLine/ExpenseReportCategory/ExpenseReport,
  EmployeeReportRow/EmployeeReport, CustomerReportRow/CustomerReport (records), ReportPeriod (today/thisWeek/thisMonth/
  previousMonth/thisYear/custom), ReportService (read-only; reuses OrderFinanceService + EmployeeBalanceService),
  ReportTables (DTO -> ReportTable mapper, i18n via Function), ExcelExporter (Apache POI).
- ui: MainController (sidebar + theme toggle button), ViewLoader, DashboardController (incl. "Oxirgi backup" card),
  TaskRunner, AlertHelper (sets the active theme on every alert DialogPane), ExceptionHandler, EnumLabels, Theme
  (LIGHT/DARK + parse), ThemeManager (active theme, weak-registered scene roots, applies dark class, persists ui.theme),
  SnapshotConfigurator (dev only), ImageDiffTool (dev only image diff; refuses to run unless MEBELSEX_SNAPSHOT_DIR is set;
  never referenced by app code).
- ui.components: MoneyField, MoneyParser, SignedMoneyField, SignedMoneyParser, QuantityField, QuantityParser,
  UzDatePicker, SearchField, StatusBadge, EnumLabels. Numeric-input fix adds MoneyInputFormatter (pure
  grouping + caret engine for MoneyField), SignedMoneyInputFormatter (leading minus) and QuantityInputFormatter
  (quantity validation extracted from the field); the three fields hold ONE TextFormatter filter each.
- ui.customers: CustomersController, CustomerFormController. ui.orders: OrdersController, OrderFormController,
  StatusDialogController, OrderDialogs, OrderCards, OrderCardController. ui.catalog: CatalogController,
  CategoryFormController, ItemFormController, OperationFormController, PositionFormController. ui.employees:
  EmployeesController (balance table + form), EmployeeFormController (signed opening balance, '+' position link),
  EmployeeCards, EmployeeCardController (work logs + wage payments); ui.reports: ReportsController (Hisobotlar screen:
  type combo, period toggles, generic report table, Excel export via FileChooser). ui.settings: SettingsController
  (Korxona/Backup/Tiklash/Dastur haqida cards, manual backup, DirectoryChooser/FileChooser, recent-backup table with
  Ko'rsatish/Tiklash row actions, restore preview dialog, window title from the company name).

- license (new, pure Java, no Spring/JavaFX): MachineIdSource (raw id abstraction), MachineIdException,
  WindowsMachineGuidSource (full-path reg.exe query, 5 s timeout, pure output parser), MachineId (SHA-256 of
  "mebelsex-machine-v1:"+guid, first 10 bytes, base32 alphabet ABCDEFGHJKLMNPQRSTUVWXYZ23456789 -> MX-ABCD-EFGH-IJKL-MNOP;
  golden test), LicensePayload (record), LicenseFormat (header + base64url payload + Ed25519 signature; tolerant parser),
  LicenseKeys (Ed25519 generate + private/public key text I/O), PublicKeyProvider, ResourcePublicKeyProvider
  (/license/license-public.key; placeholder -> no key), LicenseVerifier (parse -> key -> machine id -> signature ->
  machine -> expiry), LicenseCheck (result enum VALID/EXPIRED/MALFORMED/BAD_SIGNATURE/WRONG_MACHINE/UNSUPPORTED_VERSION/
  NO_PUBLIC_KEY/NO_MACHINE_ID + payload + daysLeft), ClockGuard (pure 36 h rollback guard), LicenseStatus,
  LicenseStatusCalculator (status + reason keys).
- license integration (new): LicensePaths (license.lic / license.lic.bak / license.state next to the DB), LicenseGate
  (current + requireWritable), LicenseService (evaluate: file -> verify -> clock guard -> calculator, persists last-seen
  to settings and license.state, caches status, notifies listeners, 30-min daemon re-check via startPeriodicChecks();
  importLicense accepts only VALID, keeps the old file as .bak and moves the new one atomically; dev flag
  -Dmebelsex.license.dev=true + -Dmebelsex.license.force=BLOCKED|READ_ONLY|EXPIRING|VALID), LicenseConfig (beans:
  Clock/MachineIdSource/PublicKeyProvider/LicensePaths/LicenseService), LicenseWriteGuard (Hibernate listeners blocking
  every entity except Setting). ExceptionHandler.findBusinessException unwraps the cause chain.
- license.tool: LicenseCli (owner tool: machine/keygen/issue/inspect; JDK only).
- resources: license/license-public.key (placeholder PLACEHOLDER-RUN-KEYGEN until the owner replaces it).
- tools: license.cmd (runs the CLI from target/classes), README-license.md (Uzbek owner guide).
- ui (license): ActivationController (activation window: machine id + copy, reason, license file chooser,
  inline errors, exit) + fxml/activation.fxml; MainController license banner (HBox hidden by default; amber/red
  style classes; "Faollashtirish" opens Sozlamalar; live updates from the service listener); SettingsController
  "Litsenziya" card (state, customer, valid until/days left, machine id + copy, short license id, import button,
  DEV marker, renew hint in READ_ONLY); MebelSexApplication splits into showMainWindow / showActivation +
  stageSizeForActivation / snapshotActivationIfDev; SnapshotConfigurator gained the license-banner and
  license-settings steps and setDevLicenseState.

Behaviour that exists: customer CRUD + search; orders (create with optional advance in one transaction, edit, status change,
list with filters); order card with tabs To'lovlar (quick-add, void with reason), Xarajatlar (quick-add, void with reason),
Xulosa (price, expenses by category, profit before labor with %, wages with per-employee lines, net profit with %, paid, debt);
Katalog (expense categories, operation types and positions with default price / unit, deactivate instead of delete);
Bosh sahifa numbers incl. "Ishchilarga qarzimiz"; Ishchilar (balances as of a date, search, inactive toggle, legend);
employee card (metrics, period-filtered work logs and wage payments, quick-add payment with live remaining balance);
order-card "Ishlar" tab (quick-add work log with frozen price, void with reason); Xulosa wages (per employee) and net
profit with percentage; Hisobotlar (5 reports - Davr hisoboti, Pul harakati, Xarajatlar, Ishchilar, Mijozlar - with period
filters, summary cards, double-click order rows opens the order card, Excel export through a FileChooser); order date on
create/edit/list/card; Sozlamalar (company name/phone/address, backup folder chooser + open in Explorer, automatic backup
toggle + time combos + close-backup toggle + keep-days, "Hozir backup olish" with a "Faylni ko'rsatish" result dialog,
age-coloured "Oxirgi backup" status, 15 recent backups with Ko'rsatish/Tiklash, restore from a .db file with a preview
dialog then restart, and an about card with version and the DB path); dashboard "Oxirgi backup" card; startup
restore-result dialog.

## 3. Deviations (accumulated, never delete)
- TimestampConverter reads both "yyyy-MM-dd HH:mm:ss" (SQLite datetime('now')) and ISO-8601 with 'T'.
- spring-boot-maven-plugin and javafx-maven-plugin mainClass = com.mebelsex.Launcher (Launcher does not extend Application).
- Tests use spring-boot-starter-test and spring-boot-starter-flyway-test; each run uses a temp SQLite file under target/.
- IDENTITY insert failure: Order.agreedPrice was typed Long while MoneyConverter expects BigDecimal; fixed with BigDecimal.
- messages_uz.properties once got UTF-16 text (NUL bytes) from a shell append; repaired. Never write files with PowerShell redirection.
- Dialogs opened behind the main window (missing owner); owner now set, AlertHelper picks the active window.
- orders_screen.fxml once had `<?xml version="1.xml"?>`; JavaFX failed to load it and MainController hid the error. MainController
  now logs the error and shows a real error text for built screens.
- FXML load failures were also caused by: TextField in customers.fxml vs SearchField field; `fas-` icons with only the Feather pack;
  `prefWidth="Infinity"` in GridPane forms; wrong relative stylesheet paths. FxmlFilesTest guards these.
- An agent session rewrote CustomersController/OrdersController from scratch and broke the build; the owner restored a backup.
  Rule: smallest edits, never rewrite a working class.
- Java 17 is intentional (JDK 21 not installed on the owner's machine); no Java 18+ language features.
- Packages use plural names (customers, orders, payments, expenses).
- AlertHelper confirm: YES = ButtonData.YES, NO = CANCEL_CLOSE (X and Esc close the dialog).
- Order edit crashed with LazyInitializationException (UI used the lazy Order.customer proxy). Fixed with OrderEditDto +
  OrderService.getForEdit; UiDoesNotImportEntityAssociationsTest forbids ui imports of Order/Payment entities.
- Clipped ComboBox/DatePicker text: the global `.text-field, .combo-box, .date-picker` CSS rule also hit their inner editors.
  Fixed with inner-control reset rules (ApplicationCssTest guards them). Raw enum "IN_PRODUCTION" came from mismatching label
  keys in ui.components.EnumLabels; fixed to use the same `OrderStatus.<NAME>` keys.
- V1 `active` columns are INTEGER but Hibernate validates Boolean as BOOLEAN; fixed per field with `@Convert(BooleanIntConverter)`.
  Use it for every `active` column (positions, employees, operation_types as well).
- catalog.fxml had a wrong relative stylesheet path (fixed). A relative `@...css/application.css` path must match the folder
  depth of each FXML, otherwise styling is silently lost; check it for every new FXML.
- reports (Hisobotlar) is a top-level FXML, so its stylesheet is `@../css/application.css`.
- The reports table first used CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN; with 12 columns it clipped every header and value to
  "...". Switched to UNCONSTRAINED_RESIZE_POLICY with a per-cell-type preferred width (horizontal scroll when needed).
- Switching report type crashed a table cell: formatValue cast DATE to LocalDate, but a stale row from the previous report was
  laid out under the new columns. Fixed by clearing the table items before rebuilding the columns, and by making formatValue
  type-tolerant.
- The reports filter row did not fit at 1280x800 (period toggles and date labels clipped); split into two rows (toggles, then
  Dan/Gacha + only-debtors).
- The report summary cards squeezed and clipped at 1024x680 ("Zakaz...", "37 00..."); the summary row is now a wrapping
  FlowPane (185px cards) instead of one stretched HBox.
- License CLI: its visible Uzbek text lives inside LicenseCli, not in messages_uz.properties (documented exception; the
  tool is not part of the app UI). CLI tests and the secret-scan test are pure JUnit (no Spring, no toolkit).
- License secret scan skips target/ and any dot-directory (.git, .freebuff, .agents, ...), not only .git/, to stay fast and
  to avoid tooling metadata; the two sources that must mention the private header are allow-listed by file name.
- `issue` verifies its own output with the public key found next to the private key (or --public-key): RFC 8410 PKCS#8
  does not carry the public key, so it cannot be derived from the private key in the JDK.
- License integration: `src/main/resources/license/license-public.key` now holds a real public key (the owner replaced
  the placeholder). The two tests that hard-asserted the placeholder were updated by owner decision kept in the test
  resource `src/test/resources/license/placeholder-public.key` and an empty provider; this is the only deviation from
  "do not touch existing tests".
- LicenseService is registered through `LicenseConfig` as a @Bean, not with @Service, so the test `@Primary`
  ToggleableLicenseGate (in LicenseTestConfig) can replace it without bean-definition conflicts.
- The 30-minute re-check is started by the UI (startPeriodicChecks) rather than @PostConstruct, so Spring test contexts
  stay inert; it is not disabled by the dev flag (the dev flag only forces the state).
- A4 audit: the only raw SQL write in src/main is `BackupService` line ~120 `VACUUM INTO` (allowed: backups must work in
  every state); no `@Modifying`, `executeUpdate`, `createNativeQuery`, `JdbcTemplate` or `Statement.execute` write path
  exists. All business writes go through Hibernate entity saves and are covered by LicenseWriteGuard.
- The write guard throws inside a Hibernate listener; Spring/Hibernate wrap it, so the pure
  `ExceptionHandler.findBusinessException` walks the cause chain and the UI still shows the Uzbek key.
- The activation window reuses the primary Stage (its scene replaces the main scene), so the saved main-window
  geometry is NOT overwritten while BLOCKED (mainStage stays null until a usable window exists).
- The dev force state is read from the system property on every evaluate() so the snapshot tool can flip states at
  runtime; the javafx-maven-plugin `options` sets only -Dmebelsex.license.dev=true. The BLOCKED activation snapshot was
  taken by starting the app with the standard JAVA_TOOL_OPTIONS=-Dmebelsex.license.force=BLOCKED (no new env var was
  added to the app).
- SnapshotConfigurator.runOnly now receives the ApplicationContext (signature change only; existing steps untouched)
  and its license steps call LicenseService.evaluate() to switch states.
- Numeric inputs: a TextInputControl must never be modified from its own `textProperty()` listener (JavaFX throws
  `IllegalArgumentException: The start must be <= the end` on Backspace); ALL reformatting happens in one
  TextFormatter filter that sets range 0..controlText.length(), the new text and the caret. NumericFieldListenerScanTest
  guards this. The dev self-check (`MEBELSEX_SNAPSHOT_ONLY=inputs`, text log only, non-zero exit on failure) drives
  real fields on a real Stage and is the way to reproduce a JavaFX-only crash without the toolkit tests.

## 4. Decisions (accumulated, never delete)
- Versions: Java 17, Spring Boot 4.1.1, Hibernate 7.4.5.Final, hibernate-community-dialects (Boot-managed), JavaFX 21.0.12,
  sqlite-jdbc 3.53.4.0, ikonli 12.4.0 (javafx + feather pack), javafx-maven-plugin 0.0.8. Verify against pom.xml when in doubt.
- SQLite pragmas via JDBC URL: foreign_keys=on, journal_mode=wal, busy_timeout=5000; Hikari pool size 1.
- DB path: %APPDATA%\MebelSex\mebel.db. Dev override env MEBELSEX_DB_PATH.
- Snapshot tool (dev only): MEBELSEX_SNAPSHOT_DIR (output), MEBELSEX_DB_PATH (REQUIRED; temp DB deleted after run),
  MEBELSEX_WINDOW_SIZE (e.g. 1024x680). Run it once per new screen; snapshot folders are disposable.
- No login, no users, no roles, no warehouse module, no order_items table. One order = one price = one profit.
- agreedPrice > 0 on create and update. Cancelling an order never creates a refund. COMPLETED and CANCELLED orders are hidden in
  the list by default.
- Money: BigDecimal in Java, BIGINT so'm in SQLite, scale 0 HALF_UP. Quantity: BigDecimal as TEXT. Profit, debt and balances
  are never stored; they are computed from ACTIVE records.
- Frozen prices: catalog default price is a suggestion; a record copies unit_price and amount at creation and never changes.
- Records are never deleted: VOIDED + reason. Catalog items and categories are only deactivated.
- Expenses are allowed on every order except CANCELLED; a one-off expense (no catalog item) needs a description.
- profit_before_labor = agreed_price - expenses_total (Excel's first "Foyda jami"); percentage null when price is 0.
  Net profit (after wages) arrives with Phase 4.
- UI rules: interface fully Uzbek (Latin); texts only in messages_uz.properties; icons only Feather (fth-); fx:id element type
  must match the controller field type; never `prefWidth="Infinity"`; every dialog has an owner and closes with X and Esc;
  UI never touches JPA entities, services return DTO records built inside a transaction.
- Order date: orders carry an explicit date (orders.order_date, ISO TEXT, V2). Dashboard/list/card show it; the create form
  requires it and the list has its own column.
- Reports: profit reports select orders whose ORDER date is in the period (excluding CANCELLED) and then use ALL their ACTIVE
  expenses/work logs regardless of expense/work date. Cash-flow uses transaction dates (payments by payment_date, expenses by
  expense_date, salary by payment_date, all orders including cancelled). Profit and cash are different numbers.
- Report model: services return plain records; ReportTables maps them to a JavaFX-free ReportTable; the controller renders it
  generically. Excel export goes through ExcelExporter (Apache POI 5.2.3; poi-ooxml).
- Dark theme: the palette lives in the SAME application.css as a `.root.dark` block (no second stylesheet, no swapping);
  switching adds/removes the `dark` class on the scene root (or the DialogPane for alerts). The splash stays light. Dev
  override MEBELSEX_THEME=DARK is read before the first scene and never writes the stored setting. Known limitation: the
  native Windows title bar and native FileChooser/DirectoryChooser stay light.
- The light token `-mx-voided-text` was darkened from #9CA3AF to #8A919D: the WCAG test wants >= 3.0:1 for voided text and
  #9CA3AF only reaches 2.54:1 on white. This is the only light-theme colour changed in Part C.
- Stability Part A: the single-instance lock file lives next to the DB (`<dbfilename>.lock`); only the OS lock
  matters, the file may stay on disk. Startup-failure and already-running dialogs use the minimal JavaFX path
  (light theme) because Spring/ThemeManager do not exist there.
- Stability Part B: logs in `%APPDATA%\MebelSex\logs` (property `mebelsex.log.dir`), daily rotation, 14 days, 50 MB
  total cap, file `mebelsex.log`, console appender kept; dev runs log next to the temp DB. Diagnostics ZIP in
  `Documents\MebelSex\Diagnostika` (never contains the DB or backups).
- Stability Part C: a damaged DB is never replaced by an empty one. Launcher runs StartupDatabaseCheck after the
  lock; DAMAGED launches the recovery dialog (no Spring), which only swaps files when the user restores; the damaged
  files are moved to `replaced/mebel_damaged_<timestamp>.*`. A locked DB is not damage. Missing/zero-byte = first run.
- Dark theme Part 0 (text inputs): `.text-input` now sets `-fx-text-fill: -mx-text`, `-fx-prompt-text-fill: -mx-muted`,
  `-fx-highlight-fill: -mx-row-selected`, `-fx-highlight-text-fill: -mx-text`; disabled inputs use `-mx-disabled-text`.
  This changes the light placeholder colour slightly (Modena's derive(~#B3B3B3) -> `-mx-muted` #6B7280).
- License: Ed25519 via the JDK (Signature/KeyPairGenerator, no new dependency); file header `MEBELSEX-LICENSE-1` with a
  base64url (no padding) payload and signature; the payload is UTF-8 `k=v` LF lines (customer base64url-encoded).
- License binding: machine id = SHA-256 of `mebelsex-machine-v1:` + lower-cased MachineGuid, first 10 bytes, base32 with
  the safe alphabet (no I, O, 0, 1), formatted MX-XXXX-XXXX-XXXX-XXXX. Private key files live outside the project tree;
  the agent never generated a real key pair. Clock rollback tolerance = 36 h. Expiring within 14 days stays VALID
  (warning only); expired or clock rolled back = READ_ONLY; everything else = BLOCKED.
- License integration: BLOCKED blocks business writes but not Setting rows (backup status, window geometry, theme,
  last-seen); READ_ONLY keeps reports, Excel export, backup, restore and diagnostics working. The dev flag is set only
  in the javafx-maven-plugin options (never in jpackage); it logs a WARN and is shown as DEV in Sozlamalar.
- License UI: the banner lives in main.fxml inside the content area (hidden/managed=false by default) and is styled
  only through the new tokens -mx-banner-readonly-bg/-text and -mx-banner-warning-bg/-text (defined in BOTH theme
  blocks); the activation window and the Sozlamalar card use message keys only. When READ_ONLY the app does not
  disable buttons — an attempted save shows the existing Uzbek error dialog with license.error.readonly.

## 5. Verification
- Command: `mvn -l build.log clean verify` (UTF-8 log). Last result: Tests run: 178, Failures: 0, Errors: 0, Skipped: 0
  (BUILD SUCCESS, 2026-10-04).
- Dark-theme Part B (ThemeManager + persistence + toggles): Theme enum; SettingsService.loadTheme()/saveTheme() on the
  lazily created key `ui.theme` (default LIGHT); ThemeManager.loadFromSettings() runs before the main window is built;
  install(Scene)/install(DialogPane) add application.css and the `dark` class; setTheme() updates every registered
  scene/dialog on the FX thread and persists in a daemon thread. Wired in all `new Scene(` sites (MebelSexApplication
  main window, TaskRunner, OrderDialogs x2, CatalogController, CustomersController, EmployeeFormController,
  EmployeesController), all TextInputDialogs (OrderCardController x3, EmployeeCardController), SettingsController alerts
  and AlertHelper. Sozlamalar gained a first "Ko'rinish" card (Kunduzgi/Tungi ToggleButtons) and the sidebar a moon/sun
  button (fth-moon light / fth-sun dark). Tests: ThemeTest (3), ThemeSettingsIntegrationTest (3), ThemeWiringTest (2,
  source scan).
- Dark-theme Part C (palette + QA): `.root.dark` defines every `-mx-*` token plus the Modena keys (`-fx-base`,
  `-fx-background`, `-fx-control-inner-background`, `-fx-text-base-color`, `-fx-text-background-color`, `-fx-accent`,
  `-fx-focus-color`, `-fx-faint-focus-color`, `-fx-box-border`). ThemeTokensTest (2 tests, no toolkit) parses both token
  blocks, asserts the dark block overrides every light token and checks WCAG contrast (>= 4.5:1, >= 3.0:1 for
  voided/disabled text) in BOTH themes. Dev run `MEBELSEX_DB_PATH=target/snapdark.db MEBELSEX_SNAPSHOT_DIR=snapshots_dark
  MEBELSEX_THEME=DARK mvn -o -q javafx:run` completed (exit 0, 76 PNGs incl. live-switch T1-T4). Measured with a dev
  probe: dark dashboard/order-card ~97% dark pixels; settings cards `#1E293B`; the live switch changed the main window
  (mean luma 0.135 -> 0.784) and an open dialog (0.127 -> 0.955). PNGs 00-dashboard, 01-order-card, S1-settings,
  S3-restore-preview, T1/T3 were opened and looked correct. The white top/right band in the settings snapshot is a
  snapshot-only artifact (transparent regions are filled white by SnapshotParameters; the same band is white in the light
  run), not a UI defect. Popups and the Alert confirm dialog were NOT captured/inspected (see known issue 9).
- Dark-theme Part A (token refactor, light theme unchanged): application.css now defines ~50 `-mx-*` looked-up colour
  tokens on `.root` (light values = the previous literals); every selector uses tokens, the only literal left is the
  neutral-black `rgba(0,0,0,0.05)` drop shadow. FXML inline colours and `StatusBadge` inline colours were moved to
  stylesheet classes (`sidebar-version`, `sidebar-logo-icon`, `muted-icon`, `accent-icon`, `primary-button .ikonli-font-icon`,
  `advance-section`, `warning-box`, `badge-<status>`). NoHardcodedColorsTest (3 tests) scans application.css outside the
  token blocks, all FXML and all `com.mebelsex.ui` sources for hex/rgb/rgba/`Color.`/`-fx-*-color` literals.
- Visual regression check: baseline `snapshots_theme_before/` (72 PNGs, taken 21:33-21:36, before the first CSS edit at
  21:42:50) vs `snapshots_theme_after/` (72 PNGs). `ImageDiffTool` compared all 72: 67 pixel-identical (0.000%), worst
  5.443%. All 5 non-zero files were analysed pixel-by-pixel (bounding box + colour pairs) and are explained: 00-dashboard
  (4.204%), 01-order-card (5.441%), 02-order-card-payment (5.443%) differ only in a single table row (F3F6FB = `-mx-row-hover`,
  i.e. the run-time mouse-hovered row, one row high); the two S1b settings-scrolled shots (1.619% / 2.235%) differ only by
  scattered antialiasing of the recent-backups table content (timestamps/sizes). No colour regression; both snapshot
  folders were then deleted.
- Phase 4 Part A tests (JUnit, temp DB, no GUI): WorkLogIntegrationTest (11) and EmployeeBalanceIntegrationTest (7):
  amount rounding, frozen unit price/amount, VOIDED excluded, void reason required, CANCELLED order rejected,
  inactive employee/operation rejected, quantity/price validation, as-of balance boundary, negative balance, payments
  larger than the balance, totalOwedToWorkers counts only positive balances, laborTotal/netProfit/percent (null at
  price 0), unique names, deactivated employee still shown by name, dashboard workersOwed.
- Automated coverage: converters, formatters, Flyway migration content, order/payment/expense business rules, frozen prices,
  order card / edit / dashboard read models, catalog rules, MoneyParser, QuantityParser, i18n keys/encoding/apostrophes,
  enum labels, FXML structure (declaration, controller fields, handlers, field types, icon pack, no Infinity), CSS inner-control
  rules, AlertHelper button data, search race counter, ui-does-not-import-entities scan.
- Phase 4 Part B tests: SignedMoneyParserTest (signed parse/format); FxmlFilesTest and I18NValidationTest now also cover the
  new FXML (catalog/operation_form, catalog/position_form, employees, employees_form, employees/employee_card,
  order-card Ishlar tab) and the new message keys.
- Dev snapshots: `MEBELSEX_DB_PATH=target/snap4.db MEBELSEX_SNAPSHOT_DIR=snapshots_work mvn -o -q javafx:run` ran to
  completion (exit 0) and wrote all PNGs, incl. C1-ishchilar, C2-order-works, C3-work-voided, C4-xulosa-labor,
  C5-employee-card, C6-dashboard at 1280x800 and 1024x680. Only C1-ishchilar-1280x800.png was visually inspected
  (header, date, search, table and money formatting looked correct).
- Phase 5 tests: OrderDateMigrationTest (V2 backfill from created_at, both timestamp formats); OrderDateIntegrationTest
  (explicit orderDate kept through create/getCard/getForEdit/listItems, default today, update); ReportPeriodTest (period helpers
  + validation); ReportTablesTest (mapper titles/tones/orderId); ReportServiceIntegrationTest (~6, temp DB): period report selects
  by order date, excludes CANCELLED, boundary last day, excludes out-of-period order, ignores VOIDED, totals = sum of rows, percent
  null at price 0; cash-flow daily rows include cancelled but exclude voided; expense grouping + subtotals; employee
  closing = opening + earned - paid; customer report + onlyDebtors; ExcelExporterTest (writes temp xlsx, reopens with POI:
  sheet name, title/period cells, bold header, numeric money, bold totals; sheetName strips illegal chars and caps 31).
- Dev snapshots (Phase 5): `MEBELSEX_DB_PATH=target/reports.db MEBELSEX_SNAPSHOT_DIR=snapshots_reports mvn -o -q javafx:run`
  ran to completion and wrote (each at 1280x800 and 1024x680):
  R1-report-month = Davr hisoboti (Shu oy); R2-report-month = Pul harakati (Shu oy); R3-report-month = Xarajatlar (Shu oy);
  R4-report-month = Ishchilar (Shu oy); R5-report-month = Mijozlar (Shu oy); R1-report-range = Davr hisoboti (o'tgan oy
  boshi .. bugun); R2-report-range = Pul harakati (custom); R3-report-range = Xarajatlar (custom); R4-report-range = Ishchilar (custom);
  R5-report-range = Mijozlar (custom); R6-dashboard-profit = Bosh sahifa with "Bu oy foyda". R1-R6 at 1280x800 and the
  1024x680 variants were inspected; the column, filter-row and summary-card clipping found there was fixed and the run repeated
  (final run clean, no exceptions).
- Phase 6 Part A tests (backend, temp DB + temp folders; scheduler disabled by `mebelsex.backup.scheduler.enabled=false` in
  the test profile): BackupPolicyTest (startup/scheduled/close decisions); SettingsServiceIntegrationTest (defaults, company/backup
  round-trip, invalid HH:mm, keep_days range, last_success/error, default folder); BackupServiceIntegrationTest (day-folder name and
  path, valid copy with integrity_check and matching counts, folder with apostrophe, retention deletes only old dated folders and keeps
  Tiklashdan_oldin + unrelated files, keep_days 0 keeps all, unwritable target -> BusinessException with no .tmp left, listRecent
  newest-first incl. same-minute _2 suffix, dataChangedSince ignores settings-only changes); RestoreServiceIntegrationTest (rejects
  text/truncated/newer-Flyway files, accepts a valid backup with matching counts, scheduleRestore stages restore_pending.db and leaves
  the live DB untouched); PendingRestoreApplierTest (swap keeps a replaced copy + success marker, failure keeps live DB + failure marker);
  RestoreFlowTest (backup -> modify -> stage -> apply restores original data and writes the replaced copy).
- Phase 6 Part B verification: `MEBELSEX_DB_PATH=target/snap6b.db MEBELSEX_SNAPSHOT_DIR=snapshots_settings
  mvn -o -q javafx:run` completed (exit 0, 68 snapshots, no exception). The tool points backup.folder at
  target/snapshot_backups and runs no FileChooser/DirectoryChooser. The S1-S4 1280x800 PNGs were opened and inspected:
  S1 top + S1b scrolled, S2 after a manual backup, S3 restore preview dialog, S4 dashboard card. Defects found and fixed:
  the folder row could clip its buttons (field now shrinks, buttons on their own row) and the restore dialog header was
  mislabelled. One snapshot run was repeated after the fixes (final run clean).
- NOT covered by tests: JavaFX runtime behaviour (screens, dialogs, layout, snapshot numbers). Verified by the owner by hand and by dev snapshots.

- Stability Part 0: `.text-input` token rule + disabled tokens + `.list-view .list-cell`; ApplicationCssTest gained
  `textInputsUseTokensForTextAndPrompt` (parses the `.text-input` block), ThemeTokensTest gained the
  disabled-text/disabled-surface >= 3.0 check. Build: 181 tests, 0 failures. Dev snapshots (focused mode
  `MEBELSEX_SNAPSHOT_ONLY=orders,new-order`): `snapshots_p0_dark/p0-orders.png`, `snapshots_p0_dark/p0-new-order.png`
  (MEBELSEX_THEME=DARK) and `snapshots_p0_light/p0-orders.png`. The preview compositor produced no frames this
  session, so the PNGs could not be opened; instead a dev pixel probe counted exact token colours: the placeholder
  region is `-mx-muted` in BOTH themes (identical 458 px of #94A3B8 dark / #6B7280 light) and typed text is
  `-mx-text` (#E5E7EB dark). Text inputs NOT visually verified by the agent.

- Stability Part A: `SingleInstanceGuard` holds an OS lock on `<db>.lock` for the process lifetime; the Launcher takes
  it before Spring (0 s wait normally, 15 s when started with `--relaunch`), shows a minimal Uzbek "Dastur allaqachon
  ochiq" dialog and exits 0 when another copy runs. Any Spring/Flyway boot exception now shows "Dastur ishga tushmadi"
  with a short reason (`StartupFailureMapper`: newer-schema / DB-locked / generic), buttons "Log papkasini ochish" and
  "Chiqish", full stack trace logged, exit code 1. Tests: SingleInstanceGuardTest (5), StartupFailureMapperTest (5).
  The launcher/startup dialogs use the minimal JavaFX path (light theme) because Spring/ThemeManager are not available.

- Stability Part B: file logging in `%APPDATA%\MebelSex\logs` (Launcher sets `mebelsex.log.dir`; dev runs log next to
  the temp DB, `MEBELSEX_LOG_DIR` overrides). Daily rotation, 14 days, 50 MB cap, `mebelsex.log`, console appender kept.
  Startup line (version/Java/OS/DB/flyway/backup scheduler/theme) + shutdown uptime line. Sozlamalar "Dastur haqida"
  gained "Log papkasini ochish" and "Diagnostika faylini yaratish" (DiagnosticsBuilder ZIP in
  `Documents\MebelSex\Diagnostika`, `MEBELSEX_DIAGNOSTICS_DIR` overrides; last 3 logs + info.txt, never the DB) run in
  a Task; success dialog shows the path with "Faylni ko'rsatish". Console output under src/main replaced with SLF4J
  (dev-only SnapshotConfigurator/ImageDiffTool keep theirs). Tests: DiagnosticsBuilderTest (3), LogbackConfigTest (2),
  NoConsoleOutputTest (1). Sozlamalar "Dastur haqida" snapshot: the preview compositor produced no frames, so the PNG
  was not opened; a dev layout probe confirmed both new buttons visible and full size (169.6x41 / 212x41 at y=712.8,
  versionLabel visible) — card NOT visually verified by the agent.

- Stability Part C: `BackupFileInspector` extracted from RestoreService (RestoreService still passes its existing tests);
  `StartupDatabaseCheck` runs in the Launcher after the lock and before Spring (OK / FIRST_RUN / DAMAGED / LOCKED).
  `backup-folder.txt` written after every successful backup (+ via `BackupService.rememberBackupFolder`). New
  `RecoveryApplication` (minimal JavaFX, no Spring, light theme) lists the 5 newest valid backups and, on restore, copies
  the chosen file to `restore_pending.db` then `PendingRestoreApplier.apply(dataDir, <db>, "mebel_damaged_")` immediately,
  then relaunches. Tests: StartupDatabaseCheckTest (5), BackupFileInspectorTest (6), BackupFolderMarkerTest (3),
  RecoveryApplyTest (3), BackupFolderMarkerIntegrationTest (1). Dev snapshots `snapshots_pC/pC-recovery.png` (540x471,
  800 colours) and `pC-startup-failure.png` (479x164, 649 colours) were rendered but NOT opened (preview compositor
  produced no frames) — both dialogs NOT visually verified by the agent.
- Stability Part D: one enum-label implementation (`components.EnumLabels.getLabel`, the `ui.EnumLabels` bean delegates)
  and one message accessor (`common.Messages`; `ResourceBundle.getBundle(` now appears only inside Messages — MessagesTest).
  Hardcoded controller strings moved to keys ("Xatolik" -> ui.error, "Hammasi" -> orders.filter.all, "Jami qarz" ->
  orders.total_debt, TaskRunner progress title -> ui.in_progress); UiStringsTest scans com.mebelsex.ui for inline
  Label/Button/title/header/content/prompt literals (empty strings allowed). Main-window geometry is saved on stop
  (`ui.window.x/y/width/height/maximized`) and restored only when it still intersects a connected screen, else the
  1280x800 default centred; min 1024x680. Tests: MessagesTest (3), UiStringsTest (1), WindowGeometryTest (6),
  WindowSettingsIntegrationTest (3). A focused dev boot (`MEBELSEX_SNAPSHOT_ONLY=nothing`) logged the startup line
  ("Started Mebel Sex 0.0.1 | Java 17 ... | flyway 2 | backup scheduler off | theme LIGHT") and a clean shutdown line
  with no exception.
- Table alignment fix: column headers were centred while data cells were left-aligned (`-fx-alignment: center-left`) and the
  `money-col` columns right-aligned, so rows did not line up under their headers in any table. `application.css` now sets
  `.table-view .table-cell`, `.table-view .column-header .label` and `money-col` to `-fx-alignment: center`, and the
  per-column inline `-fx-alignment` overrides in orders_screen.fxml were removed. `ApplicationCssTest.
  tableCellsAndHeadersAreHorizontallyCentred` guards the CSS (0 left/right in any table-cell/money-col rule). Verified on a
  real dev render (`MEBELSEX_SNAPSHOT_ONLY=orders`, dark theme): measured text-pixel centres of header vs first data row now
  match (№ 51/51, Zakaz sanasi 125.5/127.5, Mijoz 377/378, Kelishilgan narx 802/802, To'langan 952.5/951.5), versus the
  owner's screenshot where they were ~47-69 px apart.
- License core + generator: `mvn -l build.log clean verify` -> Tests run: 312, Failures: 0, Errors: 0, Skipped: 0,
  BUILD SUCCESS (2026-10-05). 83 new pure tests: MachineIdTest (10, incl. the golden code MX-2UJ9-8Q3J-Z7EK-5R7F for
  guid 4f2a9c1e-7b3d-4a58-9e21-c0d6b7a8f913), MachineGuidParserTest (6), LicenseFormatTest (12), LicenseKeysTest (6),
  ResourcePublicKeyProviderTest (2), LicenseVerifierTest (15), ClockGuardTest (7), LicenseStatusCalculatorTest (10),
  LicenseCliTest (14), LicenseSecretScanTest (1). License tests were also run alone
  (`mvn -o -q test -Dtest='com.mebelsex.license.**'`: 83, 0 failures). NOT verified: the `machine` command and a real
  reg.exe run (no registry access in the test run; only the pure output parser is covered); no real key pair generated.
- License integration backend: `mvn -l build.log clean verify` -> Tests run: 341, Failures: 0, Errors: 0, Skipped: 0,
  BUILD SUCCESS (2026-10-05). 29 new tests: LicenseServiceTest (21: status matrix incl. expiring 14/15, expired, clock
  rollback from settings and from license.state, READ_ONLY/BLOCKED reasons, no-key, no-machine-id, wrong machine, bad
  signature, malformed, last-seen persistence and never-backwards, import success/.bak and four rejected imports, dev
  flag, requireWritable), LicenseWriteGuardIntegrationTest (2 Spring tests: READ_ONLY and BLOCKED block customer/order/
  expense/work-log writes with an unwrapped BusinessException, reads + Setting writes + backup still work, VALID works
  again), ExceptionCauseUnwrapperTest (4), LicenseReasonKeysTest (1), plus the updated key/provider tests. Also run
  alone (`mvn -o -q test -Dtest='com.mebelsex.license.**'`: 112, 0 failures).
- License integration UI: `mvn -l build.log clean verify` -> Tests run: 341, Failures: 0, Errors: 0, Skipped: 0,
  BUILD SUCCESS (2026-10-05). The new FXML (activation), the banner in main.fxml, the Sozlamalar card and all new
  message keys are covered by the existing FxmlFilesTest, I18NValidationTest, ThemeTokensTest, NoHardcodedColorsTest
  and UiStringsTest (all pass).
- Four dev snapshots at 1280x800 (MEBELSEX_SNAPSHOT_ONLY=license-banner,license-settings; the activation run with
  JAVA_TOOL_OPTIONS=-Dmebelsex.license.force=BLOCKED) were rendered AND visually opened in the browser preview:
  lic-activation.png (title "Litsenziyani faollashtirish", machine id MX-BT42-FY59-HJWQ-JV6V + "Nusxa olish", red
  "Litsenziya fayli topilmadi.", "Litsenziya faylini tanlash...", "Chiqish"), lic-banner-readonly.png (red banner
  "Litsenziya muddati tugagan. Dastur faqat ko'rish rejimida." + Faollashtirish, main window behind it),
  lic-banner-expiring.png (amber "Litsenziya 10 kundan keyin tugaydi."), lic-settings.png (Litsenziya card: Faol/DEV,
  mijoz DEV, 05.10.2027 (365 kun qoldi), machine id + copy, license id, import button). No visual defect found; the
  banner live-switch was proven by the run log flipping READ_ONLY -> EXPIRING and the two different banner colours.
  Snapshot folders and probes were deleted afterwards.

## 5b. Verification (numeric input fix, 2026-10-06)
- Reproduced on the ORIGINAL code with the dev self-check: `MEBELSEX_DB_PATH=target/inputcheck.db MEBELSEX_SNAPSHOT_DIR=inputcheck MEBELSEX_SNAPSHOT_ONLY=inputs mvn -o -q javafx:run` -> 4 failures, all `java.lang.IllegalArgumentException: The start must be <= the end at ...javafx.scene.control.TextInputControl.getText(TextInputControl.java:496)` (MoneyField + SignedMoneyField, backspace loop and delete loop), exit 1.
- After the fix the same self-check passes 44/44 operations, FAILURES: 0, exit 0 (`inputcheck/inputs-check.txt`); programmatic `setText("5000000")` ends as "5 000 000" for MoneyField/SignedMoneyField.
- `mvn -l build.log clean verify` -> Tests run: 372, Failures: 0, Errors: 0, Skipped: 0, BUILD SUCCESS (2026-10-06). 31 new pure tests: MoneyInputFormatterTest (14, scripted + 8000-op randomized invariants), SignedMoneyInputFormatterTest (8), QuantityInputFormatterTest (8), NumericFieldListenerScanTest (1 source scan).
- Audited and left unchanged (no self-mutating pattern, no crash): QuantityField (filter only, now via QuantityInputFormatter), UzDatePicker editor (StringConverter), SearchField (debounce only), and all controller `textProperty()` listeners (OrderCardController x5, EmployeeCardController x1 - they only read the field and update labels).

## 6. Known issues / TODO
1. Dark theme: popup windows (ComboBox dropdown list, DatePicker calendar, tooltip/context menu) and the Alert confirm
   dialog were not captured/inspected; the Modena overrides in `.root.dark` should cover them, but this is unverified.
   The native Windows title bar and native FileChooser/DirectoryChooser stay light by design. The stability dialogs
   (recovery, startup failure, already running) use the minimal JavaFX light path by design (no Spring/ThemeManager there).
2. GUI behaviour (screens, dialogs, layout, snapshot numbers) is not verified by automated tests. The dev snapshots made
   this session could not be opened (the preview compositor produced no frames); GUI behaviour not verified by agent.
3. The report table scrolls horizontally for the wide Davr hisoboti (12 columns); check the owner is happy with that
   instead of a fixed compact layout.
4. V1 seeds snake_case settings keys (company_name, company_phone, backup_folder, backup_time); the app uses dotted keys
   (company.name, company.phone, backup.folder, backup.time, ...) created lazily with defaults, so the V1 rows stay unused.
   V1 is immutable; the dotted rows do not hurt.
5. The close backup runs through TaskRunner; if it fails, the shared error dialog is shown before the app exits (the exit is
   not blocked once the user dismisses it). Automatic backups never show a modal on success.
6. Backups are plain, unencrypted SQLite files; moving them off the machine (Telegram Saved Messages) is manual by decision.
7. A few FXML files still contain hardcoded Uzbek text (for example the initial "Jami qarz: 0 so'm" total label in
   orders_screen.fxml); the controller overwrites it, but it should be moved to a message key.
8. messages_uz.properties uses a mix of CRLF and lone-CR line endings in older blocks (valid for java.util.Properties,
   and the i18n test reads them correctly); new keys are appended with CRLF.
9. License is wired in. `src/main/resources/license/license-public.key` now holds the owner's real public key, so the
   shipped build verifies licenses; the placeholder branch is covered by the test resource
   `src/test/resources/license/placeholder-public.key`. Still open: the import -> main-window transition and the
   READ_ONLY save-error dialog were not exercised end to end (only the write guard is covered by tests).
10. The license CLI runs only on the owner's PC and is not covered by the GUI; it has no code signing or obfuscation.
11. Restore while READ_ONLY: RestoreService only inspects and stages files, so a restore is not blocked by the write
   guard; the swap happens before Spring on the next start. Not covered by a license-specific test.
12. The activation window and the banner were verified only as static snapshots; the FileChooser interaction and a
   real license import at startup were not driven end to end.

## 7. Open questions
- Interface script: Latin is used; the client's Excel uses Cyrillic. Ask the workshop owner which script workers prefer.

## 8. Next step
Owner: confirm by hand that the activation window, the banner and the Sozlamalar license card behave on a real machine
(the public key is already in place). Then the next agent task is the RELEASE step: jpackage packaging plus a release
gate that fails the build/packaging if `license-public.key` is still the placeholder or if `-Dmebelsex.license.dev` is
configured for the packaged app (the dev flag must never reach jpackage). Still open from before: the owner's manual
confirmation of the stability dialogs (Parts 0/A/B/C/D).

## 9. Changelog (append-only)
- 2026-10-03: Phase 1 foundation (Spring Boot + JavaFX + SQLite + Flyway), V1 schema, converters, i18n, tests.
- 2026-10-03: Phase 2A backend: customers, orders, payments, finance service, integration tests.
- 2026-10-03: Phase 2B-1: UI shell, design tokens, Mijozlar screen, TaskRunner, AlertHelper, snapshot tool.
- 2026-10-03/04: Phase 2B-2a: Zakazlar list, order form, status dialog, reusable components.
- 2026-10-04: Recovery after a broken agent session; FXML load fixes; FxmlFilesTest; UI defects fixed. 63 tests.
- 2026-10-04: Phase 2B-2b order card + dashboard; stubs; LazyInitializationException fix (OrderEditDto). 69 tests.
- 2026-10-04: UI polish (CSS inner controls, enum labels, tables, tabs). 73 tests.
- 2026-10-04: Phase 3 Xarajatlar: expense catalog, order expenses (frozen prices), QuantityField, Katalog, Xarajatlar tab, Xulosa. 95 tests.
- 2026-10-04: Phase 4 Part A (backend): employees/positions/operation types/work logs/salary payments, EmployeeBalanceService, labor + net-profit formulas, dashboard workersOwed. 113 tests.
- 2026-10-04: Phase 4 Part B (screens): Katalog Ish turlari/Lavozimlar tabs, Ishchilar + employee card, order-card Ishlar tab, Xulosa wages/net profit, dashboard Ishchilarga qarzimiz, SignedMoneyField, dev snapshots. 116 tests.
- 2026-10-04: Phase 5 Hisobotlar: order date (migration V2), report backend (5 reports), Hisobotlar screen, Excel export (Apache POI 5.2.3), dashboard "Bu oy foyda", dev snapshots. 138 tests.
- 2026-10-04: Phase 6 Part A (backend): SettingsService/AppSettings, BackupService (VACUUM INTO + verify + retention), BackupPolicy, BackupScheduler, RestoreService, PendingRestoreApplier, RelaunchHelper, Launcher swap + close hook. 167 tests.
- 2026-10-04: Phase 6 Part B (screens): real Sozlamalar screen (company/backup/restore/about), manual backup + show-file, recent-backup table with show/restore actions, restore preview + restart, startup restore-result dialog, dashboard "Oxirgi backup" card, dev snapshots in snapshots_settings/. 167 tests.
- 2026-10-04: Dark theme Part A: "-mx-*" colour token system in application.css (light values unchanged), inline colours
  removed from FXML/Java, ImageDiffTool dev tool, NoHardcodedColorsTest, before/after snapshot diff (67/72 identical,
  remainder = hover/data only). 170 tests.
- 2026-10-04: Dark theme Part B: Theme enum + ThemeManager (live switching, ui.theme persisted through SettingsService),
  theme wired into every Scene/dialog/Alert, Sozlamalar "Ko'rinish" toggles, sidebar moon/sun button. 178 tests.
- 2026-10-04: Dark theme Part C: `.root.dark` palette + Modena overrides, ThemeTokensTest (token coverage + WCAG contrast),
  MEBELSEX_THEME dev switch, dark snapshots + live-switch check, light `-mx-voided-text` darkened for contrast. 180 tests.
- 2026-10-05: Stability Part 0: `.text-input` text/prompt/highlight token rules (dark placeholder was invisible),
  disabled-input tokens, focused MEBELSEX_SNAPSHOT_ONLY dev mode, CSS/contrast tests. 181 tests.
- 2026-10-05: Stability Part C: BackupFileInspector (RestoreService delegates), StartupDatabaseCheck, backup-folder.txt,
  RecoveryApplication (recovery dialog + immediate apply via PendingRestoreApplier), launcher DB check, tests,
  two dev snapshots. 215 tests.
- 2026-10-05: Stability Part B: logback-spring.xml (daily rotation, 14 days, 50 MB, mebelsex.log, console kept),
  Launcher sets `mebelsex.log.dir` (dev logs next to the temp DB), startup/shutdown log lines, DiagnosticsBuilder +
  Sozlamalar "Dastur haqida" log-folder/diagnostics buttons, console output replaced with SLF4J. 197 tests.
- 2026-10-05: Stability Part A: SingleInstanceGuard (lock = `<db>.lock`), Launcher lock + `--relaunch` 15 s wait +
  already-running dialog, Uzbek startup-failure dialog with reason/open-logs/exit, StartupFailureMapper, RelaunchHelper
  passes `--relaunch`. 191 tests.
- 2026-10-05: Stability Part D: single enum-label implementation (components.EnumLabels, bean delegates), common.Messages
  accessor (only place loading ResourceBundle), hardcoded controller strings moved to keys + UiStringsTest, main-window
  geometry saved/restored via WindowGeometry + WindowState (`ui.window.*`). 228 tests.
- 2026-10-05: Table alignment fix: table data cells centred horizontally to line up with the centred column headers in all
  tables (base `.table-cell`, `money-col` and column-header label set to `center`; orders_screen per-column overrides removed);
  ApplicationCssTest guard; verified on a dev render with a pixel probe. 229 tests.
- 2026-10-05: License core (package com.mebelsex.license: machine id, payload/format, Ed25519 keys, verifier, clock guard,
  status calculator) + owner generator CLI (LicenseCli, tools/license.cmd, tools/README-license.md), .gitignore key rules
  and a private-key secret-scan test. 312 tests.
- 2026-10-05: License integration Part A (backend): LicensePaths, LicenseGate, LicenseService (evaluate/import/
  last-seen persistence/periodic re-check/dev flag), LicenseConfig, LicenseWriteGuard (Hibernate pre-write gate),
  ExceptionHandler cause-chain unwrapping, license message keys, 29 tests. 341 tests.
- 2026-10-05: License integration Part B (UI): activation window (activation.fxml + ActivationController), content-area
  banner (tokens + live update), Sozlamalar "Litsenziya" card, startup BLOCKED/READ_ONLY routing, dev-flag pom option,
  snapshot steps + 4 snapshots verified visually. 341 tests.
- 2026-10-06: Numeric input fix: MoneyField/SignedMoneyField reformat inside one TextFormatter filter (the old
  self-mutating text listener threw IllegalArgumentException "The start must be <= the end" on Backspace in
  "Yangi zakaz"); pure MoneyInputFormatter/SignedMoneyInputFormatter/QuantityInputFormatter; dev `inputs`
  self-check (text log, non-zero exit on failure) reproduces the crash before and passes after; source-scan guard. 372 tests.