package github.nighter.smartspawner.utils;

public enum SupportedLanguage {
    ENGLISH("en_US", "English", "messages/en_US.yml"),
    VIETNAMESE("vi_VN", "Tiếng Việt", "messages/vi_VN.yml"),
    CHINESE("zh_CN", "简体中文", "messages/zh_CN.yml");

    private final String code;
    private final String displayName;
    private final String resourcePath;

    SupportedLanguage(String code, String displayName, String resourcePath) {
        this.code = code;
        this.displayName = displayName;
        this.resourcePath = resourcePath;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getResourcePath() {
        return resourcePath;
    }

    public static SupportedLanguage fromCode(String code) {
        for (SupportedLanguage lang : values()) {
            if (lang.getCode().equalsIgnoreCase(code)) {
                return lang;
            }
        }
        return ENGLISH; // Default fallback
    }
}