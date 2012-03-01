/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings;

import com.android.settings.wifi.WifiApEnabler;

import android.bluetooth.BluetoothPan;
import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.os.SystemProperties;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.AssetManager;
import android.net.ConnectivityManager;
import android.os.Environment;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.util.Log;
import android.webkit.WebView;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Locale;

/*
 * Displays preferences for Tethering.
 */
public class TetherSettings extends PreferenceActivity {
    private static final String USB_TETHER_SETTINGS = "usb_tether_settings";
    private static final String BT_TETHER_SETTINGS = "bt_tether_settings";
    private static final String ENABLE_WIFI_AP = "enable_wifi_ap";
    private static final String WIFI_AP_SETTINGS = "wifi_ap_settings";
    private static final String TETHERING_HELP = "tethering_help";
    private static final String USB_HELP_MODIFIER = "usb_";
    private static final String WIFI_HELP_MODIFIER = "wifi_";
    private static final String HELP_URL = "file:///android_asset/html/%y%z/tethering_%xhelp.html";
    private static final String HELP_PATH = "html/%y%z/tethering_help.html";
    private static final String TETHER_ON_PLUGIN = "pref_tether_on_plugin";

    private static final int DIALOG_TETHER_HELP = 1;

    private WebView mView;
    private CheckBoxPreference mUsbTether;
    private CheckBoxPreference mTetherOnPlugin;
    private CheckBoxPreference mBtTether;

    private CheckBoxPreference mEnableWifiAp;
    private PreferenceScreen mWifiApSettings;
    private WifiApEnabler mWifiApEnabler;
    private PreferenceScreen mTetherHelp;

    private BroadcastReceiver mTetherChangeReceiver;

    private String[] mUsbRegexs;
    private ArrayList mUsbIfaces;

    private String[] mBtRegexs;
    private ArrayList mBtIfaces;

    private String[] mWifiRegexs;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        addPreferencesFromResource(R.xml.tether_prefs);

        mEnableWifiAp = (CheckBoxPreference) findPreference(ENABLE_WIFI_AP);
        mWifiApSettings = (PreferenceScreen) findPreference(WIFI_AP_SETTINGS);
        mUsbTether = (CheckBoxPreference) findPreference(USB_TETHER_SETTINGS);
        mBtTether = (CheckBoxPreference) findPreference(BT_TETHER_SETTINGS);
        mTetherHelp = (PreferenceScreen) findPreference(TETHERING_HELP);
        mTetherOnPlugin = (CheckBoxPreference) findPreference(TETHER_ON_PLUGIN);
        mTetherOnPlugin.setChecked(Settings.System.getInt(getContentResolver(), 
            Settings.System.TETHER_ON_PLUGIN, 0) != 0);

        ConnectivityManager cm =
                (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);

