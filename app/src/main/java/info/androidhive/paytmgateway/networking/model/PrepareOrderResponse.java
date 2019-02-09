package info.androidhive.paytmgateway.networking.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class PrepareOrderResponse {
    @SerializedName("order_id")
    public String orderId;

    @SerializedName("amount")
    public String amount;

    @SerializedName("order_items")
    public List<OrderItem> orderItems;

    public String status;
}
