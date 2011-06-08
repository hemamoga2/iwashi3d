package jp.co.qsdn.android.iwashi3d.setting;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;

import android.os.Bundle;

import android.preference.CheckBoxPreference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;

import android.util.Log;

import android.view.View;

import android.widget.Button;

import jp.co.qsdn.android.iwashi3d.R;


public class SettingActivity extends PreferenceActivity {
  private static final String TAG = SettingActivity.class.getName();

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.setting);
    addPreferencesFromResource(R.xml.setting);

    {
      boolean b = Prefs.getInstance(this).getIwashiBoids();
  
      Resources res = getResources();
  
      String key = res.getString(R.string.key_preference_iwashi_boids);
      CheckBoxPreference pref = (CheckBoxPreference)findPreference(key);
      final Prefs prefs = Prefs.getInstance(this);
      pref.setOnPreferenceChangeListener(
        new OnPreferenceChangeListener() {
          @Override
          public boolean onPreferenceChange(Preference preference, Object newValue) {
            Boolean nv = (Boolean)newValue;
            prefs.setIwashiBoids(nv);
            ((CheckBoxPreference) preference).setChecked((Boolean)newValue);
            return false;
          }
        });
      pref.setChecked(b);
    }
    {
      boolean b = Prefs.getInstance(this).getCameraMode();
  
      Resources res = getResources();
  
      String key = res.getString(R.string.key_preference_camera_mode);
      CheckBoxPreference pref = (CheckBoxPreference)findPreference(key);
      final Prefs prefs = Prefs.getInstance(this);
      pref.setOnPreferenceChangeListener(
        new OnPreferenceChangeListener() {
          @Override
          public boolean onPreferenceChange(Preference preference, Object newValue) {
            Boolean nv = (Boolean)newValue;
            prefs.setCameraMode(nv);
            ((CheckBoxPreference) preference).setChecked((Boolean)newValue);
            return false;
          }
        });
      pref.setChecked(b);
    }
    {
      final SettingActivity __this = this;
      Button button = (Button)findViewById(R.id.preference_button_ok);
      button.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          Button button = (Button) v;
          __this.finish();
        }
      });
    }
    {
      Button button = (Button)findViewById(R.id.preference_button_about);
      button.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          Intent intent = new Intent(getApplicationContext(),AboutActivity.class);
          startActivity(intent); 
        }
      });
    }
  }

  @Override
  protected void onDestroy() {
    Log.d(TAG, "start onDestroy");
    boolean b = getPreferenceManager().getSharedPreferences().getBoolean(Prefs.KEY_IWASHI_BOIDS, Prefs.DEFAULT_IWASHI_BOIDS);
    Prefs.getInstance(this).setIwashiBoids(b);
    b = getPreferenceManager().getSharedPreferences().getBoolean(Prefs.KEY_CAMERA_MODE, Prefs.DEFAULT_CAMERA_MODE);
    Prefs.getInstance(this).setCameraMode(b);
    super.onDestroy();
    Log.d(TAG, "end onDestroy");
  }


  public static int getIwashiCount(Context context) {
    return Prefs.getInstance(context).getIwashiCount();
  }

  public static float getIwashiSpeed(Context context) {
    return Prefs.getInstance(context).getIwashiSpeed();
  }

  public static void setIwashiCount(Context context, int iwashiCount) {
    Prefs.getInstance(context).setIwashiCount(iwashiCount);
  }

  public static void setIwashiSpeed(Context context, int iwashiSpeed) {
    Prefs.getInstance(context).setIwashiSpeed(iwashiSpeed);
  }
}