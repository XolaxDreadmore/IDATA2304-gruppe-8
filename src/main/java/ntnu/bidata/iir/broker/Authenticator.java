package ntnu.bidata.iir.broker;

import java.util.Map;
import ntnu.bidata.iir.common.Role;

/**
 * Checks the password a panel sends at login.
 * Simplification: one fixed password per role, sent in plain text.
 * (Real systems would use per-user accounts, hashing and TLS; see "Possible
 * extras".)
 */
final class Authenticator {
    private static final Map<Role, String> PASSWORDS = Map.of(
            Role.ADMIN, "admin123",
            Role.OWNER, "owner123",
            Role.TECHNICIAN, "tech123",
            Role.GUEST, "guest123");

    /**
     * The code that arms and disarms the house. Simplification: one fixed code,
     * sent in plain text.
     */
    private static final String HOUSE_CODE = "1234";

    private Authenticator() {
    }

    static boolean isValidHouseCode(String code) {
        return HOUSE_CODE.equals(code);
    }

    static boolean isValid(Role role, String password) {
        return PASSWORDS.get(role).equals(password);
    }
}
