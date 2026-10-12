package com.jpd.hz.platform.jellyfin.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.UUID

class DeviceIdentityTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `the same ID on every call, and from a new instance on the same folder`() {
        val folder = temp.newFolder("no_backup")

        val first = DeviceIdentity(folder).id()

        assertEquals(first, DeviceIdentity(folder).id())
        assertEquals(first, UUID.fromString(first).toString())
    }

    @Test
    fun `the ID is stored in the given folder`() {
        val folder = temp.newFolder("no_backup")

        val id = DeviceIdentity(folder).id()

        assertEquals(id, File(folder, "device_id").readText())
    }

    @Test
    fun `a missing or blank file gets a new ID`() {
        val folder = temp.newFolder("no_backup")
        val first = DeviceIdentity(folder).id()
        File(folder, "device_id").writeText("  ")

        val second = DeviceIdentity(folder).id()
        File(folder, "device_id").delete()
        val third = DeviceIdentity(folder).id()

        assertTrue(second.isNotBlank())
        assertNotEquals(first, second)
        assertNotEquals(second, third)
    }
}
