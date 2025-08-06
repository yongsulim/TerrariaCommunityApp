package com.example.terrariacommunity

import org.junit.Test

import org.junit.Assert.*
import kotlinx.coroutines.runBlocking

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun testAddBlockedUser() = runBlocking {
        val repo = UserRepository()
        val myUid = "testUser1"
        val targetUid = "testUser2"
        // 차단 추가
        val result = repo.addBlockedUser(myUid, targetUid)
        assertTrue(result)
        // 차단 해제
        val result2 = repo.removeBlockedUser(myUid, targetUid)
        assertTrue(result2)
    }
}