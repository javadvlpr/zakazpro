# ZakazPro — Windows o'rnatuvchi (installer)

Bu hujjat dasturni **mijozga berish uchun** `.exe` yasashni tushuntiradi.

## 1. Nima kerak

| Kerak | Nega | Tekshirish |
| --- | --- | --- |
| **JDK 17** | `jpackage` va `jlink` shu bilan keladi | `java -version` -> `17.0.x` |
| **WiX Toolset 3.x** (3.14) | faqat `.exe` yasash uchun | `candle.exe` va `light.exe` PATH da |

- WiX 3.14: https://github.com/wixtoolset/wix3/releases
  **Diqqat:** WiX 4 yoki 5 **ishlamaydi** — JDK 17 `jpackage` faqat WiX 3 bilan ishlaydi.
- WiX bo'lmasa ham davom etish mumkin: faqat app-image yasaladi (`--skip-exe`).

## 2. Yasash (build)

```
installer\build-installer.cmd --skip-exe
```

Skript nima qiladi:

1. `mvn -l build.log clean verify` — hamma testlar o'tishi shart.
2. `jdeps` bilan kerakli JDK modullarini topadi (+ doimiy asosiy ro'yxat: `jdk.localedata`,
   `java.sql`, `java.desktop` va h.k.), `jlink` bilan `target\runtime` yasaydi.
3. `jpackage --type app-image` bilan `target\installer\ZakazPro` yasaydi.
4. **Smoke test**: tayyor `ZakazPro.exe --smoke-test` ni GUI ochmasdan ishlatib tekshiradi.
5. **Release gate**: paket to'g'ri yig'ilganini tekshiradi.
6. WiX bo'lsa `--type exe` bilan o'rnatuvchi yasaydi; bo'lmasa ogohlantiradi va to'xtamaydi.
7. Portativ ZIP yozadi.

Qo'shimcha kalitlar:

| Kalit | Ma'nosi |
| --- | --- |
| `--skip-exe` | faqat app-image (WiX kerak emas) |
| `--keep-temp` | smoke test papkasini o'chirmaydi |
| `--verbose` | xato bo'lsa to'liq stack trace chiqaradi |
| `--help` | yordam |

## 3. Natijalar qayerda

