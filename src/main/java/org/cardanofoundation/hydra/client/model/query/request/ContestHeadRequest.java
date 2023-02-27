package org.cardanofoundation.hydra.client.model.query.request;

import lombok.val;
import org.cardanofoundation.hydra.client.model.Request;
import org.cardanofoundation.hydra.client.model.Tag;
import org.stringtemplate.v4.ST;

public class ContestHeadRequest extends Request {

    public ContestHeadRequest() {
        super(Tag.Contest);
    }

    @Override
    public String getRequestBody() {
        val template = new ST("{ \"tag\": \"<tag>\" }");
        template.add("tag", tag);

        return template.render();
    }

    @Override
    public String toString() {
        return "ContestHead{" +
                "tag=" + tag +
                '}';
    }

}
