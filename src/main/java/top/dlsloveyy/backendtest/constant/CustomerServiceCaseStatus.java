package top.dlsloveyy.backendtest.constant;

public final class CustomerServiceCaseStatus {
    private CustomerServiceCaseStatus() {}

    public static final int PENDING_ASSIGN = 0;
    public static final int IN_PROGRESS = 1;
    public static final int WAITING_EVIDENCE = 2;
    public static final int RESOLVED = 3;
    public static final int CLOSED = 4;
}
