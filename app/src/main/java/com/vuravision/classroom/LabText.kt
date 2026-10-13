package com.vuravision.classroom

import android.content.Context

/**
 * Persian for the text drawn inside labs (captions, slider names, bar labels). These strings mix words with live
 * numbers and formulas, so instead of one resource per sentence the words are replaced phrase by phrase (longest
 * first) and formulas, symbols and units stay as they are. In English the text is returned unchanged.
 */
object LabText {
    private val fa=listOf(
        // Sliders
        "Added NaOH (mL)" to "NaOH افزوده (mL)","Amplitude" to "دامنه","Balls" to "تعداد توپ","Bars" to "تعداد ستون",
        "Bond: 1 ionic / 2 covalent / 3 metallic" to "پیوند: ۱ یونی / ۲ کووالانسی / ۳ فلزی","Branch angle (°)" to "زاویهٔ شاخه (°)",
        "Current (A)" to "جریان (A)","D (relative)" to "D (نسبی)","Depth (m)" to "عمق (m)","Depth" to "عمق","Diffusivity" to "ضریب پخش",
        "Drag k/m (1/s)" to "مقاومت هوا k/m (1/s)","Half-life (s)" to "نیم‌عمر (s)","Height (m)" to "ارتفاع (m)","Initial atoms" to "اتم‌های اولیه",
        "Initial capital" to "سرمایهٔ اولیه","Leading digit" to "رقم اول","Left C (mol/L)" to "غلظت چپ (mol/L)","Left T (°C)" to "دمای چپ (°C)",
        "Right C (mol/L)" to "غلظت راست (mol/L)","Right T (°C)" to "دمای راست (°C)","Mass / M☉" to "جرم / M☉","Radius / AU" to "شعاع / AU",
        "Molecule: 1 H₂O / 2 CO₂ / 3 CH₄" to "مولکول: ۱ H₂O / ۲ CO₂ / ۳ CH₄","Multiplier" to "ضریب","Noise" to "نویز",
        "Object ρ (kg/m³)" to "ρ جسم (kg/m³)","Odd harmonics" to "هارمونیک‌های فرد","Outlier" to "دادهٔ پرت","P(head)" to "احتمال شیر",
        "p(heads)" to "احتمال شیر","P(rain → sun)" to "P(باران ← آفتاب)","P(sun → rain)" to "P(آفتاب ← باران)","P(right)" to "احتمال راست",
        "P(win)" to "احتمال برد","People" to "تعداد افراد","Phase (°)" to "فاز (°)","Points" to "تعداد نقطه","Population p" to "p جامعه",
        "Prevalence (%)" to "شیوع (٪)","Ratio r" to "نسبت r","Reduction (kJ/mol)" to "کاهش (kJ/mol)","Rotation (°)" to "چرخش (°)","Rows" to "ردیف‌ها",
        "Salt: 1 Na / 2 K / 3 Cu / 4 Ca" to "نمک: ۱ Na / ۲ K / ۳ Cu / ۴ Ca","Salt (g)" to "نمک (g)","Sample size" to "اندازهٔ نمونه","Samples" to "تعداد نمونه",
        "Sensitivity (%)" to "حساسیت (٪)","Specificity (%)" to "ویژگی (٪)","Separate groups" to "جدایی گروه‌ها","Sides" to "تعداد ضلع","Slope" to "شیب",
        "Source v (m/s)" to "سرعت منبع (m/s)","Start integer" to "عدد شروع","Steps" to "تعداد گام","T (°C), water at 1 atm" to "دما (°C)، آب در ۱ atm",
        "Terms" to "تعداد جمله","Throws" to "تعداد پرتاب","Trials" to "تعداد آزمایش","Turns" to "دور","Water (mL)" to "آب (mL)","x at tangent" to "x نقطهٔ مماس",
        "k forward" to "k رفت","k reverse" to "k برگشت","Added water (mL)" to "آب افزوده (mL)",
        // Bar labels
        "Stay wins" to "برد با ماندن","Switch wins" to "برد با تعویض","Shared birthday" to "تولد مشترک","All distinct" to "همه متفاوت",
        "Reach 20" to "رسیدن به ۲۰","Ruin at 0" to "ورشکستگی در ۰",
        // Captions: whole sentences first, then phrases, then single words
        "Host knows prize, always opens a goat" to "مجری جای جایزه را می‌داند و همیشه درِ بز را باز می‌کند",
        "Prime numbers have exactly two positive divisors" to "اعداد اول دقیقاً دو مقسوم‌علیه مثبت دارند",
        "Each inner entry is the sum of the two entries above" to "هر خانهٔ داخلی جمع دو خانهٔ بالای آن است",
        "Each group slopes down; combined data slopes up due to group differences" to "شیب هر گروه نزولی است؛ دادهٔ ترکیبی به‌خاطر تفاوت گروه‌ها صعودی می‌شود",
        "Bubble sort: compare adjacent values and swap if out of order" to "مرتب‌سازی حبابی: هر دو همسایه را مقایسه و در صورت نامرتب بودن جابه‌جا کن",
        "Koch curve: segments ×4, each length ÷3 at each iteration" to "منحنی کخ: در هر مرحله پاره‌خط‌ها ×۴ و طول هر کدام ÷۳",
        "Ionic: electron transfer creates oppositely charged ions" to "یونی: انتقال الکترون یون‌های با بار مخالف می‌سازد",
        "Covalent: atoms share electron pairs" to "کووالانسی: اتم‌ها جفت‌الکترون را شریک می‌شوند",
        "Metallic: positive ion lattice with delocalized electrons" to "فلزی: شبکهٔ یون‌های مثبت با الکترون‌های آزاد",
        "outliers shift the mean" to "دادهٔ پرت میانگین را جابه‌جا می‌کند","streaks occur in independent sequences" to "رشته‌ها در دنباله‌های مستقل طبیعی‌اند",
        "teaching model, not clinical advice" to "مدل آموزشی، نه توصیهٔ پزشکی","general conjecture unproved" to "حدس کلی هنوز اثبات نشده",
        "absolute adds atmospheric pressure" to "فشار مطلق، فشار هوا را هم دارد","moving-clock pulse slows by γ" to "تپش ساعت متحرک γ برابر کند می‌شود",
        "water rises on concentrated side" to "آب در سمت غلیظ‌تر بالا می‌آید","not equilibrium ΔG" to "نه ΔG تعادل","uniform 365-day model" to "مدل یکنواخت ۳۶۵روزه",
        "ideal strong acid/base" to "اسید و باز قوی ایده‌آل","inscribed / circumscribed" to "محاطی / محیطی","shell schematic" to "طرح لایه‌ها",
        "schematic molecular model" to "مدل طرح‌وار مولکول","tetrahedral (2D schematic)" to "چهاروجهی (طرح دوبعدی)","Two equal heat capacities" to "دو ظرفیت گرمایی برابر",
        "Stationary observer ahead" to "ناظر ساکن جلوی منبع","Circular orbit" to "مدار دایره‌ای","Elastic collision" to "برخورد کشسان","Fixed ends" to "دو سر بسته",
        "internal nodes" to "گره داخلی","harmonic" to "هارمونیک","Water at 1 atm" to "آب در ۱ atm","Diffusion: characteristic distance" to "پخش: فاصلهٔ مشخصه",
        "First-order" to "مرتبهٔ اول","neutral atom" to "اتم خنثی","electrons" to "الکترون","NaCl at ~20°C" to "NaCl در حدود ۲۰°C","water" to "آب","dissolved" to "حل‌شده",
        "Binary fractal tree" to "درخت فراکتال دوشاخه","length ratio" to "نسبت طول","depth" to "عمق","Golden spiral" to "مارپیچ طلایی","every quarter-turn" to "در هر ربع دور",
        "Linear case" to "حالت خطی","Discriminant" to "دلتا","no real roots" to "ریشهٔ حقیقی ندارد","Roots" to "ریشه‌ها","Square-wave Fourier approximation" to "تقریب فوریهٔ موج مربعی",
        "odd harmonics" to "هارمونیک فرد","Binomial distribution" to "توزیع دوجمله‌ای","rows" to "ردیف","balls" to "توپ","Heads / trials" to "شیر / پرتاب‌ها",
        "expected" to "مورد انتظار","Sum of two fair dice" to "جمع دو تاس سالم","independent throws" to "پرتاب مستقل","Biased random walk" to "قدم‌زدن تصادفی",
        "final position" to "مکان نهایی","Means of" to "میانگین‌های","OLS slope" to "شیب رگرسیون","intercept" to "عرض از مبدأ","from rest" to "از حالت سکون",
        "mean" to "میانگین","median" to "میانه","Mean" to "میانگین","Highlighted digit" to "رقم برجسته","people" to "نفر","Longest heads streak" to "بلندترین رشتهٔ شیر",
        "Linear drag" to "مقاومت خطی","distance" to "مسافت","Gauge pressure" to "فشار پیمانه‌ای","Catalyst lowers" to "کاتالیزور کاهش می‌دهد","Ideal osmotic" to "اسمزی ایده‌آل",
        "Schematic layers" to "لایه‌های طرح‌وار","object" to "جسم","Even" to "زوج","odd" to "فرد","steps" to "گام","Connect" to "وصل کن","Bernoulli sample proportion" to "نسبت نمونهٔ برنولی",
        "Gambler's ruin" to "ورشکستگی قمارباز","initial" to "اولیه","Current" to "اکنون","rain" to "باران","sun" to "آفتاب","stationary" to "پایدار","equilibrium" to "تعادل",
        "first order" to "مرتبهٔ اول","Range" to "بُرد","Flight" to "زمان پرواز","years" to "سال","solid" to "جامد","liquid" to "مایع","gas" to "گاز","schematic" to "طرح‌وار",
        "linear" to "خطی","Sodium: yellow" to "سدیم: زرد","Potassium: lilac" to "پتاسیم: یاسی","Copper: blue-green" to "مس: سبزآبی","Calcium: orange-red" to "کلسیم: نارنجی‌قرمز",
    ).sortedByDescending{it.first.length}
    fun t(context:Context,text:String):String{
        if(context.resources.configuration.locales[0].language!="fa")return text
        var out=text
        fa.forEach{(en,value)->out=out.replace(en,value)}
        return out
    }
}
