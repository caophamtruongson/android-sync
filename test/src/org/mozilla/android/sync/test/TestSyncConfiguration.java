/* Any copyright is dedicated to the Public Domain.
   http://creativecommons.org/publicdomain/zero/1.0/ */

package org.mozilla.android.sync.test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.mozilla.gecko.sync.PrefsSource;
import org.mozilla.gecko.sync.SyncConfiguration;

import android.content.SharedPreferences;

public class TestSyncConfiguration extends AndroidSyncTestCase implements PrefsSource {
  public static final String TEST_PREFS_NAME = "test";

  /*
   * PrefsSource methods.
   */
  @Override
  public SharedPreferences getPrefs(String name, int mode) {
    return this.getApplicationContext().getSharedPreferences(name, mode);
  }

  public void testEnabledEngineNames() {
    SyncConfiguration config = null;
    SharedPreferences prefs = getPrefs(TEST_PREFS_NAME, 0);

    config = new SyncConfiguration(TEST_PREFS_NAME, this);
    config.enabledEngineNames = new HashSet<String>();
    config.enabledEngineNames.add("test1");
    config.enabledEngineNames.add("test2");
    config.persistToPrefs();
    assertTrue(prefs.contains(SyncConfiguration.PREF_ENABLED_ENGINE_NAMES));
    config = new SyncConfiguration(TEST_PREFS_NAME, this);
    Set<String> expected = new HashSet<String>();
    for (String name : new String[] { "test1", "test2" }) {
      expected.add(name);
    }
    assertEquals(expected, config.enabledEngineNames);

    config.enabledEngineNames = null;
    config.persistToPrefs();
    assertFalse(prefs.contains(SyncConfiguration.PREF_ENABLED_ENGINE_NAMES));
    config = new SyncConfiguration(TEST_PREFS_NAME, this);
    assertNull(config.enabledEngineNames);
  }

  public void testSyncID() {
    SyncConfiguration config = null;
    SharedPreferences prefs = getPrefs(TEST_PREFS_NAME, 0);

    config = new SyncConfiguration(TEST_PREFS_NAME, this);
    config.syncID = "test1";
    config.persistToPrefs();
    assertTrue(prefs.contains(SyncConfiguration.PREF_SYNC_ID));
    config = new SyncConfiguration(TEST_PREFS_NAME, this);
    assertEquals("test1", config.syncID);
  }

  public void testStoreSelectedEnginesToPrefs() {
    SharedPreferences prefs = getPrefs(TEST_PREFS_NAME, 0);
    // Store engines, excluding history/forms special case.
    Map<String, Boolean> expectedEngines = new HashMap<String, Boolean>();
    expectedEngines.put("test1", true);
    expectedEngines.put("test2", false);
    expectedEngines.put("test3", true);

    SyncConfiguration.storeSelectedEnginesToPrefs(prefs, expectedEngines);

    // Read values from selectedEngines.
    assertTrue(prefs.contains(SyncConfiguration.PREF_USER_SELECTED_ENGINES_TO_SYNC));
    SyncConfiguration config = null;
    config = new SyncConfiguration(TEST_PREFS_NAME, this);
    config.loadFromPrefs(prefs);
    assertEquals(expectedEngines, config.userSelectedEngines);
  }

  /**
   * Tests dependency of forms engine on history engine.
   */
  public void testSelectedEnginesHistoryAndForms() {
    SharedPreferences prefs = getPrefs(TEST_PREFS_NAME, 0);
    // Store engines, excluding history/forms special case.
    Map<String, Boolean> storedEngines = new HashMap<String, Boolean>();
    storedEngines.put("history", true);

    SyncConfiguration.storeSelectedEnginesToPrefs(prefs, storedEngines);

    // Expected engines.
    storedEngines.put("forms", true);
    // Read values from selectedEngines.
    assertTrue(prefs.contains(SyncConfiguration.PREF_USER_SELECTED_ENGINES_TO_SYNC));
    SyncConfiguration config = null;
    config = new SyncConfiguration(TEST_PREFS_NAME, this);
    config.loadFromPrefs(prefs);
    assertEquals(storedEngines, config.userSelectedEngines);
  }

  public void testsSelectedEnginesNoHistoryNorForms() {
    SharedPreferences prefs = getPrefs(TEST_PREFS_NAME, 0);
    // Store engines, excluding history/forms special case.
    Map<String, Boolean> storedEngines = new HashMap<String, Boolean>();
    storedEngines.put("forms", true);

    SyncConfiguration.storeSelectedEnginesToPrefs(prefs, storedEngines);

    // Read values from selectedEngines.
    assertTrue(prefs.contains(SyncConfiguration.PREF_USER_SELECTED_ENGINES_TO_SYNC));
    SyncConfiguration config = null;
    config = new SyncConfiguration(TEST_PREFS_NAME, this);
    config.loadFromPrefs(prefs);
    // Forms should not be selected if history is not present.
    assertTrue(config.userSelectedEngines.isEmpty());
  }
}
