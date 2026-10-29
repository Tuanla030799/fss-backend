package com.fss.backend.content.page;

/** The fixed set of editable storefront pages, in the order the admin lists them, with their default titles. */
public enum SitePageKey {
    ABOUT("about", "Về chúng tôi"),
    PURCHASE_SHIPPING("purchase-shipping", "Cách mua hàng và hình thức vận chuyển");

    private final String key;
    private final String defaultTitle;

    SitePageKey(String key, String defaultTitle) {
        this.key = key;
        this.defaultTitle = defaultTitle;
    }

    public String key() {
        return key;
    }

    public String defaultTitle() {
        return defaultTitle;
    }

    /** The page for a key from the URL, or {@code null} when the key is unknown. */
    public static SitePageKey fromKey(String key) {
        for (SitePageKey page : values()) {
            if (page.key.equals(key)) return page;
        }
        return null;
    }
}
