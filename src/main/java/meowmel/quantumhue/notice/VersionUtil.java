package meowmel.quantumhue.notice;

/**
 * 极简版本号比较：只比较数字段，忽略 {@code -pre} 之类后缀。
 *
 * <p>更新日志只拉取“不低于本地版本”的条目，因此需要稳定的 {@code >=} 判断；
 * 这里不做完整 SemVer，只按点分数字比较，足够处理 1.2 / 1.10 / 2.0.0pre 这类版本。
 */
public final class VersionUtil {

    private VersionUtil() {}

    /** 返回负数、0、正数，语义同 {@link Comparable}。 */
    public static int compare(String left, String right) {
        int[] a = parts(left);
        int[] b = parts(right);
        int length = Math.max(a.length, b.length);
        for (int i = 0; i < length; i++) {
            int va = i < a.length ? a[i] : 0;
            int vb = i < b.length ? b[i] : 0;
            if (va != vb) {
                return Integer.compare(va, vb);
            }
        }
        return 0;
    }

    public static boolean atLeast(String version, String base) {
        return compare(version, base) >= 0;
    }

    /** 是否像 1.2 / 1.2.3 / 2.0.0pre 这样的版本号。 */
    public static boolean looksLikeVersion(String value) {
        if (value == null) {
            return false;
        }
        String text = value.trim();
        if (text.isEmpty() || !Character.isDigit(text.charAt(0))) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!Character.isDigit(c) && c != '.' && c != '-' && !Character.isLetter(c)) {
                return false;
            }
        }
        return true;
    }

    /** 从文件名推断版本号（去掉 .json）；不像版本号时返回空串。 */
    public static String versionFromName(String fileName) {
        if (fileName == null) {
            return "";
        }
        String base = fileName;
        if (base.toLowerCase(java.util.Locale.ROOT).endsWith(".json")) {
            base = base.substring(0, base.length() - 5);
        }
        return looksLikeVersion(base) ? base.trim() : "";
    }

    private static int[] parts(String version) {
        if (version == null) {
            return new int[0];
        }
        String cleaned = version.trim();
        int end = 0;
        while (end < cleaned.length()) {
            char c = cleaned.charAt(end);
            if (Character.isDigit(c) || c == '.') {
                end++;
            } else {
                break;
            }
        }
        if (end == 0) {
            return new int[0];
        }
        String[] pieces = cleaned.substring(0, end).split("\\.");
        int[] result = new int[pieces.length];
        for (int i = 0; i < pieces.length; i++) {
            try {
                result[i] = pieces[i].isEmpty() ? 0 : Integer.parseInt(pieces[i]);
            } catch (NumberFormatException ignored) {
                result[i] = 0;
            }
        }
        return result;
    }
}
