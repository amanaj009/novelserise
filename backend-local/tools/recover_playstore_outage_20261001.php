<?php

// Idempotent recovery for the verified-purchase outage that began on 2026-10-01.
// Run without --apply for a dry run. The endpoint later matches and consumes the
// original Play token without issuing a second wallet credit.

$_SERVER['DOCUMENT_ROOT'] = '/home/app.novelseries.in/public_html';
require $_SERVER['DOCUMENT_ROOT'] . '/assets/init.php';

global $sqlConnect;

$apply = in_array('--apply', $argv ?? [], true);
$outageKey = 'playstore-helper-outage-20261001';
$outageStartLocal = new DateTimeImmutable('2026-10-01 14:29:00', new DateTimeZone('Asia/Kolkata'));
$outageStartUtc = $outageStartLocal->setTimezone(new DateTimeZone('UTC'))->getTimestamp();
$logPath = $_SERVER['DOCUMENT_ROOT'] . '/error_logs/playstore_purchase/verify_playstore_purchase.log';

if (!is_file($logPath)) {
    fwrite(STDERR, "Verification log not found.\n");
    exit(1);
}

$schema = "CREATE TABLE IF NOT EXISTS `wo_playstore_manual_recoveries` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `user_id` INT NOT NULL,
    `product_id` VARCHAR(255) NOT NULL,
    `amount` INT UNSIGNED NOT NULL,
    `outage_key` VARCHAR(64) NOT NULL,
    `first_seen_at` INT NOT NULL,
    `credited_at` INT NOT NULL,
    `matched_purchase_id` INT NULL,
    `matched_at` INT NULL,
    `notes` VARCHAR(255) NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uniq_manual_recovery` (`user_id`, `product_id`, `outage_key`),
    KEY `idx_pending_recovery` (`user_id`, `product_id`, `matched_purchase_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";

if (!mysqli_query($sqlConnect, $schema)) {
    fwrite(STDERR, "Could not create recovery table: " . mysqli_error($sqlConnect) . "\n");
    exit(1);
}

$raw = file_get_contents($logPath);
preg_match_all('/^\[(?<time>2026-10-0[12] [^\]]+)\]\s+(?<body>.*?)(?=^\[|\z)/ms', $raw, $blocks, PREG_SET_ORDER);

$candidates = [];
foreach ($blocks as $block) {
    if (strpos($block['body'], 'Purchase-token uniqueness protection is unavailable.') === false) {
        continue;
    }
    $seen = DateTimeImmutable::createFromFormat('Y-m-d H:i:s', $block['time'], new DateTimeZone('Asia/Kolkata'));
    if (!$seen || $seen < $outageStartLocal) {
        continue;
    }
    if (!preg_match('/"user_id":\s*"?(\d+)/', $block['body'], $userMatch)
        || !preg_match('/"product_id":\s*"([^"]+)"/', $block['body'], $productMatch)) {
        continue;
    }
    $userId = (int)$userMatch[1];
    $productId = $productMatch[1];
    $key = $userId . ':' . $productId;
    if (!isset($candidates[$key]) || $seen < $candidates[$key]['first_seen']) {
        $candidates[$key] = [
            'user_id' => $userId,
            'product_id' => $productId,
            'first_seen' => $seen,
        ];
    }
}

ksort($candidates);
$summary = ['mode' => $apply ? 'apply' : 'dry-run', 'candidates' => count($candidates), 'credit' => [], 'skip' => [], 'errors' => []];
$creditedUsers = [];

