package top.dlsloveyy.backendtest.constant;

public final class OrderStatus {
    private OrderStatus() {}

    public static final int PENDING_PAYMENT = 0;
    public static final int PAID_PENDING_SHIPMENT = 1;
    public static final int SHIPPED_PENDING_RECEIPT = 2;
    public static final int COMPLETED = 3;
    public static final int CLOSED = 4;
    public static final int REFUNDED = 5;
    public static final int REFUND_REQUESTED = 6;
}
