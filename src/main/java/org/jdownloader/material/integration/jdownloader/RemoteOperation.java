package org.jdownloader.material.integration.jdownloader;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Typed subset of the published local Remote API, including every stock page
 * category. Parameter counts follow the documented positional order.
 */
public enum RemoteOperation {
    DEVICE_PING(RemoteCategory.DEVICE, "/device/ping", 0),
    DEVICE_DIRECT_CONNECTIONS(RemoteCategory.DEVICE, "/device/getDirectConnectionInfos", 0),
    SYSTEM_INFO(RemoteCategory.DEVICE, "/system/getSystemInfos", 0),

    ACCOUNTS_LIST(RemoteCategory.ACCOUNTS, "/accountsV2/listAccounts", 1),
    ACCOUNTS_ADD(RemoteCategory.ACCOUNTS, "/accountsV2/addAccount", 3),
    ACCOUNTS_ENABLE(RemoteCategory.ACCOUNTS, "/accountsV2/enableAccounts", 1),
    ACCOUNTS_DISABLE(RemoteCategory.ACCOUNTS, "/accountsV2/disableAccounts", 1),
    ACCOUNTS_REMOVE(RemoteCategory.ACCOUNTS, "/accountsV2/removeAccounts", 1, true),

    EXTENSIONS_LIST(RemoteCategory.EXTENSIONS, "/extensions/list", 1),
    EXTENSIONS_INSTALL(RemoteCategory.EXTENSIONS, "/extensions/install", 1),
    EXTENSIONS_ENABLE(RemoteCategory.EXTENSIONS, "/extensions/setEnabled", 2),
    PLUGINS_LIST(RemoteCategory.PLUGINS, "/plugins/list", 1),
    PLUGINS_QUERY(RemoteCategory.PLUGINS, "/plugins/query", 1),
    PLUGINS_GET(RemoteCategory.PLUGINS, "/plugins/get", 3),
    PLUGINS_SET(RemoteCategory.PLUGINS, "/plugins/set", 4),

    CONFIG_LIST(RemoteCategory.CONFIG, "/config/list", 0),
    CONFIG_LIST_FILTERED(RemoteCategory.CONFIG, "/config/list", 5),
    CONFIG_QUERY(RemoteCategory.CONFIG, "/config/query", 1),
    CONFIG_GET(RemoteCategory.CONFIG, "/config/get", 3),
    CONFIG_GET_DEFAULT(RemoteCategory.CONFIG, "/config/getDefault", 3),
    CONFIG_LIST_ENUM(RemoteCategory.CONFIG, "/config/listEnum", 1),
    CONFIG_SET(RemoteCategory.CONFIG, "/config/set", 4),
    CONFIG_RESET(RemoteCategory.CONFIG, "/config/reset", 3, true),

    CAPTCHA_LIST(RemoteCategory.CAPTCHA, "/captcha/list", 0),
    CAPTCHA_GET(RemoteCategory.CAPTCHA, "/captcha/getCaptchaJob", 1),
    CAPTCHA_ANSWER(RemoteCategory.CAPTCHA, "/captcha/solve", 3),
    DIALOGS_LIST(RemoteCategory.DIALOGS, "/dialogs/list", 0),
    DIALOGS_GET(RemoteCategory.DIALOGS, "/dialogs/get", 3),
    DIALOGS_ANSWER(RemoteCategory.DIALOGS, "/dialogs/answer", 2),

    DOWNLOADS_QUERY_PACKAGES(RemoteCategory.DOWNLOADS, "/downloadsV2/queryPackages", 1),
    DOWNLOADS_QUERY_LINKS(RemoteCategory.DOWNLOADS, "/downloadsV2/queryLinks", 1),
    DOWNLOADS_START_SELECTED(RemoteCategory.DOWNLOADS, "/downloadsV2/startDownloads", 2),
    DOWNLOADS_FORCE(RemoteCategory.DOWNLOADS, "/downloadsV2/forceDownload", 2),
    DOWNLOADS_REMOVE(RemoteCategory.DOWNLOADS, "/downloadsV2/removeLinks", 2, true),
    DOWNLOAD_CONTROLLER_START(RemoteCategory.DOWNLOADS, "/downloadcontroller/start", 0),
    DOWNLOAD_CONTROLLER_STOP(RemoteCategory.DOWNLOADS, "/downloadcontroller/stop", 0),
    DOWNLOAD_CONTROLLER_PAUSE(RemoteCategory.DOWNLOADS, "/downloadcontroller/pause", 1),

