package com.accretivetg.ml.esplugin;

import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.rest.RestStatus;

public class BadModelResponseException extends ElasticsearchStatusException {
    public BadModelResponseException(String msg, RestStatus status, Object... args) {
        super(msg, status, args);
    }
}
