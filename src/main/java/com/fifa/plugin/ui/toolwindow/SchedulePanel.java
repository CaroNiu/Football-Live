package com.fifa.plugin.ui.toolwindow;

import com.fifa.plugin.i18n.FBundle;
import com.fifa.plugin.api.BaiduMatchListClient;
import com.fifa.plugin.api.BaiduSportsClient;
import com.fifa.plugin.core.LeagueRegistry;
import com.fifa.plugin.model.Match;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.fifa.plugin.ui.component.ClickableStatusLabel;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 赛程面板 - 数据源: 百度体育全赛事聚合 (近 3 天, 跨所有联赛)
 * <p>
 * 日期选择窗口 = 今天前后各 7 天:
 * - 昨天/今天/明天: 来自聚合数据 (30s 轮询, 零额外请求)
 * - 其余日期: 懒加载 — 选中时走 /al/api/match/schedules (实测支持任意日期),
 *   具体赛事拉 1 个请求; "全部赛事" 拉主要赛事 (8 个) 合并, 返回的多日数据顺带回填缓存
 * <p>
 * 赛事过滤跟随全局选择条 (LeagueRegistry)
 */
public class SchedulePanel extends JPanel {

    private static final Logger LOG = Logger.getInstance(SchedulePanel.class);

    /** 日期窗口: 今天前后各 7 天 */
    private static final int WINDOW_DAYS = 7;
    /** "全部赛事" 视角下远日期懒加载的主要赛事列表 (schedules 端点不支持全赛事聚合) */
    private static final String[] TOP_LEAGUES = {"英超", "西甲", "意甲", "德甲", "法甲", "欧冠", "中超", "亚冠"};
    private static final String[] WEEK_CN = {"一", "二", "三", "四", "五", "六", "日"};

