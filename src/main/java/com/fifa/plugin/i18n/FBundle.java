package com.fifa.plugin.i18n;

import com.intellij.AbstractBundle;
import com.intellij.reference.SoftReference;
import org.jetbrains.annotations.PropertyKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.Reference;
import java.util.ResourceBundle;

/**
 * UI 文案资源包 — 默认英文, zh_CN 覆盖中文
 * <p>
 * 语言自动跟随 IDE 设置 (Settings → Appearance → Language), 无需插件侧开关。
 * 比赛/球队/新闻等业务数据保持百度中文源, 不走本 bundle。
 */
public final class FBundle {

    private static final String BUNDLE_NAME = "i18n.FootballLiveBundle";
    private static Reference<ResourceBundle> ourBundle;

    private FBundle() {
    }

    private static ResourceBundle getBundle() {
        // 语言选择: 设置里的手动覆盖 (en/zh) 优先; 空值 = 跟随 JVM 默认区域
        // (注意: 不能用 DynamicBundle — 它只跟随 IDE 的 Language 设置, 中文 Windows + 英文 IDE 会显示英文)
        String lang = com.fifa.plugin.settings.FifaSettingsState.getInstance().getUiLanguage();
        java.util.Locale locale = "en".equals(lang) ? java.util.Locale.ENGLISH
                : "zh".equals(lang) ? java.util.Locale.SIMPLIFIED_CHINESE
                : java.util.Locale.getDefault();
        ResourceBundle bundle = SoftReference.dereference(ourBundle);
        if (bundle == null || !locale.equals(ourLocale)) {
            bundle = java.util.ResourceBundle.getBundle(
                    BUNDLE_NAME, locale, FBundle.class.getClassLoader());
            ourLocale = locale;
            ourBundle = new SoftReference<>(bundle);
        }
        return bundle;
    }

    private static java.util.Locale ourLocale = null;

    public static String message(@PropertyKey(resourceBundle = BUNDLE_NAME) @NotNull String key,
                                 Object @Nullable ... params) {
        return AbstractBundle.messageOrDefault(getBundle(), key, key, params);
    }
}
