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
        Panel("این چطور کار می‌کند") {
            Text(
                "حرکت دست از دوربین جلو (یا سنسور مجاورت) خوانده و به یک «اشاره» تبدیل می‌شود. " +
                    "برای اینکه همان حرکت روی صفحه اجرا شود، از سرویس دسترسی (AccessibilityService) استفاده می‌شود: " +
                    "اندروید رویدادها را از همان مسیری می‌فرستد که انگشت می‌فرستد، پس اینستا و بقیه فرقی را نمی‌فهمند.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Panel("چیزهایی که شدنی نیست") {
            GuideLine("دیالوگ‌های اجازهٔ سیستمی و خودِ صفحهٔ تنظیمات دسترسی را نمی‌توان با اشاره زد؛ بار اول باید با انگشت اجازه بدهی.")
            GuideLine("اپ‌های بانکی و ویدیوهای DRM (پرچم FLAG_SECURE) رویداد تزریقی را دور می‌ریزند.")
            GuideLine("دکمه‌های فیزیکی و نوار پایین صفحه با اشاره زده نمی‌شوند؛ فقط برگشت/خانه/Recentها به‌صورت فرمان جهانی.")
            GuideLine("اسکرول ریز و بازی که به دقت پیکسل نیاز دارد، با اشارهٔ هوا خوب کار نمی‌کند؛ این برای فید و ریلز ساخته شده.")
            GuideLine("چراغ سبز دوربین از اندروید ۱۲ تا وقتی سرویس روشن است روشن می‌ماند، و این مصرف باتری دارد.")
        }
        Panel("اگر کار نکرد") {
            GuideLine("تب تشخیص را ببین: خط «inject: cancelled by the window» یعنی اپِ جلوی صفحه اجازهٔ لمس تزریقی نداد.")
            GuideLine("«hand: camera error 3» یعنی یک اپ دیگر دوربین را گرفته (مثلاً اسنپ/تماس).")
            GuideLine("«prox: this phone reports no proximity sensor» یعنی فقط حالت دوربین ممکن است.")
            GuideLine("اگر بعد از چند دقیقه از کار افتاد، استثنای باتری را بده: بعضی رام‌ها سرویس دارای دوربین را می‌بندند.")
            GuideLine("اشارهٔ دست باید تند باشد: کندی حرکت، fling نمی‌سازد و صفحه تکان نمی‌خورد.")
        }
    }
}

@Composable
private fun GuideLine(text: String) {
    Text("• $text", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 3.dp))
}
