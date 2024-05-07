package mezz.jei.search;

import codechicken.nei.NEIClientConfig;

public class TokenInfo {

    public static TokenInfo parseRawToken(String token) {
        if (token.isEmpty()) {
            return null;
        }
        PrefixInfo prefixInfo = PrefixInfo.get(token.charAt(0));
        if (prefixInfo == null || prefixInfo.getMode() == NEIClientConfig.SearchMode.DISABLED) {
            return new TokenInfo(token, PrefixInfo.NO_PREFIX);
        }
        if (token.length() == 1) {
            return null;
        }
        return new TokenInfo(token.substring(1), prefixInfo);
    }

    public final String token;
    public final PrefixInfo prefixInfo;

    public TokenInfo(String token, PrefixInfo prefixInfo) {
        this.token = token;
        this.prefixInfo = prefixInfo;
    }

}
