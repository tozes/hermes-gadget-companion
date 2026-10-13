package com.tozes.hermescompanion

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tozes.hermescompanion.core.GadgetLink
import com.tozes.hermescompanion.core.GadgetStore
import com.tozes.hermescompanion.core.MockGadgetLink
import com.tozes.hermescompanion.core.MockScenario
import com.tozes.hermescompanion.link.BluetoothGadgetLink

/**
 * Holds the app's [GadgetStore] across configuration changes. The store
 * talks to real Bluetooth, unless the debug menu picked a mock scenario.
 */
class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private var bluetoothLink: BluetoothGadgetLink? = null

    /** The one real link, made when first needed and kept while the app runs. */
    val bluetooth: BluetoothGadgetLink
        get() = bluetoothLink ?: BluetoothGadgetLink(getApplication()).also { bluetoothLink = it }

    val store = GadgetStore(
        link = link(MockScenario.of(preferences.getString(MockScenario.PREFERENCE_KEY, null))),
        scope = viewModelScope,
        makeRealLink = { bluetooth },
    )

    /** The scenario the debug menu picked, or null for real Bluetooth. */
    val scenario: MockScenario? get() = (store.link as? MockGadgetLink)?.scenario

    private fun link(scenario: MockScenario?): GadgetLink = scenario?.let { MockGadgetLink(it, viewModelScope) } ?: bluetooth

    /**
     * The debug menu's choice, kept across launches. The demo isn't kept: the
     * app always opens on Bluetooth after it.
     */
    fun choose(scenario: MockScenario?) {
        preferences.edit {
            if (scenario == null || scenario == MockScenario.DEMO) {
                remove(MockScenario.PREFERENCE_KEY)
            } else {
                putString(MockScenario.PREFERENCE_KEY, scenario.rawValue)
            }
        }
        store.replaceLink(link(scenario))
    }

    /** `adb shell am start -n … -e mockScenario online`: a scenario for this launch only. */
    fun launchWith(scenario: MockScenario) {
        store.replaceLink(link(scenario))
    }

    override fun onCleared() {
        store.link.disconnect()
        bluetoothLink?.close()
    }

    private companion object {
        const val PREFERENCES = "settings"
    }
}
