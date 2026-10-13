# Litsenziya generatori (faqat egasi uchun)

Bu vosita oflayn litsenziya faylini (`license.lic`) yaratadi va tekshiradi.
Dastur ichida faqat **public** kalit turadi; **private** kalit faqat sizda bo'ladi.

## 1. Bir marta yig'ish

Loyiha papkasida:

```
mvn -q compile
```

Keyin barcha buyruqlarni `tools\license.cmd` orqali ishlatasiz.

## 2. Bu kompyuterning Machine ID sini bilish

```
tools\license.cmd machine
```

Natija: `Machine ID: MX-ABCD-EFGH-IKLM-NPQR` — mijoz shu kodni yuboradi.

## 3. Kalit juftini yaratish (bir marta, loyihadan tashqarida)

```
tools\license.cmd keygen --out-dir C:\MebelSexKeys
```

Papkada ikkita fayl paydo bo'ladi:

- `license-private.key` — **maxfiy**, hech kimga bermang, ikki xavfsiz joyda nusxa saqlang.
- `license-public.key` — ochiq, dasturga qo'yiladi.

**Muhim:** vosita kalitlarni loyiha papkasi ichida saqlamaydi. Agar `--out-dir`
loyiha ichida bo'lsa (pom.xml topilsa), u ishlamaydi.

## 4. Public kalitni dasturga qo'yish

`license-public.key` faylini quyidagi joyga nusxalang (mavjud vaqtinchalik
`PLACEHOLDER-RUN-KEYGEN` matnining o'rniga):

```
src\main\resources\license\license-public.key
```

Keyin loyihani qayta yig'ing (`mvn -q compile`). Kalit faylini hech qachon
o'zgartirmang va almashtirmang — eski litsenziyalar ishlamay qoladi.

## 5. Litsenziya berish

```
tools\license.cmd issue --private-key C:\MebelSexKeys\license-private.key ^
  --customer "Mebel Sex MChJ" --machine MX-ABCD-EFGH-IKLM-NPQR ^
  --expires 2027-12-31 --out C:\MebelSexKeys\mijoz1.lic
```

- `--expires` berilmasa, litsenziya **muddatsiz** bo'ladi.
- Muddati o'tgan (`--expires` bugundan oldin) litsenziya yaratilmaydi.
- Fayl mavjud bo'lsa, `--force` bilan almashtiriladi.
- Vosita public kalitni private kalit yonidan topadi; boshqa joyda bo'lsa
  `--public-key <fayl>` qo'shing.

Faylni mijozga yuboring; mijoz uni dastur papkasiga `license.lic` nomi bilan
qo'yadi.

## 6. Litsenziyani tekshirish

```
tools\license.cmd inspect --license C:\MebelSexKeys\mijoz1.lic ^
  --public-key C:\MebelSexKeys\license-public.key --machine MX-ABCD-EFGH-IKLM-NPQR
```

Chiqarish litsenziya maydonlari va holatini ko'rsatadi:
`to'g'ri` / `muddati tugagan` / `boshqa kompyuter uchun` / `imzo noto'g'ri` va h.k.

## 7. Muddatni yangilash

Yangi `--expires` bilan `issue` buyrug'ini qayta ishlating va yangi faylni
mijozga yuboring. Eski litsenziya fayli almashadi.

## 8. Chiqish kodlari

- `0` — muvaffaqiyat.
- `1` — buyruq yoki ma'lumot xatosi.
- `2` — tekshiruv muvaffaqiyatsiz (imzo yoki machine mos emas).

## 9. Ogohlantirish

`license-private.key` yo'qolsa, yangi litsenziya bera olmaysiz va eski
litsenziyalarni tekshirib bo'lmaydi. Uni loyihaga, chatga yoki backupga
qo'shmang.
