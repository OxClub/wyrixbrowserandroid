package com.wyrix.browser

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.wyrix.browser.data.WyrixDatabase
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Only schema v1 exists today, so this validates that the exported v1 schema matches the entities.
 * When v2 is introduced: add its Migration to WyrixDatabase.build() and a test here using
 * helper.runMigrationsAndValidate(NAME, 2, true, MIGRATION_1_2).
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), WyrixDatabase::class.java, emptyList(), FrameworkSQLiteOpenHelperFactory())

    @Test fun v1SchemaCreatesAndValidates() {
        helper.createDatabase("migration-test", 1).close()
        helper.runMigrationsAndValidate("migration-test", 1, true).close()
    }
}
