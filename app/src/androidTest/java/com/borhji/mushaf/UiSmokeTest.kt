package com.borhji.mushaf

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * اختبار واجهة حقيقي: يفتح شاشة التطبيق ويتحقق من العناصر الأساسية المطلوبة.
 */
@RunWith(AndroidJUnit4::class)
class UiSmokeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun showsAppIdentityLibraryAndSurahRows() {
        runBlocking {
            TestSupport.wipeLibrary()
            TestSupport.seedLibrary(surahs = listOf(1, 2), seconds = 5)
        }
        composeRule.waitUntil(20_000L) {
            composeRule.onAllNodesWithText("المكتبة: 2 سورة مستوردة")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("المصحف المرتل").assertIsDisplayed()
        composeRule.onNodeWithText("أ.د. محمد برهجي").assertIsDisplayed()
        composeRule.onNodeWithText("حفص عن عاصم بقصر المنفصل").assertIsDisplayed()
        composeRule.onAllNodesWithText("استيراد التلاوات").onFirst().assertIsDisplayed()
        composeRule.onNodeWithText("ابحث باسم السورة أو رقمها").assertIsDisplayed()
        composeRule.onNodeWithText("الفاتحة").assertIsDisplayed()
        composeRule.onNodeWithText("البقرة").assertIsDisplayed()
    }

    @Test
    fun showsEmptyStateWhenNoSurahImported() {
        runBlocking { TestSupport.wipeLibrary() }
        composeRule.waitUntil(20_000L) {
            composeRule.onAllNodesWithText("لم تُستورد أي تلاوات بعد")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("لم تُستورد أي تلاوات بعد").assertIsDisplayed()
        composeRule.onAllNodesWithText("استيراد التلاوات").onFirst().assertIsDisplayed()
    }
}
