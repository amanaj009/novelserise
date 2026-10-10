<?php

global $sqlConnect, $wo;

if (!function_exists('Wo_ReportPlayStorePurchaseError')) {
    function Wo_ReportPlayStorePurchaseError($message, $context = [], $key = '')
    {
        if (function_exists('customErrorLogger')) {
            customErrorLogger([
                'message' => (string)$message,
                'context' => is_array($context) ? $context : ['value' => $context],
                'error_key' => $key !== '' ? $key : 'playstore-purchase'
            ], 'playstore_purchase', 'verify_playstore_purchase');
        }

        Wo_ReportSystemError(
            'Play Store purchase verification failed',
            (string)$message,
            $context,
            [
                'channel' => 'playstore_purchase',
                'log_file' => 'playstore_purchase',
                'error_key' => $key !== '' ? $key : 'playstore-purchase'
            ]
        );
    }
}

if (!function_exists('Wo_EnsurePlayStoreManualRecoverySchema')) {
    function Wo_EnsurePlayStoreManualRecoverySchema()
    {
        global $sqlConnect;

        return (bool)@mysqli_query($sqlConnect, "CREATE TABLE IF NOT EXISTS `wo_playstore_manual_recoveries` (
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
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
    }
}

// Resolve package name from server-side config instead of trusting client input
$packageName = '';
if (function_exists('playStoreConfiguredPackageName')) {
    $packageName = wo_secure(playStoreConfiguredPackageName());
}
$packageNameInput = wo_secure($_POST["package_name"] ?? null);
$productId     = wo_secure($_POST["product_id"] ?? null);
$purchaseToken = wo_secure($_POST["purchase_token"] ?? null);
$userId        = $wo['user']['user_id'] ?? null;
$type          = wo_secure($_POST["type"] ?? 'diamond'); 
$purchaseTokenHash = substr(hash('sha256', (string)$purchaseToken), 0, 16);

if (!$packageName || !$productId || !$purchaseToken || !$userId) {
    Wo_ReportPlayStorePurchaseError('Missing or invalid parameters.', [
        'package_name' => $packageName,
        'package_name_input' => $packageNameInput,
        'product_id' => $productId,
        'user_id' => $userId,
        'type' => $type,
        'purchase_token_hash' => $purchaseTokenHash
    ], 'playstore-purchase-missing:' . $productId);
    exit(json_encode(["success" => false, "message" => "Missing or invalid parameters"]));
}

if (function_exists('verifyPlayStorePaymentDetailed')) {
    $verification = verifyPlayStorePaymentDetailed($packageName, $productId, $purchaseToken);
} else {
    $verification = ['success' => verifyPlayStorePayment($packageName, $productId, $purchaseToken), 'message' => 'Play Store payment verification failed.'];
}

if (empty($verification['success'])) {
    Wo_ReportPlayStorePurchaseError((string)($verification['message'] ?? 'Invalid or expired purchase token'), [
        'package_name' => $packageName,
        'product_id' => $productId,
        'purchase_token_suffix' => substr((string)$purchaseToken, -10),
        'purchase_token_hash' => $purchaseTokenHash,
        'user_id' => $userId,
        'type' => $type,
        'verification' => $verification
    ], 'playstore-purchase-verify:' . $productId . ':' . substr(hash('sha256', (string)$purchaseToken), 0, 16));
    exit(json_encode(["success" => false, "message" => "Invalid or expired purchase token"]));
}

if (!function_exists('wo_ensure_playstore_purchase_token_unique_schema')
    || !wo_ensure_playstore_purchase_token_unique_schema()) {
    Wo_ReportPlayStorePurchaseError('Purchase-token uniqueness protection is unavailable.', [
        'product_id' => $productId,
        'user_id' => $userId,
        'purchase_token_hash' => $purchaseTokenHash
    ], 'playstore-purchase-unique-schema');
    exit(json_encode(["success" => false, "message" => "Purchase verification is temporarily unavailable"]));
}

$checkQuery = "SELECT id FROM wo_playstore_purchases WHERE purchase_token = ?";
$stmtCheck  = mysqli_prepare($sqlConnect, $checkQuery);
mysqli_stmt_bind_param($stmtCheck, "s", $purchaseToken);
mysqli_stmt_execute($stmtCheck);
mysqli_stmt_store_result($stmtCheck);

if (mysqli_stmt_num_rows($stmtCheck) > 0) {
    mysqli_stmt_close($stmtCheck);
    Wo_ReportPlayStorePurchaseError('Duplicate purchase detected.', [
        'package_name' => $packageName,
        'product_id' => $productId,
        'purchase_token_suffix' => substr((string)$purchaseToken, -10),
        'purchase_token_hash' => $purchaseTokenHash,
        'user_id' => $userId,
        'type' => $type
    ], 'playstore-purchase-duplicate:' . substr(hash('sha256', (string)$purchaseToken), 0, 16));
    exit(json_encode(["success" => false, "message" => "Duplicate purchase detected"]));
}
mysqli_stmt_close($stmtCheck);

$query = "SELECT diamonds, bonus FROM wo_daimond_pack WHERE sku = ?";
$stmt  = mysqli_prepare($sqlConnect, $query);
mysqli_stmt_bind_param($stmt, "s", $productId);
mysqli_stmt_execute($stmt);
mysqli_stmt_bind_result($stmt, $diamonds, $bonus);

if (!mysqli_stmt_fetch($stmt)) {
    mysqli_stmt_close($stmt);
    Wo_ReportPlayStorePurchaseError('Invalid product ID during Play Store purchase verification.', [
        'package_name' => $packageName,
        'product_id' => $productId,
        'user_id' => $userId,
        'type' => $type,
        'purchase_token_hash' => $purchaseTokenHash
    ], 'playstore-purchase-product:' . $productId);
    exit(json_encode(["success" => false, "message" => "Invalid product ID"]));
}
mysqli_stmt_close($stmt);

$amount = $diamonds + $bonus;

$time = time();
$purchaseId = 0;
$consumeMessage = '';
$manualRecoverySchemaReady = Wo_EnsurePlayStoreManualRecoverySchema();

try {
    if (!mysqli_begin_transaction($sqlConnect)) {
        throw new RuntimeException('Unable to begin purchase transaction.');
    }

    // Some purchases were Google-verified during the 2026-10-01 outage but could
    // not be recorded because the uniqueness helper was unavailable. Those users
    // were credited once by the audited recovery job. When the original token is
    // retried, record and consume it without crediting the wallet a second time.
    $manualRecoveryId = 0;
    if ($manualRecoverySchemaReady) {
        $recoveryQuery = "SELECT id FROM wo_playstore_manual_recoveries
                          WHERE user_id = ? AND product_id = ? AND matched_purchase_id IS NULL
                          ORDER BY id ASC LIMIT 1 FOR UPDATE";
        $stmtRecovery = mysqli_prepare($sqlConnect, $recoveryQuery);
        if (!$stmtRecovery) {
            throw new RuntimeException('Unable to prepare manual recovery lookup.');
        }
        mysqli_stmt_bind_param($stmtRecovery, "is", $userId, $productId);
        mysqli_stmt_execute($stmtRecovery);
        mysqli_stmt_bind_result($stmtRecovery, $manualRecoveryId);
        if (!mysqli_stmt_fetch($stmtRecovery)) {
            $manualRecoveryId = 0;
        }
        mysqli_stmt_close($stmtRecovery);
    }

    $insertQuery = "INSERT INTO wo_playstore_purchases
                    (user_id, type, amount, product_id, purchase_token, status, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, 'verified', ?, ?)";
    $stmtInsert = mysqli_prepare($sqlConnect, $insertQuery);
    if (!$stmtInsert) {
        throw new RuntimeException('Unable to prepare purchase insert.');
    }
    mysqli_stmt_bind_param($stmtInsert, "isissii", $userId, $type, $amount, $productId, $purchaseToken, $time, $time);
    $insertSuccess = mysqli_stmt_execute($stmtInsert);
    $insertErrno = mysqli_stmt_errno($stmtInsert);
    $insertError = mysqli_stmt_error($stmtInsert);
    $purchaseId = $insertSuccess ? (int)mysqli_insert_id($sqlConnect) : 0;
    mysqli_stmt_close($stmtInsert);

    if (!$insertSuccess) {
        if ($insertErrno === 1062) {
            mysqli_rollback($sqlConnect);
            exit(json_encode(["success" => false, "message" => "Duplicate purchase detected"]));
        }
        throw new RuntimeException('Failed to insert purchase record: ' . $insertError);
    }

    if ($manualRecoveryId > 0) {
        $matchQuery = "UPDATE wo_playstore_manual_recoveries
                       SET matched_purchase_id = ?, matched_at = ?
                       WHERE id = ? AND matched_purchase_id IS NULL";
        $stmtMatch = mysqli_prepare($sqlConnect, $matchQuery);
        if (!$stmtMatch) {
            throw new RuntimeException('Unable to prepare manual recovery match.');
        }
        mysqli_stmt_bind_param($stmtMatch, "iii", $purchaseId, $time, $manualRecoveryId);
        $matchSuccess = mysqli_stmt_execute($stmtMatch);
        $matchedRows = mysqli_stmt_affected_rows($stmtMatch);
        $matchError = mysqli_stmt_error($stmtMatch);
        mysqli_stmt_close($stmtMatch);
        if (!$matchSuccess || $matchedRows !== 1) {
            throw new RuntimeException('Failed to match manual recovery: ' . $matchError);
        }
    } else {
        $column = ($type === 'diamond') ? 'diamond' : 'points';
        $updateQuery = "UPDATE Wo_Users SET $column = $column + ? WHERE user_id = ?";
        $stmtUpdate = mysqli_prepare($sqlConnect, $updateQuery);
        if (!$stmtUpdate) {
            throw new RuntimeException('Unable to prepare balance update.');
        }
        mysqli_stmt_bind_param($stmtUpdate, "ii", $amount, $userId);
        $executeSuccess = mysqli_stmt_execute($stmtUpdate);
        $updateError = mysqli_stmt_error($stmtUpdate);
        $updatedRows = mysqli_stmt_affected_rows($stmtUpdate);
        mysqli_stmt_close($stmtUpdate);
        if (!$executeSuccess) {
            throw new RuntimeException('Failed to update purchase balance: ' . $updateError);
        }
        if ($updatedRows !== 1) {
            throw new RuntimeException('Purchase user was not updated.');
        }
    }

    if (!mysqli_commit($sqlConnect)) {
        throw new RuntimeException('Unable to commit purchase transaction.');
    }
} catch (Throwable $exception) {
    mysqli_rollback($sqlConnect);
    if ((int)$exception->getCode() === 1062) {
        exit(json_encode(["success" => false, "message" => "Duplicate purchase detected"]));
    }
    Wo_ReportPlayStorePurchaseError('Failed to insert verified purchase record.', [
        'package_name' => $packageName,
        'product_id' => $productId,
        'user_id' => $userId,
        'type' => $type,
        'amount' => $amount,
        'purchase_token_hash' => $purchaseTokenHash,
        'sql_error' => $exception->getMessage()
    ], 'playstore-purchase-insert:' . $productId);
    exit(json_encode(["success" => false, "message" => "Purchase could not be completed"]));
}

cache($userId, 'users', 'delete');
if (function_exists('markPurchaseConsumed')) {
    $consumeMessage = (string)markPurchaseConsumed($packageName, $productId, $purchaseToken, $purchaseId);
}

exit(json_encode([
    "success" => true,
    "message" => "Purchase verified and updated successfully",
    "manual_recovery_matched" => $manualRecoveryId > 0,
    "consume_message" => $consumeMessage
]));

?>
