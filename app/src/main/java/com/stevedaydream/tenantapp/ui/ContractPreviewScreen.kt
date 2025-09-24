@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)
// ContractPreviewScreen.kt
package com.stevedaydream.tenantapp.ui

import android.app.DatePickerDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.stevedaydream.tenantapp.data.AppDatabase
import com.stevedaydream.tenantapp.data.User
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

// PDF 儲存函數：使用 SAF (Storage Access Framework) 寫入檔案
fun savePdfWithUri(context: Context, uri: Uri, content: String) {
    val pdfDocument = PdfDocument()
    val paint = Paint()
    paint.textSize = 14f
    val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4尺寸

    var page = pdfDocument.startPage(pageInfo)
    var canvas = page.canvas
    val lines = content.split("\n")
    var y = 50
    val lineHeight = 22

    for (line in lines) {
        if (y > 800) {
            try { pdfDocument.finishPage(page) } catch (_: Exception) {}
            page = pdfDocument.startPage(pageInfo)
            canvas = page.canvas
            y = 50
        }
        canvas.drawText(line, 40f, y.toFloat(), paint)
        y += lineHeight
    }
    try { pdfDocument.finishPage(page) } catch (_: Exception) {}

    try {
        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            pdfDocument.writeTo(outputStream)
        }
        Toast.makeText(context, "PDF 已儲存", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Toast.makeText(context, "PDF 產生失敗: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        e.printStackTrace()
    }
    try { pdfDocument.close() } catch (_: Exception) {}

}

@Composable
fun ContractPreviewScreen(navController: NavHostController) {
    var tenantName by remember { mutableStateOf("") }
    var tenantId by remember { mutableStateOf("") }
    var tenantPhone by remember { mutableStateOf("") }
    var landlord: User? by remember { mutableStateOf(null) }
    var address by remember { mutableStateOf("") }
    var rentAmount by remember { mutableStateOf("") }
    var deposit by remember { mutableStateOf("") }
    var showPreview by remember { mutableStateOf(false) }

    // 租期可選 0.5, 1, 2 年
    val periodOptions = listOf("0.5年", "1年", "2年")
    var periodIndex by remember { mutableStateOf(1) } // 預設一年

    val context = LocalContext.current
    val db = AppDatabase.getDatabase(context)
    val roomDao = db.roomDao()
    val userDao = db.userDao()
    val roomList by roomDao.getAllRooms().collectAsState(initial = emptyList())

    // 房號處理
    var expanded by remember { mutableStateOf(false) }
    var selectedRoom by remember { mutableStateOf("") }
    var manualRoomNumber by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val sdf = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
    var startDate by remember { mutableStateOf(sdf.format(Date())) }
    var endDate by remember { mutableStateOf("") }

    // 修正：所有可修改的狀態都用 var 宣告
    var isNotaryAgreed by remember { mutableStateOf(false) }
    var isRentalPartially by remember { mutableStateOf(false) }
    var isParkingSpace by remember { mutableStateOf(false) }
    var isRecoveryOriginal by remember { mutableStateOf(false) }
    val showPaymentDayDialog = remember { mutableStateOf(false) }
    var paymentDayOfMonth by remember { mutableStateOf("") }
    var paymentDurationInMonths by remember { mutableStateOf("") }
    val showCommunicationDialog = remember { mutableStateOf(false) }
    var communicationMethod by remember { mutableStateOf("Line") }
    val isDeposit by remember { mutableStateOf(false) }
    val isPaymentMethodCash by remember { mutableStateOf(false) }
    val isPaymentMethodTransfer by remember { mutableStateOf(false) }
    val isCommunicationEmail by remember { mutableStateOf(false) }
    val isCommunicationSms by remember { mutableStateOf(false) }
    val isCommunicationLine by remember { mutableStateOf(false) }
    var canTerminateContract by remember { mutableStateOf(false) }
    var canNotTerminateContract by remember { mutableStateOf(false) }
    var isNoParkingSpace by remember { mutableStateOf(false) }
    var isAttachedEquipment by remember { mutableStateOf(false) }
    var isNotarization by remember { mutableStateOf(false) }
    var isNotarizationNotAgree by remember { mutableStateOf(false) }
    var isRecoveryOriginalState by remember { mutableStateOf(false) }
    var isReturnAsIs by remember { mutableStateOf(false) }
    var isOtherRecovery by remember { mutableStateOf(false) }


    // 合約範本，為了簡潔，這裡只保留一部分
    val contractTemplate = """
    住宅租賃契約書
    房號：%s
    租賃地址：%s
    租賃期間：自 %s 起至 %s 止
    承租人：%s　　出租人：%s
    本契約依內政部109年8月26日內政部台內地字第1090264511號函修正
    
    第一條　租賃標的
    (一)租賃住宅標示：
    1、門牌:%s
    2、車位：□有（汽車停車位__個、機車停車位__個）□無。
    3、□有 □無查封登記。
    (二)租賃範圍：
    1、租賃住宅%s%s：房號：%s
    2、車位%s
    3、租賃附屬設備：
    %s
    
    第二條 租賃期間
    租賃期間自民國 %s 年 %s 月 %s 日起至民國 %s 年 %s 月 %s 日止。(租賃期間至少三十日以上)
    
    第三條 租金約定及支付
    承租人每月租金為新臺幣(下同)%s元整，每期應繳納 %s 個月租金，並於 %s前支付，不得藉任何理由拖延或拒絕；出租人於租賃期間亦不得藉任何理由要求調漲租金。
    租金支付方式：□現金繳付□轉帳繳付：金融機構： %s，戶名： %s，帳號： %s。□其他：   。
    
    第四條 押金約定及返還
    押金由租賃雙方約定為 2 個月租金，金額為 %s 元整(最高不得超過二個月租金之總額)。承租人應於簽訂本契約之同時給付出租人。
    前項押金，除有第十一條第四項、第十三條第三項、第十四條第四項及第十八條第二項得抵充之情形外，出租人應於租期屆滿或租賃契約終止，承租人返還租賃住宅時，返還押金或抵充本契約所生債務後之賸餘押金。

    第五條 租賃期間相關費用之約定
    租賃期間，使用租賃住宅所生之相關費用，依下列約定辦理：
    (一)管理費: □無
    (二)水費：由出租人負擔。
    (三)電費：由承租人負擔。(備註：經雙方約定以用電度數計費者，收取每月每度至低五元，且房東每月得依台電電費計算公式之夏月、非夏月之平均值做增調整，電費計算公式如附表，且每年定期由台電官網更新最新數據)
    (四)瓦斯費：□無
    (五)網路費：由出租人負擔。
    
    第六條 稅費負擔之約定
    本契約有關稅費，依下列約定辦理：
    租賃住宅之房屋稅、地價稅由出租人負擔。
    本契約租賃雙方不同意辦理公證。

    第七條 使用租賃住宅之限制
    本租賃住宅係供居住使用，承租人不得變更用途。
    承租人同意遵守公寓大廈規約或其他住戶應遵行事項，不得違法使用、存放有爆炸性或易燃性物品。
    承租人應經出租人同意始得將本租賃住宅之全部或一部分轉租、出借或以其他方式供他人使用，或將租賃權轉讓於他人。
    前項出租人同意轉租者，應出具同意書(如附件二)載明同意轉租之範圍、期間及得終止本契約之事由，供承租人轉租時向次承租人提示。
    未經同意不可養寵物，若違反條款屢勸不聽者，將依法多扣除至多1個月押金並限期搬離。立書同意人：甲方(房東)  %s       乙方(租客) %s
    
    第八條 修繕
    租賃住宅或附屬設備損壞時，應由出租人負責修繕。但租賃雙方另有約定、習慣或其損壞係可歸責於承租人之事由者，不在此限。
    前項由出租人負責修繕者，承租人得定相當期限催告修繕，如出租人未於承租人所定相當期限內修繕時，承租人得自行修繕，並請求出租人償還其費用或於第三條約定之租金中扣除。
    出租人為修繕租賃住宅所為之必要行為，應於相當期間先期通知，承租人無正當理由不得拒絕。
    前項出租人於修繕期間，致租賃住宅全部或一部不能居住使用者，承租人得請求出租人扣除該期間全部或一部之租金。

    第九條 室內裝修
    承租人有室內裝修之需要，應經出租人同意並依相關法令規定辦理，且不得損害原有建築結構之安全。
    承租人經出租人同意裝修者，其裝修增設部分若有損壞，由承租人負責修繕。        第一項情形，承租人返還租賃住宅時，應%s回復原狀%s現況返還%s其他   。

    第十條 出租人之義務及責任
    出租人應出示有權出租本租賃住宅之證明文件及國民身分證或其他足資證明身分之文件，供承租人核對。
    出租人應以合於所約定居住使用之租賃住宅，交付承租人，並應於租賃期間保持其合於居住使用之狀態。
    出租人與承租人簽訂本契約前，租賃住宅有由承租人負責修繕之項目及範圍者，出租人應先向承租人說明並經承租人確認 （如附件三） ，未經約明確認者，出租人應負責修繕，並提供有修繕必要時之聯絡方式。

    第十一條 承租人之義務及責任
    承租人應於簽訂本契約時，出示國民身分證或其他足資證明身分之文件，供出租人核對。
    1.承租人應以善良管理人之注意，保管、使用租賃住宅。
    2.承租人違反前項義務，致租賃住宅毀損或滅失者，應負損害賠償責任。但依約定之方法或依租賃住宅之性質使用、收益，致有變更或毀損者，不在此限。
    3.前項承租人應賠償之金額，得由第四條第一項規定之押金中抵充，如有不足，並得向承租人請求給付不足之金額。
    4.承租人經出租人同意轉租者，與次承租人簽訂轉租契約時，應不得逾出租人同意轉租之範圍及期間，並應於簽訂轉租契約後三十日內，以書面將轉租範圍、期間、次承租人之姓名及通訊住址等相關資料通知出租人。

    第十二條 租賃住宅部分滅失
    租賃關係存續中，因不可歸責於承租人之事由，致租賃住宅之一部滅失者，承租人得按滅失之部分，請求減少租金。

    第十三條 任意終止租約之約定
    本契約於期限屆滿前，除依第十六條及第十七條規定得提前終止租約外，租賃雙方%s任意終止租約。
    依前項約定得終止租約者，租賃之一方應至少於終止前一個月通知他方。一方未為先期通知而逕行終止租約者，應賠償他方最高不得超過一個月租金額之違約金。
    前項承租人應賠償之違約金，得由第四條第一項規定之押金中抵充，如有不足，並得向承租人請求給付不足之金額。
    租期屆滿前，依第一項終止租約者，出租人已預收之租金應返還予承租人。
    
    第十四條 租賃住宅之返還
    租賃關係消滅時，出租人應即結算租金及第五條約定之相關費用，並會同承租人共同完成屋況及附屬設備之點交手續，承租人應將租賃住宅返還出租人並遷出戶籍或其他登記。
    前項租賃之一方未會同點交，經他方定相當期限催告仍不會同者，視為完成點交。
    承租人未依第一項規定返還租賃住宅時，出租人應即明示不以不定期限繼續契約，並得向承租人請求未返還租賃住宅期間之相當月租金額，及相當月租金額計算之違約金(未足一個月者，以日租金折算)至返還為止。
    前項金額與承租人未繳清之租金及第五條約定之相關費用，出租人得由第四條第一項規定之押金中抵充，如有不足，並得向承租人請求給付不足之金額或費用。
    
    第十五條 租賃住宅所有權之讓與
    出租人於租賃住宅交付後，承租人占有中，縱將其所有權讓與第三人，本契約對於受讓人仍繼續存在。
    前項情形，出租人應移交押金及已預收之租金與受讓人，並以書面通知承租人。
    本契約如未經公證，其期限逾五年者，不適用前二項之規定。
    
    第十六條 出租人提前終止租約
    租賃期間有下列情形之一者，出租人得提前終止租約，且承租人不得要求任何賠償：
    (一)出租人為重新建築而必要收回。
    (二)承租人遲付租金之總額達二個月之租金額，經出租人定相當期限催告，仍不為支付。
    (三)承租人積欠管理費或其他應負擔之費用達二個月之租金額，經出租人定相當期限催告，仍不為支付。
    (四)承租人違反第七條第一項規定，擅自變更用途，經出租人阻止仍繼續為之。
    (五)承租人違反第七條第二項規定，違法使用、存放有爆炸性或易燃性物品，經出租人阻止仍繼續為之。
    (六)承租人違反第七條第三項規定，擅自將租賃住宅轉租或轉讓租賃權予他人。
    (七)承租人毀損租賃住宅或附屬設備，經出租人定相當期限催告修繕仍不為修繕或相當之賠償。
    (八)承租人違反第九條第一項規定，未經出租人同意，擅自進行室內裝修，經出租人阻止仍繼續為之。
    (九)承租人違反第九條第一項規定，未依相關法令規定進行室內裝修，經出租人阻止仍繼續為之。
    (十)承租人違反第九條第一項規定，進行室內裝修，損害原有建築結構之安全。
    出租人依前項規定提前終止租約者，應依下列規定期限，檢附相關事證，以書面通知承租人。但依前項第五款及第十款規定終止者，得不先期通知：
    (一)依前項第一款規定終止者，於終止前三個月。
    (二)依前項第二款至第四款、第六款至第九款規定終止者，於終止前三十日。
    
    第十七條 承租人提前終止租約
    租賃期間有下列情形之一，致難以繼續居住者，承租人得提前終止租約，出租人不得要求任何賠償：
    (一)租賃住宅未合於所約定居住使用，並有修繕之必要，經承租人定相當期限催告，仍不於期限內修繕。
    (二)租賃住宅因不可歸責承租人之事由致一部滅失，且其存餘部分不能達租賃之目的。
    (三)租賃住宅有危及承租人或其同居人之安全或健康之瑕疵；承租人於簽約時已明知該瑕疵或拋棄終止租約權利者，亦同。
    (四)承租人因疾病、意外產生有長期療養之需要。
    (五)因第三人就租賃住宅主張其權利，致承租人不能為約定之居住使用。
    承租人依前項各款規定提前終止租約者，應於終止前三十日，檢附相關事證，以書面通知出租人。但前項第三款前段其情況危急者，得不先期通知。
    承租人死亡，其繼承人得主張終止租約，其通知期限及方式，準用前項規定。
    
    第十八條 遺留物之處理
    租賃關係消滅，依第十四條完成點交或視為完成點交之手續後，承租人仍於租賃住宅有遺留物者，除租賃雙方另有約定外，經出租人定相當期限向承租人催告，屆期仍不取回時，視為拋棄其所有權。
    出租人處理前項遺留物所生費用，得由第四條第一項規定之押金中抵充，如有不足，並得向承租人請求給付不足之費用。
    
    第十九條 履行本契約之通知
    除本契約另有約定外，租賃雙方相互間之通知，以郵寄為之者，應以本契約所記載之地址為準。
    如因地址變更未告知他方，致通知無法到達時，以第一次郵遞之日期推定為到達日。
    第一項之通知得經租賃雙方約定以
    %s
    
    第二十條 條款疑義處理
    本契約各條款如有疑義時，應為有利於承租人之解釋。
    
    第二十一條 其他約定
    本契約租賃雙方%s辦理公證。
    本契約經辦理公證者，租賃雙方%s公證書載明下列事項應逕受強制執行：
    %s
    公證書載明金錢債務逕受強制執行時，如有保證人者，前項後段第  款之效力及於保證人。
    
    第二十二條 契約及其相關附件效力
    本契約自簽約日起生效，租賃雙方各執一份契約正本。
    本契約廣告及相關附件視為本契約之一部分。
    
    第二十三條 未盡事宜之處置
    本契約如有未盡事宜，依有關法令、習慣、平等互惠及誠實信用原則公平解決之。

    簽約日期：%s
    
    立契約書人
    出租人：%s，身分證字號：%s，聯絡電話：%s
    承租人：%s，身分證字號：%s，聯絡電話：%s
    中華民國 %s 年 %s 月 %s 日
    """.trimIndent()

    val today = SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault()).format(Date())
    // 確保 getFormattedValue 內部可以安全地處理 null
    fun getFormattedValue(value: Any?): String {
        return value?.toString() ?: ""
    }
    var isNotaryNotAgree by remember { mutableStateOf(false) }
    // 動態生成合約內容
    val previewText by remember(
        tenantName, tenantId, tenantPhone, selectedRoom, address, rentAmount, deposit, startDate, endDate,
        landlord, paymentDayOfMonth, paymentDurationInMonths, isNotaryAgreed, isRentalPartially,
        isParkingSpace, isRecoveryOriginal, isReturnAsIs, isOtherRecovery, canTerminateContract, canNotTerminateContract,
        isNotarization, isNotaryNotAgree
    ) {
        mutableStateOf(if (landlord != null) {
            val startDateParts = if(startDate.isNotBlank()) startDate.split("/") else listOf("","","")
            val endDateParts = if(endDate.isNotBlank()) endDate.split("/") else listOf("","","")
            val todayParts = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date()).split("/")

            val rentDurationInMonths = when(periodIndex) {
                0 -> 6
                1 -> 12
                2 -> 24
                else -> 12
            }
            val paymentOption = if (paymentDayOfMonth.isNotBlank()) "每月   $paymentDayOfMonth 日" else "每月   日"
            val communicationOption = when {
                isCommunicationEmail -> "▓電子郵件信箱"
                isCommunicationSms -> "▓手機簡訊"
                isCommunicationLine -> "▓即時通訊軟體(Line)以文字顯示方式為之"
                else -> "□電子郵件信箱：\n□手機簡訊：\n□即時通訊軟體(Line)以文字顯示方式為之"
            }
            val canTerminate = if (canTerminateContract) "□得" else if (canNotTerminateContract) "▓不得" else "□得 □不得"
            val hasAttached = if (isAttachedEquipment) "▓有" else "□有"
            val hasParking = if (isParkingSpace) "▓有車位" else "□無車位"
            val leaseRange = if (isRentalPartially) "□全部▓部分" else "▓全部□部分"
            val recoveryOriginalSymbol = if (isRecoveryOriginalState) "▓" else "□"
            val returnAsIsSymbol = if (isReturnAsIs) "▓" else "□"
            val otherRecoverySymbol = if (isOtherRecovery) "▓" else "□"
            val notaryAgree = if (isNotarization) "▓同意" else "□同意"
            val notaryNotAgree = if (isNotarizationNotAgree) "▓不同意" else "□不同意"

            String.format(
                contractTemplate,
                getFormattedValue(selectedRoom),         // 1
                getFormattedValue(address),              // 2
                getFormattedValue(startDate),            // 3
                getFormattedValue(endDate),              // 4
                getFormattedValue(tenantName),           // 5
                getFormattedValue(landlord?.username),    // 6
                getFormattedValue(address),              // 7
                leaseRange,                              // 8
                if (isRentalPartially) getFormattedValue(selectedRoom) else "", // 9
                getFormattedValue(selectedRoom),         // 10
                hasParking,                              // 11
                hasAttached,                             // 12
                getFormattedValue(startDateParts.getOrElse(0) { "" }),    // 13
                getFormattedValue(startDateParts.getOrElse(1) { "" }),    // 14
                getFormattedValue(startDateParts.getOrElse(2) { "" }),    // 15
                getFormattedValue(endDateParts.getOrElse(0) { "" }),      // 16
                getFormattedValue(endDateParts.getOrElse(1) { "" }),      // 17
                getFormattedValue(endDateParts.getOrElse(2) { "" }),      // 18
                getFormattedValue(rentAmount),           // 19
                getFormattedValue(rentDurationInMonths), // 20
                paymentOption,                           // 21
                getFormattedValue("XXXX銀行"),        // 22
                getFormattedValue(landlord?.bankAccountName),  // 23
                getFormattedValue(landlord?.bankAccountNumber),// 24
                getFormattedValue(deposit),              // 25
                getFormattedValue(landlord?.username),    // 26
                getFormattedValue(tenantName),           // 27
                recoveryOriginalSymbol,                  // 28
                returnAsIsSymbol,                        // 29
                otherRecoverySymbol,                     // 30
                canTerminate,                            // 31
                communicationOption,                     // 32
                notaryAgree,                             // 33
                notaryNotAgree,                          // 34
                if(isNotarizationNotAgree) "□一、承租人如於租期屆滿後不返還租賃住宅。 " else "▓一、承租人如於租期屆滿後不返還租賃住宅。 \n▓二、承租人未依約給付之欠繳租金、費用及出租人或租賃住宅所有權人代繳之管理費，或違約時應支付之金額。 \n▓三、出租人如於租期屆滿或本契約終止時，應返還承租人之全部或一部押金。 ", // 35
                getFormattedValue(today),                // 36
                getFormattedValue(landlord?.username),    // 37
                getFormattedValue(landlord?.idNumber),    // 38
                getFormattedValue(landlord?.phone),       // 39
                getFormattedValue(tenantName),           // 40
                getFormattedValue(tenantId),             // 41
                getFormattedValue(tenantPhone),          // 42
                getFormattedValue(todayParts[0]),        // 43
                getFormattedValue(todayParts[1]),        // 44
                getFormattedValue(todayParts[2])         // 45
            )
        } else {
            if(landlord == null) "無法載入房東資料" else ""
        })
    }

    // 動態生成收據內容
    var receiptText by remember { mutableStateOf("") }
    val receiptTemplate = """
        %s收據
        承租人：%s
        房號：%s
        地址：%s
        
        %s：NT$%s
        
        出租人：%s
        中華民國 %s
        
        -----------------------------------
        系統自動產生，無需手寫
        
        備註：
        - 本收據為%s憑證。
        - 租約期滿且無違約、設備無損毀，出租人應於租約終止後無息退還全額押金。
        - 若有損壞或違約，得自押金中扣抵相關費用。
        
        承租人簽章：
        
        出租人簽章：
    """

    fun maskId(id: String): String {
        return if (id.length >= 5)
            id.substring(0, 2) + "*".repeat(id.length - 5) + id.takeLast(3)
        else "*".repeat(id.length)
    }

    fun maskPhone(phone: String): String {
        return if (phone.length >= 5)
            phone.substring(0, 2) + "*".repeat(phone.length - 5) + phone.takeLast(3)
        else "*".repeat(phone.length)
    }

    LaunchedEffect(Unit) {
        landlord = userDao.getAllLandlords().firstOrNull()?.firstOrNull()
    }

    // 統一 PDF 儲存 Launcher
    val saveContractLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf"),
        onResult = { uri: Uri? ->
            if (uri != null && previewText.isNotBlank()) {
                savePdfWithUri(context, uri, previewText)
            }
        }
    )

    val saveReceiptLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf"),
        onResult = { uri: Uri? ->
            if (uri != null && receiptText.isNotBlank()) {
                savePdfWithUri(context, uri, receiptText)
            }
        }
    )
    fun showDatePicker(onDateSet: (String) -> Unit) {
        val c = Calendar.getInstance()
        DatePickerDialog(
            context,
            { _, y, m, d ->
                val picked = Calendar.getInstance()
                picked.set(y, m, d)
                onDateSet(sdf.format(picked.time))
            },
            c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("電子合約預覽") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            if (!showPreview) {
                Text("請填寫合約資訊", style = MaterialTheme.typography.headlineMedium)
                // 租客姓名
                OutlinedTextField(
                    value = tenantName,
                    onValueChange = { tenantName = it },
                    label = { Text("租客姓名") },
                    modifier = Modifier.fillMaxWidth()
                )
                // 租客身分證
                OutlinedTextField(
                    value = tenantId,
                    onValueChange = { tenantId = it },
                    label = { Text("租客身分證字號") },
                    modifier = Modifier.fillMaxWidth()
                )
                // 租客電話
                OutlinedTextField(
                    value = tenantPhone,
                    onValueChange = { tenantPhone = it },
                    label = { Text("租客電話") },
                    modifier = Modifier.fillMaxWidth()
                )
                // 房東欄位 disabled
                OutlinedTextField(value = landlord?.username ?: "", onValueChange = {}, label = { Text("房東姓名") }, modifier = Modifier.fillMaxWidth(), enabled = false)
                OutlinedTextField(
                    value = if (landlord != null) maskId(landlord!!.idNumber) else "",
                    onValueChange = {},
                    label = { Text("房東身分證字號") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = false
                )
                OutlinedTextField(
                    value = if (landlord != null) maskPhone(landlord!!.phone) else "",
                    onValueChange = {},
                    label = { Text("房東電話") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = false
                )

                // 房號：下拉選單和手動輸入並存
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded },
                        modifier = Modifier.weight(0.5f)
                    ) {
                        OutlinedTextField(
                            value = selectedRoom,
                            onValueChange = {},
                            label = { Text("從資料庫選擇房號") },
                            readOnly = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                                .clickable { expanded = true }
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            roomList.forEach { room ->
                                DropdownMenuItem(
                                    text = { Text(room.roomNumber) },
                                    onClick = {
                                        selectedRoom = room.roomNumber
                                        manualRoomNumber = "" // 清空手動輸入
                                        expanded = false
                                        scope.launch {
                                            val roomDetails = roomDao.getRoomByNumber(room.roomNumber)
                                            roomDetails?.let {
                                                address = it.address
                                                rentAmount = it.rentAmount.toString()
                                                deposit = it.deposit.toString()
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    OutlinedTextField(
                        value = manualRoomNumber,
                        onValueChange = {
                            manualRoomNumber = it
                            selectedRoom = it // 這裡將手動輸入的值賦予 selectedRoom
                            // 清空其他自動帶入的欄位
                            address = ""
                            rentAmount = ""
                            deposit = ""
                        },
                        label = { Text("或手動輸入") },
                        modifier = Modifier.weight(0.5f)
                    )
                }

                // 地址、租金、押金
                OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("租賃地址") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = rentAmount, onValueChange = { rentAmount = it.filter { c -> c.isDigit() } }, label = { Text("租金(元)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = deposit, onValueChange = { deposit = it.filter { c -> c.isDigit() } }, label = { Text("押金(元)") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))

                // 起租日
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = startDate,
                        onValueChange = {},
                        label = { Text("租期起日") },
                        modifier = Modifier.fillMaxWidth(),
                        readOnly = true,
                        enabled = true
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { showDatePicker { picked -> startDate = picked } }
                    )
                }
                // 租期選單
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("租期", Modifier.padding(end = 8.dp))
                    periodOptions.forEachIndexed { idx, label ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
                            RadioButton(selected = periodIndex == idx, onClick = { periodIndex = idx })
                            Text(label)
                        }
                    }
                }
                // 迄日自動產生不可改
                OutlinedTextField(
                    value = endDate,
                    onValueChange = {},
                    label = { Text("租期迄日") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = false
                )
                // 第三條 彈出視窗
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { showPaymentDayDialog.value = true },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("租金支付日: ")
                    Text(paymentDayOfMonth.ifBlank { "__" } + "日", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Text(" / 每期繳納: ")
                    Text(paymentDurationInMonths.ifBlank { "__" } + "個月", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }

                if (showPaymentDayDialog.value) {
                    AlertDialog(
                        onDismissRequest = { showPaymentDayDialog.value = false },
                        title = { Text("設定繳費方式") },
                        text = {
                            Column {
                                OutlinedTextField(
                                    value = paymentDayOfMonth,
                                    onValueChange = { paymentDayOfMonth = it.filter { c -> c.isDigit() } },
                                    label = { Text("每月幾號繳費") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                )
                                OutlinedTextField(
                                    value = paymentDurationInMonths,
                                    onValueChange = { paymentDurationInMonths = it.filter { c -> c.isDigit() } },
                                    label = { Text("每期繳納幾個月租金") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                )
                            }
                        },
                        confirmButton = {
                            Button(onClick = { showPaymentDayDialog.value = false }) {
                                Text("確認")
                            }
                        },
                    )
                }
                Spacer(Modifier.height(16.dp))

                // 新增合約選項
                Text("合約條款選項", style = MaterialTheme.typography.titleLarge)
                Divider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isParkingSpace, onCheckedChange = { isParkingSpace = it; if (it) isNoParkingSpace = false else isNoParkingSpace = true })
                    Text("有停車位")
                    Checkbox(checked = isNoParkingSpace, onCheckedChange = { isNoParkingSpace = it; if (it) isParkingSpace = false else isParkingSpace = true })
                    Text("無停車位")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isNotarization, onCheckedChange = { isNotarization = it; if(it) isNotarizationNotAgree = false })
                    Text("本契約經辦理公證")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isNotarizationNotAgree, onCheckedChange = { isNotarizationNotAgree = it; if(it) isNotarization = false })
                    Text("本契約不辦理公證")
                }

                Button(
                    onClick = {
                        if (tenantName.isBlank() || selectedRoom.isBlank() || address.isBlank() || rentAmount.isBlank() || deposit.isBlank()) {
                            Toast.makeText(context, "請填寫所有必填欄位", Toast.LENGTH_SHORT).show()
                        } else {
                            showPreview = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("預覽合約內容")
                }

                // 產生收據區塊
                Column(Modifier.fillMaxWidth().padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("產生收據", style = MaterialTheme.typography.titleLarge)
                    Divider()
                    // 產生租金收據按鈕
                    Button(
                        onClick = {
                            if (tenantName.isBlank() || selectedRoom.isBlank() || rentAmount.isBlank() || address.isBlank()) {
                                Toast.makeText(context, "請填寫租客姓名、房號、地址與租金", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            receiptText = String.format(
                                receiptTemplate,
                                "租金",
                                tenantName,
                                selectedRoom,
                                address,
                                "繳費金額",
                                rentAmount,
                                landlord?.username ?: "X",
                                today,
                                "租金收據"
                            )
                            val fileName = "租金收據_${tenantName}_${selectedRoom}_${SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())}.pdf"
                            saveReceiptLauncher.launch(fileName)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("產生租金收據") }

                    // 產生押金收據按鈕
                    Button(
                        onClick = {
                            if (tenantName.isBlank() || selectedRoom.isBlank() || deposit.isBlank() || address.isBlank()) {
                                Toast.makeText(context, "請填寫租客姓名、房號、地址與押金", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            receiptText = String.format(
                                receiptTemplate,
                                "押金",
                                tenantName,
                                selectedRoom,
                                address,
                                "押金金額",
                                deposit,
                                landlord?.username ?: "X",
                                today,
                                "押金收據"
                            )
                            val fileName = "押金收據_${tenantName}_${selectedRoom}_${SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())}.pdf"
                            saveReceiptLauncher.launch(fileName)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("產生押金收據") }
                }

            } else {
                Text("合約預覽", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 8.dp)
                ) {
                    Text(previewText, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(8.dp))

                Button(onClick = {
                    val fileName = "電子合約_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.pdf"
                    saveContractLauncher.launch(fileName)
                }) {
                    Text("產生 PDF 電子合約")
                }

                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showPreview = false },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Text("返回修改")
                }
            }
        }
    }
}