foreach ($candidates as $candidate) {
    $userId = $candidate['user_id'];
    $productId = $candidate['product_id'];
    $firstSeen = $candidate['first_seen']->setTimezone(new DateTimeZone('UTC'))->getTimestamp();

    $stmtExisting = mysqli_prepare($sqlConnect, "SELECT id FROM wo_playstore_purchases
        WHERE user_id = ? AND product_id = ? AND status = 'verified' AND created_at >= ? LIMIT 1");
    mysqli_stmt_bind_param($stmtExisting, 'isi', $userId, $productId, $outageStartUtc);
    mysqli_stmt_execute($stmtExisting);
    mysqli_stmt_store_result($stmtExisting);
    $alreadyRecovered = mysqli_stmt_num_rows($stmtExisting) > 0;
    mysqli_stmt_close($stmtExisting);
    if ($alreadyRecovered) {
        $summary['skip'][] = ['user_id' => $userId, 'product_id' => $productId, 'reason' => 'verified-record-exists'];
        continue;
    }

    $stmtPack = mysqli_prepare($sqlConnect, 'SELECT diamonds, bonus FROM wo_daimond_pack WHERE sku = ? LIMIT 1');
    mysqli_stmt_bind_param($stmtPack, 's', $productId);
    mysqli_stmt_execute($stmtPack);
    mysqli_stmt_bind_result($stmtPack, $diamonds, $bonus);
    $foundPack = mysqli_stmt_fetch($stmtPack);
    mysqli_stmt_close($stmtPack);
    if (!$foundPack) {
        $summary['errors'][] = ['user_id' => $userId, 'product_id' => $productId, 'reason' => 'pack-not-found'];
        continue;
    }
    $amount = (int)$diamonds + (int)$bonus;

    $stmtMarker = mysqli_prepare($sqlConnect, 'SELECT id FROM wo_playstore_manual_recoveries
        WHERE user_id = ? AND product_id = ? AND outage_key = ? LIMIT 1');
    mysqli_stmt_bind_param($stmtMarker, 'iss', $userId, $productId, $outageKey);
    mysqli_stmt_execute($stmtMarker);
    mysqli_stmt_store_result($stmtMarker);
    $markerExists = mysqli_stmt_num_rows($stmtMarker) > 0;
    mysqli_stmt_close($stmtMarker);
    if ($markerExists) {
        $summary['skip'][] = ['user_id' => $userId, 'product_id' => $productId, 'reason' => 'recovery-marker-exists'];
        continue;
    }

    $summary['credit'][] = ['user_id' => $userId, 'product_id' => $productId, 'amount' => $amount];
    if (!$apply) {
        continue;
    }

    try {
        mysqli_begin_transaction($sqlConnect);
        $now = time();
        $notes = 'Google verification succeeded before uniqueness-helper failure; token redacted in historical log.';
        $stmtInsert = mysqli_prepare($sqlConnect, 'INSERT INTO wo_playstore_manual_recoveries
            (user_id, product_id, amount, outage_key, first_seen_at, credited_at, notes)
            VALUES (?, ?, ?, ?, ?, ?, ?)');
        mysqli_stmt_bind_param($stmtInsert, 'isisiis', $userId, $productId, $amount, $outageKey, $firstSeen, $now, $notes);
        if (!mysqli_stmt_execute($stmtInsert)) {
            throw new RuntimeException(mysqli_stmt_error($stmtInsert));
        }
        mysqli_stmt_close($stmtInsert);

        $stmtUser = mysqli_prepare($sqlConnect, 'UPDATE Wo_Users SET diamond = COALESCE(diamond, 0) + ? WHERE user_id = ?');
        mysqli_stmt_bind_param($stmtUser, 'ii', $amount, $userId);
        if (!mysqli_stmt_execute($stmtUser) || mysqli_stmt_affected_rows($stmtUser) !== 1) {
            throw new RuntimeException('User wallet update failed: ' . mysqli_stmt_error($stmtUser));
        }
        mysqli_stmt_close($stmtUser);
        mysqli_commit($sqlConnect);
        $creditedUsers[$userId] = true;
    } catch (Throwable $error) {
        mysqli_rollback($sqlConnect);
        $summary['errors'][] = ['user_id' => $userId, 'product_id' => $productId, 'reason' => $error->getMessage()];
    }
}

if ($apply) {
    foreach (array_keys($creditedUsers) as $userId) {
        cache($userId, 'users', 'delete');
    }
}

$summary['credited_users'] = array_map('intval', array_keys($creditedUsers));
$summary['credit_count'] = count($summary['credit']);
$summary['skip_count'] = count($summary['skip']);
$summary['error_count'] = count($summary['errors']);
echo json_encode($summary, JSON_PRETTY_PRINT | JSON_UNESCAPED_SLASHES) . PHP_EOL;
