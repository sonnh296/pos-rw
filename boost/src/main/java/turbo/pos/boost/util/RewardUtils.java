package turbo.pos.boost.util;

public final class RewardUtils {

    private RewardUtils() {}

    /**
     * Tính điểm thưởng dựa trên số tiền giao dịch.
     * Tỉ lệ hiện tại: 10 điểm cho mỗi đơn vị tiền tệ.
     */
    public static long calculatePoints(double amount) {
        return Math.round(amount * 10);
    }
}
