<?php
require_once __DIR__ . '/functions_monetization_guard.php';

function Wo_AutoGenerateGeneralCosts() {
    global $db;
    $today_day   = (int)date('d'); // Aaj ki date (1-31)
    $today_date  = date('Y-m-d');
    $current_month = date('Y-m');

    // Woh saare costs uthao jo Auto-Generate hain aur jinki billing date aaj hai
    // Aur check karo ki is mahine woh pehle hi process toh nahi ho gaye (last_processed_at)
    $costs = $db->where('auto_generate', 1)
                ->where('pay_day', $today_day)
                ->where("(last_processed_at IS NULL OR last_processed_at NOT LIKE '$current_month%')")
                ->get('wo_app_general_cost');

    foreach ($costs as $cost) {
        // 1. Transaction Table mein Entry daalo
        $db->insert('wo_app_general_cost_transaction', [
            'cost_id'        => $cost->id,
            'category_id'    => $cost->category_id,
            'title'          => $cost->name . ' (Auto Generated)',
            'transaction_id' => 'AUTO-' . strtoupper(substr($cost->name, 0, 3)) . '-' . time(),
            'amount'         => $cost->amount,
            'billing_date'   => $today_date,
            'payment_method' => 'System Auto',
            'notes'          => 'Automatically generated recurring cost.'
        ]);

        // 2. Master Table update karo taaki dobara is mahine insert na ho
        $db->where('id', $cost->id)->update('wo_app_general_cost', [
            'last_processed_at' => $today_date
        ]);
    }
}

function Wo_UpdateReadingProgress($user_id, $novel_id, $chapter_id) {
    global $sqlConnect;
    $user_id = Wo_Secure($user_id);
    $novel_id = Wo_Secure($novel_id);
    $chapter_id = Wo_Secure($chapter_id);
    $time = time();

    $query = "INSERT INTO wo_user_reading_progress (user_id, novel_id, last_chapter_id, updated_at) 
              VALUES ('{$user_id}', '{$novel_id}', '{$chapter_id}', '{$time}')
              ON DUPLICATE KEY UPDATE 
              last_chapter_id = '{$chapter_id}', 
              updated_at = '{$time}'";
              
    return mysqli_query($sqlConnect, $query);
}

function GetTrendingNovelsD($period = 'daily', $limit = 6) {
    global $sqlConnect;
    
    // Select column based on period
    $column = 'daily_score';
    if ($period == 'weekly') $column = 'weekly_score';
    if ($period == 'yearly') $column = 'yearly_score';
    if ($period == 'monthly') $column = 'weekly_score'; 

    $query = "SELECT id, $column as score FROM wo_novels 
              WHERE status = '1' 
              ORDER BY $column DESC LIMIT $limit";

    $result = mysqli_query($sqlConnect, $query);
    $data = [];
    while ($row = mysqli_fetch_assoc($result)) {
        $novel = Wo_GetNovelHomeData($row['id']);
        
        if ($novel) {
            $data[] = [
                'id'    => $novel['id'],
                'name'  => $novel['title'],
                'writer'=> $novel['user_name'],
                'avatar'=> $novel['image_link'],
                'score' => (int)$row['score'],
                'views' => $novel['view']
            ];
        }
    }
    return $data;
}


function GetTopNovelsByMetric($column, $period = 'today', $limit = 5) {
    global $sqlConnect;

    
    $cache_key = "top_novels_{$column}_{$period}_{$limit}";
    $cache = new Cache();
    $cached_data = $cache->read($cache_key);

    if (!empty($cached_data)) {
        return $cached_data;
    }

    $rank_logic = "SUM(s.$column)";
    if ($column == 'total_income') {
        Wo_EnsureNovelStatsColumns(['gift_earning', 'bonus_earning', 'novel_buy_earning', 'subscription_earning']);
        $rank_logic = "SUM(s.ads_view_earning + s.coins_earning + s.diamonds_earning + s.gift_earning + s.bonus_earning + s.novel_buy_earning + s.subscription_earning)";
    }

    $today_start = strtotime(date('Y-m-d 00:00:00'));
    $today_end   = strtotime(date('Y-m-d 23:59:59'));

    switch ($period) {
        case 'yesterday':
            $start = strtotime("-1 day", $today_start);
            $end   = strtotime("-1 day", $today_end);
            break;
        case 'weekly':
            $start = strtotime("-6 days", $today_start);
            $end   = $today_end;
            break;
        case 'monthly':
            $start = strtotime("-29 days", $today_start);
            $end   = $today_end;
            break;
        case 'today':
        default:
            $start = $today_start;
            $end   = $today_end;
            break;
    }

    $query_text = "SELECT 
        n.id, 
        n.title, 
        n.image_link,
        $rank_logic as metric_value
        FROM wo_novel_statices s
        JOIN wo_novels n ON s.novel_id = n.id
        WHERE s.timestamp >= $start AND s.timestamp <= $end
        GROUP BY s.novel_id
        HAVING metric_value > 0
        ORDER BY metric_value DESC
        LIMIT $limit";

    $result = mysqli_query($sqlConnect, $query_text);
    $data = [];
    
    if ($result) {
        while ($row = mysqli_fetch_assoc($result)) {
            $row['url'] = Wo_SeoLink('index.php?link1=novel&id=' . $row['id']);
            $row['thumbnail'] = Wo_GetMedia($row['image_link']);
            if (strpos($column, 'earning') !== false || $column == 'total_income') {
                $row['formatted_value'] = 'Rs.' . number_format((float)$row['metric_value'], 2);
            } else {
                $row['formatted_value'] = number_format((int)$row['metric_value']);
            }
            
            $data[] = $row;
        }
    }
    if (!empty($data)) {
        $cache->write($cache_key, $data, 3600);
    }
    
    return $data;
}

function GetTopRankingData($entity_type, $period_type = 'monthly', $limit = 3) {
    global $sqlConnect, $wo;
    
    switch ($period_type) {
        case 'daily':   $period_key = date('Y-m-d'); break;
        case 'weekly':  $period_key = date('o-\WW'); break;
        case 'monthly': $period_key = date('Y-m');   break;
        default:        $period_key = 'overall';     break;
    }

    $query = "SELECT entity_id, score FROM wo_world_ranking 
              WHERE entity_type = '$entity_type' AND period_type = '$period_type' 
              AND period_key = '$period_key' 
              ORDER BY score DESC LIMIT $limit";

    $result = mysqli_query($sqlConnect, $query);
    $data = [];
    while ($row = mysqli_fetch_assoc($result)) {
        $id = (int)$row['entity_id'];
        $name = 'Unknown'; $avatar = '';

        if ($entity_type == 'character') {
            $res = getNovelResourceById($id);
            $name = $res['name'] ?? 'Unknown';
            $avatar = $res['image'] ?? '';
        } else {
            $user = Wo_UserData($id);
            $name = $user['name'] ?? $user['username'];
            $avatar = $user['avatar'] ?? '';
        }
        $data[] = ['name' => $name, 'avatar' => $avatar, 'score' => (int)$row['score']];
    }
    return $data;
}

