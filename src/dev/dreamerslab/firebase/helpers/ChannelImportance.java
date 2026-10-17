package dev.dreamerslab.firebase.helpers;

import android.app.NotificationManager;
import com.google.appinventor.components.common.OptionList;
import java.util.HashMap;
import java.util.Map;

public enum ChannelImportance implements OptionList<String> {
  /** Shown in the shade only, without an icon in the status bar. */
  Min("Min", NotificationManager.IMPORTANCE_MIN),
  /** Shown without a sound. */
  Low("Low", NotificationManager.IMPORTANCE_LOW),
  /** Shown with a sound. */
  Default("Default", NotificationManager.IMPORTANCE_DEFAULT),
  /** Shown with a sound, and pops up on the screen. */
  High("High", NotificationManager.IMPORTANCE_HIGH);

  private static final Map<String, ChannelImportance> lookup = new HashMap<>();

  static {
    for (ChannelImportance option : values()) {
      lookup.put(option.value, option);
    }
  }

  private final String value;
  private final int importance;

  ChannelImportance(String value, int importance) {
    this.value = value;
    this.importance = importance;
  }

  public String toUnderlyingValue() {
    return value;
  }

  public int importance() {
    return importance;
  }

  public static ChannelImportance fromUnderlyingValue(String value) {
    return lookup.get(value);
  }
}