| Fayl | Nima |
| --- | --- |
| `target\installer\ZakazPro\ZakazPro.exe` | app-image — o'rnatmasdan ham ishlaydi |
| `target\installer\ZakazPro-1.0.0-portable.zip` | portativ ZIP (sinash uchun qulay) |
| `target\installer\ZakazPro-1.0.0.exe` | o'rnatuvchi (WiX bo'lsa) |
| `target\dist\` | `mebelsex-1.0.0.jar` + `lib\` (barcha kutubxonalar) |
| `target\runtime\` | faqat kerakli JDK modullari (jlink) |
| `target\smoke\<vaqt>\smoke-result.txt` | smoke test natijasi |

## 4. Smoke test nimani tekshiradi

Tayyor paket GUI ochmasdan ishga tushadi va quyidagilarni tekshiradi:

1. Flyway: hamma migratsiya qo'llangan, sxema to'g'ri (`flyway_schema_history`).
2. Sozlamalar: yozish/o'qish ishlaydi.
3. Litsenziya dvigateli xatosiz ishlaydi; ochiq kalit fayli joyida.
   Yangi bo'sh bazada holat `BLOCKED` bo'ladi — bu **normal**, xato emas.
4. Machine ID (Windows `reg.exe` orqali) `MX-XXXX-XXXX-XXXX-XXXX` ko'rinishida hisoblanadi.
5. O'zbek tili: `menu.settings = Sozlamalar`, `o'`/`g'` harflari to'g'ri, oy nomi
   `oktabr` (bu `jdk.localedata` moduli paketga kirganini isbotlaydi), `file.encoding=UTF-8`.
6. JavaFX va ikonli kutubxonalari yuklanadi.
7. `/icons/app-256.png` ishlaydi.
8. Java versiyasi, vendor va maksimal xotira yozib qo'yiladi.

Natija fayli: `target\smoke\...\smoke-result.txt`. Oxirgi qatori `RESULT=OK` yoki
`RESULT=FAIL <sabab>`; dastur kodi 0 yoki 1. **`RESULT=OK` bo'lmasa paket mijozga berilmaydi.**

## 5. Release gate (build shu holatlarda to'xtaydi)

- `license-public.key` placeholder bo'lsa yoki haqiqiy Ed25519 kalit bo'lmasa.
- Paketda (`target\dist`, app-image) maxfiy kalit (private key) matni yoki `*.key`, `*.lic`,
  `*.db`, `*.log`, `*-wal`, `*-shm` fayllari yoxud `snapshots` papkasi bo'lsa.
- `app\ZakazPro.cfg` ichida `mebelsex.license.dev` / `mebelsex.license.force` bo'lsa.
- Versiya raqamli bo'lmasa yoki ikonka buzuq bo'lsa.
- `installer.properties` dagi upgrade UUID yaroqsiz bo'lsa.
- Paketdagi Java'da `java.sql`, `java.desktop` yoki `jdk.localedata` moduli bo'lmasa.

## 6. Mijoz kompyuteriga o'rnatish

1. `ZakazPro-1.0.0.exe` ni nusxa ko'chirib ishga tushiring.
2. Windows **SmartScreen** ogohlantirishi chiqadi ("Windows protected your PC") — buning sababi
   `.exe` imzolanmagan (code signing yo'q). **More info** -> **Run anyway** ni bosing.
3. O'rnatish **faqat shu Windows foydalanuvchisi uchun** bo'ladi: administrator huquqi kerak emas,
   papka tanlash oynasi chiqmaydi.
4. Start menyusida va ish stolida **ZakazPro** yorlig'i paydo bo'ladi.
5. Ishga tushirish: Start menyu -> ZakazPro.

## 7. Ma'lumotlar qayerda saqlanadi

- Baza: `%APPDATA%\ZakazPro\mebel.db`
- Loglar: `%APPDATA%\ZakazPro\logs\`
- Backup: `Documents\ZakazPro\Backup`, diagnostika: `Documents\ZakazPro\Diagnostika`

**O'chirish (uninstall) bu papkalarni o'chirmaydi** — mijoz ma'lumotlari saqlanadi va yangi
o'rnatish eski bazani ko'radi.

## 8. Yangilash (upgrade)

1. Versiyani oshiring: `pom.xml` dagi `<version>` **va** `AppInfo.VERSION` ni bir xil qilib
   o'zgartiring (masalan `1.0.1`). Testlar shu ikki qiymat teng bo'lishini tekshiradi.
2. Installerni qayta yasang: `installer\build-installer.cmd`.
3. Yangi `.exe` ni eskisining ustiga o'rnatsa bo'ladi — buning uchun
   `installer/installer.properties` dagi `app.upgrade.uuid` **hech qachon o'zgartirilmaydi**.
   Uni o'zgartirsangiz, Windows buni boshqa dastur deb hisoblab, ikkinchi nusxa o'rnatadi.
4. Ma'lumot papkasi nomini (`%APPDATA%\ZakazPro`) ham o'zgartirmang — baza yo'qoladi.

## 9. Yangi mijozga litsenziya berish

1. Mijoz dasturni birinchi marta ochadi -> **faollashtirish oynasi** chiqadi va **Machine ID**
   (`MX-....`) ni ko'rsatadi. Uni nusxa olib oling.
2. O'z kompyuteringizda `tools\license.cmd` yordamida shu Machine ID uchun `license.lic` yasang.
3. `license.lic` ni mijozga yuboring; u faylni faollashtirish oynasida yuklaydi (import).
4. Litsenziya qabul qilinsa dastur asosiy oynaga o'tadi.
5. Mijozning eski bazasi bo'lsa: **Sozlamalar -> Tiklash** orqali backupni tiklang (dastur avval
   joriy bazadan avtomatik backup oladi).

## 10. JVM sozlamalarini o'zgartirish

Hammasi `installer/installer.properties` faylida:

- `app.jvm.options` — har bir so'z alohida `--java-options` bo'lib o'tadi.
  Hozir: `-Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8 -Xmx768m -XX:+UseSerialGC`.
- `-Xmx` ni oshirsangiz katta bazada qulayroq ishlaydi (masalan `-Xmx1024m`), lekin kompyuterda
  kamida shuncha bo'sh xotira bo'lishi kerak.
- `jdk.extra.modules` — qo'shimcha JDK modul kerak bo'lsa (vergul bilan).
- **`mebelsex.license.*` hech qachon qo'shilmaydi** — release gate buildni to'xtatadi.

## 11. Ikonkani qayta yasash

`installer/assets/zakazpro-icon.png` (1254x1254, kvadrat) o'zgartirilsa:

```
java -cp target\classes com.mebelsex.release.IconBuilder installer\assets\zakazpro-icon.png installer\assets
```

Bu `installer/assets/zakazpro.ico` (16, 24, 32, 48, 64, 128, 256 px) ni yasaydi. Chiqqan
`app-256.png`, `app-64.png`, `app-32.png` fayllari `src\main\resources\icons\` ga ko'chiriladi
(oyna va vazifalar panelidagi ikonka uchun).

## 12. Ma'lum cheklovlar

- **O'rnatuvchining oynalari ingliz tilida** (jpackage cheklovi). Dasturning o'zi to'liq o'zbekcha.
- **Imzo yo'q**: SmartScreen ogohlantiradi; antivirus ba'zan "noma'lum dastur" deb belgilaydi.
- **Windows 10/11 (64-bit)** uchun. Windows 7/8 sinalmagan.
- **Per-user o'rnatish**: faqat joriy foydalanuvchi uchun; "hamma foydalanuvchi" varianti yo'q.
- **Avtomatik yangilanish yo'q**: yangi `.exe` ni qo'lda o'rnatasiz.
