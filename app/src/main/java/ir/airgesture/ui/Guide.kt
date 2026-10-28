package ir.airgesture.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun Guide() {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        SectionCard("چطور کار می‌کند") {
            Text(
                "تصویر دوربین جلو یا سنسور مجاورت خوانده و به یک «اشاره» تبدیل می‌شود. برای اجرا روی صفحه، " +
                    "از سرویس دسترسی (AccessibilityService) استفاده می‌شود: اندروید رویدادها را از همان مسیری " +
                    "می‌فرستد که انگشت می‌رود، پس اینستا و بقیه فرقی را نمی‌فهمند. همه‌ی مدل‌ها روی گوشی اجرا " +
                    "می‌شوند و هیچ فریمی به اینترنت نمی‌رود.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        SectionCard("چهار حالت کار") {
            GuideLine("نشانگر با سر: نوک بینی سرعت مکان‌نما را می‌کند. پینچ = کلیک جایی که نشانگر ایستاده. پینچ را نگه داری و سر تکان بخورد = کشیدن (drag). دو پینچ = نقطهٔ مرجع روی وضعیت فعلی سر.")
            GuideLine("نشانگر با انگشت: نوک انگشت اشاره مکان‌نما را می‌برد؛ همان کلیک و درگ، با دست. تا دو دست هم‌زمان رد می‌شوند و هر دو کشیده می‌شوند.")
            GuideLine("دست: بالا/پایین، پینچ، دو پینچ، کف باز ثابت - بدون نشانگر، برای فید و ریلز.")
            GuideLine("سنسور مجاورت: بدون دوربین؛ یک موج، دو موج، پوشاندن طولانی.")
        }
        SectionCard("نکته‌های نشانگر") {
            GuideLine("اول دو پینچ بزن تا سرِ فعلی‌ات نقطهٔ مرجع شود؛ بعد با حرکت کوچک سر، نشانگر جابه‌جا می‌شود. برگرداندن سر به مرکز، نشانگر را نگه می‌دارد - مثل جوی‌استیک، نه مثل برداشتن ماوس.")
            GuideLine("اگر نشانگر معکوس می‌رود یا خیلی کند/تند است، «سرعت نشانگر» و «نرمی حرکت» را از تب خانه عوض کن.")
            GuideLine("حلقهٔ آب یعنی «در حال توقف برای کلیک»؛ اگر روی دکمه‌ای نشست و پر نشد، یا فاصله از حد تحمل بیشتر است یا درختِ صفحه آن کنترل را نشان نمی‌دهد.")
            GuideLine("«فقط روی دکمه‌های واقعی توقف کن» را روشن کن تا روی جای خالی کلیک تصادفی نزنی.")
        }
        SectionCard("اگر هیچ چیز شناسایی نمی‌شود") {
            GuideLine("تب تشخیص دو عدد می‌دهد: «دیدن/فریم» مثل hand 45/300 و «framerate». اگر عدد اول صفر است، مدل هیچ دستی پیدا نمی‌کند - اول «چرخش تصویر» را روی 90 یا 270 بگذار؛ مدل کف دست به جهت‌گیری حساس است و چرخش غلط یعنی هرگز پیداش نمی‌کند.")
            GuideLine("اگر نسبت دیدن خوب است ولی اشاره‌ای ثبت نمی‌شود، مشکل آستانه است: «سرعت» را آرام‌تر کن، دست را نزدیک‌تر (۳۰ سانتی‌متر) و در نور بهتر بگیر.")
            GuideLine("اگر اشاره ثبت می‌شود ولی صفحه تکان نمی‌خورد، در لاگ «inject: cancelled by the window» را می‌بینی: آن اپ لمس تزریقی را رد کرده.")
            GuideLine("اسکلت دست روی صفحه یعنی تشخیص زنده است؛ کادر نقطه‌چین «دست پیدا نیست» یعنی مدل چیزی نمی‌بیند. این دو مشکل، دو راه‌حل دارند.")
            GuideLine("نشانگر تا وقتی سر یک‌بار تکان نخورد حلقهٔ توقف را روشن نمی‌کند - تا شروع به‌محض روشن کردن، وسط صفحه کلیک نزند.")
        }
        SectionCard("چیزهایی که شدنی نیست") {
            GuideLine("دیالوگ‌های اجازهٔ سیستمی و خودِ صفحهٔ تنظیمات دسترسی را هیچ لمسی نمی‌تواند بزند؛ بار اول باید با انگشت اجازه بدهی.")
            GuideLine("اپ‌های بانکی و ویدیوی DRM (FLAG_SECURE) رویداد تزریقی را دور می‌ریزند.")
            GuideLine("بازی و هر چیزی که روی Canvas می‌کشد درخت کنترل ندارد، پس حلقه دور «دکمه» نمی‌پیچد؛ ولی کلیک روی همان نقطه کار می‌کند.")
            GuideLine("دکمه‌های فیزیکی و نوار پایین: فقط برگشت/خانه/Recentها.")
            GuideLine("چراغ سبز دوربین تا وقتی سرویس روشن است روشن می‌ماند؛ این مصرف باتری دارد.")
            GuideLine("نور کم، عینک آفتابی و فاصلهٔ زیاد، چهره و دست را سخت می‌کند - این فیزیک نور است، نه ضعف مدل.")
        }
        SectionCard("اگر کار نکرد") {
            GuideLine("تب تشخیص: «inject: cancelled by the window» یعنی اپِ جلوی صفحه لمس تزریقی را رد کرد.")
            GuideLine("«cam: camera error 3» یعنی اپ دیگری دوربین را گرفته است (تماس، اسنپ، دوربین).")
            GuideLine("«model: GPU refused ... retrying on CPU» یعنی delegate سخت‌افزاری روی این رام نشدنی است؛ سرعت به حالت عادی برمی‌گردد.")
            GuideLine("«cursor: overlay not allowed» یعنی نشانگر نامرئی است؛ دکمهٔ «مجوز پنجره» را بزن (کلیک و توقف همچنان کار می‌کنند).")
            GuideLine("«dwell: fired on nothing» یعنی حلقه روی جای خالی کلیک کرد؛ حالت «فقط روی دکمه‌های واقعی» را روشن کن.")
            GuideLine("«prox: this phone reports no proximity sensor» یعنی این گوشی سنسور مجاورت گزارش‌دهنده ندارد.")
            GuideLine("اگر بعد از چند دقیقه از کار افتاد، استثنای باتری را بده: بعضی رام‌ها سرویس دارای دوربین را می‌بندند.")
        }
    }
}

@Composable
private fun GuideLine(text: String) {
    Text("• $text", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 3.dp))
}