        mUsbRegexs = cm.getTetherableUsbRegexs();
        mBtRegexs = cm.getTetherableBtRegexs();
        mWifiRegexs = cm.getTetherableWifiRegexs();
        if ((mUsbRegexs.length == 0 || Utils.isMonkeyRunning()) &&
                                        mBtRegexs.length == 0 && mWifiRegexs.length == 0) {
            getPreferenceScreen().removePreference(mUsbTether);
            getPreferenceScreen().removePreference(mTetherOnPlugin);
            getPreferenceScreen().removePreference(mBtTether);
            getPreferenceScreen().removePreference(mEnableWifiAp);
            getPreferenceScreen().removePreference(mWifiApSettings);

            setTitle(R.string.tether_settings_title_usb);
        } else if ((mUsbRegexs.length == 0 || Utils.isMonkeyRunning()) &&
                                        mBtRegexs.length == 0) {
            getPreferenceScreen().removePreference(mUsbTether);
            getPreferenceScreen().removePreference(mTetherOnPlugin);
            getPreferenceScreen().removePreference(mBtTether);
            setTitle(R.string.tether_settings_title_wifi);
        } else if (mBtRegexs.length == 0 && mWifiRegexs.length == 0) {
            getPreferenceScreen().removePreference(mEnableWifiAp);
            getPreferenceScreen().removePreference(mWifiApSettings);
            getPreferenceScreen().removePreference(mBtTether);
            setTitle(R.string.tether_settings_title_usb);
        } else if ((mUsbRegexs.length == 0 || Utils.isMonkeyRunning()) &&
                                        mWifiRegexs.length == 0) {
            getPreferenceScreen().removePreference(mUsbTether);
            getPreferenceScreen().removePreference(mTetherOnPlugin);
            getPreferenceScreen().removePreference(mEnableWifiAp);
            getPreferenceScreen().removePreference(mWifiApSettings);
            setTitle(R.string.tether_settings_title_bt);
        } else {
            setTitle(R.string.tether_settings_title_all);
        }
        if(mWifiRegexs.length != 0) {
             mWifiApEnabler = new WifiApEnabler(this, mEnableWifiAp);
        }
        mView = new WebView(this);
        updateState();
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        if (id == DIALOG_TETHER_HELP) {
            Locale locale = Locale.getDefault();

            // check for the full language + country resource, if not there, try just language
            AssetManager am = getAssets();
            String path = HELP_PATH.replace("%y", locale.getLanguage().toLowerCase());
            path = path.replace("%z", "_"+locale.getCountry().toLowerCase());
            boolean useCountry = true;
            InputStream is = null;
            try {
                is = am.open(path);
            } catch (Exception e) {
                useCountry = false;
            } finally {
                if (is != null) {
                    try {
                        is.close();
                    } catch (Exception e) {}
                }
            }
            String url = HELP_URL.replace("%y", locale.getLanguage().toLowerCase());
            url = url.replace("%z", (useCountry ? "_"+locale.getCountry().toLowerCase() : ""));
            //TODO Add help info for Bluetooth tethering
            if ((mUsbRegexs.length != 0) && (mWifiRegexs.length == 0)) {
                url = url.replace("%x", USB_HELP_MODIFIER);
            } else if ((mWifiRegexs.length != 0) && (mUsbRegexs.length == 0)) {
                url = url.replace("%x", WIFI_HELP_MODIFIER);
            } else {
                // could assert that both wifi and usb have regexs, but the default
                // is to use this anyway so no check is needed
                url = url.replace("%x", "");
            }

            mView.loadUrl(url);

            return new AlertDialog.Builder(this)
                .setCancelable(true)
                .setTitle(R.string.tethering_help_button_text)
                .setView(mView)
                .create();
        }
        return null;
    }

    private class TetherChangeReceiver extends BroadcastReceiver {
        public void onReceive(Context content, Intent intent) {
            if (intent.getAction().equals(ConnectivityManager.ACTION_TETHER_STATE_CHANGED)) {
                // TODO - this should understand the interface types
                ArrayList<String> available = intent.getStringArrayListExtra(
                        ConnectivityManager.EXTRA_AVAILABLE_TETHER);
                ArrayList<String> active = intent.getStringArrayListExtra(
                        ConnectivityManager.EXTRA_ACTIVE_TETHER);
                ArrayList<String> errored = intent.getStringArrayListExtra(
                        ConnectivityManager.EXTRA_ERRORED_TETHER);
                updateUsbState(available.toArray(), active.toArray(), errored.toArray());
                updateBtState(available.toArray(), active.toArray(), errored.toArray());
            } else if (intent.getAction().equals(Intent.ACTION_MEDIA_SHARED) ||
                       intent.getAction().equals(Intent.ACTION_MEDIA_UNSHARED)) {
                updateState();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        IntentFilter filter = new IntentFilter(ConnectivityManager.ACTION_TETHER_STATE_CHANGED);
        mTetherChangeReceiver = new TetherChangeReceiver();
        Intent intent = registerReceiver(mTetherChangeReceiver, filter);

        filter = new IntentFilter();
        filter.addAction(Intent.ACTION_MEDIA_SHARED);
        filter.addAction(Intent.ACTION_MEDIA_UNSHARED);
        filter.addDataScheme("file");
        registerReceiver(mTetherChangeReceiver, filter);

        if (intent != null) mTetherChangeReceiver.onReceive(this, intent);
        mWifiApEnabler.resume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mTetherChangeReceiver);
        mTetherChangeReceiver = null;
        mWifiApEnabler.pause();
    }

    private void updateState() {
        ConnectivityManager cm =
                (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);

        String[] available = cm.getTetherableIfaces();
        String[] tethered = cm.getTetheredIfaces();
        String[] errored = cm.getTetheringErroredIfaces();
        updateUsbState(available, tethered, errored);
        updateBtState(available, tethered, errored);
    }

    private void updateUsbState(Object[] available, Object[] tethered,
            Object[] errored) {
        ConnectivityManager cm =
                (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
        boolean usbTethered = false;
        boolean usbAvailable = false;
        int usbError = ConnectivityManager.TETHER_ERROR_NO_ERROR;
        boolean usbErrored = false;
        boolean massStorageActive =
                Environment.MEDIA_SHARED.equals(Environment.getExternalStorageState());
        for (Object o : available) {
            String s = (String)o;
            for (String regex : mUsbRegexs) {
                if (s.matches(regex)) {
                    usbAvailable = true;
                    if (usbError == ConnectivityManager.TETHER_ERROR_NO_ERROR) {
                        usbError = cm.getLastTetherError(s);
                    }
                }
            }
        }
        for (Object o : tethered) {
            String s = (String)o;
            for (String regex : mUsbRegexs) {
                if (s.matches(regex)) usbTethered = true;
            }
        }
        for (Object o: errored) {
            String s = (String)o;
            for (String regex : mUsbRegexs) {
                if (s.matches(regex)) usbErrored = true;
            }
        }

        if (usbTethered) {
            mUsbTether.setSummary(R.string.usb_tethering_active_subtext);
            mUsbTether.setEnabled(true);
            mUsbTether.setChecked(true);
            mTetherOnPlugin.setEnabled(true);
            mTetherOnPlugin.setChecked(false);
        } else if (usbAvailable) {
            if (usbError == ConnectivityManager.TETHER_ERROR_NO_ERROR) {
                mUsbTether.setSummary(R.string.usb_tethering_available_subtext);
            } else {
                mUsbTether.setSummary(R.string.usb_tethering_errored_subtext);
            }
            mUsbTether.setEnabled(true);
            mUsbTether.setChecked(false);
            mTetherOnPlugin.setEnabled(true);
            mTetherOnPlugin.setChecked(false);
        } else if (usbErrored) {
            mUsbTether.setSummary(R.string.usb_tethering_errored_subtext);
            mUsbTether.setEnabled(false);
            mUsbTether.setChecked(false);
            mTetherOnPlugin.setEnabled(false);
            mTetherOnPlugin.setChecked(false);
        } else if (massStorageActive) {
            mUsbTether.setSummary(R.string.usb_tethering_storage_active_subtext);
            mUsbTether.setEnabled(false);
            mUsbTether.setChecked(false);
            mTetherOnPlugin.setEnabled(false);
            mTetherOnPlugin.setChecked(false);
        } else {
            mUsbTether.setSummary(R.string.usb_tethering_unavailable_subtext);
            mUsbTether.setEnabled(false);
            mUsbTether.setChecked(false);
            mTetherOnPlugin.setEnabled(false);
            mTetherOnPlugin.setChecked(false);
        }
    }

    private void updateBtState(Object[] available, Object[] tethered,
            Object[] errored) {
        ConnectivityManager cm =
                (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
        BluetoothPan btp = new BluetoothPan();

        boolean btTethered = false;
        boolean btAvailable = false;
        int btError = ConnectivityManager.TETHER_ERROR_NO_ERROR;
        boolean btErrored = false;
        boolean btNapEnabled = btp.isPanNapServiceEnabled();
	
        for (Object o : available) {
            String s = (String)o;
            for (String regex : mBtRegexs) {
                if (s.matches(regex)) {
                    btAvailable = true;
                    if (btError == ConnectivityManager.TETHER_ERROR_NO_ERROR) {
                        btError = cm.getLastTetherError(s);
                    }
                }
            }
        }
        for (Object o : tethered) {
            String s = (String)o;
            for (String regex : mBtRegexs) {
                if (s.matches(regex)) btTethered = true;
            }
        }
        for (Object o: errored) {
            String s = (String)o;
            for (String regex : mBtRegexs) {
                if (s.matches(regex)) btErrored = true;
            }
        }

        if (btTethered) {
            mBtTether.setSummary(R.string.bt_tethering_active_subtext);
            mBtTether.setEnabled(true);
            mBtTether.setChecked(true);
        } else if (btNapEnabled) {
            if (btError == ConnectivityManager.TETHER_ERROR_NO_ERROR) {
                mBtTether.setSummary(R.string.bt_tethering_available_subtext);
            } else {
                mBtTether.setSummary(R.string.bt_tethering_errored_subtext);
            }
            mBtTether.setEnabled(true);
            mBtTether.setChecked(true);
        } else if (btErrored) {
            mBtTether.setSummary(R.string.bt_tethering_errored_subtext);
            mBtTether.setEnabled(false);
            mBtTether.setChecked(false);
        } else {
            mBtTether.setSummary(R.string.bt_tethering_unavailable_subtext);
            mBtTether.setEnabled(true);
            mBtTether.setChecked(false);
        }
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (preference == mUsbTether) {
            boolean newState = mUsbTether.isChecked();

            ConnectivityManager cm =
                    (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);

            if (newState) {
                String[] available = cm.getTetherableIfaces();

                String usbIface = findIface(available, mUsbRegexs);
                if (usbIface == null) {
                    updateState();
                    return true;
                }
                if (cm.tether(usbIface) != ConnectivityManager.TETHER_ERROR_NO_ERROR) {
                    mUsbTether.setChecked(false);
                    mTetherOnPlugin.setChecked(false);
                    mUsbTether.setSummary(R.string.usb_tethering_errored_subtext);
                    return true;
                }
                mUsbTether.setSummary("");
            } else {
                String [] tethered = cm.getTetheredIfaces();

                String usbIface = findIface(tethered, mUsbRegexs);
                if (usbIface == null) {
                    updateState();
                    return true;
                }
                if (cm.untether(usbIface) != ConnectivityManager.TETHER_ERROR_NO_ERROR) {
                    mUsbTether.setSummary(R.string.usb_tethering_errored_subtext);
                    return true;
                }
                mUsbTether.setSummary("");
            }
        } else if (preference == mBtTether) {
            boolean newState = mBtTether.isChecked();

            ConnectivityManager cm =
                    (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
            BluetoothPan btp = new BluetoothPan();

            if (newState) {
                if(!btp.enablePanNapService(true)) {
                    mBtTether.setChecked(false);
                    mBtTether.setSummary(R.string.bt_tethering_errored_subtext);
                }
                updateState();
            } else {
                btp.enablePanNapService(false);
                updateState();
            }
        } else if (preference == mTetherHelp) {

            showDialog(DIALOG_TETHER_HELP);
        } else if (preference == mTetherOnPlugin) {
            boolean value;
            value = mTetherOnPlugin.isChecked();
            Settings.System.putInt(getContentResolver(), 
                Settings.System.TETHER_ON_PLUGIN, value ? 1 : 0);
            return true;
        }
        return false;
    }

    private String findIface(String[] ifaces, String[] regexes) {
        for (String iface : ifaces) {
            for (String regex : regexes) {
                if (iface.matches(regex)) {
                    return iface;
                }
            }
        }
        return null;
    }
}
