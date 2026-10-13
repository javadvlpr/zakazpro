# ZakazPro - Project State (HANDOFF)

Full history: docs/HANDOFF_ARCHIVE.md (read only if a question needs it).

Rules for agents: sections 3, 4 and 9 are append-only (never delete earlier lines). Update sections 1, 2, 5, 6, 7 and 8
IN PLACE at the end of every session. No marketing words. Report only what you ran.

## 1. Status
- Done: Phase 1 (foundation), 2A (customers/orders/payments backend), 2B-1/2 (UI shell, Mijozlar, Zakazlar list +
  order form + status dialog, order card, real Bosh sahifa), 3 (expenses, Katalog, Xarajatlar tab, Xulosa), 4
  (employees/positions/operation types, work logs, salary payments, worker balances, labor + net profit, Ishchilar),
  5 (order date + V2, 5 reports, Excel export), 6 (settings, backup/restore, scheduler).
- Done: dark theme (token system, ThemeManager live switch, Ko'rinish toggles, dark palette).
- Done: stability (single instance, file logging + diagnostics, startup DB safety + recovery dialog, cleanup).
- Done: table alignment (all table data cells centred under their headers).
- Done: license core + owner CLI/generator; license integration (activation window, content-area banner,
  Sozlamalar "Litsenziya" card, LicenseWriteGuard).
- Done: numeric input crash fix (MoneyField/SignedMoneyField reformat inside one TextFormatter filter; Backspace in
  "Yangi zakaz" no longer throws `IllegalArgumentException: The start must be <= the end`).
- Done: rebrand to ZakazPro - the visible name, window/sidebar/splash, data/log/backup folder + file names and the
  log/Excel/diagnostics identity all come from `common.AppInfo`; internal identifiers stay unchanged (see section 3).
- Done: installer Part A - version 1.0.0 (pom + AppInfo), source icon imported, IconBuilder (.ico + window PNGs),
  AppIcons wired into ThemeManager and the pre-Spring windows.
- Done: installer Part B - `Launcher --smoke-test`: headless self check (Spring, no JavaFX) writing
  `smoke-result.txt` next to the smoke DB; refuses to run unless MEBELSEX_DB_PATH is an absolute .db path outside
  the real data folder (exit 2, nothing written).
- Done: installer Part C - thin jar + `target/dist/lib` (maven-dependency-plugin), `InstallerConfig`,
  `InstallerBuilder` (jdeps -> jlink -> jpackage app-image -> smoke test -> release gate -> exe -> portable ZIP),
  `ReleaseGate`, `installer/build-installer.cmd`. A real packaged app-image passed the smoke test and the gate.
- Done: installer Part D - `installer/README-installer.md` (Uzbek, 12 sections) + this HANDOFF.
- Installer task COMPLETE (A-D). The `.exe` installer step itself was NOT run: WiX Toolset 3.x is not installed on
  this PC (the driver reports it and continues, exit 0).
- Last build: `mvn -l build.log clean verify` = 443 tests, 0 failures, BUILD SUCCESS (2026-10-06).
- Owner manually confirmed: Mijozlar, Zakazlar, order create with/without advance, order edit, order card payments,
  Bosh sahifa; Phase 3 after Part A.

## 2. What exists
FXML (src/main/resources/fxml): main, dashboard, customers/{customers,customers_form},
orders/{orders_screen,order_form,status_dialog,order_card}, catalog/{catalog,category_form,item_form,operation_form,
position_form}, employees/{employees,employees_form,employee_card}, reports, settings, activation. CSS:
css/application.css holds every colour as a `-mx-*` token on `.root` (light) and `.root.dark` (dark overrides +
Modena keys). Messages: i18n/messages_uz.properties. Flyway: `V1__init.sql` (12 tables + a few seeds) and
`V2__order_date.sql`; applied migrations are immutable, schema changes need a NEW V<n>__*.sql, no demo data.

Java (com.mebelsex):
- root: Launcher (DB path/env MEBELSEX_DB_PATH, single-instance lock, --relaunch, already-running + startup-failure
  dialogs), MebelSexApplication (splash, Spring start, global exception handler, showMainWindow/showActivation).
- common: BaseEntity, BusinessException, Formatters, MoneyConverter, QuantityConverter, LocalDateConverter,
  TimestampConverter, BooleanIntConverter, RecordStatus, SingleInstanceGuard, StartupFailureMapper, FileExplorer,
  AppInfo, DiagnosticsBuilder, Messages (the only ResourceBundle loader).
- customers: Customer/Repository/Service. orders: Order, OrderStatus, OrderRepository/Service, OrderFinanceService
  (paid/debt/expenses/profit/labor/netProfit - the only place), OrderListItem/CardDto/EditDto, DashboardService.
  payments: Payment, PaymentKind/Method, Repository/Service, PaymentRow.
- expenses: ExpenseCategory/Item/Expense + repositories, CategoryRow/ItemRow/ExpenseRow, ExpenseCatalogService,
  ExpenseService.
- employees: SalaryType, Position, Employee + repositories, PositionRow/EmployeeRow/EmployeeBalanceRow,
  PositionService, EmployeeService, EmployeeBalanceService (the only balance logic).
- work: OperationType, WorkLog, SalaryPayment + repositories, OperationTypeService, WorkLogService (frozen price),
  SalaryPaymentService.
- report: JavaFX-free model (Cell/Column/Row/ReportTable...), ReportPeriod, ReportService (read-only),
  ReportTables, ExcelExporter (Apache POI).
- backup: BackupReason/Result/Status/FileRow, RestorePreview/ApplyResult, BackupPolicy, BackupService (VACUUM INTO),
  BackupScheduler, RestoreService, PendingRestoreApplier, BackupFileInspector, StartupDatabaseCheck,
  BackupFolderMarker, RecoveryBackups, RelaunchHelper.
- settings: Setting/Repository, SettingsService (typed keys incl. ui.theme, ui.window.*, license.lastSeen),
  AppSettings, WindowState.
- license: MachineIdSource/Exception/WindowsMachineGuidSource, MachineId, LicensePayload, LicenseFormat, LicenseKeys,
  PublicKeyProvider/ResourcePublicKeyProvider, LicenseVerifier, LicenseCheck, ClockGuard, LicenseStatus,
  LicenseStatusCalculator; integration: LicensePaths, LicenseGate, LicenseService (evaluate/import/periodic/
  dev-force), LicenseConfig, LicenseWriteGuard; tool/LicenseCli.
- ui: MainController (sidebar + theme toggle + license banner), ViewLoader, DashboardController, TaskRunner,
  AlertHelper, ExceptionHandler, EnumLabels, ThemeManager/Theme, AppIcons (window icons from /icons/),
  SnapshotConfigurator (dev), ImageDiffTool (dev).
- ui.components: MoneyField, MoneyParser, SignedMoneyField, SignedMoneyParser, QuantityField, QuantityParser,
  MoneyInputFormatter, SignedMoneyInputFormatter, QuantityInputFormatter, UzDatePicker, SearchField, StatusBadge,
  EnumLabels.
- ui.customers/orders/catalog/employees/reports/settings: one controller per screen (or card/dialog);
  ActivationController drives the license activation window.
- release: InstallerVersion (normalizes the pom version for jpackage), IconBuilder (owner PNG -> zakazpro.ico +
  app-{256,64,32}.png), ConsoleOutput (the only console writer for build-time release tools), SmokeTestGuard
  (pure checks for the `--smoke-test` environment), SmokeTestRunner (headless 8-check self test + smoke-result.txt),
  InstallerConfig (installer/installer.properties + validation), InstallerBuilder (the packaging driver; pure
  command builders + the steps), ReleaseGate (file-based release checks + summary).
- Launcher `--smoke-test`: handled before the single-instance lock and before any JavaFX class is touched.
- resources: logback-spring.xml (daily rotation, 14 days, 50 MB); icons/app-{256,64,32}.png (window icons);
  license/license-public.key (the owner's REAL key); tools/license.cmd + tools/README-license.md.
- installer/: installer.properties (identity, upgrade UUID, JVM options, modules, icon paths),
  build-installer.cmd (pre-clean + `mvn clean verify` + driver), README-installer.md (Uzbek owner doc),
  assets/zakazpro-icon.png (owner's source), assets/zakazpro.ico (generated, 16-256 px).
- pom.xml: `maven-dependency-plugin:copy-dependencies` (runtime scope -> target/dist/lib) and
  spring-boot-maven-plugin `<classifier>exec</classifier>` so the MAIN artifact stays a thin jar
  (mebelsex-1.0.0.jar) and the fat one is mebelsex-1.0.0-exec.jar.

Visible identity: `common.AppInfo` is the single source (`PRODUCT_NAME`/`SLUG`/`DATA_DIR_NAME`/`DOCUMENTS_DIR_NAME`/
`LOG_FILE_BASENAME`/`BACKUP_FILE_PREFIX`/`VERSION` + `windowTitle`). Data inside `%APPDATA%\ZakazPro`, logs under it,
default backup/diagnostics `Documents\ZakazPro\{Backup,Diagnostika}`, log `zakazpro.log`, backups
`zakazpro_<stamp>.db` (old `mebel_*.db` files are still recognised).

Behaviour: customer CRUD + search; orders (create + optional advance in one transaction, edit, status, filters);
order card tabs To'lovlar/Ishlar/Xarajatlar/Xulosa (Excel-like quick-add, void + reason, live sums, net profit);
Katalog (activate/deactivate); Ishchilar + employee card (balances, work logs, wage payments); Hisobotlar (5 reports,
period filters, Excel export); Sozlamalar (company/backup/restore/about + Litsenziya card); Bosh sahifa cards;
license banner + activation window; numeric fields format while typing.

## 3. Deviations (append-only)
- spring-boot-maven-plugin and javafx-maven-plugin mainClass = com.mebelsex.Launcher (not an Application subclass).
- Java 17 is intentional; no Java 18+ features. Packages use plural names.
- Tests use spring-boot-starter-test and spring-boot-starter-flyway-test; each run uses a temp SQLite file under target/.
- IDENTITY insert: Order.agreedPrice must be BigDecimal (MoneyConverter).
- Never write files with PowerShell redirection; messages_uz.properties was once corrupted with NUL bytes.
- Dialogs always have an owner; AlertHelper picks the active window and applies the theme to every DialogPane.
- FXML guard rules (FxmlFilesTest): exact XML declaration, controller field/fx:id type match, Feather-only icons,
  no prefWidth/prefHeight "Infinity", correct relative stylesheet depth (@../css top-level, @../../css one level deep).
- Rule: smallest edits, NEVER rewrite a working class/FXML/CSS (an agent once rewrote controllers and broke the build).
- UiDoesNotImportEntityAssociationsTest: ui must not import Order/Payment entities (services return DTO records).
- ComboBox/DatePicker inner editors need CSS reset rules (ApplicationCssTest); reports table uses
  UNCONSTRAINED_RESIZE_POLICY + per-cell preferred width, clears items before rebuilding columns, wrapping summary cards.
- V1 `active` columns are INTEGER -> @Convert(BooleanIntConverter) on every active field.
- License CLI visible Uzbek text lives inside LicenseCli (documented exception, not in messages_uz.properties).
- License secret scan skips target/ and dot-directories; the two sources that must mention the private header are
  allow-listed by name.
- `license-public.key` holds the owner's REAL key; the two placeholder-assuming tests were changed by owner decision
  (test resource placeholder-public.key + empty provider) - the only deviation from "do not touch existing tests".
- LicenseService is a @Bean in LicenseConfig (not @Service) so the test @Primary ToggleableLicenseGate wins.
- The 30-minute re-check starts from the UI (startPeriodicChecks), not @PostConstruct; the dev flag only forces the state.
- The only raw SQL write in src/main is BackupService `VACUUM INTO` (allowed).
- LicenseWriteGuard throws inside a Hibernate listener; ExceptionHandler.findBusinessException walks the cause chain.
- The activation window reuses the primary Stage; mainStage stays null while BLOCKED so saved geometry is not overwritten.
- Dev force state is read from the system property on every evaluate(); pom javafx options set only -Dmebelsex.license.dev=true.
- SnapshotConfigurator.runOnly takes the ApplicationContext (signature change only; existing steps untouched).
- Numeric inputs: NEVER modify a TextInputControl from its own `textProperty()` listener (JavaFX throws
  `IllegalArgumentException: The start must be <= the end`); all reformatting happens in ONE TextFormatter filter
  (range 0..controlText.length() + new text + caret). NumericFieldListenerScanTest guards this. The dev self-check
  (`MEBELSEX_SNAPSHOT_ONLY=inputs`, text log only, non-zero exit on failure) reproduces JavaFX-only crashes.
- Rebrand: only VISIBLE names changed. Internal identifiers keep the old spelling on purpose because renaming them
  would invalidate Machine IDs and existing licenses: the packages `com.mebelsex`, class names (incl.
  MebelSexApplication), the Maven coordinates, the license header `MEBELSEX-LICENSE-1`, the key file headers, the
  Machine ID salt `mebelsex-machine-v1:`, the DB file name `mebel.db`, the `mebelsex.*` system properties and the
  restore/replace markers `mebel_replaced_`/`mebel_damaged_` inside the data folder.
- ProductNameTest scans messages/FXML/CSS/XML, Java string literals under src/main and pom.xml for the old visible
  names; it allow-lists three files that keep `mebel_` only to RECOGNISE old backups (BackupService,
  RecoveryApplication) and the internal markers (PendingRestoreApplier). SQL is not scanned: V1 is immutable, so its
  header comment still says the old name.
- BackupService scans backup day folders by `.db` extension (not by prefix) and parseStamp tolerates both the new
  `zakazpro_` and the legacy `mebel_` prefixes, so old backups stay listed/restorable.
- Installer: NoConsoleOutputTest allow-lists ONE more file, `release/ConsoleOutput.java`, because the packaging
  driver runs from `installer\build-installer.cmd` where logback is not on the classpath (all its output goes
  through that single class; no other file in the release package writes to the console). Keep it in mind when
  adding console output to new release classes.
- Installer: the pom copies only the DEPENDENCIES into target/dist/lib. The thin application jar is copied into
  target/dist by InstallerBuilder (`dependency:copy` would need the artifact installed in the local repository and
  `copy-resources` only handles directories).
- Installer: jdeps gets an explicit classpath list (thin jar + every jar in target/dist/lib, joined with the OS path
  separator) instead of the literal `--class-path "target/dist/lib/*"`; jdeps does not expand the `*` wildcard, and
  the list stays far below the Windows command-line limit.
- Installer: `build-installer.cmd` does a retrying pre-clean of target\installer, target\runtime, target\dist and
  target\smoke before `mvn clean verify`, and InstallerBuilder retries its own deletions (20 x 3 s). Reason: this
  project lives inside OneDrive and Windows/Defender/OneDrive briefly lock freshly built files, which made
  `mvn clean` fail with "Failed to delete ...\ZakazPro.exe" (reproduced twice).
- Installer: the smoke test LOADS javafx.scene.control.TextField (initialize=false) and only initializes
  Platform/Image/Feather; `Control.<clinit>` calls PlatformImpl.runLater -> "Toolkit not initialized" on any
  headless JVM, which is normal and not a packaging problem (reproduced in a plain JVM probe).
- `installer/README-installer.md` never spells the private-key header: LicenseSecretScanTest scans .md files too
  and failed the build when the exact string was written there.

## 4. Decisions (append-only)
- Versions: Java 17, Spring Boot 4.1.1, Hibernate 7.4.5.Final, hibernate-community-dialects, JavaFX 21.0.12,
  sqlite-jdbc 3.53.4.0, ikonli 12.4.0 (Feather only, `fth-`), javafx-maven-plugin 0.0.8, Apache POI poi-ooxml 5.2.3.
- SQLite via JDBC URL: foreign_keys=on, journal_mode=wal, busy_timeout=5000; Hikari pool size 1.
- DB path %APPDATA%\MebelSex\mebel.db; dev override MEBELSEX_DB_PATH.
- Snapshot tool (dev only): MEBELSEX_SNAPSHOT_DIR (output), MEBELSEX_DB_PATH (required, temp DB), MEBELSEX_WINDOW_SIZE,
  MEBELSEX_THEME, MEBELSEX_SNAPSHOT_ONLY.
- No login/users/roles, no warehouse module, no order_items. One order = one price = one profit.
- agreedPrice > 0 on create/update; cancelling never creates a refund; COMPLETED/CANCELLED hidden in the list by default.
- Money: BigDecimal in Java, BIGINT so'm, scale 0 HALF_UP. Quantity: BigDecimal as TEXT. Profit/debt/balances are
  never stored, always computed from ACTIVE records.
- Frozen prices: catalog default_price is a suggestion; a record copies unit_price + amount and never changes.
- Records are never deleted (VOIDED + reason); catalog items/categories are only deactivated.
- Expenses allowed on every order except CANCELLED; a one-off expense (no catalog item) needs a description.
- profit_before_labor = agreed_price - expenses_total; net_profit = that - labor_total; percent null when price is 0.
- UI fully Uzbek (Latin); texts only in messages_uz.properties; Feather icons; fx:id element type must match the
  controller field; every dialog has an owner and closes with X and Esc; UI never touches JPA entities.
- Order date: orders carry an explicit date (orders.order_date, ISO TEXT, V2); form requires it, list/card show it.
- Reports: profit reports select orders whose ORDER date is in the period (excluding CANCELLED) and then use ALL
  their ACTIVE expenses/work logs; cash-flow uses transaction dates and includes cancelled orders. Profit != cash.
- Report model: services return records; ReportTables maps them to a JavaFX-free ReportTable; Excel via ExcelExporter.
- Dark theme: the palette is the same application.css as a `.root.dark` block; switching toggles the `dark` class on
  the scene root / DialogPane; splash stays light; MEBELSEX_THEME overrides for dev and never persists. Native title
  bar and FileChooser stay light (known limitation).
- The light token `-mx-voided-text` is #8A919D (darkened for WCAG >= 3.0 contrast on white).
- Stability: single-instance lock file is `<db>.lock` (only the OS lock matters); logs in %APPDATA%\MebelSex\logs,
  diagnostics ZIP in Documents\MebelSex\Diagnostika (never the DB); a damaged DB is never replaced by an empty one
  (recovery dialog swaps files only on request, keeping the old files under replaced/).
- Dark text inputs: `.text-input` sets token text/prompt/highlight/disabled colours (light placeholder changed slightly).
- License: Ed25519 via the JDK (no new dependency); header `MEBELSEX-LICENSE-1` + base64url payload + signature;
  machine id = SHA-256 of `mebelsex-machine-v1:` + lower-cased MachineGuid, first 10 bytes, safe base32, MX-XXXX-XXXX.
  Clock tolerance 36 h; expiring within 14 days stays VALID (warning); expired/clock-rollback = READ_ONLY; else BLOCKED.
- License integration: BLOCKED blocks business writes but not Setting rows; READ_ONLY keeps reports/Excel/backup/
  restore/diagnostics working; the dev flag is set only in the javafx-maven-plugin options (never jpackage).
- License UI: banner lives in main.fxml (hidden by default) styled only via the -mx-banner-* tokens (both themes);
  READ_ONLY does not disable buttons - an attempted save shows the Uzbek license.error.readonly dialog.
- Numeric inputs: grouping is a space every three digits from the right; leading zeros collapse; at most 12 digits;
  no-digit input is rejected; paste keeps only digits; caret sits after the same digit count; Backspace/Delete over a
  separator removes the neighbouring digit.
- Product name (owner-approved): visible product name = `ZakazPro`, slug `ZakazPro`. Only visible names change; the
  internal identifiers listed in section 3 stay. `AppInfo.VERSION` must stay equal to the pom version (AppInfoTest
  reads pom.xml).
- The product targets ANY order-based workshop (furniture, doors/windows, sewing, ...): no visible text may assume
  furniture; the existing screen/field wording (Mijoz, Zakaz) is unchanged.
- After the first client install, the data folder name must never change without a migration. The owner renames his
  own dev folders by hand; there is no migration code.
- Installer: release version 1.0.0 (pom + AppInfo.VERSION), installer `--type exe`, per-user install
  (`--win-per-user-install`), Start-menu group `ZakazPro` + desktop shortcut, no directory chooser. Uninstall keeps
  `%APPDATA%\ZakazPro` and `Documents\ZakazPro` (jpackage never touches them).
- Installer: ONE stable upgrade UUID lives in `installer/installer.properties` (`app.upgrade.uuid`, generated once
  with UUID.randomUUID). A newer installer with the same UUID upgrades the old installation; it must never change.
- Installer: the packaged app runs from the CLASSPATH - thin jar + all runtime jars under `app\lib` - launched via
  `com.mebelsex.Launcher`. The bundled runtime is a jlink image of exactly the jdeps modules + the fixed base list in
  `InstallerBuilder.BASE_MODULES` (which always contains jdk.localedata, java.sql, java.desktop).
- Installer: the packaged JVM options live ONLY in `installer/installer.properties`
  (`-Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8 -Xmx768m -XX:+UseSerialGC`), one `--java-options` each; no
  `mebelsex.license.*` may ever appear there and ReleaseGate fails the build if it does.
- Installer: the release gate stops the build before the exe step on a placeholder/missing public key, leaked
  private-key text, `*.key/*.lic/*.db/*.log/*-wal/*-shm` or a `snapshots` folder in dist/app-image, a license dev
  flag in `app\ZakazPro.cfg`, a non-numeric version, a broken `.ico`, an invalid upgrade UUID or a bundled runtime
  without java.sql/java.desktop/jdk.localedata.
- Installer: the smoke test is the acceptance test of a package - GUI-less, temp DB only, `RESULT=OK|FAIL` in
  `smoke-result.txt`, exit 0/1. `target/installer/ZakazPro-<version>-portable.zip` is written for quick testing.
- Installer: the installer UI stays English (jpackage limitation, documented); the application UI stays 100% Uzbek.

## 5. Verification
- Last result: `mvn -l build.log clean verify` = Tests run: 443, Failures: 0, Errors: 0, Skipped: 0, BUILD SUCCESS (2026-10-06).
- Installer Parts C+D: +32 tests - InstallerConfigTest (9), InstallerBuilderCommandTest (10), ReleaseGateTest (13).
- Installer C4, real run of the shipped entry point: `cmd /c installer\build-installer.cmd --skip-exe` ->
  "Tests run: 443, Failures: 0, BUILD SUCCESS", then jdeps -> 22 modules, jlink -> target/runtime (55 MB),
  jpackage app-image -> target/installer/ZakazPro (137 MB), smoke test of the PACKAGED app:
  `RESULT=OK` with `file.encoding=UTF-8`, `uz.month=oktabr`, `machine.id=MX-BT42-FY59-HJWQ-JVV6`,
  `max.heap.mb=742` (the -Xmx768m option reached the app), 2 migrations applied, settings round trip, license BLOCKED
  with the real public key; gate "7 gate checks, 0 failed -> PASS"; portable ZIP 114 MB; exit 0.
- Installer C4, WiX fallback: `java -cp target/classes com.mebelsex.release.InstallerBuilder` (no --skip-exe) ->
  "WiX Toolset 3.x was not found, so only the app-image was built." + install hint, gate PASS, exit 0 (the .exe was
  NOT produced or tested - WiX is not installed here).
- The packaged `app\ZakazPro.cfg` was inspected: 91 app.classpath entries (thin jar + 90 lib jars) and the four
  JavaOptions, no license option.
- `Launcher --smoke-test` end to end from the classpath (pre-packaging): without `-Dfile.encoding=UTF-8` ->
  `RESULT=FAIL file-encoding: ... file.encoding=Cp1252`, exit 1; with it -> `RESULT=OK`, exit 0. Without
  MEBELSEX_DB_PATH -> message + exit 2, no file written.
- Not verified: installing the .exe, upgrading an existing installation, uninstalling, the packaged GUI on a clean
  machine, and the app-image on a machine without the JDK.
- Installer Part B: +14 tests - SmokeTestGuardTest (8), SmokeTestReportTest (4), SmokeTestRunnerIntegrationTest (2,
  real Spring context against a temp DB). Real run from the classpath (no packaging):
  `java -cp "target/classes;target/cp.txt" com.mebelsex.Launcher --smoke-test` with MEBELSEX_DB_PATH set, no
  -Dfile.encoding -> `RESULT=FAIL file-encoding: IllegalStateException: file.encoding=Cp1252`, exit 1; with
  `-Dfile.encoding=UTF-8` -> `RESULT=OK`, exit 0. All other checks OK (2 migrations applied, settings round trip,
  license BLOCKED on the fresh DB with the real public key, machine.id=MX-..., uz.month=oktabr, JavaFX/ikonli
  classes, /icons/app-256.png). Without MEBELSEX_DB_PATH: console message + exit 2, no file written.
- Installer Part A: +18 tests - InstallerVersionTest (7), IconBuilderTest (9), AppIconsTest (2). AppInfoTest stays green
  with version 1.0.0. Generated assets: `installer/assets/zakazpro.ico` (178 874 bytes, 7 images) and the three
  `src/main/resources/icons/app-*.png` from the owner's 1254x1254 PNG. IconBuilder only reads the source PNG and
  writes into the repo, so the real APPDATA/Documents folders are untouched.
- Rebrand: `mvn -l build.log clean verify` = 379 tests, 0 failures (2026-10-06). 7 new tests: AppInfoTest (5: slug
  ASCII + valid Windows folder name, log/backup names, windowTitle, VERSION == pom), ProductNameTest (1 source/
  resource scan), BackupServiceIntegrationTest.listRecentRecognisesLegacyAndNewBackupPrefixes (1). Updated per rule 4
  (old visible names): BackupServiceIntegrationTest expected backup prefix mebel_ -> zakazpro_; LogbackConfigTest log
  file mebelsex.log -> zakazpro.log. The run log shows the real new backup name
  `zakazpro_2026-10-06_11-17.db`.
- Coverage (summary): converters + formatters; Flyway content (V2 backfill); order/payment/expense business rules;
  frozen prices; order card/edit/dashboard read models; catalog rules; Money/Quantity/SignedMoney parsers;
  MoneyInputFormatter (scripted + 8000-op randomized invariants), SignedMoneyInputFormatter, QuantityInputFormatter;
  NumericFieldListenerScanTest (source scan); i18n keys/encoding/apostrophes; enum labels; FXML structure; CSS inner
  controls + tokens + WCAG contrast; text-input tokens; theme/settings round-trips; window geometry; single instance;
  startup-failure mapping; logging config; diagnostics; backup policy/verify/retention; restore flows; reports;
  Excel export; license machine id/format/keys/verifier/clock/status/service/write-guard/CLI/secret scan.
- JavaFX runtime behaviour (screens, dialogs, layout, snapshot numbers) is NOT covered by tests; verified by the owner
  by hand and by dev runs/self-checks.
- Dev self-check (numeric input fix): before = 4 failures (`IllegalArgumentException: The start must be <= the end`),
  after = 44/44 operations PASS, FAILURES: 0, exit 0 (`inputcheck/inputs-check.txt`).
- UI parts NOT visually verified by the agent: popup windows (ComboBox list, DatePicker calendar, tooltips, context
  menus) and the Alert confirm dialog; the native title bar/FileChooser; the stability dialogs (recovery,
  startup-failure, already-running); the license import -> main-window transition and the READ_ONLY save-error dialog.
  The activation window, banner and Sozlamalar license card WERE verified as static snapshots (browser preview).

## 6. Known issues / TODO
1. Popup windows and the Alert confirm dialog under the dark theme are unverified (Modena overrides should cover them).
2. GUI behaviour is not covered by automated tests (see section 5 for what was NOT visually verified).
3. The Davr hisoboti report scrolls horizontally (12 columns); confirm the owner accepts that.
4. V1 seeds snake_case settings keys (company_name...) while the app uses dotted keys created lazily; the V1 rows stay unused.
5. The close backup runs through TaskRunner; a failure shows the error dialog before exit (exit not blocked after dismiss).
6. Backups are plain, unencrypted SQLite files; moving them off the machine is manual by decision.
7. A few FXML files still contain hardcoded Uzbek text (e.g. the initial "Jami qarz" label); the controller overwrites it.
8. messages_uz.properties mixes CRLF and lone-CR in older blocks (valid for Properties); new keys use CRLF.
9. License: import -> main-window transition and the READ_ONLY save-error dialog were not exercised end to end
   (only the write guard is covered by tests).
10. The license CLI runs only on the owner's PC, is not covered by the GUI and has no code signing/obfuscation.
11. Restore while READ_ONLY is not blocked by the write guard (RestoreService only stages; the swap happens before Spring).
12. The activation window and banner were verified only as static snapshots; a real license import at startup was not driven.
13. RESOLVED (installer Part A): the icon exists - installer/assets/zakazpro.ico + /icons/app-*.png, applied by AppIcons.
14. The smoke test needs `-Dfile.encoding=UTF-8` to pass, which is exactly the JVM option the installer must pass;
    a plain `java -cp ... com.mebelsex.Launcher --smoke-test` on this PC fails that one check (Cp1252).
15. javafx.scene.control classes cannot be INITIALIZED headless (Control's static block calls the FX toolkit), so
    the smoke test loads them without initializing and initializes Platform/Image/Feather as the live proof.
16. The installer `.exe` step is UNTESTED: WiX Toolset 3.x is not installed on this PC. The app-image, smoke test,
    gate and portable ZIP are all verified; only `jpackage --type exe` + the installed program are not.
17. The installer UI is English (jpackage limitation). The .exe is unsigned -> Windows SmartScreen warns the client
    on first run (README explains "More info -> Run anyway"). No auto-update; upgrades are manual.
18. The project folder is inside OneDrive: freshly built files are sometimes locked for a while, which broke
    `mvn clean` once. Both the .cmd (retrying pre-clean) and InstallerBuilder (retrying delete, 20 x 3 s) now handle
    it, but pausing OneDrive syncing is the safest remedy if the build still reports a lock.
19. The `vendor` shown by the installer and in "Programs and features" is `ZakazPro` (a placeholder company name);
    the owner should decide the real business name (section 7).

## 7. Open questions
- Interface script: Latin is used; the client's Excel uses Cyrillic. Ask the owner which script the workers prefer.
- Installer vendor name: `installer/installer.properties` -> `app.vendor` is currently `ZakazPro`. What company name
  should the installer and "Programs and features" show? (It can be changed any time before shipping; changing the
  upgrade UUID is NOT allowed.)
- Should the owner buy a code-signing certificate? Without it every client sees the SmartScreen warning.

## 8. Next step
1. Owner: install WiX Toolset 3.14, run `installer\build-installer.cmd` (without `--skip-exe`) and install the
   resulting `.exe` on a CLEAN machine (no JDK): check the icon in the Start menu/taskbar, the Uzbek UI, a backup,
   and that uninstalling keeps `%APPDATA%\ZakazPro` + `Documents\ZakazPro`.
2. Then the performance task: a synthetic 10-year database (orders/payments/work_logs/expenses) to tune `-Xmx` if
   needed and to check the reports stay fast.
3. Still open from earlier sessions: manual confirmation of the stability dialogs and the license import flow.

## 9. Changelog (append-only)
- 2026-10-03: Phase 1 foundation (Spring Boot + JavaFX + SQLite + Flyway), V1 schema, converters, i18n, tests.
- 2026-10-03: Phase 2A backend: customers, orders, payments, finance service, integration tests.
- 2026-10-03: Phase 2B-1: UI shell, design tokens, Mijozlar screen, TaskRunner, AlertHelper, snapshot tool.
- 2026-10-03/04: Phase 2B-2a: Zakazlar list, order form, status dialog, reusable components.
- 2026-10-04: Recovery after a broken agent session; FXML load fixes; FxmlFilesTest; 63 tests.
- 2026-10-04: Phase 2B-2b order card + dashboard; LazyInitializationException fix (OrderEditDto); 69 tests.
- 2026-10-04: UI polish (CSS inner controls, enum labels, tables, tabs); 73 tests.
- 2026-10-04: Phase 3 Xarajatlar (catalog, frozen prices, QuantityField, Xulosa); 95 tests.
- 2026-10-04: Phase 4 Part A backend (employees/positions/work logs/salary, balances, formulas); 113 tests.
- 2026-10-04: Phase 4 Part B screens (Ishchilar, employee card, Ishlar tab, SignedMoneyField); 116 tests.
- 2026-10-04: Phase 5 Hisobotlar (order date V2, 5 reports, Excel export); 138 tests.
- 2026-10-04: Phase 6 Part A backend (settings, backup/restore, scheduler); 167 tests.
- 2026-10-04: Phase 6 Part B screens (Sozlamalar, restore dialogs, dashboard backup card); 167 tests.
- 2026-10-04: Dark theme Part A (token system, ImageDiffTool, NoHardcodedColorsTest); 170 tests.
- 2026-10-04: Dark theme Part B (ThemeManager, persistence, toggles); 178 tests.
- 2026-10-04: Dark theme Part C (`.root.dark` palette, ThemeTokensTest, MEBELSEX_THEME); 180 tests.
- 2026-10-05: Stability Part 0 (dark text inputs, focused dev mode, CSS tests); 181 tests.
- 2026-10-05: Stability Part C (StartupDatabaseCheck, BackupFileInspector, recovery dialog); 215 tests.
- 2026-10-05: Stability Part B (logback, diagnostics ZIP, startup/shutdown log lines); 197 tests.
- 2026-10-05: Stability Part A (SingleInstanceGuard, startup-failure dialog, relaunch); 191 tests.
- 2026-10-05: Stability Part D (single enum-label, Messages accessor, window geometry); 228 tests.
- 2026-10-05: Table alignment fix (centred cells + ApplicationCssTest guard); 229 tests.
- 2026-10-05: License core + owner generator CLI (machine id, format, Ed25519, verifier, clock guard); 312 tests.
- 2026-10-05: License integration Part A backend (LicenseService/Gate/WriteGuard, cause-chain unwrap); 341 tests.
- 2026-10-05: License integration Part B UI (activation window, banner, Sozlamalar card, snapshots); 341 tests.
- 2026-10-06: Numeric input fix (TextFormatter filter + pure formatters, dev `inputs` self-check, source-scan guard); 372 tests.
- 2026-10-06: Installer Parts C+D: pom (copy-dependencies + exec classifier, thin jar), installer.properties
  (identity + stable upgrade UUID + JVM options + modules), InstallerBuilder (jdeps/jlink/jpackage app-image/smoke
  test/release gate/portable ZIP, exe when WiX is present), ReleaseGate, build-installer.cmd (retrying pre-clean),
  README-installer.md (Uzbek); the packaged app-image passed the smoke test and the gate; 443 tests.
- 2026-10-06: Installer Part B: `Launcher --smoke-test` + SmokeTestGuard (refuses a non-temp DB, exit 2) +
  SmokeTestRunner (8 isolated checks, smoke-result.txt, exit 0/1); verified from the classpath in both encoding
  modes. 411 tests.
- 2026-10-06: Installer Part A: version 1.0.0 (pom + AppInfo), InstallerVersion, IconBuilder (.ico with 16-256 px +
  window PNGs), AppIcons wired centrally and into the splash/already-running/recovery/startup-failure windows; 397 tests.
- 2026-10-06: Rebrand to ZakazPro: `common.AppInfo` visible-name constants, window/sidebar/splash/about, data dir
  `%APPDATA%\ZakazPro`, Documents\ZakazPro folders, `zakazpro.log`, `zakazpro_*.db` backups (old `mebel_*.db` still
  recognised), Excel/diagnostics identity, pom name, AGENTS.md; AppInfoTest + ProductNameTest + legacy-prefix test. 379 tests.
