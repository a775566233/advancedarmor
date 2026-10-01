package org.wgx.advancedarmor;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

final class AdvancedarmorLog {
    private static final Logger LOGGER = LogUtils.getLogger();
    static void warn(String message, Object first, Object second) { LOGGER.warn(message, first, second); }
}
