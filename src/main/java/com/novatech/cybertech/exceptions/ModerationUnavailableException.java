package com.novatech.cybertech.exceptions;

/**
 * The comment moderation service could not give a verdict (unreachable, timeout, non-2xx status,
 * malformed answer). Distinct from {@link CommentPostNotAllowedException}: the text is not at
 * fault, the client should retry later. The comment is never published unmoderated.
 */
public class ModerationUnavailableException extends RuntimeException {
    public ModerationUnavailableException(String message) {
        super(message);
    }

    public ModerationUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
