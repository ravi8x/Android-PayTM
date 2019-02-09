package info.androidhive.paytmgateway.ui.paytm;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.paytm.pgsdk.PaytmOrder;
import com.paytm.pgsdk.PaytmPGService;
import com.paytm.pgsdk.PaytmPaymentTransactionCallback;
import com.wang.avi.AVLoadingIndicatorView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import butterknife.BindView;
import butterknife.ButterKnife;
import info.androidhive.paytmgateway.R;
import info.androidhive.paytmgateway.app.Constants;
import info.androidhive.paytmgateway.db.AppDatabase;
import info.androidhive.paytmgateway.db.model.CartItem;
import info.androidhive.paytmgateway.db.model.User;
import info.androidhive.paytmgateway.networking.model.AppConfig;
import info.androidhive.paytmgateway.networking.model.ChecksumResponse;
import info.androidhive.paytmgateway.networking.model.OrderItem;
import info.androidhive.paytmgateway.networking.model.OrderResponse;
import info.androidhive.paytmgateway.networking.model.PrepareOrderRequest;
import info.androidhive.paytmgateway.networking.model.PrepareOrderResponse;
import info.androidhive.paytmgateway.ui.BaseActivity;
import io.realm.Realm;
import io.realm.RealmResults;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import timber.log.Timber;

public class PayTMActivity extends BaseActivity {
    @BindView(R.id.lbl_status)
    TextView lblStatus;

    @BindView(R.id.loader)
    AVLoadingIndicatorView loader;

    @BindView(R.id.layout_order_placed)
    LinearLayout layoutOrderPlaced;

