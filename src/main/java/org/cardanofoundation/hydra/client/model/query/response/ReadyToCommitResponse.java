package org.cardanofoundation.hydra.client.model.query.response;

import lombok.Getter;
import lombok.Setter;
import org.cardanofoundation.hydra.client.model.Party;
import org.cardanofoundation.hydra.client.model.query.request.base.Tag;
import org.cardanofoundation.hydra.client.model.query.response.base.QueryResponse;

import java.util.List;

@Getter
@Setter
public class ReadyToCommitResponse extends QueryResponse {

    List<Party> parties;

    public ReadyToCommitResponse(List<Party> parties) {
        super(Tag.ReadyToCommit);
        this.parties = parties;
    }

    public List<Party> getParties() {
        return parties;
    }

    @Override
    public String toString() {
        return "ReadyToCommitResponse{" +
                "parties=" + parties +
                ", tag=" + tag +
                '}';
    }

}

// {
//    "parties": [
//        {
//            "vkey": "7abcda7de6d883e7570118c1ccc8ee2e911f2e628a41ab0685ffee15f39bba96"
//        },
//        {
//            "vkey": "b37aabd81024c043f53a069c91e51a5b52e4ea399ae17ee1fe3cb9c44db707eb"
//        },
//        {
//            "vkey": "f68e5624f885d521d2f43c3959a0de70496d5464bd3171aba8248f50d5d72b41"
//        }
//    ],
//    "tag": "ReadyToCommit"
//}