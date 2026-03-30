package turbo.pos.boost.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rewards")
public class RewardModeProperties {

	/**
	 * {@code redis} — Redisson + Redis cache + ghi ledger MySQL (mặc định).<br>
	 * {@code mysql-only} — chỉ MySQL (profile {@code mysql-only} tắt Redis/Redisson).
	 */
	private String mode = "redis";

	public String getMode() {
		return mode;
	}

	public void setMode(String mode) {
		this.mode = mode;
	}

	public boolean isMysqlOnly() {
		return "mysql-only".equalsIgnoreCase(mode);
	}
}