    private Realm realm;
    private RealmResults<CartItem> cartItems;
    private AppConfig appConfig;
    // private String orderId;
    private User user;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pay_tm);
        ButterKnife.bind(this);
        setToolbar();
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        init();
    }

    private void init() {
        realm = Realm.getDefaultInstance();
        realm.where(CartItem.class).findAllAsync()
                .addChangeListener(cartItems -> {

                });

        /*realm.where(AppConfig.class).findFirst().addChangeListener(new RealmChangeListener<AppConfig>() {
            @Override
            public void onChange(AppConfig appConfig) {
                getChecksum();
            }
        });*/

        user = AppDatabase.getUser();
        appConfig = realm.where(AppConfig.class).findFirst();

        prepareOrder();
    }

    private void setStatus(int message) {
        lblStatus.setText(message);
    }

    private void prepareOrder() {
        setStatus(R.string.msg_preparing_order);

        List<CartItem> cartItems = realm.where(CartItem.class).findAll();
        PrepareOrderRequest request = new PrepareOrderRequest();
        // request.orderId = generateOrderId();
        List<OrderItem> orderItems = new ArrayList<>();
        for (CartItem cartItem : cartItems) {
            OrderItem orderItem = new OrderItem();
            orderItem.productId = cartItem.product.id;
            orderItem.quantity = cartItem.quantity;

            orderItems.add(orderItem);
        }

        request.orderItems = orderItems;

        getApi().prepareOrder(request).enqueue(new Callback<PrepareOrderResponse>() {
            @Override
            public void onResponse(Call<PrepareOrderResponse> call, Response<PrepareOrderResponse> response) {
                if (!response.isSuccessful()) {
                    Timber.e("prepareOrder not successful!");
                    return;
                }

                Timber.e("prepareOrder response: %s", response.body());
                getChecksum(response.body());
            }

            @Override
            public void onFailure(Call<PrepareOrderResponse> call, Throwable t) {
                Timber.e("prepareOrder onFailure %s", t.getMessage());
            }
        });
    }

    void getChecksum(PrepareOrderResponse response) {
        setStatus(R.string.msg_fetching_checksum);

        if (appConfig == null) {
            // TODO config is null
            // handle this error
            Timber.e("App config is null! Can't place the order");
            return;
        }

        // final Map<String, String> paramMap = new HashMap<String, String>();

        // these are mandatory parameters
        // https://pguat.paytm.com/paytmchecksum/paytmCallback.jsp

        // String orderId = generateOrderId();
        //String orderId = "ORD7829385262";

        /*paramMap.put("CALLBACK_URL", "https://securegw-stage.paytm.in/theia/paytmCallback?ORDER_ID="+orderId);
        paramMap.put("CHANNEL_ID", "WAP");
        paramMap.put("CUST_ID", "CUST001");
        paramMap.put("INDUSTRY_TYPE_ID", "Retail");
        paramMap.put("MID", "Androi78288874845632");
        paramMap.put("TXN_AMOUNT", "1.00");
        paramMap.put("WEBSITE", "APPSTAGING");
        paramMap.put("ORDER_ID", orderId);
        paramMap.put("MOBILE_NO", "8179679983");
        paramMap.put("EMAIL", "ravi@droid5.com");*/

        /*paramMap.put("CALLBACK_URL", "https://securegw-stage.paytm.in/theia/paytmCallback?ORDER_ID=" + orderId);
        paramMap.put("CHANNEL_ID", appConfig.getChannel());
        paramMap.put("CUST_ID", "CUSTOMER909090");
        paramMap.put("INDUSTRY_TYPE_ID", appConfig.getIndustryType());
        paramMap.put("MID", appConfig.getMerchantId());
        paramMap.put("TXN_AMOUNT", "1.00");
        paramMap.put("WEBSITE", appConfig.getWebsite());
        paramMap.put("ORDER_ID", orderId);
        //paramMap.put("MOBILE_NO", "7777777777");*/

        Map<String, String> paramMap = getParams(response);

        Timber.e("Params: %s", paramMap);

        getApi().getCheckSum(paramMap).enqueue(new Callback<ChecksumResponse>() {
            @Override
            public void onResponse(Call<ChecksumResponse> call, Response<ChecksumResponse> response) {
                if (!response.isSuccessful()) {
                    // TODO - handle error
                    return;
                }

                Timber.e("Checksum: " + response.body().checksum);

                paramMap.put("CHECKSUMHASH", response.body().checksum);
                placeOrder(paramMap);
            }

            @Override
            public void onFailure(Call<ChecksumResponse> call, Throwable t) {
                // TODO - handle error
                Timber.e("checksum onFailure %s", t.getMessage());
            }
        });
    }

    public Map<String, String> getParams(PrepareOrderResponse response) {
        Map<String, String> paramMap = new HashMap<String, String>();
        paramMap.put("CALLBACK_URL", "https://securegw-stage.paytm.in/theia/paytmCallback?ORDER_ID=" + response.orderId);
        paramMap.put("CHANNEL_ID", appConfig.getChannel());
        paramMap.put("CUST_ID", "CUSTOMER_" + user.id);
        paramMap.put("INDUSTRY_TYPE_ID", appConfig.getIndustryType());
        paramMap.put("MID", appConfig.getMerchantId());
        paramMap.put("WEBSITE", appConfig.getWebsite());
        paramMap.put("ORDER_ID", response.orderId);
        paramMap.put("TXN_AMOUNT", response.amount);
        return paramMap;
    }

    public void placeOrder(Map<String, String> params) {
        setStatus(R.string.msg_redirecting_to_paytm);


        // TODO - decide on staging or prod
        // PaytmPGService.getProductionService()
        PaytmPGService pgService = PaytmPGService.getStagingService();
        PaytmOrder Order = new PaytmOrder(params);

		/*PaytmMerchant Merchant = new PaytmMerchant(
				"https://pguat.paytm.com/paytmchecksum/paytmCheckSumGenerator.jsp",
				"https://pguat.paytm.com/paytmchecksum/paytmCheckSumVerify.jsp");*/

        pgService.initialize(Order, null);

        pgService.startPaymentTransaction(this, true, true,
                new PaytmPaymentTransactionCallback() {
                    @Override
                    public void someUIErrorOccurred(String inErrorMessage) {
                        Timber.e("someUIErrorOccurred: %s", inErrorMessage);
                        // Some UI Error Occurred in Payment Gateway Activity.
                        // // This may be due to initialization of views in
                        // Payment Gateway Activity or may be due to //
                        // initialization of webview. // Error Message details
                        // the error occurred.
                    }

					/*@Override
					public void onTransactionSuccess(Bundle inResponse) {
						// After successful transaction this method gets called.
						// // Response bundle contains the merchant response
						// parameters.
						Log.d("LOG", "Payment Transaction is successful " + inResponse);
						Toast.makeText(getApplicationContext(), "Payment Transaction is successful ", Toast.LENGTH_LONG).show();
					}

					@Override
					public void onTransactionFailure(String inErrorMessage,
							Bundle inResponse) {
						// This method gets called if transaction failed. //
						// Here in this case transaction is completed, but with
						// a failure. // Error Message describes the reason for
						// failure. // Response bundle contains the merchant
						// response parameters.
						Log.d("LOG", "Payment Transaction Failed " + inErrorMessage);
						Toast.makeText(getBaseContext(), "Payment Transaction Failed ", Toast.LENGTH_LONG).show();
					}*/

                    @Override
                    public void onTransactionResponse(Bundle inResponse) {
                        Timber.e("onTransactionResponse: %s", inResponse.toString());

                        String checkSum = inResponse.getString("CHECKSUMHASH");
                        String orderId = inResponse.getString("ORDERID");
                        Timber.e("onTransactionResponse CHECKSUMHASH: %s", checkSum);
                        // Toast.makeText(getApplicationContext(), "Payment Transaction response " + inResponse.toString(), Toast.LENGTH_LONG).show();
                        // verifyCheckSum(checkSum);
                        verifyTransactionStatus(orderId);
                    }

                    @Override
                    public void networkNotAvailable() { // If network is not
                        Timber.e("networkNotAvailable");
                        // available, then this
                        // method gets called.
                    }

                    @Override
                    public void clientAuthenticationFailed(String inErrorMessage) {
                        Timber.e("clientAuthenticationFailed: %s", inErrorMessage);
                        // This method gets called if client authentication
                        // failed. // Failure may be due to following reasons //
                        // 1. Server error or downtime. // 2. Server unable to
                        // generate checksum or checksum response is not in
                        // proper format. // 3. Server failed to authenticate
                        // that client. That is value of payt_STATUS is 2. //
                        // Error Message describes the reason for failure.
                    }

                    @Override
                    public void onErrorLoadingWebPage(int iniErrorCode,
                                                      String inErrorMessage, String inFailingUrl) {
                        Timber.e("onErrorLoadingWebPage: %d | %s | %s", iniErrorCode, inErrorMessage, inFailingUrl);

                    }

                    // had to be added: NOTE
                    @Override
                    public void onBackPressedCancelTransaction() {
                        Toast.makeText(PayTMActivity.this, "Back pressed. Transaction cancelled", Toast.LENGTH_LONG).show();
                        finish();
                    }

                    @Override
                    public void onTransactionCancel(String inErrorMessage, Bundle inResponse) {
                        Timber.e("onTransactionCancel: %s | %s", inErrorMessage, inResponse);
                    }

                });
    }

    private void verifyTransactionStatus(String orderId) {
        setStatus(R.string.msg_verifying_status);
        getApi().checkTransactionStatus(orderId).enqueue(new Callback<OrderResponse>() {
            @Override
            public void onResponse(Call<OrderResponse> call, Response<OrderResponse> response) {
                if (!response.isSuccessful()) {
                    // TODO - handle response
                    return;
                }

                Timber.e("Order: " + response.body().status);

                if (response.body().status.equalsIgnoreCase(Constants.ORDER_STATUS_COMPLETED)) {
                    setStatus(R.string.msg_order_placed_successfully);
                    loader.setVisibility(View.GONE);
                    lblStatus.setVisibility(View.GONE);
                    layoutOrderPlaced.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onFailure(Call<OrderResponse> call, Throwable t) {
                // TODO - handle error
                Timber.e("checksum onFailure %s", t.getMessage());
            }
        });
    }

    /*private void verifyCheckSum(String checkSum) {
        Map<String, String> paramMap = getParams();
        paramMap.put("check_sum", checkSum);

        Timber.e("Params: %s", paramMap);

        getApi().verifyChecksum(paramMap).enqueue(new Callback<Boolean>() {
            @Override
            public void onResponse(Call<Boolean> call, Response<Boolean> response) {
                if (!response.isSuccessful()) {
                    Timber.e("request failed!");
                    return;
                }
                Timber.e("onResponse: %s", response.body());
            }

            @Override
            public void onFailure(Call<Boolean> call, Throwable t) {
                Timber.e("onFailure %s", t.getMessage());
            }
        });
    }*/

    /*private String generateOrderId() {
        return UUID.randomUUID().toString();
    }*/

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (realm != null) {
            realm.removeAllChangeListeners();
            realm.close();
        }
    }
}
