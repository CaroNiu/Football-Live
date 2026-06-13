package com.fifa.plugin.util;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 世界杯 2026 参赛队英文名 → 中文名翻译
 * <p>
 * TheSportsDB 返的是英文, 百度体育的 HTML 是中文. 这里把英文统一成中文显示
 */
public final class TeamNames {

    private static final Map<String, String> EN_TO_CN = new LinkedHashMap<>();

    static {
        // ---- Group A ----
        EN_TO_CN.put("Mexico", "墨西哥");
        EN_TO_CN.put("South Africa", "南非");
        EN_TO_CN.put("South Korea", "韩国");
        EN_TO_CN.put("Korea Republic", "韩国");
        EN_TO_CN.put("Czech Republic", "捷克");
        EN_TO_CN.put("Czechia", "捷克");

        // ---- Group B ----
        EN_TO_CN.put("Canada", "加拿大");
        EN_TO_CN.put("USA", "美国");
        EN_TO_CN.put("United States", "美国");
        EN_TO_CN.put("Qatar", "卡塔尔");
        EN_TO_CN.put("Switzerland", "瑞士");
        EN_TO_CN.put("Bosnia-Herzegovina", "波黑");
        EN_TO_CN.put("Bosnia and Herzegovina", "波黑");
        EN_TO_CN.put("Paraguay", "巴拉圭");

        // ---- Group C ----
        EN_TO_CN.put("Brazil", "巴西");
        EN_TO_CN.put("Morocco", "摩洛哥");
        EN_TO_CN.put("Haiti", "海地");
        EN_TO_CN.put("Scotland", "苏格兰");

        // ---- Group D ----
        EN_TO_CN.put("Australia", "澳大利亚");
        EN_TO_CN.put("Turkey", "土耳其");

        // ---- Group E ----
        EN_TO_CN.put("Germany", "德国");
        EN_TO_CN.put("Curaçao", "库拉索");
        EN_TO_CN.put("Curacao", "库拉索");
        EN_TO_CN.put("Ivory Coast", "科特迪瓦");
        EN_TO_CN.put("Côte d'Ivoire", "科特迪瓦");
        EN_TO_CN.put("Ecuador", "厄瓜多尔");

        // ---- Group F ----
        EN_TO_CN.put("Netherlands", "荷兰");
        EN_TO_CN.put("Japan", "日本");
        EN_TO_CN.put("Sweden", "瑞典");
        EN_TO_CN.put("Tunisia", "突尼斯");

        // ---- Group G ----
        EN_TO_CN.put("Belgium", "比利时");
        EN_TO_CN.put("Egypt", "埃及");
        EN_TO_CN.put("Iran", "伊朗");
        EN_TO_CN.put("New Zealand", "新西兰");

        // ---- Group H ----
        EN_TO_CN.put("Saudi Arabia", "沙特");
        EN_TO_CN.put("Uruguay", "乌拉圭");
        EN_TO_CN.put("Spain", "西班牙");
        EN_TO_CN.put("Cape Verde", "佛得角");
        EN_TO_CN.put("Cape Verde Islands", "佛得角");

        // ---- Group I ----
        EN_TO_CN.put("France", "法国");
        EN_TO_CN.put("Senegal", "塞内加尔");
        EN_TO_CN.put("Iraq", "伊拉克");
        EN_TO_CN.put("Norway", "挪威");

        // ---- Group J ----
        EN_TO_CN.put("Argentina", "阿根廷");
        EN_TO_CN.put("Algeria", "阿尔及利亚");
        EN_TO_CN.put("Austria", "奥地利");
        EN_TO_CN.put("Jordan", "约旦");

        // ---- Group K ----
        EN_TO_CN.put("Portugal", "葡萄牙");
        EN_TO_CN.put("DR Congo", "刚果(金)");
        EN_TO_CN.put("Congo DR", "刚果(金)");
        EN_TO_CN.put("Uzbekistan", "乌兹别克斯坦");
        EN_TO_CN.put("Colombia", "哥伦比亚");

        // ---- Group L ----
        EN_TO_CN.put("England", "英格兰");
        EN_TO_CN.put("Croatia", "克罗地亚");
        EN_TO_CN.put("Ghana", "加纳");
        EN_TO_CN.put("Panama", "巴拿马");
    }

    private TeamNames() {
    }

    /**
     * 把英文队名翻译成中文. 找不到时返回原文
     */
    public static String toChinese(String english) {
        if (english == null || english.isEmpty()) return "";
        String trimmed = english.trim();
        // 直接匹配
        String cn = EN_TO_CN.get(trimmed);
        if (cn != null) return cn;
        // 大小写不敏感匹配
        for (Map.Entry<String, String> e : EN_TO_CN.entrySet()) {
            if (e.getKey().equalsIgnoreCase(trimmed)) return e.getValue();
        }
        return trimmed;  // 找不到时返原文
    }
}