    LINKGRABBER_QUERY_PACKAGES(RemoteCategory.LINKGRABBER, "/linkgrabberv2/queryPackages", 1),
    LINKGRABBER_QUERY_LINKS(RemoteCategory.LINKGRABBER, "/linkgrabberv2/queryLinks", 1),
    LINKGRABBER_ADD_LINKS(RemoteCategory.LINKGRABBER, "/linkgrabberv2/addLinks", 1),
    LINKGRABBER_ADD_CONTAINER(RemoteCategory.LINKGRABBER, "/linkgrabberv2/addContainer", 2),
    LINKGRABBER_MOVE_TO_DOWNLOADS(RemoteCategory.LINKGRABBER, "/linkgrabberv2/moveToDownloadlist", 2),
    LINKGRABBER_REMOVE(RemoteCategory.LINKGRABBER, "/linkgrabberv2/removeLinks", 2, true),
    LINKGRABBER_CLEAR(RemoteCategory.LINKGRABBER, "/linkgrabberv2/clearList", 0, true),

    EXTRACTION_QUEUE(RemoteCategory.EXTRACTION, "/extraction/getQueue", 0),
    EXTRACTION_INFO(RemoteCategory.EXTRACTION, "/extraction/getArchiveInfo", 2),
    EXTRACTION_GET_SETTINGS(RemoteCategory.EXTRACTION, "/extraction/getArchiveSettings", 1),
    EXTRACTION_SET_SETTINGS(RemoteCategory.EXTRACTION, "/extraction/setArchiveSettings", 2),
    EXTRACTION_START(RemoteCategory.EXTRACTION, "/extraction/startExtractionNow", 2),

    RECONNECT_NOW(RemoteCategory.RECONNECT, "/reconnect/doReconnect", 0, true),
    LOGS_LIST(RemoteCategory.LOGS, "/log/getAvailableLogs", 0),
    LOGS_SEND(RemoteCategory.LOGS, "/log/sendLogFile", 1),
    UPDATE_AVAILABLE(RemoteCategory.UPDATE, "/update/isUpdateAvailable", 0),
    UPDATE_CHECK(RemoteCategory.UPDATE, "/update/runUpdateCheck", 0),
    UPDATE_RESTART(RemoteCategory.UPDATE, "/update/restartAndUpdate", 0, true),

    SYSTEM_EXIT_JDOWNLOADER(RemoteCategory.SYSTEM, "/system/exitJD", 0, true),
    SYSTEM_SHUTDOWN(RemoteCategory.SYSTEM, "/system/shutdownOS", 1, true),
    SYSTEM_STANDBY(RemoteCategory.SYSTEM, "/system/standbyOS", 0, true),
    SYSTEM_HIBERNATE(RemoteCategory.SYSTEM, "/system/hibernateOS", 0, true),
    SYSTEM_RESTART_JDOWNLOADER(RemoteCategory.SYSTEM, "/system/restartJD", 0, true),

    SESSION_HANDSHAKE(RemoteCategory.SESSION, "/session/handshake", 2);

    private final RemoteCategory category;
    private final RemoteEndpoint endpoint;
    private final HttpMethod method;
    private final int parameterCount;
    private final boolean confirmationRequired;

    RemoteOperation(RemoteCategory category, String path, int parameterCount) {
        this(category, path, parameterCount, false);
    }

    RemoteOperation(RemoteCategory category, String path, int parameterCount, boolean confirmationRequired) {
        this.category = category;
        this.endpoint = RemoteEndpoint.of(path);
        this.method = HttpMethod.GET;
        this.parameterCount = parameterCount;
        this.confirmationRequired = confirmationRequired;
    }

    public RemoteCategory category() { return category; }
    public RemoteEndpoint endpoint() { return endpoint; }
    public HttpMethod method() { return method; }
    public int parameterCount() { return parameterCount; }
    public boolean confirmationRequired() { return confirmationRequired; }

    public static List<RemoteOperation> inCategory(RemoteCategory category) {
        return Arrays.stream(values()).filter(operation -> operation.category == category).toList();
    }

    public static List<RemoteOperation> forEndpoint(RemoteEndpoint endpoint) {
        return Arrays.stream(values()).filter(operation -> operation.endpoint.equals(endpoint)).toList();
    }

    public static Optional<RemoteOperation> exact(RemoteEndpoint endpoint, int parameterCount) {
        return Arrays.stream(values()).filter(operation -> operation.endpoint.equals(endpoint)
                && operation.parameterCount == parameterCount).findFirst();
    }
}
