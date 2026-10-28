# اشاره — Air Gestures

کنترل گوشی با حرکت دست در هوا، بدون لمس صفحه. دو مسیر، یک راه اجرا:

| بیلد | منبع تشخیص | ژست‌ها | وابستگی |
|---|---|---|---|
| `hand` | دوربین جلو + MediaPipe HandLandmarker (آفلاین روی دستگاه) | دست بالا/پایین، پینچ، دو پینچ، کف باز ثابت | `com.google.mediapipe:tasks-vision` |
| `prox` | سنسور مجاورت | یک موج، دو موج، پوشاندن طولانی | هیچ |

هر دو، اشاره را با `AccessibilityService.dispatchGesture()` به همان مسیری می‌فرستند که انگشت می‌رود،
پس اینستا/یوتیوب/هر اپ دیگری بدون تغییر جواب می‌دهد.

## چرا این‌جوری

اندروید (و iOS) هیچ API عمومی برای «حرکت دست در هوا» ندارند. تنها استثناها سخت‌افزار-خاص بودند:
رادار Soli در Pixel 4 (حذف شد)، S Pen Air Actions، Air Actions روی ساعت. پس راه عملی روی هر گوشی،
خودتان تشخیص را بسازید: دوربین → مدل → ژست → لمس تزریقی.

## ساخت

```bash
gradle :app:assembleProxRelease      # بیلد سبک، بدون دانلود مدل
gradle :app:assembleHandRelease      # بیلد دوربین‌دار
```

`local.properties` باید `sdk.dir` داشته باشد. هر دو بیلد در GitHub Actions ساخته می‌شوند
(`.github/workflows/build.yml`) و دو APK جدا به‌عنوان artifact می‌گذارند.
`hand` به Maven Central نیست: `com.google.mediapipe:tasks-vision:0.10.29` از `google()` می‌آید.

مدل `app/src/hand/assets/hand_landmarker.task` (۷٫۸ مگ) از
`https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/1/hand_landmarker.task`
است و داخل ریپو commit شده، تا ساخت بدون شبکه هم ممکن بماند.

## نصب و اولین اجرا

1. APK را نصب کن (sideload؛ مجوز Accessibility در Play Store فقط برای ابزار کمکی مجاز است).
2. تب خانه → «تنظیمات دسترسی» → «اشاره» → روشن. این یکی را **باید با انگشت** بزنی؛
   هیچ لمس تزریقی به دیالوگ اجازه‌های سیستمی راه ندارد.
3. در بیلد `hand`: مجوز دوربین، و اگر خواستی مجوز پنجرهٔ شناور.
4. «روشن کردن اشاره» را بزن. گوشی را بگذار جایی که دوربین جلوی دستت را ببیند.
5. تب تشخیص: هر خط لاگ یک علت را می‌گوید؛ اگر جایی خالی است، آن مسیر اجرا نشده.

## محدودیت‌های واقعی

- دیالوگ‌های اجازه و خودِ صفحهٔ تنظیمات: تزریق لمس مسدود است.
- پنجره‌های `FLAG_SECURE` (بانکی، DRM): رویداد دور ریخته می‌شود.
- کیبورد نرم باز: بعضی مسیرها بلاک می‌کنند.
- دکمه‌های فیزیکی/نوار پایین: فقط `performGlobalAction` (back/home/recents).
- لاتنسی تقریباً ۱۲۰ تا ۲۰۰ میلی‌ثانیه؛ برای فید و ریلز خوب است، برای بازی نه.
- fling به سرعت اشاره وابسته است: حرکت آهسته، صفحه را نمی‌کَنَد.
- رام‌های شیائومی/اوپو سرویس دارای دوربین را می‌کشند → استثنای باتری بده.

## نقشهٔ کد

```
decode/HandDecoder.kt   مسیر.landmark → ژست   (خالص Kotlin، روی JVM تست می‌شود)
decode/ProxDecoder.kt   زمانِ پوشیده/باز بودن → ژست
inject/Synth.kt         ژست → Path/StrokeDescription (duration = سرعت = fling)
inject/AirInputService.kt  صف تزریق، روی نتایج callback لاگ می‌نویسد
svc/AirService.kt       Foreground: سنسور/دوربین را نگه می‌دارد و ژست را تبدیل به کار می‌کند
sense/CameraHandSource.kt  فقط در src/hand: Camera2 → JPEG → Bitmap → MediaPipe
ui/                     خانه، نگاشت، تشخیص، راهنما (Compose، RTL)
```
