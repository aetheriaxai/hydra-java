package org.cardanofoundation.hydra.client.model.query.response;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.val;
import org.cardanofoundation.hydra.client.model.Tag;
import org.cardanofoundation.hydra.client.util.MoreJson;

import java.time.LocalDateTime;


// Something wrong happened when trying to post a transaction on-chain. Provides information about what kind of transaction was tentatively posted, and the reason for failure.
public class PostTxOnChainFailedResponse extends Response {

    private final int seq;

    private final LocalDateTime timestamp;

    public PostTxOnChainFailedResponse(int seq, LocalDateTime timestamp) {
        super(Tag.PostTxOnChainFailed);
        this.seq = seq;
        this.timestamp = timestamp;
    }

    public static PostTxOnChainFailedResponse create(JsonNode raw) {
        val seq = raw.get("seq").asInt();
        val timestamp = MoreJson.convert(raw.get("timestamp"), LocalDateTime.class);

        return new PostTxOnChainFailedResponse(seq, timestamp);
    }

    public int getSeq() {
        return seq;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return "PostTxOnChainFailed{" +
                "seq=" + seq +
                ", timestamp=" + timestamp +
                ", tag=" + tag +
                '}';
    }

}
