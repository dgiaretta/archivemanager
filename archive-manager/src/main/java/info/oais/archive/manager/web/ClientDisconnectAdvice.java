package info.oais.archive.manager.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

/**
 * A client that goes away before its response has been written -- a browser
 * leaving a page that's still loading, a cancelled download, a speculative
 * preload the browser drops -- makes the next write fail with "Broken pipe"
 * ({@link AsyncRequestNotUsableException}). That's routine, but Spring's
 * {@code DefaultHandlerExceptionResolver} logs it as a warning. Handling it
 * here, before that resolver, logs it at debug level instead; nothing is
 * written, since there's no one left to write to.
 */
@ControllerAdvice
public class ClientDisconnectAdvice {

    private static final Logger log = LoggerFactory.getLogger(ClientDisconnectAdvice.class);

    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void clientWentAway(AsyncRequestNotUsableException e) {
        log.debug("The client went away before its response was written: {}", e.getMessage());
    }
}
