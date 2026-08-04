package com.shenxunchat.sonant.app;

import static com.android.billingclient.api.BillingClient.BillingResponseCode.OK;
import static com.android.billingclient.api.BillingClient.ProductType.INAPP;
import static com.android.billingclient.api.Purchase.PurchaseState.PENDING;
import static java.util.concurrent.TimeUnit.DAYS;

import android.app.Activity;
import android.content.SharedPreferences;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.QueryPurchasesParams;

import com.shenxunchat.sonant.R;

public class StartupAction implements IStartupAction {
    private static final String DONATION_PRODUCT_ID = "mumla_donation_1";
    private static final String PREF_HAS_DONATED = "hasDonated";
    private static final String PREF_LAST_DONATION_VERIFY_TIMESTAMP = "lastDonationVerifyTimestamp";
    private static final long VERIFY_INTERVAL = DAYS.toMillis(2);
    private BillingClient billingClient;

    private void showToast(Activity activity, String text) {
        activity.runOnUiThread(() -> {
            if (activity.isFinishing() || activity.isDestroyed()) {
                return;
            }
            Toast.makeText(activity, text, Toast.LENGTH_LONG).show();
        });
    }

    @Override
    public void execute(@NonNull Activity activity) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(activity);

        if (preferences.getBoolean(PREF_HAS_DONATED, false)) {
            final long lastVerification = preferences.getLong(PREF_LAST_DONATION_VERIFY_TIMESTAMP, 0);
            if ((System.currentTimeMillis() - lastVerification) > VERIFY_INTERVAL) {
                verifyPurchase(activity, preferences);
            }
            return;
        }

        billingClient = BillingClient.newBuilder(activity)
                .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
                .setListener((result, purchases) -> {
                    // This is a callback for handling a new purchase
                    if ((result.getResponseCode() != OK) || (purchases == null)) {
                        return;
                    }
                    for (Purchase purchase : purchases) {
                        if (purchase.getProducts().contains(DONATION_PRODUCT_ID)) {
                            handleAndAckPurchase(activity, purchase, () -> {
                                preferences.edit().putBoolean(PREF_HAS_DONATED, true).apply();
                                showToast(activity, activity.getString(R.string.donate_thanks_goog));
                            });
                        }
                    }
                })
                .build();

        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(@NonNull BillingResult result) {
                if (result.getResponseCode() != OK) {
                    showToast(activity, String.format("Failed to setup billing: %s (code %d)", result.getDebugMessage(), result.getResponseCode()));
                    return;
                }

                // Checking if already purchased, before possibly showing dialog
                QueryPurchasesParams params = QueryPurchasesParams.newBuilder().setProductType(INAPP).build();
                billingClient.queryPurchasesAsync(params, (queryResult, purchases) -> {
                    if (queryResult.getResponseCode() != OK) {
                        showToast(activity, String.format("Failed to query purchases: %s (code %d)", queryResult.getDebugMessage(), queryResult.getResponseCode()));
                        return;
                    }
                    for (Purchase purchase : purchases) {
                        if (purchase.getProducts().contains(DONATION_PRODUCT_ID)) {
                            handleAndAckPurchase(activity, purchase, () -> {
                                preferences.edit().putBoolean(PREF_HAS_DONATED, true).apply();
                                showToast(activity, activity.getString(R.string.donate_thanks_goog));
                            });
                            break;
                        }
                    }
                });
            }

            @Override
            public void onBillingServiceDisconnected() {
                // We just rely on our logic to be retried at app restart
            }
        });
    }

    private void verifyPurchase(Activity activity, SharedPreferences preferences) {
        BillingClient client = BillingClient.newBuilder(activity)
                .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
                .setListener((result, purchase) -> { /* NOP */ })
                .build();
        client.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(@NonNull BillingResult result) {
                if (result.getResponseCode() != OK) {
                    return;
                }
                client.queryPurchasesAsync(
                        QueryPurchasesParams.newBuilder().setProductType(INAPP).build(),
                        (queryResult, purchases) -> {
                            if (queryResult.getResponseCode() != OK) {
                                return;
                            }
                            boolean stillFound = false;
                            for (Purchase purchase : purchases) {
                                if (purchase.getProducts().contains(DONATION_PRODUCT_ID) && purchase.isAcknowledged()) {
                                    stillFound = true;
                                    break;
                                }
                            }
                            if (!stillFound) {
                                showToast(activity, "Your donation was refunded or revoked");
                                preferences.edit().putBoolean(PREF_HAS_DONATED, false).apply();
                            }
                            preferences.edit().putLong(PREF_LAST_DONATION_VERIFY_TIMESTAMP, System.currentTimeMillis()).apply();
                            client.endConnection();
                        }
                );
            }

            @Override
            public void onBillingServiceDisconnected() {
            }
        });
    }

    private void handleAndAckPurchase(Activity activity, Purchase purchase, @NonNull Runnable onAckSuccess) {
        if (purchase.isAcknowledged()) {
            onAckSuccess.run();
            return;
        }

        if (purchase.getPurchaseState() == PENDING) {
            showToast(activity, activity.getString(R.string.donate_purchase_pending_goog));
            return;
        }

        AcknowledgePurchaseParams params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.getPurchaseToken())
                .build();
        billingClient.acknowledgePurchase(params, (result) -> {
            if (result.getResponseCode() != OK) {
                showToast(activity, String.format("Failed to acknowledge purchase: %s (code %d)", result.getDebugMessage(), result.getResponseCode()));
                return;
            }
            onAckSuccess.run();
        });
    }
}
