package com.cloudzone.shopbackend.media;

/**
 * Raised when an object cannot be stored in or removed from the backing store
 * (connection failure, IO error, illegal key). Deliberately a plain runtime
 * exception: the handler's catch-all turns it into a generic 500, because a
 * storage outage is never something the caller can fix by re-sending the request.
 */
public class StorageException extends RuntimeException {

    public StorageException(String message) {
        super(message);
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}