function Wo_GetDashboardDiamondStats() {
    global $db, $cache;

    $cache_name = 'diamond_sales_dash_stats';
    // 1. Cache Check (8 Ghante = 28800 seconds)
    $cached_data = $cache->read($cache_name);
    if (!empty($cached_data)) {
        return $cached_data;
    }

    $thirty_days_ago_dt = date('Y-m-d H:i:s', strtotime('-30 days'));
    $thirty_days_ago_ts = strtotime('-30 days');

    // 2. Razorpay Stats (Object Access Fixed)
    $razorpay = $db->rawQueryOne("
        SELECT 
            SUM(CASE WHEN status = 'paid' THEN amount ELSE 0 END) as earnings,
            COUNT(CASE WHEN status = 'paid' THEN id ELSE NULL END) as success_count,
            COUNT(CASE WHEN status != 'paid' THEN id ELSE NULL END) as failed_count
        FROM wo_diamond_orders 
        WHERE created_at >= ?", [$thirty_days_ago_dt]);

    // 3. Play Store Stats (Object Access Fixed)
    $playstore = $db->rawQueryOne("
        SELECT 
            SUM(CASE WHEN status = 'verified' THEN amount ELSE 0 END) as earnings,
            COUNT(CASE WHEN status = 'verified' THEN id ELSE NULL END) as success_count,
            COUNT(CASE WHEN status != 'verified' THEN id ELSE NULL END) as failed_count
        FROM wo_playstore_purchases 
        WHERE created_at >= ? AND type = 'diamond'", [$thirty_days_ago_ts]);

    // Null checks aur Object properties ko variables mein nikalna
    $r_earn = (isset($razorpay->earnings)) ? $razorpay->earnings : 0;
    $r_succ = (isset($razorpay->success_count)) ? $razorpay->success_count : 0;
    $r_fail = (isset($razorpay->failed_count)) ? $razorpay->failed_count : 0;

    $p_earn = (isset($playstore->earnings)) ? $playstore->earnings : 0;
    $p_succ = (isset($playstore->success_count)) ? $playstore->success_count : 0;
    $p_fail = (isset($playstore->failed_count)) ? $playstore->failed_count : 0;

    // 4. Totals Calculation
    $stats = [
        'razorpay' => [
            'earnings' => (float)$r_earn,
            'success'  => (int)$r_succ,
            'failed'   => (int)$r_fail
        ],
        'playstore' => [
            'earnings' => (float)$p_earn,
            'success'  => (int)$p_succ,
            'failed'   => (int)$p_fail
        ],
        'total' => [
            'earnings' => (float)($r_earn + $p_earn),
            'success'  => (int)($r_succ + $p_succ),
            'failed'   => (int)($r_fail + $p_fail)
        ]
    ];

    // 5. Cache Save (28800 = 8 Hours)
    $cache->write($cache_name, $stats, 28800);

    return $stats;
}

function Wo_GetDashboardPackSalesStats() {
    global $db, $cache;

    $cache_name = 'pack_sales_dash_stats';
    // 1. Cache read karein
    $cached_data = $cache->read($cache_name);
    if (!empty($cached_data)) {
        return $cached_data;
    }

    $thirty_days_ago = date('Y-m-d H:i:s', strtotime('-30 days'));

    // 2. Total Earnings (Successful Sales)
    $db->where('status', 'captured');
    $db->where('created_at', $thirty_days_ago, '>=');
    $total_earnings = $db->getValue('wo_platform_writer_subscription_transaction', "SUM(amount)");

    // 3. Total Successful Buy (Count)
    $db->where('status', 'captured');
    $db->where('created_at', $thirty_days_ago, '>=');
    $total_buys = $db->getValue('wo_platform_writer_subscription_transaction', "COUNT(id)");

    // 4. Total Attempts / Failed (Anything not captured)
    $db->where('status', 'captured', '!=');
    $db->where('created_at', $thirty_days_ago, '>=');
    $total_failed = $db->getValue('wo_platform_writer_subscription_transaction', "COUNT(id)");

    $stats = [
        'earnings' => (float)($total_earnings ?: 0),
        'buys'     => (int)($total_buys ?: 0),
        'failed'   => (int)($total_failed ?: 0)
    ];

    // 5. Cache write karein (4 Ghante = 14400 seconds)
    $cache->write($cache_name, $stats, 14400);

    return $stats;
}


function Wo_GetDashboardFbAdsStats() {
    global $cache; // WoWonder cache class
    
    $cache_name = 'fb_ads_dash_stats';
    // 1. Cache Check karein
    $cached_data = $cache->read($cache_name);
    if (!empty($cached_data)) {
        return $cached_data;
    }

    // 2. Agar cache nahi hai, toh API se data fetch karein (Default 30 days)
    $ads_data = Wo_GetAdsPerformance('last_30d');
    $ads = $ads_data['data'] ?? [];

    $total_spend = 0;
    $total_installs = 0;

    if (!empty($ads)) {
        foreach ($ads as $ad) {
            $ins = $ad['insights']['data'][0] ?? [];
            $total_spend += $ins['spend'] ?? 0;
            foreach (($ins['actions'] ?? []) as $act) {
                if ($act['action_type'] == 'mobile_app_install') {
                    $total_installs += (int)$act['value'];
                }
            }
        }
    }

    $avg_cpi = ($total_installs > 0) ? ($total_spend / $total_installs) : 0;

    $stats = [
        'total_spend'    => $total_spend,
        'total_installs' => $total_installs,
        'avg_cpi'        => $avg_cpi
    ];

    // 3. Data ko 4 ghante (14400 seconds) ke liye cache mein save karein
    $cache->write($cache_name, $stats, 14400);

    return $stats;
}


function GetOverallWriterEarningsDashboard() {
    global $sqlConnect, $cache;

    $cache_key = "overall_writer_earnings_sum";
    $cached_total = $cache->read($cache_key);

    if ($cached_total !== false && $cached_total !== null) {
        return (float)$cached_total;
    }

    $membershipColumnQuery = @mysqli_query($sqlConnect, "SHOW COLUMNS FROM `Wo_Users` LIKE 'membership_income'");
    $membershipSelect = ($membershipColumnQuery && mysqli_num_rows($membershipColumnQuery) > 0) ? 'membership_income' : '0';
    $query = mysqli_query($sqlConnect, "SELECT SUM(diamond_income + ads_view_income + {$membershipSelect}) as total FROM Wo_Users");
    $data  = mysqli_fetch_assoc($query);
    $total_income = ($data['total']) ? (float)$data['total'] : 0.00;

    $cache->write($cache_key, $total_income, 21600);

    return $total_income;
}

function GetAdminDashboardData($range = 'this_month') {
    global $sqlConnect;

    RunAdminDashboardStats();

    $today = date('Y-m-d');
    $start_date = "";
    $end_date = $today;

    switch ($range) {
        case 'today':
            $start_date = $today;
            break;
        case 'yesterday':
            $start_date = date('Y-m-d', strtotime("-1 day"));
            $end_date = $start_date;
            break;
        case 'last_7': // Last 7 days including today
            $start_date = date('Y-m-d', strtotime("-6 days"));
            break;
        case 'last_30': // Last 30 days including today (Best for Graphs)
            $start_date = date('Y-m-d', strtotime("-29 days"));
            break;
        case 'last_90': 
            $start_date = date('Y-m-d', strtotime("-89 days"));
            break;
        case 'this_week':
            $start_date = date('Y-m-d', strtotime("monday this week"));
            break;
        case 'this_month':
            $start_date = date('Y-m-01');
            break;
        case 'last_month':
            $start_date = date('Y-m-d', strtotime("first day of last month"));
            $end_date = date('Y-m-d', strtotime("last day of last month"));
            break;
        default:
            $start_date = date('Y-m-01');
    }

    $metrics = [
        'chapter_published', 'chapter_ads_views', 'chapter_comments', 
        'chapter_sales_novel', 'chapter_sales_audio', 
        'blog_created', 'post_comments','novel_created', 'total_revenue', 'total_views',
        'user_signup', 'rating_count', 'diamond_purchase_revenue', 'diamond_purchase_count'
    ];

    $final_report = [];

    foreach ($metrics as $type) {
        $total_count = 0;
        $daily_points = [];

        // Fetch Historical
        $query = mysqli_query($sqlConnect, "SELECT data_date, data_value FROM wo_admin_dashbord_graph_data 
                                            WHERE data_type = '$type' 
                                            AND data_date BETWEEN '$start_date' AND '$end_date'");
        while ($row = mysqli_fetch_assoc($query)) {
            $daily_points[$row['data_date']] = (int)$row['data_value'];
            $total_count += (int)$row['data_value'];
        }

        // Fetch Today Live (if today is within range)
        if (strtotime($start_date) <= strtotime($today) && strtotime($end_date) >= strtotime($today)) {
            $today_val = GetLiveCountForToday($type);
            // Don't double count if sync already ran for today
            if (!isset($daily_points[$today])) {
                $daily_points[$today] = $today_val;
                $total_count += $today_val;
            } else {
                $total_count = ($total_count - $daily_points[$today]) + $today_val;
                $daily_points[$today] = $today_val;
            }
        }

        // Fill missing dates with 0
        $current = $start_date;
        $graph_array = [];
        while (strtotime($current) <= strtotime($end_date)) {
            $graph_array[$current] = $daily_points[$current] ?? 0;
            $current = date('Y-m-d', strtotime($current . " +1 day"));
        }

        $final_report[$type] = [
            'total' => $total_count,
            'daily' => $graph_array
        ];
    }
    return $final_report;
}

/**
 * Helper: Live count for the current unfinished day
 */
function GetLiveCountForToday($type) {
    global $sqlConnect;
    $start = strtotime(date('Y-m-d 00:00:00'));
    $end = strtotime(date('Y-m-d 23:59:59'));
    $dt_start = date('Y-m-d 00:00:00');
    $dt_end = date('Y-m-d 23:59:59');

    switch ($type) {
           case 'diamond_purchase_count':
            $q = "SELECT COUNT(*) as total FROM wo_diamond_orders WHERE status = 'completed' AND created_at BETWEEN '$dt_start' AND '$dt_end'";
            break;
            
            case 'diamond_purchase_revenue':
            $q = "SELECT SUM(amount) as total FROM wo_diamond_orders WHERE status = 'completed' AND created_at BETWEEN '$dt_start' AND '$dt_end'";
            break;

          case 'user_signup':
            $q = "SELECT COUNT(*) as total FROM Wo_Users WHERE joined >= $start AND joined <= $end";
            break;
          case 'rating_count':
            $q = "SELECT SUM(ratings) as total FROM wo_novel_statices WHERE timestamp >= $start AND timestamp <= $end";
            break;
          case 'total_revenue':
            Wo_EnsureNovelStatsColumns(['gift_earning', 'bonus_earning', 'novel_buy_earning', 'subscription_earning']);
            $q = "SELECT SUM(ads_view_earning + coins_earning + diamonds_earning + gift_earning + bonus_earning + novel_buy_earning + subscription_earning) as total FROM wo_novel_statices WHERE timestamp >= $start AND timestamp <= $end";
            break;
          case 'total_views':
            $q = "SELECT SUM(views) as total FROM wo_novel_statices WHERE timestamp >= $start AND timestamp <= $end";
            break;
          case 'novel_created':
            $q = "SELECT COUNT(*) as total FROM wo_novels WHERE created_at >= $start AND created_at <= $end";
            break;
        case 'chapter_published':
            $q = "SELECT COUNT(*) as total FROM wo_novel_chapter WHERE status = 'published' AND created_at BETWEEN '$dt_start' AND '$dt_end'";
            break;
        case 'chapter_ads_views':
            $q = "SELECT COUNT(*) as total FROM wo_chapter_ads_view WHERE created_at BETWEEN '$dt_start' AND '$dt_end'";
            break;
        case 'chapter_comments':
            $q = "SELECT COUNT(*) as total FROM wo_chapter_comment WHERE created_at >= $start AND created_at <= $end";
            break;
        case 'chapter_sales_novel':
            $q = "SELECT COUNT(*) as total FROM wo_chapter_isBuyed WHERE app_type='novel' AND created_at >= $start AND created_at <= $end";
            break;
        case 'blog_created':
            $q = "SELECT COUNT(*) as total FROM Wo_Blog WHERE posted >= $start AND posted <= $end";
            break;
        default:
            return 0;
    }
    
    $res = mysqli_query($sqlConnect, $q);
    $data = mysqli_fetch_assoc($res);
    return (int)$data['total'];
}


function RunAdminDashboardStats() {
    global $sqlConnect, $wo;

    $today = date('Y-m-d');
    
    $query = mysqli_query($sqlConnect, "SELECT value FROM " . T_CONFIG . " WHERE name = 'last_admin_stats_sync' LIMIT 1");
    $config_data = mysqli_fetch_assoc($query);
    $last_run = $config_data['value'] ?? '';

    if ($last_run == $today) {
        return; 
    }

    $start_default = "2026-01-01";
    $yesterday = date('Y-m-d', strtotime("-1 day"));

    CountChapterDashboardStats($start_default, $yesterday);
    CountBlogDashboardStats($start_default, $yesterday);
    CountNovelDashboardStats($start_default, $yesterday); 
    CountNovelStaticesDashboardStats($start_default, $yesterday);
    CountUserAndRatingStats($start_default, $yesterday);
    CountDiamondDashboardStats($start_default, $yesterday); 
    
    if (!$config_data) {
        mysqli_query($sqlConnect, "INSERT INTO " . T_CONFIG . " (name, value) VALUES ('last_admin_stats_sync', '$today')");
    } else {
        mysqli_query($sqlConnect, "UPDATE " . T_CONFIG . " SET value = '$today' WHERE name = 'last_admin_stats_sync'");
    }
}


/**
 * Historical Sync for Diamond Orders
 */
function CountDiamondDashboardStats($start_default, $yesterday) {
    global $sqlConnect;
    
    // Check last sync date for diamond data
    $query = mysqli_query($sqlConnect, "SELECT data_date FROM wo_admin_dashbord_graph_data WHERE data_type = 'diamond_purchase_count' ORDER BY data_date DESC LIMIT 1");
    $last = mysqli_fetch_assoc($query);
    $current_date = ($last) ? date('Y-m-d', strtotime($last['data_date'] . " +1 day")) : $start_default;

    while (strtotime($current_date) <= strtotime($yesterday)) {
        $dt_start = $current_date . " 00:00:00";
        $dt_end   = $current_date . " 23:59:59";

        // 1. Sync Count
        $q_count = "SELECT COUNT(*) as total FROM wo_diamond_orders WHERE status = 'completed' AND created_at BETWEEN '$dt_start' AND '$dt_end'";
        $res_count = mysqli_query($sqlConnect, $q_count);
        SaveDashboardData('diamond_purchase_count', $current_date, mysqli_fetch_assoc($res_count)['total'] ?? 0);

        // 2. Sync Revenue
        $q_rev = "SELECT SUM(amount) as total FROM wo_diamond_orders WHERE status = 'completed' AND created_at BETWEEN '$dt_start' AND '$dt_end'";
        $res_rev = mysqli_query($sqlConnect, $q_rev);
        SaveDashboardData('diamond_purchase_revenue', $current_date, mysqli_fetch_assoc($res_rev)['total'] ?? 0);

        $current_date = date('Y-m-d', strtotime($current_date . " +1 day"));
    }
}


/**
 * Historical Sync for User and Rating data
 */
function CountUserAndRatingStats($start_default, $yesterday) {
    global $sqlConnect;
    
    $query = mysqli_query($sqlConnect, "SELECT data_date FROM wo_admin_dashbord_graph_data WHERE data_type = 'user_signup' ORDER BY data_date DESC LIMIT 1");
    $last = mysqli_fetch_assoc($query);
    $current_date = ($last) ? date('Y-m-d', strtotime($last['data_date'] . " +1 day")) : $start_default;

    while (strtotime($current_date) <= strtotime($yesterday)) {
        $ts_start = strtotime($current_date . " 00:00:00");
        $ts_end   = strtotime($current_date . " 23:59:59");

        // Sync Signups
        $users = mysqli_query($sqlConnect, "SELECT COUNT(*) as total FROM Wo_Users WHERE joined >= $ts_start AND joined <= $ts_end");
        SaveDashboardData('user_signup', $current_date, mysqli_fetch_assoc($users)['total'] ?? 0);

        // Sync Ratings
        $ratings = mysqli_query($sqlConnect, "SELECT SUM(ratings) as total FROM wo_novel_statices WHERE timestamp >= $ts_start AND timestamp <= $ts_end");
        SaveDashboardData('rating_count', $current_date, mysqli_fetch_assoc($ratings)['total'] ?? 0);

        $current_date = date('Y-m-d', strtotime($current_date . " +1 day"));
    }
}



function CountChapterDashboardStats($start_default, $yesterday) {
    global $sqlConnect;
    
    $query = mysqli_query($sqlConnect, "SELECT data_date FROM wo_admin_dashbord_graph_data WHERE data_type LIKE 'chapter_%' ORDER BY data_date DESC LIMIT 1");
    $last = mysqli_fetch_assoc($query);
    $current_date = ($last) ? date('Y-m-d', strtotime($last['data_date'] . " +1 day")) : $start_default;

    while (strtotime($current_date) <= strtotime($yesterday)) {
        $ts_start = strtotime($current_date . " 00:00:00");
        $ts_end   = strtotime($current_date . " 23:59:59");
        
        $dt_start = $current_date . " 00:00:00";
        $dt_end   = $current_date . " 23:59:59";

        $chapters = mysqli_query($sqlConnect, "SELECT COUNT(*) as total FROM wo_novel_chapter 
                                               WHERE status = 'published' 
                                               AND created_at BETWEEN '$dt_start' AND '$dt_end'");
        $chapters_count = mysqli_fetch_assoc($chapters)['total'];
        SaveDashboardData('chapter_published', $current_date, $chapters_count);

        $ads = mysqli_query($sqlConnect, "SELECT COUNT(*) as total FROM wo_chapter_ads_view 
                                          WHERE created_at BETWEEN '$dt_start' AND '$dt_end'");
        $ads_count = mysqli_fetch_assoc($ads)['total'];
        SaveDashboardData('chapter_ads_views', $current_date, $ads_count);

        $comm = mysqli_query($sqlConnect, "SELECT COUNT(*) as total FROM wo_chapter_comment 
                                           WHERE created_at >= $ts_start AND created_at <= $ts_end");
        $comm_count = mysqli_fetch_assoc($comm)['total'];
        SaveDashboardData('chapter_comments', $current_date, $comm_count);

        $novel = mysqli_query($sqlConnect, "SELECT COUNT(*) as total FROM wo_chapter_isBuyed 
                                            WHERE app_type='novel' AND created_at >= $ts_start AND created_at <= $ts_end");
        SaveDashboardData('chapter_sales_novel', $current_date, mysqli_fetch_assoc($novel)['total']);

        $audio = mysqli_query($sqlConnect, "SELECT COUNT(*) as total FROM wo_chapter_isBuyed 
                                            WHERE app_type='audio' AND created_at >= $ts_start AND created_at <= $ts_end");
        SaveDashboardData('chapter_sales_audio', $current_date, mysqli_fetch_assoc($audio)['total']);

        $current_date = date('Y-m-d', strtotime($current_date . " +1 day"));
    }
}

/**
 * 2. NEW FUNCTION: Count historical novels day by day
 */
function CountNovelDashboardStats($start_default, $yesterday) {
    global $sqlConnect;
    
    $query = mysqli_query($sqlConnect, "SELECT data_date FROM wo_admin_dashbord_graph_data WHERE data_type = 'novel_created' ORDER BY data_date DESC LIMIT 1");
    $last = mysqli_fetch_assoc($query);
    $current_date = ($last) ? date('Y-m-d', strtotime($last['data_date'] . " +1 day")) : $start_default;

    while (strtotime($current_date) <= strtotime($yesterday)) {
        $ts_start = strtotime($current_date . " 00:00:00");
        $ts_end   = strtotime($current_date . " 23:59:59");

        // Use wo_novels table and created_at (UNIX timestamp)
        $q = "SELECT COUNT(*) as total FROM wo_novels WHERE created_at >= $ts_start AND created_at <= $ts_end";
        $res = mysqli_query($sqlConnect, $q);
        $count = mysqli_fetch_assoc($res)['total'];

        SaveDashboardData('novel_created', $current_date, $count);
        $current_date = date('Y-m-d', strtotime($current_date . " +1 day"));
    }
}

function CountBlogDashboardStats($start_default, $yesterday) {
    global $sqlConnect;
    
    $query = mysqli_query($sqlConnect, "SELECT data_date FROM wo_admin_dashbord_graph_data WHERE data_type = 'blog_created' ORDER BY data_date DESC LIMIT 1");
    $last = mysqli_fetch_assoc($query);
    $current_date = ($last) ? date('Y-m-d', strtotime($last['data_date'] . " +1 day")) : $start_default;

    while (strtotime($current_date) <= strtotime($yesterday)) {
        $ts_start = strtotime($current_date . " 00:00:00");
        $ts_end   = strtotime($current_date . " 23:59:59");

        // Count Blogs
        $blogs = mysqli_query($sqlConnect, "SELECT COUNT(*) as total FROM Wo_Blog WHERE posted >= $ts_start AND posted <= $ts_end");
        SaveDashboardData('blog_created', $current_date, mysqli_fetch_assoc($blogs)['total']);

        // Count General Comments
        $comments = mysqli_query($sqlConnect, "SELECT COUNT(*) as total FROM Wo_Comments WHERE time >= $ts_start AND time <= $ts_end");
        SaveDashboardData('post_comments', $current_date, mysqli_fetch_assoc($comments)['total']);

        $current_date = date('Y-m-d', strtotime($current_date . " +1 day"));
    }
}

function CountNovelStaticesDashboardStats($start_default, $yesterday) {
    global $sqlConnect;
    
    // Check last sync for these specific types
    $query = mysqli_query($sqlConnect, "SELECT data_date FROM wo_admin_dashbord_graph_data WHERE data_type = 'total_revenue' ORDER BY data_date DESC LIMIT 1");
    $last = mysqli_fetch_assoc($query);
    $current_date = ($last) ? date('Y-m-d', strtotime($last['data_date'] . " +1 day")) : $start_default;

    while (strtotime($current_date) <= strtotime($yesterday)) {
        $ts_start = strtotime($current_date . " 00:00:00");
        $ts_end   = strtotime($current_date . " 23:59:59");

        // Revenue Sync
        Wo_EnsureNovelStatsColumns(['gift_earning', 'bonus_earning', 'novel_buy_earning', 'subscription_earning']);
        $rev = mysqli_query($sqlConnect, "SELECT SUM(ads_view_earning + coins_earning + diamonds_earning + gift_earning + bonus_earning + novel_buy_earning + subscription_earning) as total FROM wo_novel_statices WHERE timestamp >= $ts_start AND timestamp <= $ts_end");
        SaveDashboardData('total_revenue', $current_date, mysqli_fetch_assoc($rev)['total'] ?? 0);

        // Views Sync
        $view = mysqli_query($sqlConnect, "SELECT SUM(views) as total FROM wo_novel_statices WHERE timestamp >= $ts_start AND timestamp <= $ts_end");
        SaveDashboardData('total_views', $current_date, mysqli_fetch_assoc($view)['total'] ?? 0);

        $current_date = date('Y-m-d', strtotime($current_date . " +1 day"));
    }
}

function SaveDashboardData($type, $date, $value) {
    global $sqlConnect;
    $type = mysqli_real_escape_string($sqlConnect, $type);
    $date = mysqli_real_escape_string($sqlConnect, $date);
    $value = (int)$value;
    
    $query = "INSERT INTO wo_admin_dashbord_graph_data (data_type, data_date, data_value) 
              VALUES ('$type', '$date', '$value') 
              ON DUPLICATE KEY UPDATE data_value = '$value'";
    return mysqli_query($sqlConnect, $query);
}





function wo_calculate_all_world_ranks() {
    global $sqlConnect;

    $today  = date('Y-m-d');
    $week   = date('o-\WW');
    $month  = date('Y-m');
    $year   = date('Y');

    $periods = [
        ['daily', $today],
        ['weekly', $week],
        ['monthly', $month],
        ['yearly', $year]
    ];

    foreach ($periods as $period) {

        $period_type = $period[0];
        $period_key  = $period[1];

        mysqli_query($sqlConnect, "
            SET @rank := 0;
        ");

        mysqli_query($sqlConnect, "
            UPDATE wo_world_ranking w
            JOIN (
                SELECT id, (@rank := @rank + 1) AS new_rank
                FROM wo_world_ranking
                WHERE period_type = '$period_type'
                AND period_key = '$period_key'
                ORDER BY score DESC
            ) ranked ON w.id = ranked.id
            SET w.rank_position = ranked.new_rank
        ");
    }

    return true;
}

function wo_update_world_rank_score($entity_id, $entity_type, $score = 1) {
    global $cache, $sqlConnect;

    $entity_id = (int)$entity_id;
    $score = (int)$score;
    if ($entity_id <= 0 || empty($entity_type) || $score <= 0) return false;

    $window_size = 600; // 10 minutes
    $current_window = floor(time() / $window_size) * $window_size;
    $cache_key = "world_rank_bucket_" . $current_window;
    $registry_key = "world_rank_registry";

    // --- WRITE TO CACHE ---
    $write_lock = "lock_wr_write_" . $current_window;
    $start = time();
    while ($cache->read($write_lock)) {
        if (time() - $start > 5) break;
        usleep(50000);
    }
    $cache->write($write_lock, true, 2);

    $data = $cache->read($cache_key) ?: [];
    $periods = [
        ['daily',   date('Y-m-d')],
        ['weekly',  date('o-\WW')],
        ['monthly', date('Y-m')],
        ['yearly',  date('Y')],
        ['overall', 'overall']
    ];

    foreach ($periods as $p) {
        $p_type = $p[0];
        $p_key  = $p[1];
        // Composite key: type | id | period_type | period_key
        $sub_key = "{$entity_type}|{$entity_id}|{$p_type}|{$p_key}";
        if (!isset($data[$sub_key])) $data[$sub_key] = 0;
        $data[$sub_key] += $score;
    }

    $registry = $cache->read($registry_key) ?: [];
    if (!in_array($current_window, $registry)) {
        $registry[] = $current_window;
        $cache->write($registry_key, $registry);
    }

    $cache->write($cache_key, $data);
    $cache->delete($write_lock);

    // --- SYNC OLD BUCKETS ---
    $registry = $cache->read($registry_key) ?: [];
    foreach ($registry as $window_ts) {
        if ($window_ts < $current_window) {
            $sync_lock = "lock_wr_sync_" . $window_ts;
            if ($cache->read($sync_lock)) continue;
            $cache->write($sync_lock, true, 120);

            $old_data = $cache->read("world_rank_bucket_" . $window_ts);
            if (!empty($old_data)) {
                mysqli_begin_transaction($sqlConnect);
                try {
                    foreach ($old_data as $composite => $val) {
                        list($e_type, $e_id, $p_type, $p_key) = explode('|', $composite);
                        $q = "INSERT INTO wo_world_ranking (entity_type, entity_id, period_type, period_key, score)
                              VALUES ('$e_type', $e_id, '$p_type', '$p_key', $val)
                              ON DUPLICATE KEY UPDATE score = score + $val";
                        mysqli_query($sqlConnect, $q);
                    }
                    mysqli_commit($sqlConnect);
                    $cache->delete("world_rank_bucket_" . $window_ts);
                    $new_reg = array_diff($cache->read($registry_key) ?: [], [$window_ts]);
                    $cache->write($registry_key, array_values($new_reg));
                } catch (Exception $e) {
                    mysqli_rollback($sqlConnect);
                    error_log("WORLD RANKING SYNC ERROR: " . $e->getMessage());

                }
            } else {
                $new_reg = array_diff($cache->read($registry_key) ?: [], [$window_ts]);
                $cache->write($registry_key, array_values($new_reg));
            }
            $cache->delete($sync_lock);
        }
    }
    return true;
}

/**
 * Logs a new income transaction for a writer.
 * This is an append-only function; it only inserts new income records.
 *
 * @param int    $writer_id  The ID of the writer who received the income.
 * @param string $type       The source of the income ('coins', 'diamonds', 'subscription', 'ads', 'gift', 'bonus', 'superfan', 'novel_buy').
 * @param float  $count      The count of the revenue driver (e.g., 100 coins, 1 sub).
 * @param float  $value_in_inr The monetary value earned in INR.
 * @return bool True on success, False on failure.
 */
function wo_writer_income_transaction($writer_id, $type, $count, $value_in_inr) {
    global $sqlConnect;

    // 1. Sanitize and validate inputs
    $writer_id  = (int)$writer_id;
    $count      = (float)$count;
    $value_in_inr = (float)$value_in_inr;

    // Define allowed types for validation
    $allowed_types = ['coins', 'diamonds', 'subscription', 'ads', 'gift', 'bonus', 'superfan', 'novel_buy'];
    $type_safe = '';

    if (in_array(strtolower($type), $allowed_types)) {
        $type_safe = mysqli_real_escape_string($sqlConnect, strtolower($type));
    } else {
        error_log("Invalid transaction type provided: " . $type);
        return false;
    }

    if ($writer_id <= 0 || $count <= 0 || $value_in_inr < 0) {
        error_log("Invalid writer_id, count, or value_in_inr provided.");
        return false;
    }

    if (!SM_ShouldCreditWriterIncome($writer_id, $type_safe, $value_in_inr, $count, [
        'source' => 'wo_writer_income_transaction',
    ])) {
        return true;
    }

    // 2. Prepare the INSERT query
    $query = "INSERT INTO wo_writer_transaction 
              (`writer_id`, `type`, `count`, `value_in_inr`, `created_at`, `updated_at`)
              VALUES (
                  '$writer_id', 
                  '$type_safe', 
                  '$count', 
                  '$value_in_inr', 
                  NOW(), 
                  NOW()
              )";

    // 3. Execute the query
    $result = mysqli_query($sqlConnect, $query);

    // 4. Check the result
    if ($result) {
        // Return the ID of the new transaction if needed, otherwise just true
        return true; 
    } else {
        error_log("Writer income transaction failed: " . mysqli_error($sqlConnect));
        return false;
    }
}

function Wo_EnsureWriterTransactionDailySourceColumns() {
    static $checked = null;
    if ($checked !== null) {
        return $checked;
    }

    $checked = function_exists('Wo_DbTableExistsSafe')
        && Wo_DbTableExistsSafe('wo_writer_transaction')
        && function_exists('Wo_DbColumnExistsSafe')
        && Wo_DbColumnExistsSafe('wo_writer_transaction', 'source')
        && Wo_DbColumnExistsSafe('wo_writer_transaction', 'source_date');
    return $checked;
}

function Wo_SyncDailyAdsEarningToWriterTransaction($date_key = '') {
    global $sqlConnect;

    $date_key = trim((string)$date_key);
    if ($date_key === '') {
        $date_key = date('Y-m-d', strtotime('-1 day'));
    }

    if (!preg_match('/^\d{4}-\d{2}-\d{2}$/', $date_key)) {
        return ['status' => false, 'message' => 'Invalid date format.', 'date' => $date_key];
    }

    $startTime = strtotime($date_key . ' 00:00:00');
    if ($startTime === false) {
        return ['status' => false, 'message' => 'Invalid date.', 'date' => $date_key];
    }
    $endTime = $startTime + 86400;

    $hasSourceColumns = Wo_EnsureWriterTransactionDailySourceColumns();
    $rowsQuery = mysqli_query($sqlConnect, "
        SELECT
            n.user_id AS writer_id,
            COALESCE(SUM(s.ads_view), 0) AS ads_views,
            COALESCE(SUM(s.ads_view_earning), 0) AS ads_income
        FROM wo_novel_statices s
        INNER JOIN wo_novels n ON n.id = s.novel_id
        WHERE s.timestamp >= {$startTime}
          AND s.timestamp < {$endTime}
        GROUP BY n.user_id
        HAVING ads_income > 0 OR ads_views > 0
    ");

    if (!$rowsQuery) {
        return ['status' => false, 'message' => mysqli_error($sqlConnect), 'date' => $date_key];
    }

    $inserted = 0;
    $skipped = 0;
    $failed = 0;
    $checked = 0;
    $createdAt = mysqli_real_escape_string($sqlConnect, $date_key . ' 23:59:59');
    $source = 'ads_daily';
    $sourceSafe = mysqli_real_escape_string($sqlConnect, $source);
    $dateSafe = mysqli_real_escape_string($sqlConnect, $date_key);

    while ($row = mysqli_fetch_assoc($rowsQuery)) {
        $checked++;
        $writerId = (int)($row['writer_id'] ?? 0);
        $adsViews = (float)($row['ads_views'] ?? 0);
        $adsIncome = round((float)($row['ads_income'] ?? 0), 4);

        if ($writerId <= 0 || $adsIncome <= 0) {
            $skipped++;
            continue;
        }

        if ($hasSourceColumns) {
            $existsQuery = mysqli_query($sqlConnect, "
                SELECT id
                FROM wo_writer_transaction
                WHERE writer_id = {$writerId}
                  AND type = 'ads'
                  AND source = '{$sourceSafe}'
                  AND source_date = '{$dateSafe}'
                LIMIT 1
            ");
        } else {
            $existsQuery = mysqli_query($sqlConnect, "
                SELECT id
                FROM wo_writer_transaction
                WHERE writer_id = {$writerId}
                  AND type = 'ads'
                  AND DATE(created_at) = '{$dateSafe}'
                LIMIT 1
            ");
        }

        if ($existsQuery && mysqli_num_rows($existsQuery) > 0) {
            $skipped++;
            continue;
        }

        if ($hasSourceColumns) {
            $insertSql = "
                INSERT INTO wo_writer_transaction
                    (`writer_id`, `type`, `count`, `value_in_inr`, `source`, `source_date`, `created_at`, `updated_at`)
                VALUES
                    ({$writerId}, 'ads', {$adsViews}, {$adsIncome}, '{$sourceSafe}', '{$dateSafe}', '{$createdAt}', NOW())
                ON DUPLICATE KEY UPDATE `updated_at` = `updated_at`
            ";
        } else {
            $insertSql = "
                INSERT INTO wo_writer_transaction
                    (`writer_id`, `type`, `count`, `value_in_inr`, `created_at`, `updated_at`)
                VALUES
                    ({$writerId}, 'ads', {$adsViews}, {$adsIncome}, '{$createdAt}', NOW())
            ";
        }

        if (mysqli_query($sqlConnect, $insertSql)) {
            if (mysqli_affected_rows($sqlConnect) > 0) {
                $inserted++;
            } else {
                $skipped++;
            }
        } else {
            $failed++;
            customErrorLogger([
                'writer_id' => $writerId,
                'date' => $date_key,
                'error' => mysqli_error($sqlConnect),
            ], 'writer_ads_daily_ledger', 'insert_failed');
        }
    }

    return [
        'status' => $failed === 0,
        'date' => $date_key,
        'checked' => $checked,
        'inserted' => $inserted,
        'skipped' => $skipped,
        'failed' => $failed,
        'source_columns' => $hasSourceColumns ? 1 : 0,
    ];
}

function Wo_GetRequireChaptersCount($afterTimestamp = null) {
    global $sqlConnect, $wo;

    $user_id = $wo['user']['user_id'] ?? 0;
    if (!$user_id) {
        return 0;
    }

    $user_id = Wo_Secure($user_id);

    // Step 1: Get all novel IDs for this user
    $novelQuery = "
        SELECT id
        FROM wo_novels
        WHERE user_id = '{$user_id}'
    ";

    $novelResult = mysqli_query($sqlConnect, $novelQuery);
    if (!$novelResult) {
        error_log("SQL Error (Wo_GetRequireChaptersCount - novels): " . mysqli_error($sqlConnect));
        return 0;
    }

    $novel_ids = [];
    while ($row = mysqli_fetch_assoc($novelResult)) {
        $novel_ids[] = (int) $row['id'];
    }

    if (empty($novel_ids)) {
        return 0;
    }

    // Step 2: Count chapters for these novels
    $novelIdsStr = implode(',', array_map('Wo_Secure', $novel_ids));

    $chapterWhere = "novel_id IN ({$novelIdsStr})";
    if ($afterTimestamp) {
        $afterTimestamp = Wo_Secure($afterTimestamp);
        $chapterWhere .= " AND updated_timestmape > '{$afterTimestamp}'";
    }

    $chapterQuery = "
        SELECT COUNT(*) AS chapter_count
        FROM wo_novel_chapter
        WHERE {$chapterWhere}
    ";

    $chapterResult = mysqli_query($sqlConnect, $chapterQuery);
    if (!$chapterResult) {
        error_log("SQL Error (Wo_GetRequireChaptersCount - chapters): " . mysqli_error($sqlConnect));
        return 0;
    }

    $row = mysqli_fetch_assoc($chapterResult);
    return (int) $row['chapter_count'];
}

function Wo_GetRequireChapters($lastTimestamp = null, $lastId = null, $limit = 20) {
    global $sqlConnect, $wo;

    $user_id = (int)($wo['user']['user_id'] ?? 0);
    if (!$user_id) return [];

    $where = "n.user_id = '$user_id'";
    
    if ($lastTimestamp && $lastId) {
        $lastTimestamp = (int)$lastTimestamp;
        $lastId = (int)$lastId;

        $where .= " AND (
            c.updated_timestmape < $lastTimestamp 
            OR 
            (c.updated_timestmape = $lastTimestamp AND c.id < $lastId)
        )";
    }

    $limit = (int)$limit;

    $chapterQuery = "
        SELECT c.*, n.lang_key 
        FROM wo_novel_chapter c
        JOIN wo_novels n ON c.novel_id = n.id
        WHERE $where 
        ORDER BY c.updated_timestmape DESC, c.id DESC 
        LIMIT $limit
    ";

    $chapterResult = mysqli_query($sqlConnect, $chapterQuery);
    if (!$chapterResult) return [];

    $final_chapters = [];

    while ($chapter = mysqli_fetch_assoc($chapterResult)) {

        $chapter_id = (int)$chapter['id'];
        $lang_key   = $chapter['lang_key'];
        $chapter['content'] = Wo_GetChapterContent($chapter_id, $lang_key);

        $final_chapters[] = $chapter;
    }

    return $final_chapters;
}


/**
 * Fetches the IDs of all novels created by the designated AI writer.
 *
 * @param string|null $afterTimestamp Optional. If provided, fetches only novels updated after this timestamp (Format: Y-m-d H:i:s).
 * @return array An array of novel IDs. Returns an empty array on failure or if no novels are found.
 */

function Wo_GetAiCreatedNovelIds($afterTimestamp = null) {
    global $sqlConnect;

    $ai_writer_id = 13433;
    $novel_ids = [];

    $whereCondition = "user_id = '{$ai_writer_id}'";

    if ($afterTimestamp) {
        $afterTimestamp = Wo_Secure($afterTimestamp);
        $whereCondition .= " AND updated_timestmape > '{$afterTimestamp}'";
    }

    $query = "
        SELECT id
        FROM wo_novels
        WHERE {$whereCondition}
        ORDER BY updated_timestmape DESC
    ";

    $result = mysqli_query($sqlConnect, $query);
    if (!$result) {
        error_log("SQL Error (Wo_GetAiCreatedNovelIds): " . mysqli_error($sqlConnect));
        return [];
    }

    while ($row = mysqli_fetch_assoc($result)) {
        $novel_ids[] = $row['id'];
    }

    return $novel_ids;
}

function Wo_GetUserOwnNovelIds($afterTimestamp = null) {
    global $sqlConnect, $wo;

    if (empty($wo['user']['user_id'])) return [];

    $user_id = (int) $wo['user']['user_id'];
    $novel_ids = [];

    $whereCondition = "user_id = '{$user_id}'";
    if ($afterTimestamp) {
        $afterTimestamp = Wo_Secure($afterTimestamp);
        $whereCondition .= " AND updated_timestmape > '{$afterTimestamp}'";
    }

    $query = "
        SELECT id
        FROM wo_novels
        WHERE {$whereCondition}
        ORDER BY updated_timestmape DESC
    ";

    $result = mysqli_query($sqlConnect, $query);
    if (!$result) {
        error_log("SQL Error (Wo_GetUserOwnNovelIds): " . mysqli_error($sqlConnect));
        return [];
    }

    while ($row = mysqli_fetch_assoc($result)) {
        $novel_ids[] = $row['id'];
    }

    return $novel_ids;
}

function Wo_GetAllNovelIdsForHomePage($afterTimestamp = null) {
    global $sqlConnect, $wo;

    $user_lang = Wo_Secure($wo['lang_key']);
    $novel_ids = [];

    $whereCondition = "`public` = 1 AND lang_key = '{$user_lang}' AND chapter_count > 25";
    if ($afterTimestamp) {
        $afterTimestamp = Wo_Secure($afterTimestamp);
        $whereCondition .= " AND updated_at > '{$afterTimestamp}'";
    }

    $query = "
        SELECT id
        FROM wo_novels
        WHERE {$whereCondition}
        ORDER BY updated_at DESC
    ";

    $result = mysqli_query($sqlConnect, $query);
    if (!$result) {
        error_log("SQL Error (Wo_GetAllNovelIdsForHomePage): " . mysqli_error($sqlConnect));
        return [];
    }

    while ($row = mysqli_fetch_assoc($result)) {
        $novel_ids[] = $row['id'];
    }

    return $novel_ids;
}
function getRequiredNovelIds($afterTimestamp = null) {
    global $sqlConnect, $wo;

    try {
        $user_id = $wo['user']['user_id'] ?? 0;
        if (!$user_id) return [];

        $afterTimestamp = $afterTimestamp ? "'" . mysqli_real_escape_string($sqlConnect, $afterTimestamp) . "'" : null;

        $whereCondition = "user_id = {$user_id}";
        if ($afterTimestamp) {
            $whereCondition .= " AND updated_timestmape > {$afterTimestamp}";
        }

        $query = mysqli_query(
            $sqlConnect,
            "SELECT novel_id, updated_timestmape 
             FROM wo_user_require_novel 
             WHERE {$whereCondition}
             ORDER BY updated_timestmape DESC"
        );

        if (!$query) {
            // Log SQL error
            error_log("MySQL error in getRequiredNovelIds: " . mysqli_error($sqlConnect) . " | Query: " . $whereCondition);
            return [];
        }

        $ids = [];
        while ($row = mysqli_fetch_assoc($query)) {
            $ids[] = [
                'novel_id'     => (int) $row['novel_id'],
                'last_updated' => $row['updated_timestmape']
            ];
        }

        return $ids;

    } catch (Throwable $e) {
        error_log("Exception in getRequiredNovelIds: " . $e->getMessage());
        return [];
    }
}

function wo_update_user_require_novel($user_id, $novel_id, $remove = false) {
    global $sqlConnect;

    $user_id  = (int)$user_id;
    $novel_id = (int)$novel_id;

    if ($remove) {
        $query = "DELETE FROM wo_user_require_novel 
                  WHERE user_id = '$user_id' AND novel_id = '$novel_id'";
        $result = mysqli_query($sqlConnect, $query);

        if ($result) {
            return true;
        } else {
            error_log("Remove user require novel failed: " . mysqli_error($sqlConnect));
            return false;
        }
    } else {
        $timestamp = date('Y-m-d H:i:s');

        if ($user_id === 0) {
            // Update all users for this novel
            $query = "UPDATE wo_user_require_novel 
                      SET last_updated = '$timestamp' 
                      WHERE novel_id = '$novel_id'";
        } else {
            // Regular insert/update for single user
            $query = "INSERT INTO wo_user_require_novel (user_id, novel_id, last_updated)
                      VALUES ('$user_id', '$novel_id', '$timestamp')
                      ON DUPLICATE KEY UPDATE last_updated = '$timestamp'";
        }

        $result = mysqli_query($sqlConnect, $query);

        if ($result) {
            return true;
        } else {
            error_log("Update user require novel failed: " . mysqli_error($sqlConnect));
            return false;
        }
    }
}



function wo_update_need_to_show_novel($novel_id) {
    global $sqlConnect;

    $novel_id  = (int)$novel_id;
    $timestamp = date('Y-m-d H:i:s');

    $query = "INSERT INTO wo_need_to_show_novel_on_homepage (novel_id, last_updated)
              VALUES ('$novel_id', '$timestamp')
              ON DUPLICATE KEY UPDATE last_updated = '$timestamp'";

    $result = mysqli_query($sqlConnect, $query);

    if ($result) {
        return true;
    } else {
        error_log("Update homepage novel failed: " . mysqli_error($sqlConnect));
        return false;
    }
}


function wo_ensure_reward_diamond_wallet_schema() {
    global $sqlConnect;

    static $ready = false;
    if ($ready) {
        return true;
    }

    $column = @mysqli_query($sqlConnect, "SHOW COLUMNS FROM `Wo_Users` LIKE 'reward_diamond'");
    if (!$column || mysqli_num_rows($column) === 0) {
        if (!@mysqli_query($sqlConnect, "ALTER TABLE `Wo_Users` ADD COLUMN `reward_diamond` INT UNSIGNED NOT NULL DEFAULT 0 AFTER `diamond`")) {
            return false;
        }
    }

    $ledgerExists = @mysqli_query($sqlConnect, "SHOW TABLES LIKE 'wo_diamond_spend_ledger'");
    $ledger = $ledgerExists && mysqli_num_rows($ledgerExists) > 0;
    if (!$ledger) {
        $ledger = @mysqli_query($sqlConnect, "CREATE TABLE IF NOT EXISTS `wo_diamond_spend_ledger` (
        `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
        `user_id` INT UNSIGNED NOT NULL,
        `writer_id` INT UNSIGNED NOT NULL DEFAULT 0,
        `novel_id` INT UNSIGNED NOT NULL DEFAULT 0,
        `chapter_id` INT UNSIGNED NOT NULL DEFAULT 0,
        `chapter_purchase_id` BIGINT UNSIGNED NOT NULL DEFAULT 0,
        `total_diamonds` INT UNSIGNED NOT NULL DEFAULT 0,
        `paid_diamonds` INT UNSIGNED NOT NULL DEFAULT 0,
        `reward_diamonds` INT UNSIGNED NOT NULL DEFAULT 0,
        `app_type` VARCHAR(30) NOT NULL DEFAULT 'novel',
        `created_at` INT UNSIGNED NOT NULL DEFAULT 0,
        `created_datetime` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
        PRIMARY KEY (`id`),
        UNIQUE KEY `uniq_chapter_purchase` (`chapter_purchase_id`),
        KEY `idx_user_created` (`user_id`, `created_at`),
        KEY `idx_writer_created` (`writer_id`, `created_at`)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
    }

    $rewardLedgerExists = @mysqli_query($sqlConnect, "SHOW TABLES LIKE 'wo_user_ad_diamond_rewards'");
    $rewardLedger = $rewardLedgerExists && mysqli_num_rows($rewardLedgerExists) > 0;
    if (!$rewardLedger) {
        $rewardLedger = @mysqli_query($sqlConnect, "CREATE TABLE IF NOT EXISTS `wo_user_ad_diamond_rewards` (
        `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
        `user_id` INT UNSIGNED NOT NULL,
        `diamonds` INT UNSIGNED NOT NULL DEFAULT 0,
        `reward_type` VARCHAR(40) NOT NULL DEFAULT 'rewarded_ad',
        `ad_network` VARCHAR(80) NOT NULL DEFAULT '',
        `placement` VARCHAR(80) NOT NULL DEFAULT 'reward_ad_page',
        `status` VARCHAR(20) NOT NULL DEFAULT 'credited',
        `created_at` INT UNSIGNED NOT NULL DEFAULT 0,
        `created_datetime` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
        PRIMARY KEY (`id`),
        KEY `idx_user_created` (`user_id`, `created_at`),
        KEY `idx_status` (`status`)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
    }

    $ready = (bool)$ledger && (bool)$rewardLedger;
    return $ready;
}

function wo_ensure_playstore_purchase_token_unique_schema() {
    global $sqlConnect;

    static $ready = false;
    if ($ready) {
        return true;
    }

    $indexes = @mysqli_query($sqlConnect, "SHOW INDEX FROM `wo_playstore_purchases` WHERE `Column_name` = 'purchase_token'");
    if ($indexes) {
        while ($index = mysqli_fetch_assoc($indexes)) {
            if ((int)($index['Non_unique'] ?? 1) === 0) {
                $ready = true;
                return true;
            }
        }
    }

    // Duplicate cleanup is intentionally not automatic. Production migration
    // creates a backup and de-duplicates first, then this becomes safe.
    if (!@mysqli_query($sqlConnect, "ALTER TABLE `wo_playstore_purchases`
        ADD UNIQUE KEY `uniq_playstore_purchase_token` (`purchase_token`)")) {
        return false;
    }

    $ready = true;
    return true;
}

function wo_credit_ad_reward_diamonds($userId, $diamonds, $adNetwork = '', $placement = 'reward_ad_page') {
    global $sqlConnect;

    $userId = (int)$userId;
    $diamonds = max(0, (int)$diamonds);
    if ($userId <= 0 || $diamonds <= 0 || !wo_ensure_reward_diamond_wallet_schema()) {
        return false;
    }

    $adNetwork = mysqli_real_escape_string($sqlConnect, (string)$adNetwork);
    $placement = mysqli_real_escape_string($sqlConnect, (string)$placement);
    $now = time();

    try {
        mysqli_begin_transaction($sqlConnect);
        $insert = mysqli_query($sqlConnect, "INSERT INTO wo_user_ad_diamond_rewards
            (user_id, diamonds, reward_type, ad_network, placement, status, created_at)
            VALUES ({$userId}, {$diamonds}, 'rewarded_ad', '{$adNetwork}', '{$placement}', 'credited', {$now})");
        if (!$insert) {
            throw new Exception('Unable to record ad reward.');
        }

        $credit = mysqli_query($sqlConnect, "UPDATE Wo_Users
            SET diamond = COALESCE(diamond, 0) + {$diamonds},
                reward_diamond = COALESCE(reward_diamond, 0) + {$diamonds}
            WHERE user_id = {$userId} LIMIT 1");
        if (!$credit || mysqli_affected_rows($sqlConnect) !== 1) {
            throw new Exception('Unable to credit ad reward.');
        }

        mysqli_commit($sqlConnect);
        return true;
    } catch (Throwable $e) {
        mysqli_rollback($sqlConnect);
        error_log('wo_credit_ad_reward_diamonds ERROR: ' . $e->getMessage() . " | user_id={$userId}");
        return false;
    }
}

function addTransaction($user_id, $writer_id, $type, $currency, $text, $text_type, $amount) {
    global $sqlConnect, $wo;

    $user_id   = (int)$user_id;
    $writer_id = (int)$writer_id;
    $type      = mysqli_real_escape_string($sqlConnect, $type);
    $currency  = mysqli_real_escape_string($sqlConnect, $currency);
    $text      = mysqli_real_escape_string($sqlConnect, $text);
    $text_type = mysqli_real_escape_string($sqlConnect, $text_type);
    $amount    = (float)$amount;
    $timestamp = time();

    // --- Convert to INR value based on currency ---
    $amount_in_inr = 0;
    if ($currency == 'coin') {
        $coin_rate = get_rate_from_config('coin_price');
        $amount_in_inr = $amount * $coin_rate;
    } 
    elseif ($currency == 'diamond') {
        $diamond_rate = get_rate_from_config('diamond_price');
        $amount_in_inr = $amount * $diamond_rate;
    } 
    else {
        // If other currencies like USD/EUR
        $amount_in_inr = $amount; // or add conversion if needed
    }

    // --- Insert transaction with INR value ---
    $query = "INSERT INTO wo_transjection 
                 (user_id, writer_id, type, currency, text, text_type, amount, amount_in_inr, created_at, updated_at) 
              VALUES 
                 ('$user_id', '$writer_id', '$type', '$currency', '$text', '$text_type', '$amount', '$amount_in_inr', '$timestamp', '$timestamp')";

    $result = mysqli_query($sqlConnect, $query);

    if ($result) {
        return mysqli_insert_id($sqlConnect);
    } else {
        error_log('Transaction insert failed: ' . mysqli_error($sqlConnect));
        return false;
    }
}


function wo_chk_for_level_upgradation($user_id) {
    return increase_writer_level($user_id);
}


function increase_writer_level($user_id) {
    global $sqlConnect, $wo;
    $user_data = Wo_UserData($user_id);
    if (!$user_data) {
        return false;
    }

    $current_level = (int)$user_data['writer_level'];
    $current_points = (int)$user_data['writing_point'];

    if (!check_level_requirements($user_id, $current_level, $current_points)) {
        return false;
    }

    $new_level = $current_level + 1;
    $update_level_query = "UPDATE Wo_Users SET writer_level = {$new_level} WHERE user_id = {$user_id}";
    $update_result = mysqli_query($sqlConnect, $update_level_query);

    if ($update_result && $new_level >= 5) {
        $channel_id = Wo_GetOrCreateInfoChannel($user_id);
        if ($channel_id) {
            $msg_text = "Congratulations everyone! Because of my hard work and your support, I have reached Level {$new_level}!";
            $message_data = array(
                'from_id'     => $user_id,
                'group_id'    => $channel_id,
                'text'        => $msg_text,
                'time'        => time(),
                'source_id'   => $new_level,
                'source_type' => 'level_up'
            );
            Wo_RegisterMessageGroup($message_data);
        }
    }

    send_level_increment_email($user_data, $new_level);
    cache($user_id, 'users', 'delete');
    return true;
}

function Wo_GetOrCreateInfoChannel($user_id) {
    global $sqlConnect, $wo;
    $user_id = Wo_Secure($user_id);
    $user_data = Wo_UserData($user_id);
    
    if (!$user_data || (int)$user_data['writer_level'] < 5) {
        return false;
    }

    $query = mysqli_query($sqlConnect, "SELECT group_id FROM " . T_GROUP_CHAT . " WHERE `user_id` = '{$user_id}' AND `is_official` = '1' LIMIT 1");
    if (mysqli_num_rows($query) > 0) {
        $fetch = mysqli_fetch_assoc($query);
        return $fetch['group_id'];
    }

    $group_name = ($user_data['name'] ?? 'Writer') . " Official Channel";
    $parts = array(); 
    
    $group_id = Wo_CreateGChat($group_name, $parts, 'channel', 1, $user_id);

    if ($group_id) {
        $avatar_path = 'upload/photos/d-group.jpg'; 
        
        if (!empty($user_data['avatar'])) {
            $pos = strpos($user_data['avatar'], 'upload/');
            if ($pos !== false) {
                $avatar_path = Wo_Secure(substr($user_data['avatar'], $pos));
            }
        }

        mysqli_query($sqlConnect, "UPDATE " . T_GROUP_CHAT . " SET `avatar` = '{$avatar_path}', `max_members` = '5000' WHERE `group_id` = '{$group_id}'");

        $add_followers_sql = "
            INSERT INTO " . T_GROUP_CHAT_USERS . " (user_id, group_id, active, last_seen)
            SELECT follower_id, '{$group_id}', '1', '0' 
            FROM " . T_FOLLOWERS . " 
            WHERE following_id = '{$user_id}' 
            AND follower_id != '{$user_id}'
            AND follower_id NOT IN (SELECT user_id FROM " . T_GROUP_CHAT_USERS . " WHERE group_id = '{$group_id}')
        ";
        mysqli_query($sqlConnect, $add_followers_sql);

        $res = mysqli_query($sqlConnect, "SELECT COUNT(*) as count FROM " . T_GROUP_CHAT_USERS . " WHERE group_id = '{$group_id}'");
        $count_data = mysqli_fetch_assoc($res);
        $total_members = $count_data['count'];
        
        mysqli_query($sqlConnect, "UPDATE " . T_GROUP_CHAT . " SET `member_count` = '{$total_members}' WHERE `group_id` = '{$group_id}'");

        return $group_id;
    }
    return false;
}

function send_level_increment_email($user_data, $new_level) {
    global $wo, $sqlConnect;

    // 1. Get Site Configs
    $siteName = (!empty($wo['config']['siteName'])) ? $wo['config']['siteName'] : 'Story Mania';

    // 2. Fetch the Level Increment Template from DB
    $temp_q = mysqli_query($sqlConnect, "SELECT `value` FROM `Wo_HTML_Emails` WHERE `name` = 'level_increment_notification' LIMIT 1");
    $temp_r = mysqli_fetch_assoc($temp_q);
    $template_html = ($temp_r) ? $temp_r['value'] : "Congratulations on reaching Level {{level}}!";

    // 3. Prepare Placeholders
    $placeholders = [
        '{{name}}'      => $user_data['name'],
        '{{level}}'     => $new_level,
        '{{points}}'    => $user_data['writing_point'],
        '{{site_name}}' => $siteName
    ];

    // 4. Generate Final Email Body
    $final_body = str_replace(array_keys($placeholders), array_values($placeholders), $template_html);

    $subject = "Congratulations on Your Level Increment to Level $new_level!";

    // 5. Add to Mail Job Queue (Priority 0)
    return wo_addmailtomailjob([
        'to_email'     => $user_data['email'],
        'to_name'      => $user_data['name'],
        'subject'      => $subject,
        'message_body' => $final_body,
        'is_html'      => true,
        'priority'     => 0
    ]);
}

function check_level_requirements($user_id, $current_level, $current_points) {
    global $sqlConnect;

    $next_level = $current_level + 1;
    $query = "SELECT point FROM wo_user_level_point_requiremnet WHERE level = {$next_level}";
    $result = mysqli_query($sqlConnect, $query);

    // 🧩 If no next level exists, create it automatically
    if (!$result || mysqli_num_rows($result) == 0) {
        $last_level_query = mysqli_query($sqlConnect, "
            SELECT level, point FROM wo_user_level_point_requiremnet ORDER BY level DESC LIMIT 1
        ");
        $last = mysqli_fetch_assoc($last_level_query);

        $last_level = $last ? (int)$last['level'] : 0;
        $last_points = $last ? (int)$last['point'] : 0;

        // Determine increment based on the range (1–10, 11–20, etc.)
        $range = ceil(($last_level + 1) / 10);
        $increment = $range * 1000;

        $new_level = $last_level + 1;
        $new_points = $last_points + $increment;

        // Insert the new level into DB. Keep required metric columns explicit
        // because older installs may have NOT NULL columns without defaults.
        mysqli_query($sqlConnect, "
            INSERT INTO wo_user_level_point_requiremnet
                (level, point, follower_count, novel_count, chapter_count, view_count)
            VALUES
                ({$new_level}, {$new_points}, 0, 0, 0, 0)
        ");

        // Re-query for the just-created level
        $result = mysqli_query($sqlConnect, "SELECT point FROM wo_user_level_point_requiremnet WHERE level = {$next_level}");
    }

    // If still no result, fail safely
    if (!$result || mysqli_num_rows($result) == 0) {
        return false;
    }

    $requirements = mysqli_fetch_assoc($result);
    return $current_points >= (int)$requirements['point'];
}

function wo_get_user_novel_count($user_id) {
    global $sqlConnect;
    $query = "SELECT COUNT(*) as count FROM wo_novels WHERE user_id = $user_id AND public = 1";
    $result = mysqli_query($sqlConnect, $query);
    $row = mysqli_fetch_assoc($result);
    return (int)$row['count'];
}



function wo_get_user_follower_count($user_id) {
    global $sqlConnect;
    $query = "SELECT COUNT(*) as count FROM Wo_Followers WHERE following_id = $user_id";
    $result = mysqli_query($sqlConnect, $query);
    $row = mysqli_fetch_assoc($result);
    return (int)$row['count'];
}

function check_novel_count($user_id) {
    global $sqlConnect;

    $novel_count_query = "SELECT COUNT(*) as novel_count FROM wo_novels WHERE user_id = $user_id AND public = 1";
    $novel_count_result = mysqli_query($sqlConnect, $novel_count_query);
    if (!$novel_count_result) {
        return false;
    }
    $novel_count_row = mysqli_fetch_assoc($novel_count_result);
    $novel_count = $novel_count_row['novel_count'];
    if ($novel_count < 1) {
        return false;
    }
    return true;
}


function check_chapter_count($user_id) {
    global $sqlConnect;
    
    $novel_ids_query = "
        SELECT id 
        FROM wo_novels 
        WHERE user_id = $user_id AND public = 1";
    $novel_ids_result = mysqli_query($sqlConnect, $novel_ids_query);
    if (!$novel_ids_result || mysqli_num_rows($novel_ids_result) == 0) {
        return false;
    }
    $novel_ids = [];
    while ($novel = mysqli_fetch_assoc($novel_ids_result)) {
        $novel_ids[] = $novel['id'];
    }
    $novel_ids_str = implode(",", $novel_ids); 
    $chapter_count_query = "
        SELECT COUNT(*) as chapter_count 
        FROM wo_novel_chapter 
        WHERE status = 'published' AND novel_id IN ($novel_ids_str)";
    $chapter_count_result = mysqli_query($sqlConnect, $chapter_count_query);
    if (!$chapter_count_result) {
        return false;
    }
    $chapter_count_row = mysqli_fetch_assoc($chapter_count_result);
    $chapter_count = $chapter_count_row['chapter_count'];
    if ($chapter_count < 1) {
        return false;
    }
    return true;
}



function check_follower_count($user_id) {
    global $sqlConnect;
    $follower_query = "
        SELECT COUNT(*) as follower_count 
        FROM Wo_Followers 
        WHERE following_id = $user_id";
    $follower_result = mysqli_query($sqlConnect, $follower_query);
    if (!$follower_result) {
        echo "Error: Failed to fetch follower count.\n";
        return false;
    }
    $follower_row = mysqli_fetch_assoc($follower_result);
    $follower_count = $follower_row['follower_count'];
    if ($follower_count < 1) {
        echo "User does not have enough followers.\n";
        return false;
    }
    return true;
}


function wo_update_user_novel_history($novel_id) {
    global $sqlConnect, $wo;
    $user_id = $wo['user']['user_id'];
    if (empty($novel_id)) {
        return false; 
    }
    $query_history = "
        SELECT `visit_count`, `updated_at`
        FROM `wo_user_novel_history`
        WHERE `user_id` = {$user_id} AND `novel_id` = {$novel_id}
        LIMIT 1";
    
    $history_sql = mysqli_query($sqlConnect, $query_history);
    if ($history_sql && mysqli_num_rows($history_sql) > 0) {
        $history = mysqli_fetch_assoc($history_sql);
        $visit_count = $history['visit_count'] + 1;
        $update_history_query = "
            UPDATE `wo_user_novel_history`
            SET `visit_count` = {$visit_count}, `updated_at` = NOW()
            WHERE `user_id` = {$user_id} AND `novel_id` = {$novel_id}
        ";
        mysqli_query($sqlConnect, $update_history_query);
    } else {
        $insert_history_query = "
            INSERT INTO `wo_user_novel_history` (`user_id`, `novel_id`, `visit_count`, `created_at`, `updated_at`)
            VALUES ({$user_id}, {$novel_id}, 1, NOW(), NOW())
        ";
        mysqli_query($sqlConnect, $insert_history_query);
    }

    return true;
}



function wo_addadsviewcountv2($novel_id, $chapter_id) {
    global $sqlConnect, $wo;
    $timings = [];
    $start_total = microtime(true);

    if (!$novel_id || !$chapter_id) return false; 

    // 1. Check Config for Ads Income (Default to true if not set)
    $allow_income = (!isset($wo['config']['allow_ads_income']) || $wo['config']['allow_ads_income'] == 1 || $wo['config']['allow_ads_income'] == 'yes');
    
    $novel_id = Wo_Secure($novel_id);
    $chapter_id = Wo_Secure($chapter_id);

    if ($allow_income) {
        // --- FULL INCOME LOGIC ---
        
        // 1. Setup Price
        $s = microtime(true);
        $google_ads_price = isset($wo['config']['google_ads_price']) ? (float)$wo['config']['google_ads_price'] : 0;
        $ads_earning = $google_ads_price * 0.08; 
        $timings['setup_calc'] = round(microtime(true) - $s, 4) . 's';

        // 2. Fetch Novel Info
        $s = microtime(true);
        $query = mysqli_query($sqlConnect, "SELECT user_id, is_promoting FROM wo_novels WHERE id = '$novel_id' LIMIT 1");
        $novel_data = mysqli_fetch_assoc($query);
        $timings['sql_fetch_novel'] = round(microtime(true) - $s, 4) . 's';
        
        if ($novel_data) {
            $user_id = $novel_data['user_id'];
            $is_promoting = (int)$novel_data['is_promoting'];

            // 3. Update writer wallet, or route below-level income to platform.
            $s = microtime(true);
            $credit_writer_income = SM_ShouldCreditWriterIncome($user_id, 'ads', $ads_earning, 1, [
                'novel_id' => $novel_id,
                'chapter_id' => $chapter_id,
                'source' => 'wo_addadsviewcountv2',
            ]);
            if ($credit_writer_income) {
                $user_sql = "UPDATE Wo_Users SET ads_view_income = ads_view_income + '{$ads_earning}', ads_view_count = ads_view_count + 1";
                if ($is_promoting === 1) {
                    $user_sql .= ", promotion_budget_deduction = promotion_budget_deduction + '{$ads_earning}'";
                }
                $user_sql .= " WHERE user_id = '{$user_id}' LIMIT 1";
                mysqli_query($sqlConnect, $user_sql);
            }
            $timings['sql_update_user'] = round(microtime(true) - $s, 4) . 's';

            // 4. Update Novel Stats. Below-level writers get views, income is routed to platform.
            $s = microtime(true);
            if ($credit_writer_income) {
                mysqli_query($sqlConnect, "UPDATE wo_novels SET add_views = add_views + 1, ads_view_income = ads_view_income + '{$ads_earning}' WHERE id = '$novel_id' LIMIT 1");
            } else {
                mysqli_query($sqlConnect, "UPDATE wo_novels SET add_views = add_views + 1 WHERE id = '$novel_id' LIMIT 1");
            }
            $timings['sql_update_novel'] = round(microtime(true) - $s, 4) . 's';

            // 5. Stats Functions
            $s = microtime(true);
            if ($credit_writer_income) {
                increaseCountInNovelStatistics($novel_id, 'ads_view_earning', $ads_earning);
            }
            increaseCountInNovelStatistics($novel_id, 'ads_view');
            $timings['func_statistics'] = round(microtime(true) - $s, 4) . 's';
        }
    } else {
        // --- LIGHTWEIGHT VIEW-ONLY LOGIC ---
        // Skip user table and income calculations entirely
        
        $s = microtime(true);
        mysqli_query($sqlConnect, "UPDATE wo_novels SET add_views = add_views + 1 WHERE id = '$novel_id' LIMIT 1");
        $timings['sql_update_novel_simple'] = round(microtime(true) - $s, 4) . 's';

        $s = microtime(true);
        increaseCountInNovelStatistics($novel_id, 'ads_view');
        $timings['func_statistics_simple'] = round(microtime(true) - $s, 4) . 's';
    }

     // 6. Update Chapter Stats (Always happens)
     //  $s = microtime(true);
    //mysqli_query($sqlConnect, "UPDATE wo_novel_chapter SET add_views = add_views + 1 WHERE id = '$chapter_id' LIMIT 1");
    // $timings['sql_update_chapter'] = round(microtime(true) - $s, 4) . 's';

    // 7. Score (Always happens)
    $s = microtime(true);
    increaseScore($novel_id, 5);
    $timings['func_score'] = round(microtime(true) - $s, 4) . 's';

    $total_time = round(microtime(true) - $start_total, 4);
    $timings['TOTAL_FUNC_TIME'] = $total_time . 's';
    $timings['income_enabled'] = $allow_income ? 'yes' : 'no';

    // Log performance to your custom logger
    customErrorLogger([
        'type' => 'ads_view_perf',
        'novel_id' => $novel_id,
        'details' => $timings
    ], "api_performance", ceil($total_time) . "s_log");

    return true;
}


function wo_addadsviewcountinusertable($novel_id, $chapter_id, $ads_earning) {
    global $sqlConnect;
    $query = "SELECT user_id FROM wo_novels WHERE id = $novel_id LIMIT 1";
    $result = mysqli_query($sqlConnect, $query);
    if (!$result) {
        echo "Query error: Failed to fetch user_id from wo_novels. Error: " . mysqli_error($sqlConnect) . "\n";
        return false;
    }
    if (mysqli_num_rows($result) == 0) {
        echo "No user found for novel_id: $novel_id\n";
        return false;
    }
    $row = mysqli_fetch_assoc($result);
    $user_id = $row['user_id'];
    if (!SM_ShouldCreditWriterIncome($user_id, 'ads', $ads_earning, 1, [
        'novel_id' => $novel_id,
        'chapter_id' => $chapter_id,
        'source' => 'wo_addadsviewcountinusertable',
    ])) {
        return true;
    }
    $updateQuery = "
        UPDATE Wo_Users
        SET ads_view_income = ads_view_income + $ads_earning
        WHERE user_id = $user_id
    ";
    $updateResult = mysqli_query($sqlConnect, $updateQuery);
    if (!$updateResult) {
        echo "Failed to update Wo_Users for user_id: $user_id. Error: " . mysqli_error($sqlConnect) . "\n";
        return false;
    }
    return true;
}


function wo_addadsviewcountinnoveltable($novel_id, $ads_earning) {
    global $sqlConnect;
    $query = "
        UPDATE wo_novels
        SET add_views = add_views + 1, ads_view_income = ads_view_income + $ads_earning
        WHERE id = $novel_id
    ";
    return mysqli_query($sqlConnect, $query) ? true : false;
}

function wo_addadsviewcountinchaptertable($chapter_id, $ads_earning) {
    global $sqlConnect;
    $query = "
        UPDATE wo_novel_chapter
        SET add_views = add_views + 1, ads_view_income = ads_view_income + $ads_earning
        WHERE id = $chapter_id
    ";

    return mysqli_query($sqlConnect, $query) ? true : false;
}



function wo_addviewcountv2($novel_id, $chapter_id) {
    global $sqlConnect;
    if (!$novel_id || !$chapter_id) {
        return false; 
    }
    wo_addviewcountinusertable($novel_id, $chapter_id);
    wo_addviewcountinnoveltable($novel_id);
    woaddviewcountinchaptertable($chapter_id);
    wo_addviewcountinnovelstatictable($novel_id);
    wo_addviewcountinwo_novel_trending_states($novel_id);

    return true;
}


function wo_addviewcountinusertable($novel_id, $chapter_id) {
    global $sqlConnect;
    $query = "
        SELECT user_id FROM wo_novels WHERE id = $novel_id LIMIT 1
    ";
    $result = mysqli_query($sqlConnect, $query);
    if (!$result) {
        return false; 
    }
    $row = mysqli_fetch_assoc($result);
    $user_id = $row['user_id'];
    $updateQuery = "
        UPDATE Wo_Users
        SET novel_view_count = novel_view_count + 1
        WHERE user_id = $user_id
    ";
    if (!mysqli_query($sqlConnect, $updateQuery)) {
        return false;
    }
    return true;
}


function wo_addviewcountinnoveltable($novel_id) {
    global $sqlConnect;
    $query = "
        UPDATE wo_novels
        SET view = view + 1
        WHERE id = $novel_id
    ";
    mysqli_query($sqlConnect, $query);
}

function woaddviewcountinchaptertable($chapter_id) {
    global $sqlConnect;
    $query = "
        UPDATE wo_novel_chapter
        SET views = views + 1
        WHERE id = $chapter_id
    ";
    mysqli_query($sqlConnect, $query);
}

function wo_addviewcountinnovelstatictable($novel_id) {
    global $sqlConnect, $wo;
    increaseCountInNovelStatistics($novel_id, 'views');
    if (empty($wo['user']['user_id'])) {
        increaseCountInNovelStatistics($novel_id, 'guest_user_view');
    }
}


if (!function_exists('Wo_NormalizeNovelStatsColumnName')) {
    function Wo_NormalizeNovelStatsColumnName($column_name) {
        $column_name = strtolower(trim((string)$column_name));
        $column_name = preg_replace('/[^a-z0-9_]/', '', $column_name);

        $allowed_columns = array(
            'ads_view',
            'ads_view_earning',
            'app_opening_banner_view_count',
            'banner_view_count',
            'bonus_admin_commission',
            'bonus_diamonds',
            'bonus_earning',
            'coins',
            'coins_earning',
            'comments',
            'diamonds',
            'diamonds_admin_commission',
            'diamonds_earning',
            'gift_admin_commission',
            'gift_count',
            'gift_diamonds',
            'gift_earning',
            'guest_user_view',
            'home_page_view_count',
            'library_page_view_count',
            'novel_buy_admin_commission',
            'novel_buy_count',
            'novel_buy_earning',
            'profile_page_view_count',
            'ratings',
            'superfan_admin_commission',
            'superfan_amount',
            'superfan_diamonds',
            'superfan_earning',
            'subscription_admin_commission',
            'subscription_count',
            'subscription_earning',
            'views',
            'writing_point'
        );

        if (in_array($column_name, $allowed_columns, true)) {
            return $column_name;
        }

        if (preg_match('/^[a-z0-9_]+_view_count$/', $column_name)) {
            return $column_name;
        }

        return '';
    }
}

if (!function_exists('Wo_EnsureNovelStatsColumns')) {
    function Wo_EnsureNovelStatsColumns($columns = array()) {
        static $checked = array();
        if (empty($columns)) {
            return false;
        }
        $available = true;
        foreach ((array)$columns as $column) {
            $column = Wo_NormalizeNovelStatsColumnName($column);
            if ($column === '') {
                continue;
            }
            if (!array_key_exists($column, $checked)) {
                $checked[$column] = function_exists('Wo_DbColumnExistsSafe')
                    && Wo_DbColumnExistsSafe('wo_novel_statices', $column);
            }
            $available = $available && $checked[$column];
        }
        return $available;
    }
}

function increaseCountInNovelStatistics($novel_id, $column_name, $increment_value = 1) {
    global $cache, $sqlConnect;

    $novel_id = (int)$novel_id;
    
    // ❌ PURANA: $increment_value = (int)$increment_value; (Yeh galti thi!)
    // ✅ NAYA: Ab yeh decimal/point ko nahi udayega
    $increment_value = is_numeric($increment_value) ? (float)$increment_value : 0; 
    
    $column_name = Wo_NormalizeNovelStatsColumnName($column_name);
    if ($novel_id <= 0 || $increment_value <= 0 || empty($column_name)) return false;
    Wo_EnsureNovelStatsColumns(array($column_name));

    $window_size = 600; 
    $current_window = floor(time() / $window_size) * $window_size;
    $current_key = "stats_bucket_" . $current_window;
    $registry_key = "stats_bucket_registry";

    // ✅ STEP 1: Write Lock
    $write_lock = "lock_write_" . $current_window;
    $timeout = 5; 
    $start = time();
    
    while ($cache->read($write_lock)) {
        if (time() - $start > $timeout) break; 
        usleep(50000); 
    }
    $cache->write($write_lock, true, 2);

    // --- CRITICAL SECTION START ---
    $data = $cache->read($current_key) ?: [];
    
    $registry = $cache->read($registry_key) ?: [];
    if (!in_array($current_window, $registry)) {
        $registry[] = $current_window;
        $cache->write($registry_key, $registry);
    }

    if (!isset($data[$novel_id][$column_name])) $data[$novel_id][$column_name] = 0;
    
    // Ab yahan 0.96 add hoga toh 0.96 hi rahega, 0 nahi banega
    $data[$novel_id][$column_name] += $increment_value; 
    
    $cache->write($current_key, $data);
    $cache->delete($write_lock); 
    // --- CRITICAL SECTION END ---


    // ✅ STEP 2: Process OLD windows (Syncing)
    $registry = $cache->read($registry_key) ?: [];
    foreach ($registry as $index => $window_ts) {
        if ($window_ts < $current_window) {
            
            $sync_lock = "lock_sync_" . $window_ts;
            if ($cache->read($sync_lock)) continue; 
            $cache->write($sync_lock, true, 120); 

            $old_data = $cache->read("stats_bucket_" . $window_ts);
            
            if (is_array($old_data) && !empty($old_data)) {
                $todayTimestamp = strtotime(date('Y-m-d'));
                mysqli_begin_transaction($sqlConnect);
                try {
                    foreach ($old_data as $nid => $columns) {
                        $update_parts = []; $insert_cols = []; $insert_vals = [];
                        foreach ($columns as $col => $val) {
                            $col = Wo_NormalizeNovelStatsColumnName($col);
                            if ($col === '') {
                                continue;
                            }
                            $insert_cols[] = "`$col`";
                            // ✅ (int) hata diya taaki DB mein point wala paisa jaye
                            $insert_vals[] = (float)$val; 
                            $update_parts[] = "`$col` = `$col` + " . (float)$val;
                        }

                        if (empty($insert_cols)) {
                            continue;
                        }

                        $q = "INSERT INTO wo_novel_statices (novel_id, timestamp, ".implode(',',$insert_cols).") 
                              VALUES ($nid, $todayTimestamp, ".implode(',',$insert_vals).") 
                              ON DUPLICATE KEY UPDATE ".implode(',',$update_parts);
                        
                        if (!mysqli_query($sqlConnect, $q)) {
                            $errno = mysqli_errno($sqlConnect);
                            if ($errno == 1054) {
                                mysqli_rollback($sqlConnect); 
                                foreach ($columns as $col => $val) {
                                    $col = Wo_NormalizeNovelStatsColumnName($col);
                                    if ($col === '') {
                                        continue;
                                    }
                                    $checkCol = mysqli_query($sqlConnect, "SHOW COLUMNS FROM `wo_novel_statices` LIKE '$col'");
                                    if (mysqli_num_rows($checkCol) == 0) {
                                        // ✅ NAYA: Column ko DECIMAL banaya taaki point support kare
                                        mysqli_query($sqlConnect, "ALTER TABLE `wo_novel_statices` ADD `$col` DECIMAL(20,2) NOT NULL DEFAULT '0.00'");
                                    }
                                }
                                mysqli_begin_transaction($sqlConnect);
                                mysqli_query($sqlConnect, $q);
                            } else {
                                throw new Exception(mysqli_error($sqlConnect));
                            }
                        }
                    }
                    mysqli_commit($sqlConnect);
                    $cache->delete("stats_bucket_" . $window_ts);
                    
                    $current_registry = $cache->read($registry_key) ?: [];
                    $new_registry = array_diff($current_registry, [$window_ts]);
                    $cache->write($registry_key, array_values($new_registry));

                } catch (Exception $e) {
                    mysqli_rollback($sqlConnect);
                    error_log("STATS SYNC FAILURE: " . $e->getMessage());
                }
            } else {
                $current_registry = $cache->read($registry_key) ?: [];
                $new_registry = array_diff($current_registry, [$window_ts]);
                $cache->write($registry_key, array_values($new_registry));
            }
            $cache->delete($sync_lock);
        }
    }
    return true;
}

function increase_writing_point($user_id, $novel_id, $points) {
    global $sqlConnect;
    if (!$user_id || !$novel_id || !$points) {
        echo "Invalid parameters: user_id, novel_id, or points missing.\n";
        return false;
    }
    $updateQuery = "
        UPDATE Wo_Users
        SET writing_point = writing_point + $points
        WHERE user_id = $user_id
    ";
    $updateResult = mysqli_query($sqlConnect, $updateQuery);
    if (!$updateResult) {
        echo "Failed to update writing points for user_id: $user_id. Error: " . mysqli_error($sqlConnect) . "\n";
        return false;
    }
    wo_chk_for_level_upgradation($user_id);
    cache($user_id, 'users', 'delete');
    return true;
}




function updateRecommendedForSpecificUsers() {
    $user_ids = [86, 70, 1260, 780, 822, 849, 829, 824, 99, 871, 842, 820];
    return updateRecommendedForUsers($user_ids); 
}


function wo_addviewcountinwo_novel_trending_states($novel_id) {
    global $sqlConnect;
    $query = "
        UPDATE wo_novel_trending_states
        SET daily_score = daily_score + 1
        WHERE novel_id = $novel_id
    ";
    mysqli_query($sqlConnect, $query);
}


function processPublishedChaptersforaddingviews($skipChapters = 300, $viewsToAdd = 1) {
    global $sqlConnect;
    $query = "
        SELECT id AS chapter_id, novel_id
        FROM wo_novel_chapter
        WHERE status = 'published'
    ";
    $result = mysqli_query($sqlConnect, $query);
    if (!$result) {
        return false; 
    }
    $chapters = [];
    while ($row = mysqli_fetch_assoc($result)) {
        $chapters[] = $row;
    }
    shuffle($chapters);
    for ($i = $skipChapters; $i < count($chapters); $i += ($skipChapters + 1)) {
        $chapter_id = $chapters[$i]['chapter_id'];
        $novel_id = $chapters[$i]['novel_id'];
        wo_addviewcountv2($novel_id, $chapter_id,);
        $updateQuery = "
            UPDATE wo_novel_chapter
            SET extra_views = extra_views + $viewsToAdd
            WHERE id = $chapter_id
        ";
        mysqli_query($sqlConnect, $updateQuery);
    }
    return true;
}


function increaseScore($novel_id, $value = 1) {
    global $sqlConnect;
    
    $novel_id = (int)$novel_id;
    $value = (int)$value;

    if ($novel_id <= 0) {
        return false;
    }

    // Use COALESCE(column, 0) so that if the current score is NULL, it starts at 0
    $update_query = "
        UPDATE wo_novels
        SET daily_score  = COALESCE(daily_score, 0) + $value,
            weekly_score = COALESCE(weekly_score, 0) + $value,
            yearly_score = COALESCE(yearly_score, 0) + $value
        WHERE id = $novel_id
    ";
    
    $result = mysqli_query($sqlConnect, $update_query);

    if (!$result) {
        error_log("SQL ERROR in increaseScore: " . mysqli_error($sqlConnect));
        return false; 
    }

    // OPTIONAL: If you want to be 100% sure the ID exists without relying on affected_rows
    // you can check if the row exists first, but that's an extra query.
    // Instead, we check affected_rows BUT we fixed the NULL issue above.
    if (mysqli_affected_rows($sqlConnect) === 0) {
        // If it still says 0, it means either:
        // 1. The ID really doesn't exist.
        // 2. The database is in 'Found Rows' mode rather than 'Affected Rows' mode.
        
        // Let's do a quick safety check only if it fails
        $check = mysqli_query($sqlConnect, "SELECT id FROM wo_novels WHERE id = $novel_id LIMIT 1");
        if (mysqli_num_rows($check) === 0) {
            error_log("NOTICE: increaseScore failed. Novel ID $novel_id actually missing.");
            return false;
        }
    }

    return true;
}

function resetTrendingStats($forceDailyReset = false) {
    global $sqlConnect;
    date_default_timezone_set('Asia/Kolkata');

    $current_day = strtotime('today midnight');
    $current_week_start = strtotime('last Sunday midnight');
    $current_year_start = strtotime(date('Y') . '-01-01 midnight');
    $forceDailyReset = (bool)$forceDailyReset;

    $dailyNeedsReset = $forceDailyReset;
    if (!$dailyNeedsReset) {
        $dailyCheck = mysqli_query(
            $sqlConnect,
            "SELECT COALESCE(MAX(daily_score_timestamp), 0) AS last_reset FROM wo_novels"
        );
        $dailyState = $dailyCheck ? mysqli_fetch_assoc($dailyCheck) : null;
        $dailyNeedsReset = $dailyState && (int)$dailyState['last_reset'] < $current_day;
    }

    $daily_result = true;
    $daily_rows = 0;
    $retainedTopThree = [];
    if ($dailyNeedsReset) {
        mysqli_begin_transaction($sqlConnect);
        try {
            $topResult = mysqli_query($sqlConnect, "
                SELECT id, COALESCE(daily_score, 0) AS daily_score
                FROM wo_novels
                WHERE public = 1
                  AND status = 1
                  AND COALESCE(chapter_count, 0) > 0
                  AND COALESCE(daily_score, 0) > 0
                ORDER BY daily_score DESC, id DESC
                LIMIT 3
                FOR UPDATE
            ");
            if (!$topResult) {
                throw new RuntimeException(mysqli_error($sqlConnect));
            }

            $retainedCases = [];
            while ($topRow = mysqli_fetch_assoc($topResult)) {
                $novelId = (int)$topRow['id'];
                $oldScore = max(0, (int)$topRow['daily_score']);
                $retainedScore = max(1, (int)floor($oldScore * 0.01));
                $retainedCases[] = "WHEN id = {$novelId} THEN {$retainedScore}";
                $retainedTopThree[] = [
                    'novel_id' => $novelId,
                    'old_score' => $oldScore,
                    'retained_score' => $retainedScore,
                ];
            }

            $scoreExpression = empty($retainedCases)
                ? '0'
                : 'CASE ' . implode(' ', $retainedCases) . ' ELSE 0 END';
            $daily_result = mysqli_query($sqlConnect, "
                UPDATE wo_novels
                SET daily_score = {$scoreExpression},
                    daily_score_timestamp = {$current_day}
            ");
            if (!$daily_result) {
                throw new RuntimeException(mysqli_error($sqlConnect));
            }
            $daily_rows = mysqli_affected_rows($sqlConnect);

            $legacyTable = mysqli_query($sqlConnect, "SHOW TABLES LIKE 'wo_novel_trending_states'");
            if ($legacyTable && mysqli_num_rows($legacyTable) > 0) {
                mysqli_query($sqlConnect, "UPDATE wo_novel_trending_states SET daily_score = 0");
                foreach ($retainedTopThree as $retained) {
                    $novelId = (int)$retained['novel_id'];
                    $retainedScore = (int)$retained['retained_score'];
                    mysqli_query(
                        $sqlConnect,
                        "UPDATE wo_novel_trending_states SET daily_score = {$retainedScore} WHERE novel_id = {$novelId}"
                    );
                }
            }
            mysqli_commit($sqlConnect);
        } catch (Throwable $exception) {
            mysqli_rollback($sqlConnect);
            $daily_result = false;
            error_log('Daily trending reset failed: ' . $exception->getMessage());
        }
    }

    $weekly_reset_query = "
        UPDATE wo_novels
        SET weekly_score = GREATEST(1, FLOOR(weekly_score * 0.01)),
            weekly_score_timestamp = $current_week_start
        WHERE weekly_score_timestamp < ($current_week_start - 604800)"; 

    $yearly_reset_query = "
        UPDATE wo_novels
        SET yearly_score = GREATEST(1, FLOOR(yearly_score * 0.01)),
            yearly_score_timestamp = $current_year_start
        WHERE yearly_score_timestamp < ($current_year_start - 31536000)";  

    $weekly_result = mysqli_query($sqlConnect, $weekly_reset_query);
    $weekly_rows = mysqli_affected_rows($sqlConnect);

    $yearly_result = mysqli_query($sqlConnect, $yearly_reset_query);
    $yearly_rows = mysqli_affected_rows($sqlConnect);

    // Log success or error
    if (!$daily_result) {
        error_log("❌ Daily Query Error: " . mysqli_error($sqlConnect));
    } else {
        error_log("✅ Daily reset: {$daily_rows} rows updated on " . date('Y-m-d H:i:s'));
    }

    if (!$weekly_result) {
        error_log("❌ Weekly Query Error: " . mysqli_error($sqlConnect));
    } else {
        error_log("✅ Weekly reset: {$weekly_rows} rows updated on " . date('Y-m-d H:i:s'));
    }

    if (!$yearly_result) {
        error_log("❌ Yearly Query Error: " . mysqli_error($sqlConnect));
    } else {
        error_log("✅ Yearly reset: {$yearly_rows} rows updated on " . date('Y-m-d H:i:s'));
    }
    return [
        'success' => (bool)($daily_result && $weekly_result && $yearly_result),
        'daily_reset_ran' => $dailyNeedsReset,
        'daily_rows' => $daily_rows,
        'retained_top_three' => $retainedTopThree,
        'reset_timestamp' => $current_day,
    ];
}

function resetAllNovelScoresToOne() {
    global $sqlConnect;
    date_default_timezone_set('Asia/Kolkata');

    // Set 9:00 AM timestamp of today
    $reset_time = strtotime(date('Y-m-d') . ' 09:00:00');

    $query = "
        UPDATE wo_novels
        SET 
            daily_score = 1,
            weekly_score = 1,
            yearly_score = 1,
            daily_score_timestamp = $reset_time,
            weekly_score_timestamp = $reset_time,
            yearly_score_timestamp = $reset_time
    ";

    $result = mysqli_query($sqlConnect, $query);
    $affected_rows = mysqli_affected_rows($sqlConnect);

    if (!$result) {
        error_log("❌ Reset All Scores Error: " . mysqli_error($sqlConnect));
    } else {
        error_log("✅ All scores reset to 1 for {$affected_rows} novels at " . date('Y-m-d H:i:s'));
    }
}



function saveTrendingNovelsToDatabase($type = 'daily', $limit = 10, $offset = 0) {
    global $sqlConnect;
    $limit = intval($limit);
    $offset = intval($offset);
    $score_column = '';
    switch ($type) {
        case 'weekly':
            $score_column = 'weekly_score';
            break;
        case 'yearly':
            $score_column = 'yearly_score';
            break;
        default:
            $score_column = 'daily_score';
            break;
    }
    $query = "
    SELECT novel_id, $score_column AS total_score
    FROM wo_novels
    ORDER BY $score_column DESC
    LIMIT $offset, $limit";
    $result = mysqli_query($sqlConnect, $query);
    if (!$result) {
        error_log('MySQL Error: ' . mysqli_error($sqlConnect));
        return false;
    }
    while ($row = mysqli_fetch_assoc($result)) {
        $novel_id = intval($row['novel_id']);
        $total_score = intval($row['total_score']);
        $check_query = "SELECT id FROM wo_trending_novel WHERE novel_id = $novel_id AND type = '$type'";
        $check_result = mysqli_query($sqlConnect, $check_query);
        if (mysqli_num_rows($check_result) > 0) {
            $update_query = "UPDATE wo_trending_novel 
                             SET total_score = $total_score, updated_at = CURRENT_TIMESTAMP 
                             WHERE novel_id = $novel_id AND type = '$type'";
            mysqli_query($sqlConnect, $update_query);
        } else {
            $insert_query = "INSERT INTO wo_trending_novel (novel_id, total_score, type, created_at) 
                             VALUES ($novel_id, $total_score, '$type', CURRENT_TIMESTAMP)";
            mysqli_query($sqlConnect, $insert_query);
        }
    }

    return true;
}

