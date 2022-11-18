package e.s.miniweb.core;

/**
 * Allows the app to
 * read the user's current page, and change page if required
 */
@SuppressWarnings("unused")
public interface RouterControls {

    /**
     * Get the page the user is currently viewing
     * @return url of the current page (loaded from history)
     */
    String getCurrentUrl();

    /**
     * Force the app to switch to another page.
     * The user will lose any work there were in the middle of.
     * @param url new page url
     * @param clearHistory if true, the user will not be able to go 'back' out of the new page (including Home screen)
     */
    void setCurrentUrl(String url, boolean clearHistory);

    /**
     * Does a 'hot reload' of the page the user is currently on.
     * This will NOT cause the controller action to be re-run,
     * but will re-run templating (so permission block visibility can change)
     *
     * If a hot reload is not possible, the page will NOT refresh.
     *
     * Returns 'true' if a hot reload was possible.
     * Returns 'false' if the page did not refresh
     */
    boolean hotReloadCurrentPage();

    /**
     * Does a 'cold reload' of the page the user is currently on.
     * This WILL cause the controller action to be re-run,
     * which may cause actions to be repeated.
     */
    void coldReloadCurrentPage();
}