    private final JComboBox<BaiduSportsClient.DateLabel> dateSelector;
    private final JBTable table;
    private final DefaultTableModel tableModel;
    private final JBLabel statusLabel;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            r -> {
                Thread t = new Thread(r, "FIFA-Schedule-Poller");
                t.setDaemon(true);
                return t;
            }
    );

    /** 数据锁: 保护 cache / fetchedFor / inFlight (EDT 渲染 + pooled 写入) */
    private final Object dataLock = new Object();
    /** 缓存: 日期 → 该日比赛列表 */
    private final Map<LocalDate, List<Match>> cache = new LinkedHashMap<>();
    /** 远日期已拉取的赛事上下文: date → "ALL" 或赛事名 (切赛事后避免命中错误缓存) */
    private final Map<LocalDate, String> fetchedFor = new HashMap<>();
    /** 正在懒加载的日期, 防重复请求 */
    private final Set<LocalDate> inFlight = new HashSet<>();
    /** 当前展示的日期 (赛事切换时从缓存即时重渲染) */
    private LocalDate currentDate;
    /** 球队过滤 (球队榜点击跳转进入; "" = 不过滤) */
    private volatile String teamFilter = "";
    /** 球队过滤清除按钮 (仅过滤激活时可见) */
    private final JButton clearTeamBtn = new JButton();

    public SchedulePanel() {
        setLayout(new BorderLayout());

        // ---- 顶部: 日期选择器 ----
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        toolbar.add(new JBLabel(FBundle.message("lbl.date")));

        dateSelector = new JComboBox<>();
        dateSelector.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof BaiduSportsClient.DateLabel dl) {
                    LocalDate today = LocalDate.now();
                    String label = dl.date.format(DateTimeFormatter.ofPattern("MM-dd")) + " · " + dl.suffix;
                    if (dl.date.equals(today)) label += FBundle.message("sch.today.suffix");
                    else if (dl.date.equals(today.plusDays(1))) label += FBundle.message("sch.tomorrow.suffix");
                    int n = cachedCount(dl.date);
                    if (n > 0) label += " · " + n + "场";
                    setText(label);
                }
                return this;
            }
        });
        dateSelector.setPreferredSize(new Dimension(240, 28));
        dateSelector.addActionListener(e -> {
            BaiduSportsClient.DateLabel dl = (BaiduSportsClient.DateLabel) dateSelector.getSelectedItem();
            if (dl != null) showForDate(dl.date);
        });
        toolbar.add(dateSelector);

        // 前后日快捷切换: 按钮 + 键盘 ← / → (表格聚焦时方向键仍归表格单元格导航)
        JButton prevBtn = new JButton("←");
        prevBtn.setToolTipText(FBundle.message("btn.prev.day.tooltip"));
        prevBtn.setMargin(new Insets(2, 8, 2, 8));
        prevBtn.addActionListener(e -> shiftDay(-1));
        toolbar.add(prevBtn);
        JButton nextBtn = new JButton("→");
        nextBtn.setToolTipText(FBundle.message("btn.next.day.tooltip"));
        nextBtn.setMargin(new Insets(2, 8, 2, 8));
        nextBtn.addActionListener(e -> shiftDay(1));
        toolbar.add(nextBtn);

        javax.swing.InputMap im = getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        javax.swing.ActionMap am = getActionMap();
        im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_LEFT, 0), "fifaPrevDay");
        im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_RIGHT, 0), "fifaNextDay");
        am.put("fifaPrevDay", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) { shiftDay(-1); }
        });
        am.put("fifaNextDay", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) { shiftDay(1); }
        });

        JButton todayBtn = new JButton(FBundle.message("btn.today"));
        todayBtn.addActionListener(e -> {
            LocalDate today = LocalDate.now();
            for (int i = 0; i < dateSelector.getItemCount(); i++) {
                if (dateSelector.getItemAt(i).date.equals(today)) {
                    dateSelector.setSelectedIndex(i);
                    return;
                }
            }
        });
        toolbar.add(todayBtn);

        JButton refreshBtn = new JButton(FBundle.message("btn.refresh"));
        refreshBtn.addActionListener(e -> refresh());
        toolbar.add(refreshBtn);

        // 球队过滤 chip (球队榜点击跳转进入时出现, 点击清除)
        clearTeamBtn.setVisible(false);
        clearTeamBtn.setToolTipText(FBundle.message("btn.clear.team.tooltip"));
        clearTeamBtn.setMargin(new Insets(2, 8, 2, 8));
        clearTeamBtn.addActionListener(e -> setTeamFilter(""));
        toolbar.add(clearTeamBtn);

        add(toolbar, BorderLayout.NORTH);

        // ---- 表格 ----
        String[] columns = {FBundle.message("col.time"), FBundle.message("col.league"), FBundle.message("col.round"), FBundle.message("col.home"), FBundle.message("col.score"), FBundle.message("col.away"), FBundle.message("col.status")};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        table = new JBTable(tableModel);
        table.setFillsViewportHeight(true);
        table.setAutoCreateRowSorter(true); // 列头点击排序
        table.setRowHeight(24);
        table.getColumnModel().getColumn(0).setPreferredWidth(60);
        table.getColumnModel().getColumn(1).setPreferredWidth(70);
        table.getColumnModel().getColumn(2).setPreferredWidth(120);
        table.getColumnModel().getColumn(3).setPreferredWidth(130);
        table.getColumnModel().getColumn(4).setPreferredWidth(55);
        table.getColumnModel().getColumn(5).setPreferredWidth(130);
        table.getColumnModel().getColumn(6).setPreferredWidth(80);
        add(new JBScrollPane(table), BorderLayout.CENTER);

        // ---- 状态栏 ----
        statusLabel = new ClickableStatusLabel(this::refresh);
        add(statusLabel, BorderLayout.SOUTH);

        // 跟随全局赛事切换: 近 3 天从缓存即时重渲染 (零网络); 远日期按新赛事上下文重新懒加载
        LeagueRegistry.getInstance().addListener(() -> SwingUtilities.invokeLater(() -> {
            if (currentDate != null) showForDate(currentDate);
        }));

        rebuildDateSelector();
        loadAndShow();
        scheduler.scheduleWithFixedDelay(() -> { if (visibilityGate.getAsBoolean()) loadAndShow(); }, 30, 30, TimeUnit.SECONDS);
    }

    /** 重建日期下拉: 今天前后各 7 天, suffix = 周几 */
    private void rebuildDateSelector() {
        LocalDate keep = currentDate;
        dateSelector.removeAllItems();
        LocalDate today = LocalDate.now();
        int selectIdx = 0;
        for (int i = -WINDOW_DAYS; i <= WINDOW_DAYS; i++) {
            LocalDate d = today.plusDays(i);
            BaiduSportsClient.DateLabel dl = new BaiduSportsClient.DateLabel();
            dl.date = d;
            dl.suffix = "周" + WEEK_CN[d.getDayOfWeek().getValue() - 1];
            dl.desc = "";
            dl.disabled = false;
            dateSelector.addItem(dl);
            if (keep != null && d.equals(keep)) selectIdx = i + WINDOW_DAYS;
        }
        dateSelector.setSelectedIndex(selectIdx);
    }

    /** 某日期缓存条数 (渲染线程用) */
    private int cachedCount(LocalDate date) {
        synchronized (dataLock) {
            List<Match> l = cache.get(date);
            return l == null ? 0 : l.size();
        }
    }

    /** 球队榜跳转: 按球队过滤赛程 (空串清除); 与赛事过滤叠加, 换日期/赛事后仍生效 */
    public void setTeamFilter(String team) {
        teamFilter = team == null ? "" : team;
        clearTeamBtn.setText(teamFilter.isEmpty() ? "" : FBundle.message("sch.team.chip", teamFilter));
        clearTeamBtn.setVisible(!teamFilter.isEmpty());
        if (currentDate != null) showForDate(currentDate);
    }

    /** 日期窗口内前后移动一天 (按钮 / ← → 快捷键共用) */
    private void shiftDay(int delta) {
        int idx = dateSelector.getSelectedIndex() + delta;
        if (idx < 0 || idx >= dateSelector.getItemCount()) {
            setStatus(delta < 0 ? FBundle.message("sch.window.start", WINDOW_DAYS)
                    : FBundle.message("sch.window.end", WINDOW_DAYS));
            return;
        }
        dateSelector.setSelectedIndex(idx);
    }

    /**
     * 拉取一次全赛事聚合数据 (近 3 天), 合并进缓存, 刷新日期条数
     * <p>
     * 网络 IO 在 pooled 线程; 远日期懒加载缓存不被轮询清除
     */
    private void loadAndShow() {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            BaiduMatchListClient client = ApplicationManager.getApplication()
                    .getService(BaiduMatchListClient.class);
            if (client == null) {
                setStatus(FBundle.message("st.service.missing"));
                return;
            }
            try {
                BaiduSportsClient.ScheduleBundle bundle = client.fetchAllFootball();
                LocalDate today = LocalDate.now();
                // 先按日分组, 近 3 天整表替换 (状态要随轮询更新), 远日期去重合并
                Map<LocalDate, List<Match>> grouped = new LinkedHashMap<>();
                for (Match m : bundle.matches) {
                    if (m.getUtcDate() == null) continue;
                    try {
                        LocalDate ld = LocalDate.parse(m.getUtcDate().substring(0, 10));
                        grouped.computeIfAbsent(ld, k -> new ArrayList<>()).add(m);
                    } catch (Exception ignored) {}
                }
                synchronized (dataLock) {
                    for (Map.Entry<LocalDate, List<Match>> en : grouped.entrySet()) {
                        LocalDate ld = en.getKey();
                        boolean near = !ld.isBefore(today.minusDays(1)) && !ld.isAfter(today.plusDays(1));
                        if (near) {
                            cache.put(ld, en.getValue()); // 替换: 新 Match 对象带最新状态
                        } else if (cache.containsKey(ld)) {
                            List<Match> dayList = cache.get(ld);
                            for (Match m : en.getValue()) {
                                boolean dup = false;
                                for (Match exist : dayList) {
                                    if (m.getBaiduMatchId() != null
                                            && m.getBaiduMatchId().equals(exist.getBaiduMatchId())) {
                                        dup = true;
                                        break;
                                    }
                                }
                                if (!dup) dayList.add(m);
                            }
                        }
                    }
                }
                SwingUtilities.invokeLater(() -> {
                    rebuildDateSelector();
                    if (bundle.matches.isEmpty()) {
                        setStatus(FBundle.message("st.no.data"));
                    } else {
                        setStatus("✓ " + FBundle.message("sch.aggregate.loaded", bundle.matches.size())
                                + " " + new java.util.Date());
                    }
                });
            } catch (Exception e) {
                LOG.warn("loadAndShow failed", e);
                SwingUtilities.invokeLater(() ->
                        setStatus(FBundle.message("st.load.fail") + e.getClass().getSimpleName() + ": " + e.getMessage()));
            }
        });
    }

    /**
     * 显示指定日期的赛程 (跟随全局赛事过滤; 远日期走懒加载)
     */
    private void showForDate(LocalDate date) {
        currentDate = date;
        String selected = LeagueRegistry.getInstance().getSelected();
        String context = (selected == null || selected.isEmpty()) ? "ALL" : selected;

        LocalDate today = LocalDate.now();
        boolean nearWindow = !date.isBefore(today.minusDays(1)) && !date.isAfter(today.plusDays(1));

        // 锁内只做快照, 渲染在锁外 (填表不阻塞后台写入)
        List<Match> snapshot = null;
        String fetchedCtx = null;
        synchronized (dataLock) {
            List<Match> l = cache.get(date);
            if (l != null) snapshot = new ArrayList<>(l);
            fetchedCtx = fetchedFor.get(date);
        }
        boolean contextMatched = nearWindow || context.equals(fetchedCtx);

        if (!nearWindow && !contextMatched) {
            // 远日期 + 缓存是别的赛事上下文拉的 → 重新懒加载
            kickFarDateFetch(date, context);
            return;
        }
        if (snapshot == null || snapshot.isEmpty()) {
            if (nearWindow || fetchedCtx != null) {
                render(date, Collections.emptyList(), selected, nearWindow);
            } else {
                kickFarDateFetch(date, context);
            }
            return;
        }
        render(date, snapshot, selected, true);
    }

    /**
     * 远日期懒加载: 具体赛事拉 1 个请求; "全部赛事" 拉主要赛事合并。
     * 返回的多日数据顺带回填缓存 (一次请求覆盖该日起的多个比赛日)。
     */
    private void kickFarDateFetch(LocalDate date, String context) {
        synchronized (dataLock) {
            if (inFlight.contains(date)) return;
            inFlight.add(date);
        }
        boolean allLeagues = "ALL".equals(context);
        setStatus(FBundle.message("sch.loading", date, allLeagues ? String.valueOf(TOP_LEAGUES.length) : context));

        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            List<Match> merged = new ArrayList<>();
            String err = null;
            try {
                BaiduSportsClient client = ApplicationManager.getApplication()
                        .getService(BaiduSportsClient.class);
                List<String> leagues = allLeagues ? Arrays.asList(TOP_LEAGUES) : Collections.singletonList(context);
                for (String lg : leagues) {
                    try {
                        BaiduSportsClient.ScheduleBundle b = client.fetchSchedule(lg, date, "after");
                        for (Match m : b.matches) {
                            if (m.getLeagueName() == null || m.getLeagueName().isEmpty()) {
                                m.setLeagueName(lg); // 端点按赛事拉取, 行内缺失 game 字段时补上
                            }
                            merged.add(m);
                        }
                    } catch (Exception ex) {
                        LOG.warn("far-date fetch failed for " + lg + " @ " + date, ex);
                    }
                }
            } catch (Exception ex) {
                LOG.warn("far-date fetch failed", ex);
                err = ex.getClass().getSimpleName() + ": " + ex.getMessage();
            }

            final String failure = err;
            final List<Match> result = merged;
            synchronized (dataLock) {
                inFlight.remove(date);
                if (failure == null) {
                    for (Match m : result) {
                        if (m.getUtcDate() == null || m.getUtcDate().length() < 10) continue;
                        try {
                            LocalDate day = LocalDate.parse(m.getUtcDate().substring(0, 10));
                            List<Match> dayList = cache.computeIfAbsent(day, k -> new ArrayList<>());
                            boolean dup = false;
                            for (Match exist : dayList) {
                                if (m.getBaiduMatchId() != null && m.getBaiduMatchId().equals(exist.getBaiduMatchId())) {
                                    dup = true;
                                    break;
                                }
                            }
                            if (!dup) dayList.add(m);
                            fetchedFor.putIfAbsent(day, context);
                        } catch (Exception ignored) {}
                    }
                    fetchedFor.put(date, context);
                }
            }
            SwingUtilities.invokeLater(() -> {
                if (failure != null) {
                    setStatus(FBundle.message("sch.fetch.fail", date, failure));
                    return;
                }
                if (date.equals(currentDate)) showForDate(date);
                else setStatus(FBundle.message("sch.loaded.far", date));
            });
        });
    }

    /**
     * 渲染 (已按赛事过滤 + 排序)
     */
    private void render(LocalDate date, List<Match> matches, String selected, boolean fromCache) {
        tableModel.setRowCount(0);
        List<Match> filtered = new ArrayList<>();
        for (Match m : matches) {
            if (selected != null && !selected.isEmpty() && !selected.equals(m.getLeagueName())) {
                continue;
            }
            if (!teamFilter.isEmpty()) {
                String home = m.getHomeTeam() != null && m.getHomeTeam().getShortName() != null
                        ? m.getHomeTeam().getShortName() : "";
                String away = m.getAwayTeam() != null && m.getAwayTeam().getShortName() != null
                        ? m.getAwayTeam().getShortName() : "";
                if (!(home.contains(teamFilter) || away.contains(teamFilter))) continue;
            }
            filtered.add(m);
        }
        if (filtered.isEmpty()) {
            String msgKey = (selected == null || selected.isEmpty()) ? "sch.no.matches"
                    : (teamFilter.isEmpty() ? "sch.no.matches.league" : "sch.no.matches.team");
            setStatus(FBundle.message(msgKey, date, teamFilter) + (fromCache ? "" : " (?)"));
            return;
        }
        filtered.sort((x, y) -> {
            String xt = x.getUtcDate() != null ? x.getUtcDate() : "";
            String yt = y.getUtcDate() != null ? y.getUtcDate() : "";
            return xt.compareTo(yt);
        });
        for (Match m : filtered) {
            tableModel.addRow(new Object[]{
                    m.getVenue() != null ? m.getVenue() : "-",
                    m.getLeagueName() != null ? m.getLeagueName() : "-",
                    m.getRoundName() != null ? m.getRoundName() : "-",
                    m.getHomeTeam() != null ? m.getHomeTeam().getShortName() : "?",
                    formatScore(m),
                    m.getAwayTeam() != null ? m.getAwayTeam().getShortName() : "?",
                    formatStatus(m.getStatus())
            });
        }
        setStatus("✓ " + date + FBundle.message("sch.count.suffix", filtered.size())
                + (selected == null || selected.isEmpty() ? "" : " · " + selected)
                + (teamFilter.isEmpty() ? "" : " · " + teamFilter));
    }

    /** 工具窗可见性闸门 (工厂注入 toolWindow::isVisible): 隐藏时轮询空转省资源 */
    public void setVisibilityGate(java.util.function.BooleanSupplier gate) {
        visibilityGate = gate;
        if (showRefreshListener == null) {
            showRefreshListener = new javax.swing.event.AncestorListener() {
                @Override
                public void ancestorAdded(javax.swing.event.AncestorEvent e) { loadAndShow(); } // 重新显示立即补一次刷新
                @Override
                public void ancestorRemoved(javax.swing.event.AncestorEvent e) {}
                @Override
                public void ancestorMoved(javax.swing.event.AncestorEvent e) {}
            };
            addAncestorListener(showRefreshListener);
        }
    }

    private volatile java.util.function.BooleanSupplier visibilityGate = () -> true;
    private javax.swing.event.AncestorListener showRefreshListener;

    /**
     * 外部 "刷新全部" 按钮调用: 全量重置 (含远日期懒加载缓存)
     */
    public void refresh() {
        synchronized (dataLock) {
            cache.clear();
            fetchedFor.clear();
        }
        loadAndShow();
    }

    private String formatScore(Match m) {
        if (m.getScore() != null && m.getScore().getFullTime() != null) {
            Match.ScoreDetail ft = m.getScore().getFullTime();
            Integer h = ft.getHome();
            Integer a = ft.getAway();
            if (h != null && a != null) return h + " - " + a;
        }
        return "vs";
    }

    private String formatStatus(String status) {
        if (status == null) return "";
        return switch (status) {
            case "SCHEDULED" -> FBundle.message("st.not.started");
            case "TIMED" -> FBundle.message("st.scheduled");
            case "IN_PLAY" -> FBundle.message("st.live");
            case "PAUSED" -> FBundle.message("st.paused");
            case "FINISHED" -> FBundle.message("st.finished");
            default -> status;
        };
    }


    private void setStatus(String text) { statusLabel.setText(text); }
}
